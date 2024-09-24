package db

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import utils.DateSerializer
import utils.NullableDateTimeSerializer
import utils.TimeSerializer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object Shares : IntIdTable("share") {
  val name = varchar("name", 255)
  val expires = datetime("expires").nullable()
  val calendar = reference("generated_id", GeneratedCalendars.id, onDelete = ReferenceOption.CASCADE)
}

class Share(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<Share>(Shares)

  var name by Shares.name
  var expires by Shares.expires
  var calendar by Shares.calendar

  fun toShareData(): ShareData {
    val calendar = transaction { GeneratedCalendar[calendar] }
    return ShareData(
      name,
      expires,
      calendar.name,
      calendar.startOfDay,
      calendar.endOfDay,
      calendar.startDate,
      calendar.endDate
    )
  }
}

@Serializable
data class ShareData(
  val name: String,
  @Serializable(with = NullableDateTimeSerializer::class)
  val expires: LocalDateTime?,
  val calendar: String,
  @Serializable(with = TimeSerializer::class)
  val startOfDay: LocalTime?,
  @Serializable(with = TimeSerializer::class)
  val endOfDay: LocalTime?,
  @Serializable(with = DateSerializer::class)
  val startDate: LocalDate?,
  @Serializable(with = DateSerializer::class)
  val endDate: LocalDate?,
)
