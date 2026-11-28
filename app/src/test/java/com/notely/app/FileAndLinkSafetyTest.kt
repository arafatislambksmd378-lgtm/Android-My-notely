package com.notely.app

import com.notely.app.util.PrivateFiles
import com.notely.app.util.SafeLinks
import com.notely.app.export.Exporters
import com.notely.app.doc.*
import com.notely.app.ai.AiAssistant
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileAndLinkSafetyTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun repeatedNamesCannotOverwriteAndTraversalCannotEscape() {
        val dir = temp.newFolder()
        val first = PrivateFiles.copy("first".byteInputStream(), dir, "../../same.txt")
        val second = PrivateFiles.copy("second".byteInputStream(), dir, "../../same.txt")
        assertNotEquals(first, second)
        assertEquals(dir.canonicalFile, first.canonicalFile.parentFile)
        assertEquals("first", first.readText())
        assertEquals("second", second.readText())
    }

    @Test fun oversizedEmptyAndInterruptedImportsLeaveNoFiles() {
        val dir = temp.newFolder()
        assertTrue(runCatching { PrivateFiles.copy("too large".byteInputStream(), dir, "large", 2) }.isFailure)
        assertTrue(runCatching { PrivateFiles.copy("".byteInputStream(), dir, "empty") }.isFailure)
        assertTrue(runCatching {
            PrivateFiles.atomicWrite(java.io.File(dir, "broken")) { it.write(1); error("interrupted") }
        }.isFailure)
        assertEquals(0, dir.listFiles()!!.size)
    }

    @Test fun activeSchemesAndControlCharactersAreNotLinks() {
        listOf("javascript:alert(1)", "data:text/html,hi", "file:///private", "content://notes", "https://good.test\n.evil", "https://user:pass@example.com").forEach {
            assertFalse(it, SafeLinks.isAllowed(it))
        }
        assertTrue(SafeLinks.isAllowed("https://example.com/path?q=a&b=c"))
        assertTrue(SafeLinks.isAllowed("mailto:user@example.com"))
    }

    @Test fun htmlKeepsTextButDoesNotEmitActiveLink() {
        val output = Exporters.toHtml(listOf(DocumentBlock(runs = listOf(
            TextRun("keep this", link = "javascript:alert(1)")
        ))), PageSetup())
        assertTrue(output.contains("keep this"))
        assertFalse(output.contains("javascript:"))
        assertFalse(output.contains("href="))
    }

    @Test fun providerRequestEscapesAllControlCharactersAndModelNames() {
        val model = "quoted\"model\\name"
        val prompt = "tab\t nul\u0000 newline\nবাংলা"
        val root = JSONObject(AiAssistant.requestBody(model, prompt))
        assertEquals(model, root.getString("model"))
        assertEquals(prompt, root.getJSONArray("messages").getJSONObject(0).getString("content"))
    }
}
