package db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime

object Share : IntIdTable("share") {
  val name = varchar("name", 255)
  val expires = datetime("expires").nullable()
  val calendar = reference("generated_id", GeneratedCalendar.id, onDelete = ReferenceOption.CASCADE)
}