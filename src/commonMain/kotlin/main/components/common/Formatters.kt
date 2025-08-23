package main.components.common

import Translations.str
import androidx.compose.runtime.Composable
import kotlin.time.Instant
import main.components.LanguageSettings.rememberCurrentLanguage
import kotlin.js.Date

@Composable
fun formatAmount(value: Long): String {
    val s = value.toString()
    val parts = s.reversed().chunked(3).joinToString(
        when (rememberCurrentLanguage()) {
            "vi" -> "."
            else -> ","
        }
    ).reversed()
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
            val months = arrayOf(
                str("Jan"),
                str("Feb"),
                str("Mar"),
                str("Apr"),
                str("May"),
                str("Jun"),
                str("Jul"),
                str("Aug"),
                str("Sep"),
                str("Oct"),
                str("Nov"),
                str("Dec"),
            )
            val month = months[date.getMonth()]
            val day = date.getDate().toString()
            "$month $day, $timeString"
        }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
fun formatTimestampLikeChat(instant: Instant): String = formatTimestampLikeChat(instant.toEpochMilliseconds())
