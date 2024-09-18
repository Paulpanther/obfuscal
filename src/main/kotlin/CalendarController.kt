import db.GeneratedCalendar
import db.GeneratedCalendars
import db.InputCalendar
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.transactions.transaction
import utils.LocalDateSlice
import utils.LocalTimeSlice
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId

object CalendarController {
  fun regenerate(
    calendar: GeneratedCalendar,
    streams: List<InputStream>,
  ) {
    transaction {
      val cal = CalendarObfuscator
        .fromStreams(streams, calendar.timezoneId, calendar.timeframe, calendar.sectionList)
        .obfuscate()
      calendar.content = cal.toByteArray()
      calendar.lastChanged = LocalDateTime.now()
    }
  }

  fun create(
    name: String,
    urls: List<String>?,
    streams: List<InputStream>,
    timezone: ZoneId,
    timeframe: LocalDateSlice,
    sections: List<LocalTimeSlice>
  ): GeneratedCalendar {
    transaction {
      if (!GeneratedCalendar.find { GeneratedCalendars.name eq name }.empty()) {
        throw IllegalArgumentException("Calendar with this name does already exist. Delete it first")
      }
    }

    val startTime = sections.first().start
    val endTime = sections.last().end
    val cal = CalendarObfuscator
      .fromStreams(streams, timezone, timeframe, sections)
      .obfuscate()

    val generatedCalendar = transaction {
      GeneratedCalendar.new {
        this.name = name
        this.content = cal.toByteArray()
        this.startOfDay = startTime
        this.endOfDay = endTime
        this.startDate = timeframe.start
        this.endDate = timeframe.end
        this.timezone = timezone.id
        this.sections = Json.encodeToString(sections)
        this.lastChanged = LocalDateTime.now()
      }
    }

    if (urls != null) {
      transaction {
        for (url in urls) {
          InputCalendar.new {
            this.url = url
            this.generate = generatedCalendar.id
          }
        }
      }
    }

    return generatedCalendar
  }

  fun list(): List<GeneratedCalendar> {
    return transaction {
      GeneratedCalendar.all().toList()
    }
  }

  fun get(name: String): GeneratedCalendar {
    return transaction {
      GeneratedCalendar.find { GeneratedCalendars.name eq name }.firstOrNull()
        ?: throw IllegalArgumentException("Calendar with this name does not exist")
    }
  }

  fun delete(name: String): Boolean {
    return transaction {
      val calendar =
        GeneratedCalendar.find { GeneratedCalendars.name eq name }.firstOrNull() ?: return@transaction false
      calendar.delete()
      true
    }
  }
}