package db

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.transactions.transaction
import utils.DateSerializer
import utils.DateTimeSerializer
import utils.LocalDateSlice
import utils.LocalTimeSlice
import utils.TimeSerializer
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object GeneratedCalendars : IntIdTable("generated_calendar") {
  val name = varchar("name", 255).uniqueIndex()
  val content = binary("content")
  val startOfDay = time("startOfDay")
  val endOfDay = time("endOfDay")
  val timezone = varchar("timezone", 255)
  val startDate = date("startDate")
  val endDate = date("endDate")
  val sections = varchar("sections", 255)
  val lastChanged = datetime("lastChanged")
}

private val json = Json { isLenient = true }

class GeneratedCalendar(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<GeneratedCalendar>(GeneratedCalendars) {
    fun parseSections(raw: String): List<LocalTimeSlice> {
      var sections = try {
        json.parseToJsonElement(raw).jsonArray.map { json.decodeFromJsonElement<LocalTimeSlice>(it) }
      } catch (e: Exception) {
        throw IllegalArgumentException("sections could not be parsed. Format: '[{start: '08:00', end: '12:00'], {start: '12:00', end: '18:00'}]'")
      }
      if (sections.isEmpty()) throw IllegalArgumentException("Given sections must not be empty")

      sections.forEach {
        if (it.start > it.end) throw IllegalArgumentException("The start time of a section must be earlier then the end time.")
      }
      sections = sections.sortedBy { it.start }

      var lastEnd = LocalTime.MIN
      for (section in sections) {
        if (section.start < lastEnd) throw IllegalArgumentException("Sections must not be overlapping")
        lastEnd = section.end
      }
      return sections
    }

    fun parseTimezone(str: String): ZoneId {
      return try {
        ZoneId.of(str)
      } catch (e: DateTimeException) {
        throw IllegalArgumentException("Given query parameter 'timezone' could not be resolved")
      }
    }
  }

  var name by GeneratedCalendars.name
  var content by GeneratedCalendars.content
  var startOfDay by GeneratedCalendars.startOfDay
  var endOfDay by GeneratedCalendars.endOfDay
  var timezone by GeneratedCalendars.timezone
  var startDate by GeneratedCalendars.startDate
  var endDate by GeneratedCalendars.endDate
  var sections by GeneratedCalendars.sections
  var lastChanged by GeneratedCalendars.lastChanged

  val sectionList get() = parseSections(sections)
  val timezoneId get() = parseTimezone(timezone)
  val timeframe get() = LocalDateSlice(startDate, endDate)

  val inputCalendars by InputCalendar referrersOn InputCalendars.generated

  fun toData() = GeneratedCalendarData(
    name,
    content.toString(Charsets.UTF_8),
    startOfDay,
    endOfDay,
    timezone,
    startDate,
    endDate,
    sections,
    lastChanged,
    transaction { inputCalendars.map { it.toData() } }
  )
}

@Serializable
data class GeneratedCalendarData(
  val name: String,
  val content: String,
  @Serializable(with = TimeSerializer::class)
  val startOfDay: LocalTime,
  @Serializable(with = TimeSerializer::class)
  val endOfDay: LocalTime,
  val timezone: String,
  @Serializable(with = DateSerializer::class)
  val startDate: LocalDate,
  @Serializable(with = DateSerializer::class)
  val endDate: LocalDate,
  val sections: String,
  @Serializable(with = DateTimeSerializer::class)
  val lastChanged: LocalDateTime,
  val inputCalendars: List<InputCalendarData>
)