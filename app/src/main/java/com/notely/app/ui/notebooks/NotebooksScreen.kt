package com.notely.app.ui.notebooks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notely.app.NotelyApp
import com.notely.app.data.db.NoteEntity
import com.notely.app.data.db.NotebookEntity
import com.notely.app.export.Exporters
import com.notely.app.ui.components.*
import com.notely.app.ui.home.NoteCard
import com.notely.app.ui.theme.CardColors
import com.notely.app.ui.theme.LocalNotelyColors
import com.notely.app.ui.theme.accentColor
import com.notely.app.util.TimeFmt
import kotlinx.coroutines.launch

enum class SortMode(val label: String) {
    RECENT("Updated"), CREATED("Created"), OLDEST("Oldest"), AZ("A–Z"), ZA("Z–A")
}

enum class FilterMode(val label: String) {
    ALL("All"), PINNED("Pinned"), FAVORITE("Favorite"), ARCHIVED("Archived"),
    IMAGES("With images"), TASKS("With tasks"), TAGS("With tags"), TRASH("Trash")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebooksScreen(app: NotelyApp, onOpenNote: (Long) -> Unit) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val notebooks by app.notebooks.observeActive().collectAsState(initial = emptyList())
    val allNotes by app.notes.observeActive().collectAsState(initial = emptyList())
    val archivedNotes by app.notes.observeArchived().collectAsState(initial = emptyList())
    val trashedNotes by app.notes.observeTrashed().collectAsState(initial = emptyList())
    val trashedNotebooks by app.notebooks.observeTrashed().collectAsState(initial = emptyList())
    val tags by app.tags.observeAll().collectAsState(initial = emptyList())
    val taggedNoteIds by (selectedTag?.let { app.tags.notesForTag(it) }
        ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())

