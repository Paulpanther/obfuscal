package db

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.time
import utils.TimeSerializer
import java.time.LocalTime

object GeneratedCalendars : IntIdTable("generated_calendar") {
  val name = varchar("name", 255).uniqueIndex()
  val content = binary("content")
  val startOfDay = time("startOfDay")
  val endOfDay = time("endOfDay")
}

class GeneratedCalendar(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<GeneratedCalendar>(GeneratedCalendars)

  var name by GeneratedCalendars.name
  var content by GeneratedCalendars.content
  var startOfDay by GeneratedCalendars.startOfDay
  var endOfDay by GeneratedCalendars.endOfDay

  fun toData() = GeneratedCalendarData(name, content.toString(Charsets.UTF_8), startOfDay, endOfDay)
}

@Serializable
data class GeneratedCalendarData(
  val name: String,
  val content: String,
  @Serializable(with = TimeSerializer::class)
  val startOfDay: LocalTime,
  @Serializable(with = TimeSerializer::class)
  val endOfDay: LocalTime,
)