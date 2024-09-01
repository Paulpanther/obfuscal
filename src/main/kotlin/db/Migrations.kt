package db

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import utils.logger

object Migrations {
  fun init() {
    logger.info("Initializing DB...")

    transaction {
      if (SchemaUtils.listTables().isEmpty()) {
        SchemaUtils.create(GeneratedCalendar, InputCalendar, Share)
        logger.info("Created DB")
      }
    }
  }
}
