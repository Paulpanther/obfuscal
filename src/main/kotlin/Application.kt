import db.Migrations
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import utils.LocalDateSlice
import utils.LocalTimeSlice
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val ics = ContentType.parse("text/calendar")

private val client = HttpClient(CIO)

fun main() {
  Database.connect("jdbc:sqlite:obfuscal.db")
  Migrations.init()

  embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
    .start(wait = true)
}

fun Application.module() {
  install(ContentNegotiation) {
    json()
  }

  configureRouting()
}

fun Application.configureRouting() {
  install(StatusPages) {
    exception<IllegalArgumentException> { call, cause ->
      call.respond(HttpStatusCode.BadRequest, cause.message?.let { "Error parsing given information: ${cause.message}" } ?: "Input could not be handled")
    }
  }

  routing {
    post("/obfuscate") {
      val contentType = call.request.contentType()

      val timezone = call.request.queryParameters["timezone"]?.let {
        try {
          ZoneId.of(it)
        } catch (e: Exception) {
          throw IllegalArgumentException("Given query parameter 'timezone' could not be resolved")
        }
      } ?: throw IllegalArgumentException("Missing required query parameter 'timezone'")

      val startDate = parseQueryDate(call.request.queryParameters, "timeframe-start")
      val endDate = parseQueryDate(call.request.queryParameters, "timeframe-end")
      if (startDate > endDate) throw IllegalArgumentException("'timeframe-start' must be earlier than 'timeframe-end'")
      val timeframe = LocalDateSlice(startDate, endDate)

      val sections = parseSections(call.request.queryParameters, "sections")

      val cal = when {
        call.request.isMultipart() -> {
          val streams =
            call.receiveMultipart()
              .readAllParts()
              .filterIsInstance<PartData.FileItem>()
              .map { it.streamProvider() }
          CalendarObfuscator
            .fromStreams(streams, timezone, timeframe, sections)
            .obfuscate()
        }

        contentType == ics -> {
          val stream = call.receiveStream()
          CalendarObfuscator
            .fromStream(stream, timezone, timeframe, sections)
            .obfuscate()
        }

        contentType == ContentType.parse("application/json") -> {
          val calendarUrls = call.receive<List<String>>()
          val streams = calendarUrls.map { fetchCalendar(it) }
          CalendarObfuscator
            .fromStreams(streams, timezone, timeframe, sections)
            .obfuscate()
        }

        else ->
          throw IllegalArgumentException("Only accepts calendar files with content type $ics, actual content type $contentType")
      }

      val filename = "obfuscal.ics"
      call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, filename).toString())
      call.respondOutputStream(ics, HttpStatusCode.OK) {
        cal.writeTo(this)
      }
    }
  }
}

private suspend fun fetchCalendar(url: String): InputStream {
  val res = client.get(url)
  val text = res.bodyAsText()
  return text.byteInputStream()
}

private fun parseQueryDate(params: Parameters, key: String): LocalDate {
  val value = params[key] ?: throw IllegalArgumentException("Missing required query parameter '$key'")
  try {
    return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
  } catch (e: DateTimeParseException) {
    throw IllegalArgumentException("Query parameter '$key' could not be parsed as a date. Try using the format yyyy-MM-dd")
  }
}

private fun parseSections(params: Parameters, key: String): List<LocalTimeSlice> {
  val raw = params[key] ?: throw IllegalArgumentException("Missing required query parameter '$key'")
  val jsonArray = try {
    Json {
      isLenient = true
    }.decodeFromString<Array<Array<String>>>(raw)
  } catch (e: Exception) {
    throw IllegalArgumentException("Query parameter '$key' could not be parsed. Format: '[[0800, 1200], [1200, 1800]]'")
  }
  if (jsonArray.isEmpty()) throw IllegalArgumentException("Given sections must not be empty")
  if (jsonArray.any { it.size != 2 }) throw IllegalArgumentException("Each section must have a start and end time")

  val sections = jsonArray.map { (start, end) ->
    LocalTimeSlice(parseTime(start), parseTime(end))
      .also {
        if (it.start > it.end) throw IllegalArgumentException("The start time of a section must be earlier then the end time.")
      }
  }.sortedBy { it.start }

  var lastEnd = LocalTime.MIN
  for (section in sections) {
    if (section.start < lastEnd) throw IllegalArgumentException("Sections must not be overlapping")
    lastEnd = section.end
  }
  return sections
}

private fun parseTime(str: String): LocalTime {
  try {
    return LocalTime.parse(str, DateTimeFormatter.ofPattern("HHmm"))
  } catch (e: Exception) {
    throw IllegalArgumentException("Could not parse time information from input '$str'. Try format 'HHmm' (4 digits).")
  }
}