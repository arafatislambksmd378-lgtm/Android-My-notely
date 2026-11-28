package com.notely.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notely.app.doc.*
import com.notely.app.reminder.ReminderReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real editor interaction + Room persistence. Activity relaunch is NOT process death. */
@RunWith(AndroidJUnit4::class)
class EditorSaveInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun aboutVersionResourceMatchesInstalledApk() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        assertEquals(installed, context.getString(R.string.app_version_name))
    }

    @Test fun immediateBackFlushSurvivesActivityRelaunchWithoutChangingRichContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context as NotelyApp
        val blocks = listOf(
            DocumentBlock(id = "heading", type = BlockType.HEADING, headingLevel = 1,
                runs = listOf(TextRun("Persistent heading", bold = true))),
            DocumentBlock(id = "paragraph", runs = listOf(TextRun("বাংলা paragraph", italic = true,
                highlightArgb = 0xFFF2C94C.toInt(), link = "https://example.com"))),
            DocumentBlock(id = "table", type = BlockType.TABLE,
                table = TableData(2, 2, true, listOf("A", "B", "1", "2"))),
            DocumentBlock(id = "chart", type = BlockType.CHART,
                chart = ChartData("bar", "Counts", listOf("A", "B"), listOf(1.0, 2.0))),
            DocumentBlock(id = "footnote", type = BlockType.FOOTNOTE, footnoteText = "Reference", footnoteRef = 1)
        )
        val page = PageSetup(orientation = "landscape", paperSize = "A5", header = "Header", pageNumbers = true)
        val original = DocumentSerializer.serialize(blocks, page)
        val notebook = runBlocking { app.notebooks.create("Editor save regression") }
        val note = runBlocking {
            app.prefs.setAppLock(false)
            app.prefs.setAutosave(false) // ensure leaving, not the debounce timer, must save
            app.notes.create(notebook, "Before", original)
        }
        fun open() = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra(ReminderReceiver.EXTRA_NOTE_ID, note).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            open().use {
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("editor-title").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("editor-title").performTextReplacement("Saved before leaving")
                compose.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("editor-title").fetchSemanticsNodes().isEmpty() }
            }
            assertEquals("Saved before leaving", runBlocking { app.notes.getById(note) }!!.title)
            open().use {
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("editor-title").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("editor-title").assertTextEquals("Saved before leaving")
                compose.onNodeWithTag("editor-title").performTextReplacement("Second saved title")
                compose.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("editor-title").fetchSemanticsNodes().isEmpty() }
            }
            val saved = runBlocking { app.notes.getById(note) }!!
            assertEquals("Second saved title", saved.title)
            val (actualBlocks, actualPage) = DocumentSerializer.deserializeForEditing(saved.documentJson)
            assertEquals(blocks, actualBlocks)
            assertEquals(page, actualPage)
        } finally {
            runBlocking {
                app.prefs.setAutosave(true)
                app.notes.deleteForever(note)
                app.notebooks.deleteForever(notebook)
            }
        }
    }
}
