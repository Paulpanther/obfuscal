package db

import org.jetbrains.exposed.dao.id.IntIdTable

object GeneratedCalendar : IntIdTable("generated_calendar") {
  val name = varchar("name", 255)
}