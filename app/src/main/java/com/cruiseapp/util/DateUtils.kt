package com.cruiseapp.util

import java.text.SimpleDateFormat
import java.util.*

fun formatDate(millis: Long, pattern: String = "EEE, MMM d"): String {
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}
fun formatTime(millis: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
}
fun formatDateTime(millis: Long): String {
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(millis))
}
fun startOfDay(millis: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = millis; set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0)}
    return cal.timeInMillis
}
fun daysBetween(start: Long, end: Long): Int {
    val diff = end - start
    return (diff / (24*60*60*1000)).toInt() + 1
}
fun addDays(base: Long, days: Int): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = base }
    cal.add(Calendar.DAY_OF_YEAR, days)
    return cal.timeInMillis
}
