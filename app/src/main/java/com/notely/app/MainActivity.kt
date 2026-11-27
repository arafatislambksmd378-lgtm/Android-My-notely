package com.notely.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notely.app.ui.NotelyNavHost
import com.notely.app.ui.security.AppLockGate
import com.notely.app.ui.theme.Accent
import com.notely.app.ui.theme.LocalNotelyColors
import com.notely.app.ui.theme.NotelyTheme
import com.notely.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* reminder permission is best-effort */ }

    /**
     * Note requested by a reminder notification tap. Held here rather than in
     * saved state so a tap on a cold start still opens the right note; the
     * navigation host consumes it exactly once.
     */
    private val pendingNoteId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NotelyApp
        askNotificationPermission()
        consumeReminderIntent(intent)

        setContent {
            val mode by app.prefs.themeMode.collectAsStateWithLifecycle(initial = ThemeMode.SYSTEM)
            val accent by app.prefs.accent.collectAsStateWithLifecycle(initial = Accent.PURPLE)
            val lockEnabled by app.prefs.appLock.collectAsStateWithLifecycle(initial = false)
            val pinHash by app.prefs.pinHash.collectAsStateWithLifecycle(initial = "")

            var unlocked by remember { mutableStateOf(false) }
            val lifecycle = LocalLifecycleOwner.current

            // Re-lock when the app returns from the background (privacy by default).
            DisposableEffect(lifecycle, lockEnabled, pinHash) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP && lockEnabled && pinHash.isNotBlank()) {
                        unlocked = false
                    }
                }
                lifecycle.lifecycle.addObserver(observer)
                onDispose { lifecycle.lifecycle.removeObserver(observer) }
            }

            NotelyTheme(mode = mode, accent = accent) {
                val c = LocalNotelyColors.current
                Box(Modifier.fillMaxSize().background(c.bg)) {
                    val needsLock = lockEnabled && pinHash.isNotBlank() && !unlocked
                    if (needsLock) {
                        AppLockGate(app) { unlocked = true }
                    } else {
                        NotelyNavHost(
                            app = app,
                            pendingNoteId = pendingNoteId.value,
                            onPendingNoteConsumed = { pendingNoteId.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeReminderIntent(intent)
    }

    /** A reminder tap carries the note id; surface it to the Compose tree. */
    private fun consumeReminderIntent(intent: Intent?) {
        val id = intent?.getLongExtra(com.notely.app.reminder.ReminderReceiver.EXTRA_NOTE_ID, -1L) ?: -1L
        if (id > 0L) {
            pendingNoteId.value = id
            intent?.removeExtra(com.notely.app.reminder.ReminderReceiver.EXTRA_NOTE_ID)
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
