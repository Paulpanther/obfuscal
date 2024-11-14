package utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal

@Serializable
data class LocalTimeSlice(
  @Serializable(with = TimeSerializer::class)
  val start: LocalTime,
  @Serializable(with = TimeSerializer::class)
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

  val days get() = ChronoUnit.DAYS.between(start, end)
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
    is OffsetDateTime -> input.atZoneSameInstant(zone).toLocalDateTime()
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

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = LocalDateTime::class)
object TimeSerializer : KSerializer<LocalTime> {
  private val formatter = DateTimeFormatter.ISO_LOCAL_TIME

  override fun serialize(encoder: Encoder, value: LocalTime) {
    encoder.encodeString(value.format(formatter))
  }

  override fun deserialize(decoder: Decoder): LocalTime {
    val raw = decoder.decodeString()
    return LocalTime.parse(raw, formatter)
  }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = LocalDate::class)
object DateSerializer : KSerializer<LocalDate> {
  private val formatter = DateTimeFormatter.ISO_DATE

  override fun serialize(encoder: Encoder, value: LocalDate) {
    encoder.encodeString(value.format(formatter))
  }

  override fun deserialize(decoder: Decoder): LocalDate {
    val raw = decoder.decodeString()
    return LocalDate.parse(raw, formatter)
  }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = Duration::class)
object DurationSerializer : KSerializer<Duration> {
  override fun serialize(encoder: Encoder, value: Duration) {
    encoder.encodeLong(value.toMillis())
  }

  fun deserialize(raw: String): Duration = deserialize(raw.toLongOrNull() ?: error("Input is not a number"))
  fun deserialize(raw: Long): Duration = Duration.of(raw, ChronoUnit.MILLIS)

  override fun deserialize(decoder: Decoder): Duration {
    val raw = decoder.decodeLong()
    return deserialize(raw)
  }
}