    var selectedNb by remember { mutableStateOf<Long?>(null) }
    var sort by remember { mutableStateOf(SortMode.RECENT) }
    var filter by remember { mutableStateOf(FilterMode.ALL) }
    var selectedTag by remember { mutableStateOf<Long?>(null) }
    var bulkMode by remember { mutableStateOf(false) }
    var bulkSelected by remember { mutableStateOf(setOf<Long>()) }
    var showEditSheet by remember { mutableStateOf<NotebookEntity?>(null) }
    var showNewNotebook by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showTrashDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    val baseNotes: List<NoteEntity> = when (filter) {
        FilterMode.ARCHIVED -> archivedNotes
        FilterMode.TRASH -> trashedNotes
        else -> if (selectedNb != null) allNotes.filter { it.notebookId == selectedNb } else allNotes
    }
    var filtered = when (filter) {
        FilterMode.PINNED -> baseNotes.filter { it.pinned }
        FilterMode.FAVORITE -> baseNotes.filter { it.favorite }
        FilterMode.IMAGES -> baseNotes.filter { n ->
            n.documentJson.contains("\"IMAGE\"") || n.documentJson.contains("\"DRAWING\"")
        }
        FilterMode.TASKS -> baseNotes.filter { n -> n.documentJson.contains("CHECKLIST") }
        FilterMode.TAGS -> if (selectedTag != null) {
            val ids = taggedNoteIds.map { it.id }.toSet()
            baseNotes.filter { it.id in ids }
        } else baseNotes
        else -> baseNotes
    }
    filtered = when (sort) {
        SortMode.RECENT -> filtered.sortedByDescending { it.updatedAt }
        SortMode.CREATED -> filtered.sortedByDescending { it.createdAt }
        SortMode.OLDEST -> filtered.sortedBy { it.createdAt }
        SortMode.AZ -> filtered.sortedBy { it.title.lowercase() }
        SortMode.ZA -> filtered.sortedByDescending { it.title.lowercase() }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notebooks", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = c.text)
                    Text(
                        "${notebooks.size} notebooks · ${allNotes.size} notes · ${trashedNotes.size} in trash",
                        fontSize = 13.sp, color = c.text2
                    )
                }
                IconAction("📊") { showStats = true }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(c.primary)
                        .clayPress { showNewNotebook = true },
                    contentAlignment = Alignment.Center
                ) { Text("+", fontSize = 22.sp, color = c.onPrimary) }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ClayPill("All notes", selectedNb == null && filter != FilterMode.TRASH) { selectedNb = null; filter = FilterMode.ALL } }
                items(notebooks, key = { it.id }) { nb ->
                    ClayPill(nb.name, selectedNb == nb.id) {
                        selectedNb = nb.id; filter = FilterMode.ALL
                    }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SortMode.entries.toList()) { m ->
                    ClayPill("↕ ${m.label}", sort == m) { sort = m }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FilterMode.entries.toList()) { m ->
                    ClayPill(m.label, filter == m) {
                        filter = m
                        if (m == FilterMode.TRASH && !bulkMode) showTrashDialog = true
                    }
                }
                item {
                    ClayPill(if (bulkMode) "Done selecting" else "Select", bulkMode) {
                        bulkMode = !bulkMode; bulkSelected = emptySet()
                    }
                }
            }
        }
        if (filter == FilterMode.TAGS) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tags, key = { it.id }) { t ->
                        ClayPill("#${t.name}", selectedTag == t.id) { selectedTag = t.id }
                    }
                    if (tags.isEmpty()) item { Text("No tags yet — add tags from a note's options.", fontSize = 12.sp, color = c.text2) }
                }
            }
        }

        // Notebook cards (hidden while browsing a specific notebook or trash)
        if (selectedNb == null && filter == FilterMode.ALL) {
            items(notebooks, key = { nb -> "nb-${nb.id}" }) { nb ->
                NotebookRow(
                    nb = nb,
                    count = allNotes.count { it.notebookId == nb.id },
                    archivedCount = archivedNotes.count { it.notebookId == nb.id },
                    onClick = { selectedNb = nb.id },
                    onEdit = { showEditSheet = nb }
                )
            }
            if (notebooks.isEmpty()) {
                item {
                    ClayCard {
                        EmptyState("No notebooks yet", "Create a notebook to organise your notes. Notes can also live without one.", "📚")
                    }
                }
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            filter == FilterMode.TRASH -> "Trash"
                            filter == FilterMode.ARCHIVED -> "Archived"
                            selectedNb != null -> notebooks.firstOrNull { it.id == selectedNb }?.name ?: "Notebook"
                            else -> "All notes"
                        },
                        fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.text,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { selectedNb = null; filter = FilterMode.ALL }) { Text("Back to all") }
                }
            }
        }

        item { Text("${filtered.size} note(s)", fontSize = 13.sp, color = c.text2) }

        if (filtered.isEmpty()) {
            item {
                ClayCard {
                    EmptyState(
                        "Nothing here yet",
                        if (filter == FilterMode.TRASH) "Deleted notes appear here. Notes are only removed forever when you empty the trash."
                        else "Notes you create appear here. Use the + button on Home or a notebook.",
                        "🗂"
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { note ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (bulkMode) {
                        Checkbox(
                            checked = note.id in bulkSelected,
                            onCheckedChange = { checked ->
                                bulkSelected = if (checked) bulkSelected + note.id else bulkSelected - note.id
                            }
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        NoteCard(note, notebooks, onClick = {
                            if (bulkMode) {
                                bulkSelected = if (note.id in bulkSelected) bulkSelected - note.id else bulkSelected + note.id
                            } else onOpenNote(note.id)
                        })
                    }
                }
            }
        }

        if (bulkMode && bulkSelected.isNotEmpty()) {
            item {
                ClayCard {
                    Text("${bulkSelected.size} selected", fontWeight = FontWeight.SemiBold, color = c.text, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    FlowRowActions(
                        actions = listOf(
                            "Move" to { showMoveDialog = true },
                            "Pin" to {
                                scope.launch {
                                    bulkSelected.forEach { id ->
                                        val n = app.notes.getById(id)
                                        app.notes.setPinned(id, !(n?.pinned ?: false))
                                    }
                                    bulkSelected = emptySet()
                                }
                            },
                            "Favorite" to {
                                scope.launch {
                                    bulkSelected.forEach { id ->
                                        val n = app.notes.getById(id)
                                        app.notes.setFavorite(id, !(n?.favorite ?: false))
                                    }
                                    bulkSelected = emptySet()
                                }
                            },
                            "Archive" to {
                                scope.launch {
                                    bulkSelected.forEach { app.notes.setArchived(it, true) }
                                    bulkSelected = emptySet()
                                }
                            },
                            "Export TXT" to {
                                scope.launch {
                                    bulkSelected.forEach { id ->
                                        val n = app.notes.getById(id) ?: return@forEach
                                        val (blocks, _) = com.notely.app.doc.DocumentSerializer.deserialize(n.documentJson)
                                        try {
                                            Exporters.writeBytes(
                                                app, "${Exporters.safeName(n.title)}.txt",
                                                Exporters.toPlainText(blocks).toByteArray()
                                            )
                                        } catch (e: Exception) { /* storage error surfaced in Library */ }
                                    }
                                    bulkSelected = emptySet()
                                }
                            },
                            "Trash" to {
                                scope.launch {
                                    bulkSelected.forEach { app.notes.setTrashed(it, true) }
                                    bulkSelected = emptySet(); bulkMode = false
                                }
                            }
                        )
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClaySoftButton("Trash (${trashedNotes.size})") { showTrashDialog = true; filter = FilterMode.TRASH }
                ClaySoftButton("Notebook stats") { showStats = true }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    showEditSheet?.let { nb ->
        NotebookEditorSheet(
            app = app,
            notebook = nb,
            onDismiss = { showEditSheet = null },
            onMovedNotes = { selectedNb = null }
        )
    }
    if (showNewNotebook) {
        NewNotebookDialog(app, onDismiss = { showNewNotebook = false })
    }
    if (showStats) {
        NotebookStatsDialog(app, onDismiss = { showStats = false })
    }
    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move to notebook") },
            text = {
                Column {
                    notebooks.forEach { nb ->
                        TextButton(onClick = {
                            scope.launch {
                                bulkSelected.forEach { app.notes.move(it, nb.id) }
                                bulkSelected = emptySet(); showMoveDialog = false
                            }
                        }) { Text(nb.name) }
                    }
                    TextButton(onClick = {
                        scope.launch {
                            val id = app.notebooks.create("Inbox")
                            bulkSelected.forEach { app.notes.move(it, id) }
                            bulkSelected = emptySet(); showMoveDialog = false
                        }
                    }) { Text("+ New notebook") }
                }
            },
            confirmButton = { TextButton(onClick = { showMoveDialog = false }) { Text("Cancel") } }
        )
    }
    if (showTrashDialog) {
        TrashSheet(
            app = app,
            notes = trashedNotes,
            notebooks = trashedNotebooks,
            onDismiss = { showTrashDialog = false }
        )
    }
}

@Composable
private fun IconAction(symbol: String, onClick: () -> Unit) {
    val c = LocalNotelyColors.current
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)
            .clayPress(onClick),
        contentAlignment = Alignment.Center
    ) { Text(symbol, fontSize = 17.sp, color = c.text) }
}

@Composable
private fun NotebookRow(
    nb: NotebookEntity,
    count: Int,
    archivedCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val c = LocalNotelyColors.current
    ClayCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ClayIconTile(accentColor(nb.colorIndex)) {
                Text(nb.icon.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accentColor(nb.colorIndex))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(nb.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$count note${if (count == 1) "" else "s"}" +
                        (if (archivedCount > 0) " · $archivedCount archived" else "") +
                        " · ${TimeFmt.relative(nb.updatedAt)}",
                    fontSize = 12.sp, color = c.text2
                )
            }
            Text("✎", fontSize = 15.sp, color = c.text2, modifier = Modifier.padding(8.dp).clayPress(onEdit))
            Text("›", fontSize = 20.sp, color = c.text2)
        }
    }
}

@Composable
private fun FlowRowActions(actions: List<Pair<String, () -> Unit>>) {
    Column {
        actions.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { (label, action) -> ClaySoftButton(label, action) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewNotebookDialog(app: NotelyApp, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("book") }
    var color by remember { mutableStateOf(0) }
    val c = LocalNotelyColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New notebook") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(12.dp))
                Text("Icon letter", fontSize = 13.sp, color = c.text2)
                OutlinedTextField(icon, { icon = it.take(2) }, singleLine = true, modifier = Modifier.width(110.dp), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardColors.forEachIndexed { i, col -> ColorDot(col, color == i) { color = i } }
                }
            }
        },
        confirmButton = {
            ClayPrimaryButton("Create", enabled = name.isNotBlank()) {
                scope.launch {
                    app.notebooks.create(name.trim(), icon.ifBlank { "book" }, color)
                    onDismiss()
                }
            }
        },
        dismissButton = { ClaySoftButton("Cancel") { onDismiss() } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookEditorSheet(
    app: NotelyApp,
    notebook: NotebookEntity,
    onDismiss: () -> Unit,
    onMovedNotes: () -> Unit
) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val allNotebooks by app.notebooks.observeActive().collectAsState(initial = emptyList())
    var name by remember(notebook.id) { mutableStateOf(notebook.name) }
    var icon by remember(notebook.id) { mutableStateOf(notebook.icon) }
    var color by remember(notebook.id) { mutableStateOf(notebook.colorIndex) }
    var mergeTarget by remember { mutableStateOf<Long?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            SheetHeader("Notebook", "Rename, restyle, merge or remove", onDismiss)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(icon, { icon = it.take(2) }, label = { Text("Icon letter") }, singleLine = true, modifier = Modifier.width(140.dp), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(12.dp))
            SectionLabel("Color")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardColors.forEachIndexed { i, col -> ColorDot(col, color == i) { color = i } }
            }
            Spacer(Modifier.height(14.dp))
            ClayPrimaryButton("Save changes") {
                scope.launch { app.notebooks.rename(notebook.id, name.trim().ifBlank { notebook.name }, icon, color); onDismiss() }
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("Merge into another notebook")
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allNotebooks.filter { it.id != notebook.id }) { other ->
                    ClayPill(other.name, mergeTarget == other.id) { mergeTarget = other.id }
                }
            }
            Spacer(Modifier.height(8.dp))
            ClaySoftButton("Merge ${if (mergeTarget == null) "…" else "now"}") {
                val target = mergeTarget ?: return@ClaySoftButton
                scope.launch {
                    app.notes.mergeNotebook(notebook.id, target)
                    onMovedNotes(); onDismiss()
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClaySoftButton("Archive") {
                    scope.launch { app.notebooks.setArchived(notebook.id, true); onDismiss() }
                }
                ClaySoftButton("Move to trash") {
                    scope.launch { app.notebooks.setTrashed(notebook.id, true); onDismiss() }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrashSheet(
    app: NotelyApp,
    notes: List<NoteEntity>,
    notebooks: List<NotebookEntity>,
    onDismiss: () -> Unit
) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    var confirmEmpty by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            SheetHeader("Trash", "Restore anything, or empty the trash permanently", onDismiss)
            Spacer(Modifier.height(10.dp))
            notes.forEach { n ->
                ClayCard(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.title, modifier = Modifier.weight(1f), color = c.text, fontSize = 15.sp)
                        TextButton(onClick = { scope.launch { app.notes.setTrashed(n.id, false) } }) { Text("Restore") }
                    }
                }
            }
            notebooks.forEach { nb ->
                ClayCard(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${nb.name} (notebook)", modifier = Modifier.weight(1f), color = c.text, fontSize = 15.sp)
                        TextButton(onClick = { scope.launch { app.notebooks.setTrashed(nb.id, false) } }) { Text("Restore") }
                        TextButton(onClick = { scope.launch { app.notebooks.deleteForever(nb.id) } }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (notes.isEmpty() && notebooks.isEmpty()) {
                Text("Trash is empty.", fontSize = 14.sp, color = c.text2)
            }
            Spacer(Modifier.height(8.dp))
            ClaySoftButton("Empty trash permanently") { confirmEmpty = true }
            Spacer(Modifier.height(28.dp))
        }
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Delete everything in trash?") },
            text = { Text("This permanently deletes ${notes.size} note(s) and their document data. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.notes.emptyTrash(); confirmEmpty = false }
                }) { Text("Delete permanently", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NotebookStatsDialog(app: NotelyApp, onDismiss: () -> Unit) {
    val notes by app.notes.observeActive().collectAsState(initial = emptyList())
    val archived by app.notes.observeArchived().collectAsState(initial = emptyList())
    val trashed by app.notes.observeTrashed().collectAsState(initial = emptyList())
    val notebooks by app.notebooks.observeActive().collectAsState(initial = emptyList())
    val c = LocalNotelyColors.current
    val bytes = notes.sumOf { it.documentJson.length.toLong() }
    val attachments = notes.sumOf { n -> n.documentJson.windowed(10, 1).count { it.contains("ATTACHMENT") } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Library statistics") },
        text = {
            Column {
                StatLine("Notebooks", notebooks.size.toString())
                StatLine("Active notes", notes.size.toString())
                StatLine("Archived", archived.size.toString())
                StatLine("In trash", trashed.size.toString())
                StatLine("Pinned", notes.count { it.pinned }.toString())
                StatLine("Favorite", notes.count { it.favorite }.toString())
                StatLine("With checklists", notes.count { it.documentJson.contains("CHECKLIST") }.toString())
                StatLine("Embedded attachments", attachments.toString())
                StatLine("Document text storage", "%.1f KB".format(bytes / 1024.0))
                StatLine(
                    "Last edit",
                    notes.maxOfOrNull { it.updatedAt }?.let { TimeFmt.relative(it) } ?: "—"
                )
                Spacer(Modifier.height(6.dp))
                Text("Storage is app-private; exports and images live in app files.", fontSize = 12.sp, color = c.text2)
            }
        },
        confirmButton = { ClayPrimaryButton("Close") { onDismiss() } }
    )
}

@Composable
private fun StatLine(label: String, value: String) {
    val c = LocalNotelyColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = c.text2)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
    }
}
