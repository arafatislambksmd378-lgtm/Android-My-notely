package com.notely.app.util

import java.net.URI

/** Allow only intentional web/mail links; escaping alone does not prevent active URLs. */
object SafeLinks {
    fun isAllowed(value: String): Boolean {
        if (value.any { it.isISOControl() } || value != value.trim()) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return when (uri.scheme?.lowercase(java.util.Locale.ROOT)) {
            "http", "https" -> !uri.host.isNullOrBlank() && uri.rawUserInfo == null
            "mailto" -> !uri.rawSchemeSpecificPart.isNullOrBlank()
            else -> false
        }
    }
}
