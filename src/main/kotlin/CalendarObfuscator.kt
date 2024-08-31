import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion
import java.io.InputStream
import java.io.OutputStream

class CalendarObfuscator(
  private val calendars: List<Calendar>
) {
  companion object {
    fun fromStream(stream: InputStream): CalendarObfuscator {
      val calendar = CalendarBuilder().build(stream)
      return CalendarObfuscator(listOf(calendar))
    }

    fun fromStreams(stream: List<InputStream>): CalendarObfuscator {
      val calendars = stream.map { CalendarBuilder().build(it) }
      return CalendarObfuscator(calendars)
    }
  }

  fun obfuscate(): Calendar {
    val events = calendars.flatMap { it.getComponents<VEvent>(Component.VEVENT) }
    return buildNewCalendar(events)
  }

  private fun buildNewCalendar(events: List<VEvent>): Calendar {
    val calendar = Calendar()
    calendar += ProdId("-//Paul Methfessel//Obfuscal 1.0//EN")  // TODO get version from env
    calendar += ImmutableVersion.VERSION_2_0
    calendar += ImmutableCalScale.GREGORIAN
    events.forEach { calendar += it }
    return calendar
  }
}

private operator fun Calendar.plusAssign(property: Property) {
  add<Calendar>(property)
}

private operator fun Calendar.plusAssign(component: CalendarComponent) {
  add<Calendar>(component)
}

fun Calendar.writeTo(stream: OutputStream) {
  CalendarOutputter().output(this, stream)
}

