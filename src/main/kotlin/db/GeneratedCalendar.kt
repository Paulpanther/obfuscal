package db

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object GeneratedCalendars : IntIdTable("generated_calendar") {
  val name = varchar("name", 255).uniqueIndex()
  val content = binary("content")
}

class GeneratedCalendar(id: EntityID<Int>): IntEntity(id) {
  companion object: IntEntityClass<GeneratedCalendar>(GeneratedCalendars)
  var name by GeneratedCalendars.name
  var content by GeneratedCalendars.content
}