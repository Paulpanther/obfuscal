package utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.test.Test


class DateTimeUtilsTest {
  @Test
  fun testIntersectsOrContains() {
    assertFalse(slice("10:00", "13:00").intersectsOrContains(slice("09:00", "10:00")))
    assertTrue(slice("10:00", "13:00").intersectsOrContains(slice("09:00", "11:00")))
    assertTrue(slice("10:00", "13:00").intersectsOrContains(slice("10:00", "11:00")))
    assertTrue(slice("10:00", "13:00").intersectsOrContains(slice("10:01", "11:00")))
    assertTrue(slice("10:00", "13:00").intersectsOrContains(slice("10:00", "13:00")))
    assertTrue(slice("10:00", "13:00").intersectsOrContains(slice("10:01", "13:00")))
    assertTrue(slice("10:00", "13:00").intersectsOrContains(slice("10:00", "14:00")))
    assertTrue(slice("10:00", "13:00").intersectsOrContains(slice("11:00", "14:00")))
    assertFalse(slice("10:00", "13:00").intersectsOrContains(slice("13:00", "14:00")))
  }

  private fun slice(from: String, to: String): LocalDateTimeSlice {
    val date = LocalDate.of(2021, 5, 21)
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val fromTime = LocalTime.parse(from, formatter)
    val toTime = LocalTime.parse(to, formatter)
    return LocalDateTimeSlice(LocalDateTime.of(date, fromTime), LocalDateTime.of(date, toTime))
  }
}