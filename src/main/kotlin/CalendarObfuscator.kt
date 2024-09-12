import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion
import utils.LocalDateSlice
import utils.LocalDateTimeSlice
import utils.LocalTimeSlice
import utils.dateTimeOrZonedDateTimeToTimezone
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.Temporal
import java.util.*
import java.util.stream.LongStream
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.toList

class CalendarObfuscator(
  private val calendars: List<Calendar>,
  private val timezone: ZoneId,
  private val timeframe: LocalDateSlice,
  private val sectionsPerDay: List<LocalTimeSlice>
) {

  companion object {
    fun fromStream(
      stream: InputStream,
      timezone: ZoneId,
      timeframe: LocalDateSlice,
      sectionsPerDay: List<LocalTimeSlice>
    ): CalendarObfuscator {
      val calendar = CalendarBuilder().build(stream)
      return CalendarObfuscator(listOf(calendar), timezone, timeframe, sectionsPerDay)
    }

    fun fromStreams(
      stream: List<InputStream>,
      timezone: ZoneId,
      timeframe: LocalDateSlice,
      sectionsPerDay: List<LocalTimeSlice>
    ): CalendarObfuscator {
      val calendars = stream.map { CalendarBuilder().build(it) }
      return CalendarObfuscator(calendars, timezone, timeframe, sectionsPerDay)
    }
  }

  /**
   * Core functionality of this project.
   * We take many calendars and combine their events.
   * Events which description starts with HIDE are discarded.
   * Multi-day events are discarded except if their description begins with SHOW.
   * Then we calculate all "sections" per days in the timeframe that have events (these are busy).
   * Additionally, we also filter for any event which description begins with SHOW
   */
  fun obfuscate(): Calendar {
    // these are not filtered for multi-day events yet, we do this later in the loop
    val events = calendars
      .flatMap { it.getComponents<VEvent>(Component.VEVENT) }
      .filter { it.description.map { !it.value.startsWith("HIDE") }.orElse(true) }

    val busySections = mutableSetOf<LocalDateTimeSlice>()
    val showFully = mutableListOf<VEvent>()

    // TODO could be parallelized
    for (event in events) {
      // Events with SHOW in the description are always shown (not obfuscated)
      if (event.description.map { it.value.startsWith("SHOW") }.orElse(false)) {
        showFully += event
        continue
      }

      // Hide multi-day events (that have no SHOW tag)
      val isMultiDay = event.getDateTimeStart<Temporal>().orElse(null)?.date is LocalDate
      if (isMultiDay) {
        continue
      }

      val occurrences = calculateOccurrences(event)

      // skip if occurrences are all outside of timeframe
      if (!occurrences.any { timeframe.toLocalDateTimeSlice().intersectsOrContains(it) }) continue

      for (occurrence in occurrences) {
        val sectionsInPeriod = LongStream
          .range(0, occurrence.toDuration().toDays() + 1)
          .toList()
          .map { i -> occurrence.start.toLocalDate().plusDays(i) }
          .flatMap { day -> sectionsPerDay.map { section -> section.atDate(day) } }

        for (section in sectionsInPeriod) {
          if (section.intersectsOrContains(occurrence)) {
            busySections += section
          }
        }
      }
    }

    val finalEvents = mutableListOf<VEvent>()

    // Add fully shown events
    for (event in showFully) {
      for (occurrences in calculateOccurrences(event)) {
        val isMultiDay = event.getDateTimeStart<Temporal>().getOrNull()?.date is LocalDate
        finalEvents += VEvent(
          if (isMultiDay) occurrences.start.toLocalDate() else occurrences.start,
          if (isMultiDay) occurrences.end.toLocalDate() else occurrences.end,
          event.summary.orElse(null)?.value ?: ""
        ).also {
          it.add<VEvent>(Uid(UUID.randomUUID().toString()))
        }
      }
    }

    // Add busy sections
    for (section in busySections) {
      finalEvents += VEvent(section.start, section.end, "").also {
        it.add<VEvent>(Uid(UUID.randomUUID().toString()))
      }
    }

    return buildNewCalendar(finalEvents)
  }

  private fun buildNewCalendar(events: List<VEvent>): Calendar {
    val calendar = Calendar()
    calendar += ProdId("-//Paul Methfessel//Obfuscal 1.0//EN")  // TODO get version from env
    calendar += ImmutableVersion.VERSION_2_0
    calendar += ImmutableCalScale.GREGORIAN
    events.forEach { calendar += it }
    return calendar
  }

  private fun calculateOccurrences(event: VEvent): List<LocalDateTimeSlice> {
    return event
      .calculateRecurrenceSet<Temporal>(timeframe.toLocalDateTimeSlice().toICal4jPeriod())
      .map {
        // Events can be in different timezones. After these two calls all times will be in the timezone given by the user
        val start = dateTimeOrZonedDateTimeToTimezone(it.start, timezone, LocalDate::atStartOfDay)
        val end = dateTimeOrZonedDateTimeToTimezone(it.end, timezone, LocalDate::atStartOfDay)
        LocalDateTimeSlice(start, end)
      }
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

fun Calendar.toByteArray(): ByteArray {
  return ByteArrayOutputStream()
    .also { writeTo(it) }
    .toByteArray()
}