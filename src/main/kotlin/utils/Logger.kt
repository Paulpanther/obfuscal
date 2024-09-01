package utils

import org.apache.logging.log4j.LogManager

val <T : Any> T.logger get() = LogManager.getLogger(this::class.java)
