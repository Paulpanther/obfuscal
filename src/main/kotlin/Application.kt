import db.GeneratedCalendar
import db.InputCalendar
import db.InputCalendars
import db.Migrations
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import utils.LocalDateSlice
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val ics = ContentType.parse("text/calendar")

private val client = HttpClient(CIO)

private val user = System.getenv("USER") ?: error("No USER env variable")
private val password = System.getenv("PASSWORD") ?: error("No PASSWORD env variable")
private val isDev = System.getenv("DEV") == "true"

private val logger = LoggerFactory.getLogger(Application::class.java)

fun main() {
  Database.connect("jdbc:sqlite:obfuscal.db", setupConnection = { connection ->
    connection.createStatement().executeUpdate("PRAGMA foreign_keys = ON")
  })
  Migrations.init()

  embeddedServer(Netty, port = 8080, host = "localhost", module = Application::module)
    .start(wait = true)
}

fun Application.module() {
  install(CORS) {
    allowHost("localhost:1234")  // Debug client
    allowMethod(HttpMethod.Options)
    allowMethod(HttpMethod.Delete)
    allowHeader("Content-Type")
    allowCredentials = true
  }

  install(ContentNegotiation) {
    json()
  }

  install(Authentication) {
    basic("auth-basic") {
      validate { credentials ->
        if (credentials.name == user && credentials.password == password) {
          UserIdPrincipal(credentials.name)
        } else {
          null
        }
      }
    }

    form("auth-form") {
      userParamName = "username"
      passwordParamName = "password"
      validate { credentials ->
        if (credentials.name == user && credentials.password == password) {
          logger.info("Logged in user ${credentials.name}")
          UserIdPrincipal(credentials.name)
        } else {
          null
        }
      }
      challenge {
        call.respond(HttpStatusCode.Unauthorized)
      }
    }

    session<UserSession>("auth-session") {
      validate { session ->
        if (session.username == user) {
          session
        } else {
          null
        }
      }
      challenge {
        call.respond(HttpStatusCode.Unauthorized)
      }
    }
  }

  install(Sessions) {
    cookie<UserSession>("user_session", SessionStorageMemory()) {
      cookie.path = "/"
      cookie.maxAgeInSeconds = 60 * 30
    }
  }

  configureRouting()
}

fun Application.configureRouting() {
  install(StatusPages) {
    exception<IllegalArgumentException> { call, cause ->
      call.respond(
        HttpStatusCode.BadRequest,
        cause.message?.let { "Error parsing given information: ${cause.message}" } ?: "Input could not be handled")
    }

    exception<Exception> { call, cause ->
      cause.printStackTrace()
      if (isDev) {
        call.respond(HttpStatusCode.InternalServerError, cause.toString())
      } else {
        call.response.status(HttpStatusCode.InternalServerError)
      }
    }
  }

  routing {
    get("/{share}") {
      val useJson = call.request.queryParameters["json"] == "true"
      val calendar = ShareController.getCalendarFromShare(call.pathParam("share"))
      if (useJson) {
        call.respond(Calendar2Json(calendar.content).convert())
      } else {
        call.respondCalendar(calendar)
      }
    }

    route("/share") {
      get("/{name}") {
        val share = ShareController.get(call.pathParam("name"))
          ?: throw NotFoundException()
        call.respond(share.toShareData())
      }
    }

    get("/logout") {
      call.sessions.clear<UserSession>()
    }

    authenticate("auth-form", "auth-session") {
      post("/login") {
        val userName = call.principal<UserIdPrincipal>()?.name.toString()
        call.sessions.set(UserSession(userName))
        call.respond(HttpStatusCode.OK)
      }
    }

    authenticate("auth-session", "auth-basic") {
      route("/share") {
        post {
          // TODO expires
          val share = ShareController.create(call.queryParam("calendar"))
          call.respond(share.toShareData())
        }

        get {
          val shares = ShareController.list()
          call.respond(shares.map { it.toShareData() })
        }

        options("/{name}") {
          call.respond(HttpStatusCode.OK)
        }

        delete("/{name}") {
          val hasDeleted = ShareController.delete(call.pathParam("name"))
          call.response.status(if (hasDeleted) HttpStatusCode.OK else HttpStatusCode.NoContent)
        }
      }

      route("/obfuscate") {
        post {
          val contentType = call.request.contentType()
          val name = call.queryParam("name")

          val timezone = GeneratedCalendar.parseTimezone(call.queryParam("timezone"))

          val startDate = parseQueryDate(call.request.queryParameters, "timeframe-start")
          val endDate = parseQueryDate(call.request.queryParameters, "timeframe-end")
          if (startDate > endDate) throw IllegalArgumentException("'timeframe-start' must be earlier than 'timeframe-end'")
          val timeframe = LocalDateSlice(startDate, endDate)

          val sections = GeneratedCalendar.parseSections(call.queryParam("sections"))

          var urls: List<String>? = null
          val streams = when {
            call.request.isMultipart() -> {
              call.receiveMultipart()
                .readAllParts()
                .filterIsInstance<PartData.FileItem>()
                .map { it.streamProvider() }
            }

            contentType == ics -> {
              listOf(call.receiveStream())
            }

            contentType == ContentType.parse("application/json") -> {
              urls = call.receive<List<String>>()
              urls.map { fetchCalendar(it) }
            }

            else ->
              throw IllegalArgumentException("Only accepts calendar files with content type $ics, actual content type $contentType")
          }

          val calendar = CalendarController.create(name, urls, streams, timezone, timeframe, sections)

          call.respond(calendar.toData())
        }


        get {
          val calendars = CalendarController.list()
          call.respond(calendars.map { it.toData() })
        }

        route("/{name}") {
          post("/regenerate") {
            val calendar = CalendarController.get(call.pathParam("name"))
            val inputs = transaction { InputCalendar.find { InputCalendars.generated eq calendar.id }.map { it.url } }
            if (inputs.isEmpty()) {
              call.respond(HttpStatusCode.NoContent)
            } else {
              val streams = inputs.map { fetchCalendar(it) }
              CalendarController.regenerate(
                calendar,
                streams,
              )
              call.respond(calendar.toData())
            }
          }

          get {
            val calendar = CalendarController.get(call.pathParam("name"))
            call.respond(calendar.toData())
          }

          options {
            call.respond(HttpStatusCode.OK)
          }

          delete {
            val hasDeleted = CalendarController.delete(call.pathParam("name"))
            call.response.status(if (hasDeleted) HttpStatusCode.OK else HttpStatusCode.NoContent)
          }
        }
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

private suspend fun ApplicationCall.respondCalendar(calendar: GeneratedCalendar) {
  val filename = "${calendar.name}.ics"
  response.header(
    HttpHeaders.ContentDisposition,
    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, filename).toString()
  )
  respondOutputStream(ics, HttpStatusCode.OK) {
    write(calendar.content)
  }
}

private fun ApplicationCall.pathParam(key: String): String {
  return parameters[key] ?: throw IllegalArgumentException("Missing required path parameter '$key'")
}

private fun ApplicationCall.queryParam(key: String): String {
  return request.queryParameters[key] ?: throw IllegalArgumentException("Missing required query parameter '$key'")
}
