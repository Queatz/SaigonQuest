package main.components.common

import kotlinx.datetime.Instant
import kotlin.js.Date

fun formatAmount(value: Long): String {
    val s = value.toString()
    val parts = s.reversed().chunked(3).joinToString(",").reversed()
    return parts
}

fun formatTimestampLikeChat(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val today = Date(now.getFullYear(), now.getMonth(), now.getDate())
    val yesterday = Date(today.getTime() - 86400000)

    val messageDate = Date(date.getFullYear(), date.getMonth(), date.getDate())

    val hours = date.getHours().toString().padStart(2, '0')
    val minutes = date.getMinutes().toString().padStart(2, '0')
    val timeString = "$hours:$minutes"

    return when (messageDate.getTime()) {
        today.getTime() -> "Today, $timeString"
        yesterday.getTime() -> "Yesterday, $timeString"
        else -> {
            val month = when (date.getMonth()) {
                0 -> "Jan"; 1 -> "Feb"; 2 -> "Mar"; 3 -> "Apr"; 4 -> "May"; 5 -> "Jun"
                6 -> "Jul"; 7 -> "Aug"; 8 -> "Sep"; 9 -> "Oct"; 10 -> "Nov"; else -> "Dec"
            }
            val day = date.getDate().toString()
            "$month $day, $timeString"
        }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
fun formatTimestampLikeChat(instant: Instant): String = formatTimestampLikeChat(instant.toEpochMilliseconds())
