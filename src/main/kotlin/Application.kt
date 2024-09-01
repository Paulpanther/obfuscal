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
import org.jetbrains.exposed.sql.Database
import java.io.InputStream

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
      call.respondText(cause.message ?: "Input could not be handled")
    }
  }

  routing {
    post("/obfuscate") {
      val contentType = call.request.contentType()

      val cal = when {
        call.request.isMultipart() -> {
          val streams =
            call.receiveMultipart()
              .readAllParts()
              .filterIsInstance<PartData.FileItem>()
              .map { it.streamProvider() }
          CalendarObfuscator
            .fromStreams(streams)
            .obfuscate()
        }

        contentType == ics -> {
          val stream = call.receiveStream()
          CalendarObfuscator
            .fromStream(stream)
            .obfuscate()
        }

        contentType == ContentType.parse("application/json") -> {
          val calendarUrls = call.receive<List<String>>()
          val streams = calendarUrls.map { fetchCalendar(it) }
          CalendarObfuscator
            .fromStreams(streams)
            .obfuscate()
        }

        else ->
          throw IllegalArgumentException("Only accepts calendar files with content type $ics, actual content type $contentType")
      }

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