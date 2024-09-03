package db

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import utils.DateTimeSerializer
import java.time.LocalDateTime

object Shares : IntIdTable("share") {
  val name = varchar("name", 255)
  val expires = datetime("expires").nullable()
  val calendar = reference("generated_id", GeneratedCalendars.id, onDelete = ReferenceOption.CASCADE)
}

class Share(id: EntityID<Int>): IntEntity(id) {
  companion object: IntEntityClass<Share>(Shares)
  var name by Shares.name
  var expires by Shares.expires
  var calendar by Shares.calendar

  fun toShareData() = ShareData(name, expires, GeneratedCalendar[calendar].name)
}

@Serializable
data class ShareData(
  val name: String,
  @Serializable(with = DateTimeSerializer::class)
  val expires: LocalDateTime?,
  val calendar: String)
