package com.notely.app

import com.notely.app.doc.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StrictDocumentReadTest {
    private fun valid() = DocumentSerializer.serialize(listOf(DocumentBlock(
        id = "stable", runs = listOf(TextRun("বাংলা", bold = true, link = "https://example.com"))
    )), PageSetup(orientation = "landscape"))

    private fun rejects(json: String) {
        assertTrue("Unsafe document must not become editable", runCatching {
            DocumentSerializer.deserializeForEditing(json)
        }.isFailure)
    }

    @Test fun strictReadPreservesFormattingAndIds() {
        val (blocks, page) = DocumentSerializer.deserializeForEditing(valid())
        assertEquals("stable", blocks.single().id)
        assertEquals("বাংলা", blocks.single().plainText())
        assertTrue(blocks.single().runs.single().bold)
        assertEquals("landscape", page.orientation)
        assertEquals(valid(), DocumentSerializer.serialize(blocks, page))
    }

    @Test fun rejectsTruncatedAndMissingStructure() {
        rejects(valid().dropLast(2))
        rejects("{}")
        rejects("{\"blocks\":[42]}")
    }

    @Test fun rejectsFutureVersionAndBlockTypes() {
        rejects(JSONObject(valid()).put("v", 2).toString())
        val root = JSONObject(valid())
        root.getJSONArray("blocks").getJSONObject(0).put("type", "FUTURE_EMBED")
        rejects(root.toString())
    }

    @Test fun rejectsDuplicateIdsAndMalformedTable() {
        val root = JSONObject(valid())
        root.getJSONArray("blocks").put(root.getJSONArray("blocks").getJSONObject(0))
        rejects(root.toString())
        rejects(DocumentSerializer.serialize(listOf(DocumentBlock(type = BlockType.TABLE,
            table = TableData(2, 2, true, listOf("only one")))), PageSetup()))
    }

    @Test fun blankLegacyNewDocumentRemainsSupported() {
        assertTrue(DocumentSerializer.deserializeForEditing("").first.isEmpty())
        assertTrue(DocumentSerializer.deserializeForEditing(null).first.isEmpty())
    }
}
