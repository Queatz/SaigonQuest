package main.utils

import Translations.str
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * Utility functions for time and date operations.
 */
object TimeUtils {
    
    /**
     * Format a timestamp into a human-readable string
     * 
     * @param timestamp The timestamp in milliseconds
     * @return A human-readable string (e.g., "2 hours ago", "Yesterday", "Jul 29")
     */
    @OptIn(ExperimentalTime::class)
    fun formatTimestamp(timestamp: Long): String {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val diff = now - timestamp
        
        // Less than a minute
        if (diff < 60 * 1000) {
            return "Just now"
        }
        
        // Less than an hour
        if (diff < 60 * 60 * 1000) {
            val minutes = diff / (60 * 1000)
            return "$minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
        }
        
        // Less than a day
        if (diff < 24 * 60 * 60 * 1000) {
            val hours = diff / (60 * 60 * 1000)
            return "$hours ${if (hours == 1L) "hour" else "hours"} ago"
        }
        
        // Less than 2 days
        if (diff < 2 * 24 * 60 * 60 * 1000) {
            return "Yesterday"
        }
        
        // Less than a week
        if (diff < 7 * 24 * 60 * 60 * 1000) {
            val days = diff / (24 * 60 * 60 * 1000)
            return "$days ${if (days == 1L) "day" else "days"} ago"
        }
        
        // For older dates, just return a simple date string
        // Create a Kotlin datetime object from the timestamp
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        
        // Get month name (abbreviated)
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
        val month = months[localDateTime.month.ordinal]
        
        // Get day of month
        val day = localDateTime.dayOfMonth
        
        return "$month $day"
    }
}
