import db.GeneratedCalendar
import db.GeneratedCalendars
import org.jetbrains.exposed.sql.transactions.transaction
import utils.LocalDateSlice
import utils.LocalTimeSlice
import java.io.InputStream
import java.time.ZoneId

object CalendarController {
  fun create(
    name: String,
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

    val cal = CalendarObfuscator
      .fromStreams(streams, timezone, timeframe, sections)
      .obfuscate()

    return transaction {
      GeneratedCalendar.new {
        this.name = name
        this.content = cal.toByteArray()
      }
    }
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