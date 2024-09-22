package db

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object InputCalendars : IntIdTable("input_calendar") {
  val url = varchar("url", 255)
  val generated = reference("generated_id", GeneratedCalendars.id, onDelete = ReferenceOption.CASCADE)
}

class InputCalendar(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<InputCalendar>(InputCalendars)

  var url by InputCalendars.url
  var generate by GeneratedCalendar referencedOn InputCalendars.generated

  fun toData() = InputCalendarData(url)
}

@Serializable
data class InputCalendarData(
  val url: String,
)