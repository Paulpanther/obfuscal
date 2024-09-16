package utils

import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.CalendarComponent

inline fun <reified T : CalendarComponent> Calendar.filterComponents(): List<T> {
  return getComponents<CalendarComponent>().filterIsInstance<T>()
}