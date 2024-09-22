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
    val startTime = sections.first().start
    val endTime = sections.last().end
    val cal = CalendarObfuscator
      .fromStreams(streams, timezone, timeframe, sections)
      .obfuscate()

    val generatedCalendar = transaction {
      val existingCalendar = GeneratedCalendar.find { GeneratedCalendars.name eq name }.firstOrNull()
      val calendar = existingCalendar ?: GeneratedCalendar.new {}

      calendar.name = name
      calendar.content = cal.toByteArray()
      calendar.startOfDay = startTime
      calendar.endOfDay = endTime
      calendar.startDate = timeframe.start
      calendar.endDate = timeframe.end
      calendar.timezone = timezone.id
      calendar.sections = Json.encodeToString(sections)
      calendar.lastChanged = LocalDateTime.now()

      calendar
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