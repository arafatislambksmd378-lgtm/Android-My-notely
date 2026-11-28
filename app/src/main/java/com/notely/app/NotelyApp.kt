package com.notely.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.notely.app.data.db.NotelyDatabase
import com.notely.app.data.prefs.PreferencesRepository
import com.notely.app.data.repo.*
import com.notely.app.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotelyApp : Application() {
    lateinit var db: NotelyDatabase
        private set
    lateinit var prefs: PreferencesRepository
        private set

    // Getters (not cached instances) so background threads never observe a half-initialised app.
    val notebooks: NotebookRepository get() = NotebookRepository(db.notebookDao())
    val notes: NoteRepository get() = NoteRepository(db.noteDao(), db.versionDao(), db.notebookDao(), db)
    val tags: TagRepository get() = TagRepository(db.tagDao())
    val comments: CommentRepository get() = CommentRepository(db.commentDao())
    val bookmarks: BookmarkRepository get() = BookmarkRepository(db.bookmarkDao())
    val footnotes: FootnoteRepository get() = FootnoteRepository(db.footnoteDao())
    val versions: VersionRepository get() = VersionRepository(db.versionDao())
    val templates: TemplateRepository get() = TemplateRepository(db.templateDao())
    val reminders: ReminderRepository get() = ReminderRepository(db.reminderDao())

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        db = NotelyDatabase.get(this)
        prefs = PreferencesRepository(this)
        createChannels()
        seedFirstRun()
        ReminderScheduler.rescheduleAll(this)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    ReminderScheduler.CHANNEL_ID, "Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Note reminders scheduled in Notely" }
            )
        }
    }

    private fun seedFirstRun() {
        scope.launch {
            // Built-in templates are seeded once. No fake notes or demo documents are created:
            // the user's workspace starts empty and every note comes from their own action.
            val existing = templates.observeAll().first()
            if (existing.isEmpty()) {
                BuiltinTemplates.all().forEach { (name, json) ->
                    templates.save(name, json, builtin = true)
                }
            }
        }
    }
}
