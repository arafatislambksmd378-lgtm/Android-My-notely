package com.notely.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.notely.app.doc.*
import com.notely.app.reminder.ReminderReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Host drives real force-stop/SIGKILL while this test deliberately holds the editor open. */
@RunWith(AndroidJUnit4::class)
class ProcessRecoveryInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun journalAndExternallyTerminatedEditor() {
        val app = ApplicationProvider.getApplicationContext<Context>() as NotelyApp
        val stage = InstrumentationRegistry.getArguments().getString("recoveryStage")
        if (stage == null) {
            // Ordinary connected suite: real Room journal, not a skipped/empty test.
            runBlocking {
                val nb = app.notebooks.create("Journal regression")
                val id = app.notes.create(nb, "old")
                try {
                    val session = app.recovery.beginSession(id)
                    val done = CompletableDeferred<Result<Unit>>()
                    app.recovery.write(id, session, "durable", listOf(DocumentBlock(runs = listOf(TextRun("বাংলা", bold = true)))), PageSetup(), false) { done.complete(it) }
                    done.await().getOrThrow()
                    assertEquals("old", app.notes.getById(id)!!.title)
                    assertEquals("durable", app.recovery.load(id).first.title)
                    assertTrue(app.recovery.load(id).second)
                } finally { app.notes.deleteForever(id); app.notebooks.deleteForever(nb) }
            }
            return
        }
        val dir = File(app.filesDir, "recovery-e2e").apply { mkdirs() }
        val stateFile = File(dir, "state.json")
        if (stage == "prepare") runBlocking {
            val (blocks, page) = ComplexFixture.create(app)
            val nb = app.notebooks.create("Process recovery test ${System.currentTimeMillis()}")
            val id = app.notes.create(nb, "Recovery fixture", DocumentSerializer.serialize(blocks, page))
            app.comments.add(id, "Comment preserved")
            app.tags.assign(id, "recovery-test")
            app.prefs.setAppLock(false); app.prefs.setAutosave(false)
            stateFile.writeText(JSONObject().put("notebook", nb).put("note", id).put("original", DocumentSerializer.serialize(blocks, page)).toString())
        }
        val state = JSONObject(stateFile.readText())
        val id = state.getLong("note")
        val (original, page) = DocumentSerializer.deserializeForEditing(state.getString("original"))
        fun expected(text: String, bold: Boolean) = original.map { if (it.id == "body") it.copy(runs = listOf(TextRun(text, bold = bold))) else it }
        fun verify(text: String, bold: Boolean) = runBlocking {
            val (note, recovered) = app.recovery.load(id)
            assertTrue("Must recover from journal, not lifecycle flush", recovered)
            val (actual, actualPage) = DocumentSerializer.deserializeForEditing(note.documentJson)
            assertEquals(expected(text, bold), actual); assertEquals(page, actualPage)
            assertEquals(1, app.db.commentDao().observeForNote(id).first().size)
            actual.filter { it.imagePath != null || it.drawingPath != null }.forEach {
                assertTrue(File(it.imagePath ?: it.drawingPath!!).length() > 0)
            }
        }
        if (stage != "prepare") verify(if (stage == "second") "Rapid edit final বাংলা" else "Second recovered edit", stage == "second")
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java)
            .putExtra(ReminderReceiver.EXTRA_NOTE_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(15000) { compose.onAllNodesWithTag("block-body").fetchSemanticsNodes().isNotEmpty() }
        if (stage == "verify") {
            compose.onNodeWithTag("block-body").assertTextEquals("Second recovered edit")
            scenario.close()
            runBlocking { app.prefs.setAutosave(true); app.notes.deleteForever(id); app.notebooks.deleteForever(state.getLong("notebook")) }
            File(dir, "verified").writeText("Force Stop and SIGKILL recovery verified")
            return
        }
        val text = if (stage == "prepare") "Rapid edit final বাংলা" else "Second recovered edit"
        listOf("Rapid one", "Rapid two", text).forEach { compose.onNodeWithTag("block-body").performTextReplacement(it) }
        compose.onNodeWithContentDescription("Bold", useUnmergedTree = true).performClick()
        // Bold toggle starts off in each new editor; second recovery must remove bold explicitly.
        if (stage == "second") compose.onNodeWithContentDescription("Bold", useUnmergedTree = true).performClick()
        compose.waitUntil(15000) { runBlocking {
            val draft = app.db.recoveryDao().get(id) ?: return@runBlocking false
            val body = DocumentSerializer.deserializeForEditing(draft.documentJson).first.first()
            body.plainText() == text && body.runs.all { it.bold == (stage == "prepare") }
        } }
        verify(text, stage == "prepare")
        // No close/back/onStop before the host kills us: main note must remain original.
        assertEquals(state.getString("original"), runBlocking { app.notes.getById(id) }!!.documentJson)
        File(dir, "ready").writeText(stage)
        Thread.sleep(120000) // instrumentation thread only; host must kill this process before timeout
        error("Host did not terminate the app")
    }
}
