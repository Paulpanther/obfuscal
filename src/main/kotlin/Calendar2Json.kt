import kotlinx.serialization.Serializable
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VFreeBusy
import java.time.LocalDate
import java.time.temporal.Temporal
import kotlin.jvm.optionals.getOrNull

class Calendar2Json private constructor(
  private val calendar: Calendar
) {

  constructor(bytes: ByteArray) : this(CalendarBuilder().build(bytes.inputStream()))

  fun convert(): JCalendar {
    val events = calendar.getComponents<VEvent>()
    val freeBusy = calendar.getComponents<VFreeBusy>()

    // We don't use rrules in obfuscator, so we can just copy events here :)
    val jEvents = events.map {
      val start = it.getDateTimeStart<Temporal>().get().date
      val end = it.getDateTimeEnd<Temporal>().get().date
      val isMultiDay = start is LocalDate
      JEvent(start.toString(), end.toString(), it.summary.getOrNull()?.value ?: "", isMultiDay)
    }

    val jFreeBusy = freeBusy.map {
      val start = it.getDateTimeStart<Temporal>().get().date
      val end = it.getDateTimeEnd<Temporal>().get().date
      JFreeBusy(start.toString(), end.toString())
    }

    return JCalendar(jEvents, jFreeBusy)
  }
}

@Serializable
data class JCalendar(
  val events: List<JEvent>,
  val freeBusy: List<JFreeBusy>
)

@Serializable
data class JEvent(
  val start: String,
  val end: String,
  val summary: String,
  val isMultiDay: Boolean
)

@Serializable
data class JFreeBusy(
  val start: String,
  val end: String,
)
