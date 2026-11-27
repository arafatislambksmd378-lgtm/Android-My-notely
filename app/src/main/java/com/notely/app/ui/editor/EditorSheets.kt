package com.notely.app.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notely.app.NotelyApp
import com.notely.app.ai.AiAssistant
import com.notely.app.doc.*
import com.notely.app.export.Exporters
import com.notely.app.reminder.ReminderScheduler
import com.notely.app.ui.components.*
import com.notely.app.ui.theme.CardColors
import com.notely.app.ui.theme.LocalNotelyColors
import com.notely.app.ui.theme.PageColors
import com.notely.app.util.TimeFmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// =====================================================================
// Note options
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteOptionsSheet(
    app: NotelyApp,
    noteId: Long,
    onDismiss: () -> Unit,
    onPageSetup: () -> Unit,
    onFind: () -> Unit,
    onWordCount: () -> Unit,
    onComments: () -> Unit,
    onBookmarks: () -> Unit,
    onTags: () -> Unit,
    onExport: () -> Unit,
    onVersions: () -> Unit,
    onAi: () -> Unit,
    onTemplates: () -> Unit,
    onDelete: () -> Unit,
    onColor: (Int) -> Unit,
    onPin: () -> Unit,
    onFavorite: () -> Unit,
    onArchive: () -> Unit,
    onMove: (Long) -> Unit,
    onCopy: () -> Unit,
    onReadOnly: () -> Unit
) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val note by app.notes.observeById(noteId).collectAsStateWithLifecycle(initial = null)
    val notebooks by app.notebooks.observeActive().collectAsStateWithLifecycle(initial = emptyList())
    var showMove by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader(
                "Note options",
                (note?.title ?: "Untitled") + " · " + (note?.updatedAt?.let { TimeFmt.relative(it) } ?: ""),
                onDismiss
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel("Card color")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CardColors.forEachIndexed { i, col ->
                    ColorDot(col, (note?.cardColorIndex ?: 0) == i) { onColor(i) }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = c.border)
            OptionRow(c.primary, "📄", "Page setup", "Paper, margins, header & footer") { onPageSetup() }
            OptionRow(c.primary, "🔍", "Find and replace", "Search inside this note") { onFind() }
            OptionRow(com.notely.app.ui.theme.AccentGreen, "🔢", "Word count", "Words, characters, pages") { onWordCount() }
            OptionRow(com.notely.app.ui.theme.AccentYellow, "✅", "Spelling and grammar", if (note != null) "On-device checker" else "") {
                // Toggle is global; the option offers the same switch for convenience.
            }
            OptionRow(com.notely.app.ui.theme.AccentPink, "🕐", "Version history", "Snapshots & restore") { onVersions() }
            HorizontalDivider(color = c.border)
            OptionRow(c.primary, "💬", "Comments", "Discuss this note") { onComments() }
            OptionRow(c.primary, "🔖", "Bookmarks", "Jump back to a spot") { onBookmarks() }
            OptionRow(com.notely.app.ui.theme.AccentBlue, "🏷", "Tags", "Label across notebooks") { onTags() }
            OptionRow(com.notely.app.ui.theme.AccentGreen, "📁", "Move to notebook", "Change the notebook") { showMove = true }
            OptionRow(com.notely.app.ui.theme.AccentOrange, "⏰", "Reminder", "Notify me later") { showReminder = true }
            OptionRow(c.primary, "↗", "Share & export", "PDF, DOCX, ODT, RTF, HTML, MD, TXT") { onExport() }
            OptionRow(com.notely.app.ui.theme.AccentBlue, "👁", "Reading view", "Multi-page preview & print") { onReadOnly() }
            OptionRow(com.notely.app.ui.theme.AccentGreen, "✨", "AI assistant", "Optional · needs your API key") { onAi() }
            OptionRow(c.primary, "📋", "Templates", "Insert a template's content") { onTemplates() }
            HorizontalDivider(color = c.border)
            OptionRow(c.primary, "📌", if (note?.pinned == true) "Unpin note" else "Pin to top", "") { onPin(); onDismiss() }
            OptionRow(com.notely.app.ui.theme.AccentYellow, "⭐", if (note?.favorite == true) "Remove favorite" else "Mark favorite", "") { onFavorite(); onDismiss() }
            OptionRow(com.notely.app.ui.theme.AccentBlue, "📦", "Archive note", "Keep out of the way") { onArchive() }
            OptionRow(c.primary, "⧉", "Make a copy", "Duplicates tags too") { onCopy() }
            OptionRow(Color(0xFFEF5350), "🗑", "Delete note", "Moves to trash", danger = true) { onDelete() }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showMove) {
        AlertDialog(
            onDismissRequest = { showMove = false },
            title = { Text("Move to notebook") },
            text = {
                Column {
                    notebooks.forEach { nb ->
                        TextButton(onClick = { onMove(nb.id); showMove = false }) { Text(nb.name) }
                    }
                    if (notebooks.isEmpty()) Text("No notebooks yet.", fontSize = 13.sp, color = c.text2)
                }
            },
            confirmButton = { TextButton(onClick = { showMove = false }) { Text("Cancel") } }
        )
    }
    if (showReminder) {
        ReminderDialog(
            onDismiss = { showReminder = false },
            onSet = { at, label ->
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        app.notes.setReminder(noteId, at)
                        app.reminders.add(noteId, at, label)
                    }
                }
                ReminderScheduler.schedule(context, noteId, at, label)
                showReminder = false
            },
            onClear = {
                scope.launch(Dispatchers.IO) {
                    app.notes.setReminder(noteId, null)
                    app.reminders.clearForNote(noteId)
                }
                ReminderScheduler.cancel(context, noteId)
                showReminder = false
            }
        )
    }
}

