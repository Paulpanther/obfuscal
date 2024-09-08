import db.GeneratedCalendar
import db.GeneratedCalendars
import db.Share
import db.Shares
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object ShareController {
  fun create(calendarName: String): Share {
    return transaction {
      val name = UUID.randomUUID().toString()
      val calendar = GeneratedCalendar.find { GeneratedCalendars.name eq calendarName }.firstOrNull()
        ?: throw IllegalArgumentException("No calendar with Name '$calendarName'")

      val existingShare = Share.find { Shares.name eq name }.firstOrNull()
      if (existingShare != null) throw IllegalArgumentException("Share with that name already exists, delete it first")

      Share.new {
        this.name = name
        this.calendar = calendar.id
      }
    }
  }

  fun list(): List<Share> {
    return transaction {
      Share.all().toList()
    }
  }

  fun get(name: String): Share? {
    return transaction {
      Share.find { Shares.name eq name }.firstOrNull()
    }
  }

  fun getCalendarFromShare(shareId: String): GeneratedCalendar {
    return transaction {
      val share = Share.find { Shares.name eq shareId }.firstOrNull()
        ?: throw IllegalArgumentException("Share with given id '$shareId' does not exist")
      GeneratedCalendar[share.calendar]
    }
  }

  fun delete(name: String): Boolean {
    return transaction {
      val share = Share.find { Shares.name eq name }.firstOrNull() ?: return@transaction false
      share.delete()
      true
    }
  }
}