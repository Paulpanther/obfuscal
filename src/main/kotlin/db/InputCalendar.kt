package db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object InputCalendar : IntIdTable("input_calendar") {
  val url = varchar("url", 255)
  val generated = reference("generated_id", GeneratedCalendar.id, onDelete = ReferenceOption.CASCADE)
}