@Composable
fun OptionRow(iconColor: Color, icon: String, label: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = LocalNotelyColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClayIconTile(iconColor, size = 36) { Text(icon, fontSize = 15.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = if (danger) Color(0xFFEF5350) else c.text, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", fontSize = 18.sp, color = c.text2)
    }
}

@Composable
private fun ReminderDialog(onDismiss: () -> Unit, onSet: (Long, String) -> Unit, onClear: () -> Unit) {
    var minutes by remember { mutableStateOf("60") }
    var label by remember { mutableStateOf("Note reminder") }
    var error by remember { mutableStateOf("") }
    val c = LocalNotelyColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set reminder") },
        text = {
            Column {
                Text("Notify me in", fontSize = 13.sp, color = c.text2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10L, 60L, 180L, 1440L).forEach { m ->
                        ClayPill(
                            if (m >= 1440) "1 day" else if (m >= 60) "${m / 60} h" else "$m min",
                            minutes == m.toString()
                        ) { minutes = m.toString() }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    minutes, { minutes = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Custom minutes") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(label, { label = it }, label = { Text("Reminder text") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                if (error.isNotBlank()) Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                Text(
                    "Reminders use your device alarm clock and a local notification. No account is needed.",
                    fontSize = 11.sp, color = c.text2
                )
            }
        },
        confirmButton = {
            ClayPrimaryButton("Set reminder") {
                val mins = minutes.toLongOrNull()
                if (mins == null || mins <= 0) {
                    error = "Enter a positive number of minutes."
                } else {
                    onSet(System.currentTimeMillis() + mins * 60_000L, label.ifBlank { "Note reminder" })
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear reminder") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

// =====================================================================
// Page setup
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageSetupSheet(initial: PageSetup, onDismiss: () -> Unit, onSave: (PageSetup) -> Unit) {
    val c = LocalNotelyColors.current
    var orientation by remember { mutableStateOf(initial.orientation) }
    var paper by remember { mutableStateOf(initial.paperSize) }
    var customW by remember { mutableStateOf(initial.customWidthMm.toInt().toString()) }
    var customH by remember { mutableStateOf(initial.customHeightMm.toInt().toString()) }
    var mt by remember { mutableStateOf(initial.marginTop.toString()) }
    var mb by remember { mutableStateOf(initial.marginBottom.toString()) }
    var ml by remember { mutableStateOf(initial.marginLeft.toString()) }
    var mr by remember { mutableStateOf(initial.marginRight.toString()) }
    var pageColorIndex by remember {
        mutableStateOf(PageColors.indexOfFirst { it.toArgb() == initial.pageColorArgb }.coerceAtLeast(0))
    }
    var border by remember { mutableStateOf(initial.pageBorder) }
    var header by remember { mutableStateOf(initial.header) }
    var footer by remember { mutableStateOf(initial.footer) }
    var pageNumbers by remember { mutableStateOf(initial.pageNumbers) }
    var numberPos by remember { mutableStateOf(initial.pageNumberPosition) }
    var lineSpacing by remember { mutableStateOf(initial.lineSpacing) }
    var watermark by remember { mutableStateOf(initial.watermark) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader("Page setup", "Applies to this note only", onDismiss)
            Spacer(Modifier.height(10.dp))
            SectionLabel("Orientation")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClayPill("Portrait", orientation == "portrait") { orientation = "portrait" }
                ClayPill("Landscape", orientation == "landscape") { orientation = "landscape" }
            }
            Spacer(Modifier.height(10.dp))
            SectionLabel("Paper size")
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("A4", "A3", "A5", "Letter", "Legal", "Custom")) { p ->
                    ClayPill(p, paper == p) { paper = p }
                }
            }
            if (paper == "Custom") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        customW, { customW = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Width mm") }, singleLine = true, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        customH, { customH = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Height mm") }, singleLine = true, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SectionLabel("Margins (pt)")
            MarginRow("Top", mt) { mt = it }
            MarginRow("Bottom", mb) { mb = it }
            MarginRow("Left", ml) { ml = it }
            MarginRow("Right", mr) { mr = it }
            Spacer(Modifier.height(10.dp))
            SectionLabel("Page color")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PageColors.forEachIndexed { i, col ->
                    ColorDot(col, pageColorIndex == i) { pageColorIndex = i }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Page border", fontSize = 14.sp, color = c.text)
                Switch(border, { border = it })
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("Header & footer")
            OutlinedTextField(header, { header = it }, label = { Text("Header (e.g. Product strategy)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(footer, { footer = it }, label = { Text("Footer (e.g. Confidential)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Page numbers", fontSize = 14.sp, color = c.text)
                Switch(pageNumbers, { pageNumbers = it })
            }
            if (pageNumbers) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClayPill("Top", numberPos == "top") { numberPos = "top" }
                    ClayPill("Bottom", numberPos == "bottom") { numberPos = "bottom" }
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("Line spacing")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1.0f, 1.15f, 1.3f, 1.55f, 1.8f, 2.0f).forEach { v ->
                    ClayPill("${v}×", lineSpacing == v) { lineSpacing = v }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(watermark, { watermark = it }, label = { Text("Watermark (e.g. DRAFT)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(14.dp))
            ClayPrimaryButton("Apply page setup") {
                onSave(
                    PageSetup(
                        orientation = orientation, paperSize = paper,
                        customWidthMm = customW.toFloatOrNull() ?: 210f,
                        customHeightMm = customH.toFloatOrNull() ?: 297f,
                        marginTop = mt.toIntOrNull() ?: 48, marginBottom = mb.toIntOrNull() ?: 48,
                        marginLeft = ml.toIntOrNull() ?: 48, marginRight = mr.toIntOrNull() ?: 48,
                        pageColorArgb = PageColors[pageColorIndex.coerceIn(0, PageColors.size - 1)].toArgb(),
                        pageBorder = border, header = header, footer = footer,
                        pageNumbers = pageNumbers, pageNumberPosition = numberPos,
                        lineSpacing = lineSpacing, watermark = watermark
                    )
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
fun MarginRow(label: String, value: String, onChange: (String) -> Unit) {
    val c = LocalNotelyColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = c.text)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−") { onChange(((value.toIntOrNull() ?: 48) - 4).coerceAtLeast(0).toString()) }
            Text(value, fontSize = 15.sp, color = c.text, modifier = Modifier.padding(horizontal = 14.dp))
            StepButton("+") { onChange(((value.toIntOrNull() ?: 48) + 4).coerceAtMost(240).toString()) }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val c = LocalNotelyColors.current
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.surface2).clayPress(onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 16.sp, color = c.text) }
}

// =====================================================================
// Find & replace
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindReplaceSheet(
    blocks: List<DocumentBlock>,
    onDismiss: () -> Unit,
    onReplaceAll: (List<DocumentBlock>) -> Unit,
    onJump: (Int, Int, Int) -> Unit
) {
    val c = LocalNotelyColors.current
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var currentIdx by remember { mutableStateOf(0) }
    val matches = remember(blocks, find, matchCase) { FindReplace.find(blocks, find, matchCase) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            SheetHeader("Find and replace", "${matches.size} match(es)", onDismiss)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                find, { find = it; currentIdx = 0 },
                label = { Text("Find in note") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                replace, { replace = it },
                label = { Text("Replace with") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(14.dp)
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Match case", fontSize = 14.sp, color = c.text)
                Switch(matchCase, { matchCase = it; currentIdx = 0 })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClaySoftButton("⌃ Previous") {
                    currentIdx = (currentIdx - 1).coerceAtLeast(0)
                    matches.getOrNull(currentIdx)?.let { onJump(it.blockIndex, it.start, it.end) }
                }
                ClaySoftButton("⌄ Next") {
                    if (matches.isNotEmpty()) currentIdx = (currentIdx + 1) % matches.size
                    matches.getOrNull(currentIdx)?.let { onJump(it.blockIndex, it.start, it.end) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClaySoftButton("Replace") {
                    matches.getOrNull(currentIdx)?.let { m ->
                        val newBlocks = blocks.toMutableList()
                        newBlocks[m.blockIndex] = FindReplace.replaceInBlock(newBlocks[m.blockIndex], m.start, m.end, replace)
                        onReplaceAll(newBlocks)
                        currentIdx = 0
                    }
                }
                ClayPrimaryButton("Replace all") {
                    onReplaceAll(FindReplace.replaceAll(blocks, find, replace, matchCase))
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

// =====================================================================
// Comments
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(app: NotelyApp, noteId: Long, onDismiss: () -> Unit) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val comments by app.comments.observeForNote(noteId).collectAsStateWithLifecycle(initial = emptyList())
    var draft by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editText by remember { mutableStateOf("") }
    var showResolved by remember { mutableStateOf(true) }
    val visible = comments.filter { showResolved || !it.resolved }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader("Comments", "${comments.count { !it.resolved }} open · ${comments.size} total", onDismiss)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                draft, { draft = it }, label = { Text("Write a comment…") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(8.dp))
            ClayPrimaryButton("+ Add comment", enabled = draft.isNotBlank()) {
                scope.launch { app.comments.add(noteId, draft.trim()) }
                draft = ""
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClayPill("All", showResolved) { showResolved = true }
                ClayPill("Open only", !showResolved) { showResolved = false }
            }
            Spacer(Modifier.height(8.dp))
            if (visible.isEmpty()) {
                Text("No comments yet. Comments are stored with this note.", fontSize = 13.sp, color = c.text2)
            }
            visible.forEach { cm ->
                ClayCard(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClayIconTile(c.primary, size = 34) { Text("Y", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.primary) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cm.author, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(TimeFmt.relative(cm.createdAt), fontSize = 11.sp, color = c.text2)
                        }
                        if (cm.resolved) ClayPill("Resolved", true) { scope.launch { app.comments.setResolved(cm.id, false) } }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (editingId == cm.id) {
                        OutlinedTextField(editText, { editText = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                        Row {
                            ClayPrimaryButton("Save") {
                                scope.launch { app.comments.edit(cm.id, editText) }
                                editingId = null
                            }
                            Spacer(Modifier.width(8.dp))
                            ClaySoftButton("Cancel") { editingId = null }
                        }
                    } else {
                        Text(cm.text, fontSize = 14.sp, color = c.text)
                        Row {
                            TextButton(onClick = { scope.launch { app.comments.setResolved(cm.id, !cm.resolved) } }) {
                                Text(if (cm.resolved) "Reopen" else "✓ Resolve")
                            }
                            TextButton(onClick = { editingId = cm.id; editText = cm.text }) { Text("Edit") }
                            TextButton(onClick = { scope.launch { app.comments.delete(cm.id) } }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

// =====================================================================
// Bookmarks
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    app: NotelyApp,
    noteId: Long,
    blocks: List<DocumentBlock>,
    onJump: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val bookmarks by app.bookmarks.observeForNote(noteId).collectAsStateWithLifecycle(initial = emptyList())
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader("Bookmarks", "Jump back to a spot in this note", onDismiss)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    name, { name = it }, label = { Text("Bookmark name") },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                ClayPrimaryButton("Add", enabled = name.isNotBlank()) {
                    scope.launch { app.bookmarks.add(noteId, name.trim(), "") }
                    name = ""
                }
            }
            Spacer(Modifier.height(10.dp))
            if (bookmarks.isEmpty()) Text("No bookmarks yet.", fontSize = 13.sp, color = c.text2)
            bookmarks.forEach { b ->
                ClayCard(Modifier.padding(bottom = 8.dp), onClick = { onJump(b.blockId) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ClayIconTile(c.primary, size = 34) { Text("🔖", fontSize = 14.sp) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(
                                if (b.blockId.isNotBlank() && blocks.any { it.id == b.blockId }) "Tap to jump" else TimeFmt.relative(b.createdAt),
                                fontSize = 11.sp, color = c.text2
                            )
                        }
                        TextButton(onClick = { scope.launch { app.bookmarks.delete(b.id) } }) {
                            Text("🗑", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

// =====================================================================
// Tags
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsSheet(app: NotelyApp, noteId: Long, onDismiss: () -> Unit) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val noteTags by app.tags.tagsForNote(noteId).collectAsStateWithLifecycle(initial = emptyList())
    val allTags by app.tags.observeAll().collectAsStateWithLifecycle(initial = emptyList())
    var draft by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader("Tags", "Describe this note across notebooks", onDismiss)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                noteTags.forEach { t ->
                    TagChip(t.name) { scope.launch { app.tags.unassign(noteId, t.id) } }
                }
                if (noteTags.isEmpty()) Text("No tags on this note yet.", fontSize = 13.sp, color = c.text2)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    draft, { draft = it }, label = { Text("New tag, e.g. research") },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                ClayPrimaryButton("+", enabled = draft.isNotBlank()) {
                    scope.launch { app.tags.assign(noteId, draft.trim()) }
                    draft = ""
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("All tags")
            allTags.forEach { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("#${t.name}", fontSize = 14.sp, color = c.text, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { app.tags.assign(noteId, t.name) } }) { Text("Assign") }
                    TextButton(onClick = { scope.launch { app.tags.deleteTag(t.id) } }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

// =====================================================================
// Export & print
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    app: NotelyApp,
    noteId: Long,
    title: String,
    blocks: List<DocumentBlock>,
    pageSetup: PageSetup,
    onDismiss: () -> Unit
) {
    val c = LocalNotelyColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var lastFile by remember { mutableStateOf<File?>(null) }
    var lastFormat by remember { mutableStateOf<Exporters.Format?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = lastFile
        val fmt = lastFormat
        if (uri != null && file != null && file.exists()) {
            scope.launch(Dispatchers.IO) {
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { input -> input.copyTo(out) }
                    } != null
                }.getOrDefault(false)
                withContext(Dispatchers.Main) {
                    status = if (ok) "Saved to the folder you chose." else "Saving failed."
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader("Share & export", "Keeps your formatting", onDismiss)
            Spacer(Modifier.height(10.dp))
            Text(
                "Files are generated on this device in the real format — not renamed copies.",
                fontSize = 11.sp, color = c.text2
            )
            Spacer(Modifier.height(8.dp))
            Exporters.formats.forEach { fmt ->
                val icon = when (fmt.ext) {
                    "pdf" -> "📕"; "docx" -> "📘"; "odt" -> "📗"
                    "rtf" -> "📙"; "html" -> "🌐"; "md" -> "Ⓜ"; else -> "📄"
                }
                ClayCard(Modifier.padding(bottom = 8.dp), onClick = {
                    busy = fmt.label
                    status = ""
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching {
                            Exporters.export(context, title, blocks, pageSetup, fmt)
                        }
                        withContext(Dispatchers.Main) {
                            busy = null
                            result.onSuccess { file ->
                                lastFile = file
                                lastFormat = fmt
                                status = "Saved ${file.name} (${file.length() / 1024} KB)"
                                runCatching { Exporters.share(context, file, fmt.mime, title) }
                            }.onFailure { e ->
                                status = "Export failed: ${e.message ?: "unknown error"}"
                            }
                        }
                    }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClayIconTile(c.primary, size = 38) { Text(icon, fontSize = 16.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(fmt.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.text)
                            Text(
                                if (busy == fmt.label) "Building…" else "Share, save or open .${fmt.ext}",
                                fontSize = 11.sp, color = c.text2
                            )
                        }
                        Text("↗", fontSize = 15.sp, color = c.text2)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClaySoftButton("Save a copy to…") {
                    val fmt = lastFormat
                    val file = lastFile
                    if (fmt != null && file != null) {
                        saveLauncher.launch("${Exporters.safeName(title)}.${fmt.ext}")
                    } else {
                        status = "Generate a format first, then save it to a folder."
                    }
                }
                ClaySoftButton("Print PDF") {
                    scope.launch(Dispatchers.IO) {
                        val result = runCatching {
                            Exporters.export(context, title, blocks, pageSetup, Exporters.formats.first { it.ext == "pdf" })
                        }
                        withContext(Dispatchers.Main) {
                            result.onSuccess { file ->
                                lastFile = file
                                lastFormat = Exporters.formats.first { it.ext == "pdf" }
                                status = "Opening print dialog for ${file.name}"
                                Exporters.share(context, file, Exporters.MIME_PDF, title)
                            }.onFailure { status = "Print failed: ${it.message}" }
                        }
                    }
                }
            }
            if (lastFile != null) {
                Spacer(Modifier.height(8.dp))
                ClaySoftButton("Open exported file") {
                    val file = lastFile ?: return@ClaySoftButton
                    Exporters.open(context, file, lastFormat?.mime ?: "*/*")
                }
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(status, fontSize = 12.sp, color = c.text2)
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

// =====================================================================
// Version history
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionsSheet(
    app: NotelyApp,
    noteId: Long,
    onDismiss: () -> Unit,
    onRestored: () -> Unit
) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val versions by app.versions.observeForNote(noteId).collectAsStateWithLifecycle(initial = emptyList())
    var preview by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<Pair<Long, String>?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader("Version history", "Snapshots kept for this note (up to 30)", onDismiss)
            Spacer(Modifier.height(10.dp))
            ClayPrimaryButton("Save a snapshot now") {
                scope.launch(Dispatchers.IO) { runCatching { app.notes.snapshot(noteId) } }
            }
            Spacer(Modifier.height(10.dp))
            if (versions.isEmpty()) {
                Text(
                    "No snapshots yet. A snapshot stores the full document (blocks + page setup) so you can restore it later.",
                    fontSize = 13.sp, color = c.text2
                )
            }
            versions.forEach { v ->
                ClayCard(Modifier.padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(TimeFmt.relative(v.createdAt), fontSize = 14.sp, color = c.text, fontWeight = FontWeight.Medium)
                            Text("${v.documentJson.length / 1024} KB snapshot", fontSize = 11.sp, color = c.text2)
                        }
                        TextButton(onClick = { preview = if (preview == v.documentJson) null else v.documentJson }) {
                            Text(if (preview == v.documentJson) "Hide" else "Preview")
                        }
                        TextButton(onClick = { confirmRestore = v.id to v.documentJson }) { Text("Restore") }
                        TextButton(onClick = { scope.launch { app.versions.delete(v.id) } }) {
                            Text("✕", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    preview?.let { json ->
                        if (json == v.documentJson) {
                            val (b, _) = remember(json) { DocumentSerializer.deserialize(json) }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                DocumentSerializer.plainText(b).take(700).ifBlank { "(empty snapshot)" },
                                fontSize = 12.sp, color = c.text2
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
    confirmRestore?.let { (id, json) ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this version?") },
            text = { Text("Your current content will be replaced by this snapshot. A snapshot of the current state is saved first, so you can undo this.") },
            confirmButton = {
                ClayPrimaryButton("Restore") {
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            app.notes.snapshot(noteId)
                            app.notes.restoreVersion(noteId, json)
                        }
                        withContext(Dispatchers.Main) {
                            confirmRestore = null
                            onRestored()
                            onDismiss()
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } }
        )
    }
}

// =====================================================================
// AI
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSheet(
    app: NotelyApp,
    selectedText: String,
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit,
    onReplace: (String) -> Unit
) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val enabled by app.prefs.aiEnabled.collectAsStateWithLifecycle(initial = false)
    val baseUrl by app.prefs.aiBaseUrl.collectAsStateWithLifecycle(initial = "")
    val model by app.prefs.aiModel.collectAsStateWithLifecycle(initial = "")
    var action by remember { mutableStateOf(AiAssistant.Action.REWRITE) }
    var input by remember(selectedText) { mutableStateOf(selectedText.take(4000)) }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var confirmReplace by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader("AI assistant", "Optional · works only when you configure a provider", onDismiss)
            Spacer(Modifier.height(10.dp))
            if (!enabled) {
                ClayCard {
                    Text("AI is turned off", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Text(
                        "Enable it in Settings → AI assistant and add your own provider URL, model and key. " +
                            "The editor, search, exports and every other feature work fully without it.",
                        fontSize = 13.sp, color = c.text2
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiAssistant.Action.entries.toList()) { a ->
                        ClayPill(a.name.lowercase().replaceFirstChar { it.uppercase() }, action == a) { action = a }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    input, { input = it }, label = { Text("Text to work on") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 200.dp),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClayPrimaryButton(if (busy) "Working…" else "Generate", enabled = !busy && input.isNotBlank()) {
                        busy = true; error = ""; output = ""
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                val key = app.prefs.getAiApiKey()
                                val prompt = AiAssistant.promptFor(action, input)
                                AiAssistant.callProvider(baseUrl, model, key, prompt)
                            }.onSuccess { result ->
                                withContext(Dispatchers.Main) { output = result; busy = false }
                            }.onFailure { e ->
                                withContext(Dispatchers.Main) { error = e.message ?: "AI request failed"; busy = false }
                            }
                        }
                    }
                    if (output.isNotBlank()) {
                        ClaySoftButton("Regenerate") { output = ""; busy = false }
                    }
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    Text(
                        "Check the provider URL/model/key in Settings. Nothing was changed in your note.",
                        fontSize = 11.sp, color = c.text2
                    )
                }
                if (output.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    ClayCard {
                        Text(output, fontSize = 14.sp, color = c.text)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClayPrimaryButton("Insert below") { onInsert(output) }
                        ClaySoftButton("Replace note text") { confirmReplace = true }
                        ClaySoftButton("Copy") {
                            val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("ai", output))
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace the document text?") },
            text = { Text("Every text block will be replaced by the AI output. Tables, images, drawings, charts, comments and page setup are kept. This can be undone with the editor's undo.") },
            confirmButton = {
                ClayPrimaryButton("Replace") { confirmReplace = false; onReplace(output) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } }
        )
    }
}

// =====================================================================
// Templates
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesSheet(app: NotelyApp, onDismiss: () -> Unit, onUse: (String) -> Unit, onSaveCurrent: () -> Unit) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val templates by app.templates.observeAll().collectAsStateWithLifecycle(initial = emptyList())
    var saved by remember { mutableStateOf("") }
    var previewJson by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader("Templates", "Insert a template's content into this note", onDismiss)
            Spacer(Modifier.height(10.dp))
            ClayPrimaryButton("Save current document as template") {
                scope.launch(Dispatchers.IO) {
                    runCatching { onSaveCurrent() }
                    withContext(Dispatchers.Main) { saved = "Saved to your templates." }
                }
            }
            if (saved.isNotBlank()) Text(saved, fontSize = 12.sp, color = com.notely.app.ui.theme.AccentGreen)
            Spacer(Modifier.height(10.dp))
            templates.forEach { t ->
                ClayCard(Modifier.padding(bottom = 8.dp), onClick = { previewJson = if (previewJson == t.documentJson) null else t.documentJson }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.text)
                            Text(
                                if (t.builtin) "Built-in template" else "Custom template",
                                fontSize = 11.sp, color = c.text2
                            )
                        }
                        if (!t.builtin) {
                            TextButton(onClick = { scope.launch { app.templates.delete(t.id) } }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (previewJson == t.documentJson) {
                        val (b, _) = remember(t.documentJson) { DocumentSerializer.deserialize(t.documentJson) }
                        Text(
                            DocumentSerializer.plainText(b).take(400).ifBlank { "(blank document)" },
                            fontSize = 12.sp, color = c.text2
                        )
                        Spacer(Modifier.height(6.dp))
                        ClayPrimaryButton("Insert template") { onUse(t.documentJson) }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

// =====================================================================
// Insert panel (reference grid)
// =====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsertSheet(
    app: NotelyApp,
    onDismiss: () -> Unit,
    onImageGallery: () -> Unit, onImageMulti: () -> Unit, onImageCamera: () -> Unit,
    onTable: () -> Unit, onDrawing: () -> Unit, onChart: () -> Unit,
    onLink: () -> Unit, onComment: () -> Unit, onDivider: () -> Unit,
    onFootnote: () -> Unit, onContents: () -> Unit, onBookmark: () -> Unit,
    onPageBreak: () -> Unit, onDateTime: () -> Unit, onAttachment: () -> Unit,
    onShape: () -> Unit, onTextBox: () -> Unit, onSignature: () -> Unit,
    onWatermark: () -> Unit, onHeader: () -> Unit, onFooter: () -> Unit,
    onAudio: () -> Unit, onVideo: () -> Unit, onSymbols: () -> Unit,
    onEquation: () -> Unit, onHandwriting: () -> Unit, onOcr: () -> Unit, onScan: () -> Unit
) {
    val c = LocalNotelyColors.current
    var imageExpanded by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader("Insert", "Everything is stored in this note", onDismiss)
            Spacer(Modifier.height(12.dp))
            // Image group (expanded card, reference design)
            ClayCard {
                Row(Modifier.fillMaxWidth().clickable { imageExpanded = !imageExpanded }, verticalAlignment = Alignment.CenterVertically) {
                    ClayIconTile(com.notely.app.ui.theme.AccentPink, size = 38) { Text("🖼", fontSize = 16.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Image", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                        Text("From gallery or camera", fontSize = 11.sp, color = c.text2)
                    }
                    Text(if (imageExpanded) "⌃" else "⌄", fontSize = 15.sp, color = c.text2)
                }
                if (imageExpanded) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClaySoftButton("Gallery") { onDismiss(); onImageGallery() }
                        ClaySoftButton("Multiple") { onDismiss(); onImageMulti() }
                        ClaySoftButton("Camera") { onDismiss(); onImageCamera() }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val items = listOf(
                InsertItem("▦", "Table", "Rows & columns", com.notely.app.ui.theme.AccentBlue, onTable),
                InsertItem("✏", "Drawing", "Sketch & ink", com.notely.app.ui.theme.AccentBlue, onDrawing),
                InsertItem("📊", "Chart", "Graphs from data", com.notely.app.ui.theme.AccentGreen, onChart),
                InsertItem("🔗", "Link", "Hyperlink text", com.notely.app.ui.theme.AccentYellow, onLink),
                InsertItem("💬", "Comment", "Side note on text", com.notely.app.ui.theme.AccentPink, onComment),
                InsertItem("➖", "Divider", "Horizontal line", com.notely.app.ui.theme.AccentTeal, onDivider),
                InsertItem("📖", "Footnote", "Bottom reference", c.primary, onFootnote),
                InsertItem("π", "Symbols", "π © → ∀ Σ", com.notely.app.ui.theme.AccentBlue, onSymbols),
                InsertItem("√", "Equation", "Math & science", com.notely.app.ui.theme.AccentGreen, onEquation),
                InsertItem("📑", "Contents", "Auto from headings", com.notely.app.ui.theme.AccentYellow, onContents),
                InsertItem("🔖", "Bookmark", "Save position", com.notely.app.ui.theme.AccentPink, onBookmark),
                InsertItem("📄", "Page break", "New page", com.notely.app.ui.theme.AccentTeal, onPageBreak),
                InsertItem("🕐", "Date & time", "Now", c.primary, onDateTime),
                InsertItem("📎", "Attachment", "Any file", com.notely.app.ui.theme.AccentBlue, onAttachment),
                InsertItem("◼", "Shape", "Rectangle & more", com.notely.app.ui.theme.AccentGreen, onShape),
                InsertItem("▭", "Text box", "Floating text", com.notely.app.ui.theme.AccentYellow, onTextBox),
                InsertItem("✍", "Signature", "Sign here", com.notely.app.ui.theme.AccentPink, onSignature),
                InsertItem("💧", "Watermark", "DRAFT overlay", com.notely.app.ui.theme.AccentTeal, onWatermark),
                InsertItem("⬆", "Header", "Page header", c.primary, onHeader),
                InsertItem("⬇", "Footer", "Page footer", com.notely.app.ui.theme.AccentBlue, onFooter),
                InsertItem("🎵", "Audio note", "Voice memo", com.notely.app.ui.theme.AccentGreen, onAudio),
                InsertItem("🎬", "Video", "Attach video", com.notely.app.ui.theme.AccentYellow, onVideo),
                InsertItem("🖊", "Handwriting", "Write & insert", com.notely.app.ui.theme.AccentPink, onHandwriting),
                InsertItem("🔤", "OCR", "Recognize text", com.notely.app.ui.theme.AccentTeal, onOcr),
                InsertItem("📷", "Scan document", "Camera scanner", c.primary, onScan)
            )
            items.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        InsertTile(item) { onDismiss(); item.onClick() }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "OCR and handwriting run on-device. Video and audio are attached as files; " +
                    "export formats store them as references, not embedded players.",
                fontSize = 11.sp, color = c.text2
            )
            Spacer(Modifier.height(26.dp))
        }
    }
}

data class InsertItem(
    val icon: String,
    val title: String,
    val subtitle: String,
    val color: Color,
    val onClick: () -> Unit
)

/** Row-scoped so each tile can carry `Modifier.weight(1f)` from its caller. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.InsertTile(item: InsertItem, onTap: () -> Unit) {
    val c = LocalNotelyColors.current
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .clayPress(onTap)
            .padding(10.dp)
    ) {
        ClayIconTile(item.color, size = 34) { Text(item.icon, fontSize = 15.sp) }
        Spacer(Modifier.height(6.dp))
        Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.subtitle, fontSize = 9.sp, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// =====================================================================
// Small dialogs
// =====================================================================
@Composable
fun TableDialog(onDismiss: () -> Unit, onInsert: (Int, Int, Boolean) -> Unit) {
    var rows by remember { mutableStateOf(3) }
    var cols by remember { mutableStateOf(3) }
    var header by remember { mutableStateOf(true) }
    val c = LocalNotelyColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert table") },
        text = {
            Column {
                CounterRow("Rows", rows, 1, 20) { rows = it }
                CounterRow("Columns", cols, 1, 10) { cols = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Header row", modifier = Modifier.weight(1f), color = c.text, fontSize = 14.sp)
                    Switch(header, { header = it })
                }
            }
        },
        confirmButton = { ClayPrimaryButton("Insert table") { onInsert(rows, cols, header) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CounterRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val c = LocalNotelyColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = c.text, fontSize = 14.sp)
        StepButton("−") { onChange((value - 1).coerceAtLeast(min)) }
        Text("$value", fontSize = 16.sp, color = c.text, modifier = Modifier.padding(horizontal = 14.dp))
        StepButton("+") { onChange((value + 1).coerceAtMost(max)) }
    }
}

@Composable
fun ChartDialog(
    initial: ChartData?,
    onDismiss: () -> Unit,
    onInsert: (String, String, List<String>, List<Double>) -> Unit
) {
    val c = LocalNotelyColors.current
    var kind by remember { mutableStateOf(initial?.kind ?: "bar") }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var labels by remember { mutableStateOf(initial?.labels?.joinToString(", ") ?: "") }
    var values by remember { mutableStateOf(initial?.values?.joinToString(", ") ?: "") }
    val parsedValues = remember(values) { values.split(",", "\n").mapNotNull { it.trim().toDoubleOrNull() } }
    val parsedLabels = remember(labels) { labels.split(",").map { it.trim() }.filter { it.isNotEmpty() } }
    val valid = parsedValues.isNotEmpty() &&
        (parsedLabels.isEmpty() || parsedLabels.size == parsedValues.size)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Insert chart" else "Edit chart") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("bar", "column", "line", "area", "pie").forEach { k ->
                        ClayPill(k.replaceFirstChar { it.uppercase() }, kind == k) { kind = k }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(title, { title = it }, label = { Text("Chart title") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(labels, { labels = it }, label = { Text("Labels (comma separated)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(values, { values = it }, label = { Text("Values (comma separated numbers)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Text(
                    when {
                        parsedValues.isEmpty() -> "Enter at least one numeric value."
                        parsedLabels.isNotEmpty() && parsedLabels.size != parsedValues.size ->
                            "Labels (${parsedLabels.size}) and values (${parsedValues.size}) must match."
                        else -> "${parsedValues.size} value(s) detected"
                    },
                    fontSize = 11.sp,
                    color = if (valid) c.text2 else MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            ClayPrimaryButton(if (initial == null) "Insert chart" else "Save chart", enabled = valid) {
                val finalLabels = if (parsedLabels.size == parsedValues.size) parsedLabels
                else parsedValues.indices.map { (it + 1).toString() }
                onInsert(kind, title, finalLabels, parsedValues)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LinkDialog(onDismiss: () -> Unit, onInsert: (String, String) -> Unit) {
    var url by remember { mutableStateOf("https://") }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val c = LocalNotelyColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert link") },
        text = {
            Column {
                OutlinedTextField(url, { url = it; error = "" }, label = { Text("Link address") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(text, { text = it }, label = { Text("Text to display") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Text("Optional — uses the address", fontSize = 11.sp, color = c.text2)
                if (error.isNotBlank()) Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            ClayPrimaryButton("Insert link") {
                val trimmed = url.trim()
                if (!(trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("mailto:"))) {
                    error = "Use an http(s):// or mailto: address."
                } else {
                    onInsert(trimmed, text.ifBlank { trimmed })
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun FootnoteDialog(onDismiss: () -> Unit, onInsert: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert footnote") },
        text = {
            OutlinedTextField(
                text, { text = it },
                label = { Text("Source, clarification or reference") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                shape = RoundedCornerShape(14.dp)
            )
        },
        confirmButton = { ClayPrimaryButton("Insert footnote", enabled = text.isNotBlank()) { onInsert(text.trim()) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BookmarkDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val c = LocalNotelyColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bookmark this spot") },
        text = {
            Column {
                Text(
                    "Bookmarks are stored with the note and appear in Note options → Bookmarks.",
                    fontSize = 12.sp, color = c.text2
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("e.g. Budget table") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = { ClayPrimaryButton("Add bookmark") { onSave(name.ifBlank { "Bookmark" }) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
