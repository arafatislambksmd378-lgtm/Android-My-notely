package com.notely.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFmt {
    private val dayFmt = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val relFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    fun greeting(): String {
        val h = Date().hours
        return when (h) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun todayLine(): String = dayFmt.format(Date())
    fun savedLine(millis: Long): String = "Saved · ${timeFmt.format(Date(millis))}"
    fun relative(millis: Long): String {
        val diff = System.currentTimeMillis() - millis
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} h ago"
            else -> relFmt.format(Date(millis))
        }
    }
}

object Security {
    fun hashPin(pin: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
