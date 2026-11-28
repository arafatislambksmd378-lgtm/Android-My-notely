package com.notely.app.util

import android.content.Context
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Real on-device spelling using the platform SpellCheckerService (Android's TextServicesManager).
 * Works offline when the device ships a spell checker and degrades silently to "no flags" when
 * none is installed. Words never leave the device.
 */
object SpellCheck {
    data class Issue(val start: Int, val end: Int, val word: String)

    /** Runs the platform checker over [text] and returns the words it does not recognise. */
    suspend fun issues(context: Context, text: String): List<Issue> {
        if (text.isBlank() || text.length > 20000) return emptyList()
        val tsm = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
            ?: return emptyList()
        return withContext(Dispatchers.Default) {
            runCatching {
                suspendCancellableCoroutine { cont ->
                    var finished = false
                    val activeSession = arrayOfNulls<SpellCheckerSession>(1)
                    fun finish(list: List<Issue>) {
                        if (finished) return
                        finished = true
                        runCatching { activeSession[0]?.close() }
                        cont.resume(list)
                    }
                    val listener = object : SpellCheckerSession.SpellCheckerSessionListener {
                        override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) = Unit
                        override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
                            val found = mutableListOf<Issue>()
                            results?.forEach { sentence ->
                                // API: getSuggestionsCount() + getSuggestionsInfoAt(i) +
                                // getOffsetAt(i)/getLengthAt(i). Offsets are relative to the
                                // TextInfo we submitted, which is the whole block.
                                for (i in 0 until sentence.suggestionsCount) {
                                    val info = sentence.getSuggestionsInfoAt(i) ?: continue
                                    val flags = info.suggestionsAttributes
                                    val looksLikeTypo =
                                        (flags and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) != 0
                                    val inDictionary =
                                        (flags and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) != 0
                                    if (!looksLikeTypo || inDictionary) continue
                                    val start = sentence.getOffsetAt(i)
                                    val len = sentence.getLengthAt(i)
                                    val end = start + len
                                    if (len > 0 && start >= 0 && end <= text.length) {
                                        found.add(Issue(start, end, text.substring(start, end)))
                                    }
                                }
                            }
                            finish(found)
                        }
                    }
                    val session = runCatching {
                        tsm.newSpellCheckerSession(null, Locale.getDefault(), listener, true)
                    }.getOrNull()
                    if (session == null) {
                        finish(emptyList())
                    } else {
                        activeSession[0] = session
                        session.getSentenceSuggestions(arrayOf(TextInfo(text)), 3)
                        cont.invokeOnCancellation { runCatching { session.close() } }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

    @Composable
    fun rememberIssues(enabled: Boolean, text: String): List<Issue> {
        var issues by remember { mutableStateOf<List<Issue>>(emptyList()) }
        val ctx = LocalContext.current.applicationContext
        LaunchedEffect(enabled, text) {
            if (!enabled || text.isBlank()) {
                issues = emptyList()
                return@LaunchedEffect
            }
            // Debounce: re-checking the platform service on every keystroke both
            // spams IPC and fights the caret while the user types.
            kotlinx.coroutines.delay(450)
            issues = issues(ctx, text)
        }
        return issues
    }
}
