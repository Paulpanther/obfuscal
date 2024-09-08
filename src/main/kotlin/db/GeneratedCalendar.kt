package db

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object GeneratedCalendars : IntIdTable("generated_calendar") {
  val name = varchar("name", 255).uniqueIndex()
  val content = binary("content")
}

class GeneratedCalendar(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<GeneratedCalendar>(GeneratedCalendars)

  var name by GeneratedCalendars.name
  var content by GeneratedCalendars.content

  fun toData() = GeneratedCalendarData(name, content.toString(Charsets.UTF_8))
}

@Serializable
data class GeneratedCalendarData(
  val name: String,
  val content: String
)