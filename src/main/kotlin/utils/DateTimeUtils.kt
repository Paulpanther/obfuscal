package utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal

data class LocalTimeSlice(
  val start: LocalTime,
  val end: LocalTime,
) {
  fun atDate(date: LocalDate): LocalDateTimeSlice {
    return LocalDateTimeSlice(date.atTime(start), date.atTime(end))
  }
}

data class LocalDateSlice(
  val start: LocalDate,
  val end: LocalDate,
) {
  constructor(start: LocalDate, period: Period) : this(start, start + period)

  fun toICal4jPeriod() = net.fortuna.ical4j.model.Period(start, end)

  fun toLocalDateTimeSlice() = LocalDateTimeSlice(start.atStartOfDay(), end.atEndOfDay())

  val period get() = Period.between(start, end)
}

data class LocalDateTimeSlice(
  val start: LocalDateTime,
  val end: LocalDateTime,
) {
  fun toICal4jPeriod() = net.fortuna.ical4j.model.Period(start, end)

  fun intersectsOrContains(o: LocalDateTimeSlice): Boolean {
    return (o.start <= start && o.end > start) ||  // other intersects starts
        (o.start < end && o.end >= end) ||  // other intersects end
        (o.start >= start && o.end <= end)  // other is contained
  }

  fun toDuration() = Duration.between(start, end)
}

fun net.fortuna.ical4j.model.Period<LocalDateTime>.toSlice() = LocalDateTimeSlice(start, end)
fun net.fortuna.ical4j.model.Period<LocalDate>.toSlice() = LocalDateSlice(start, end)

fun LocalDate.atEndOfDay(): LocalDateTime {
  return plusDays(1).atStartOfDay()
}

/**
 * Convert given input time to be in the given timezone
 */
fun dateTimeOrZonedDateTimeToTimezone(
  input: Temporal,
  zone: ZoneId,
  dateToDateTime: (date: LocalDate) -> LocalDateTime
): LocalDateTime {
  return when (input) {
    is LocalDate -> dateToDateTime(input)
    is LocalDateTime -> input
    else -> {
      (input as ZonedDateTime).withZoneSameInstant(zone).toLocalDateTime()
    }
  }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = LocalDateTime::class)
object NullableDateTimeSerializer : KSerializer<LocalDateTime?> {
  private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  override fun serialize(encoder: Encoder, value: LocalDateTime?) {
    encoder.encodeString(value?.format(formatter) ?: "null")
  }

  override fun deserialize(decoder: Decoder): LocalDateTime? {
    val raw = decoder.decodeString()
    if (raw == "null") return null
    return LocalDateTime.parse(raw, formatter)
  }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = LocalDateTime::class)
object DateTimeSerializer : KSerializer<LocalDateTime> {
  private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  override fun serialize(encoder: Encoder, value: LocalDateTime) {
    encoder.encodeString(value.format(formatter))
  }

  override fun deserialize(decoder: Decoder): LocalDateTime {
    val raw = decoder.decodeString()
    return LocalDateTime.parse(raw, formatter)
  }
}
