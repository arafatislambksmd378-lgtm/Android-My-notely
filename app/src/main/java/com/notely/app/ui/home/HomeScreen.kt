package com.notely.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notely.app.NotelyApp
import com.notely.app.data.db.NoteEntity
import com.notely.app.data.db.NotebookEntity
import com.notely.app.doc.DocumentSerializer
import com.notely.app.ui.components.*
import com.notely.app.ui.theme.CardColors
import com.notely.app.ui.theme.LocalNotelyColors
import com.notely.app.ui.theme.accentColor
import com.notely.app.util.TimeFmt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(app: NotelyApp, onOpenNote: (Long) -> Unit) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val notebooks by app.notebooks.observeActive().collectAsState(initial = emptyList())
    val recent by app.notes.observeRecent(30).collectAsState(initial = emptyList())
    val workspace by app.prefs.workspaceName.collectAsState(initial = "Writer")
    val workspaceIcon by app.prefs.workspaceIcon.collectAsState(initial = "W")
    val recentSearches by app.prefs.recentSearches.collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showNotebookPicker by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<Long?>(null) }

    val searchResults by app.notes.search(debounced).collectAsState(initial = emptyList())
    val searching = debounced.isNotBlank()

    LaunchedEffect(query) {
        searchJob?.cancel()
        if (query.isBlank()) {
            debounced = ""
        } else {
            searchJob = launch {
                delay(180)
                debounced = query.trim()
                app.prefs.addRecentSearch(query.trim())
            }
        }
    }

    fun newNote(notebookId: Long) {
        scope.launch {
            val id = app.notes.create(notebookId)
            onOpenNote(id)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(TimeFmt.todayLine(), fontSize = 13.sp, color = c.text2)
                        Text("${TimeFmt.greeting()},", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = c.text)
                        Text(workspace, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = c.text)
                    }
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(c.primary)
                            .clayPress { showNotebookPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(workspaceIcon.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.onPrimary)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search notes, tables, ideas…", fontSize = 15.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = c.text2) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            Text(
                                "✕", fontSize = 15.sp, color = c.text2,
                                modifier = Modifier.padding(end = 14.dp).clayPress { query = "" }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = c.border, focusedBorderColor = c.primary,
                        unfocusedContainerColor = c.surface, focusedContainerColor = c.surface
                    )
                )
            }

            if (searching) {
                item {
                    Text(
                        if (searchResults.isEmpty()) "No matches for “$debounced”" else "${searchResults.size} result(s) for “$debounced”",
                        fontSize = 13.sp, color = c.text2
                    )
                }
                if (searchResults.isEmpty()) {
                    item {
                        ClayCard {
                            Text("Search tips", fontWeight = FontWeight.SemiBold, color = c.text, fontSize = 15.sp)
                            Text(
                                "Search matches note titles, document text, table cells and notebook context. " +
                                    "Try a shorter word.",
                                fontSize = 13.sp, color = c.text2
                            )
                        }
                    }
                } else {
                    items(searchResults, key = { it.id }) { note ->
                        NoteCard(note, notebooks, onClick = { onOpenNote(note.id) }, highlight = debounced)
                    }
                }
            } else {
                if (recentSearches.isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recentSearches) { s ->
                                ClayPill(s, false) { query = s }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Notebooks", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.weight(1f))
                        Text("Manage", fontSize = 13.sp, color = c.primary, modifier = Modifier.clayPress { showNotebookPicker = true })
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(notebooks, key = { it.id }) { nb ->
                            NotebookPreviewCard(nb, recent.count { it.notebookId == nb.id }) {
                                pickerTarget = nb.id
                                showCreate = true
                            }
                        }
                        item {
                            ClayCard(Modifier.width(132.dp), onClick = { pickerTarget = null; showCreate = true }) {
                                Box(
                                    Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                                        .background(c.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) { Text("+", fontSize = 22.sp, color = c.primary) }
                                Spacer(Modifier.height(8.dp))
                                Text("New", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
                                Text("notebook or note", fontSize = 11.sp, color = c.text2)
                            }
                        }
                    }
                }
                item {
                    Text("Recent notes", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.text)
                }
                if (recent.isEmpty()) {
                    item {
                        ClayCard {
                            EmptyState(
                                "Your workspace is empty",
                                "Tap the + button to write your first note. Everything is saved locally and works offline.",
                                "📝"
                            )
                        }
                    }
                } else {
                    items(recent, key = { it.id }) { note ->
                        NoteCard(note, notebooks, onClick = { onOpenNote(note.id) })
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }

        // Floating square Plus button (reference design)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.primary)
                .clayPress { pickerTarget = null; showCreate = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New note", tint = c.onPrimary)
        }
    }

    if (showNotebookPicker) {
        NotebookSwitcherSheet(
            app = app,
            onDismiss = { showNotebookPicker = false },
            onOpen = { nbId -> showNotebookPicker = false; pickerTarget = nbId; showCreate = true }
        )
    }
    if (showCreate) {
        CreateSheet(
            app = app,
            initialNotebook = pickerTarget ?: notebooks.firstOrNull()?.id,
            onDismiss = { showCreate = false },
            onCreated = { id -> showCreate = false; onOpenNote(id) }
        )
    }
}

@Composable
private fun NotebookPreviewCard(nb: NotebookEntity, count: Int, onClick: () -> Unit) {
    val c = LocalNotelyColors.current
    val color = accentColor(nb.colorIndex)
    ClayCard(Modifier.width(132.dp), onClick = onClick) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(nb.icon.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(8.dp))
        Text(nb.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$count note${if (count == 1) "" else "s"}", fontSize = 11.sp, color = c.text2)
    }
}

@Composable
fun NoteCard(
    note: NoteEntity,
    notebooks: List<NotebookEntity>,
    onClick: () -> Unit,
    highlight: String = ""
) {
    val c = LocalNotelyColors.current
    val (blocks, _) = remember(note.documentJson) { DocumentSerializer.deserialize(note.documentJson) }
    val preview = remember(blocks) { DocumentSerializer.preview(blocks) }
    val notebook = notebooks.firstOrNull { it.id == note.notebookId }
    val stripe = CardColors[note.cardColorIndex.coerceIn(0, CardColors.size - 1)]
    ClayCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).height(38.dp).clip(RoundedCornerShape(3.dp)).background(stripe))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    note.title.ifBlank { "Untitled" },
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(TimeFmt.relative(note.updatedAt))
                        if (notebook != null) append(" · ${notebook.name}")
                    },
                    fontSize = 11.sp, color = c.text2, maxLines = 1
                )
            }
            if (note.pinned) Text("📌", fontSize = 13.sp)
            if (note.favorite) Text(" ⭐", fontSize = 12.sp)
            if (note.reminderAt != null) Text(" ⏰", fontSize = 12.sp)
        }
        if (preview.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            if (highlight.isNotBlank() && preview.contains(highlight, ignoreCase = true)) {
                val idx = preview.indexOf(highlight, ignoreCase = true)
                androidx.compose.foundation.text.BasicText(
                    buildAnnotatedPreview(preview, idx, highlight.length, c.primary, c.text2)
                )
            } else {
                Text(preview, fontSize = 13.sp, color = c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun buildAnnotatedPreview(text: String, idx: Int, len: Int, highlight: androidx.compose.ui.graphics.Color, base: androidx.compose.ui.graphics.Color) =
    androidx.compose.ui.text.buildAnnotatedString {
        val end = (idx + len).coerceAtMost(text.length)
        append(text.substring(0, idx))
        withStyle(androidx.compose.ui.text.SpanStyle(color = highlight, fontWeight = FontWeight.Bold)) {
            append(text.substring(idx, end))
        }
        append(text.substring(end))
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookSwitcherSheet(app: NotelyApp, onDismiss: () -> Unit, onOpen: (Long?) -> Unit) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val notebooks by app.notebooks.observeActive().collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            SheetHeader("Notebooks", "Open a notebook to write in it", onDismiss)
            Spacer(Modifier.height(12.dp))
            notebooks.forEach { nb ->
                ClayCard(Modifier.padding(bottom = 8.dp), onClick = { onOpen(nb.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClayIconTile(accentColor(nb.colorIndex)) {
                            Text(nb.icon.take(1).uppercase(), fontWeight = FontWeight.Bold, color = accentColor(nb.colorIndex))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(nb.name, fontWeight = FontWeight.SemiBold, color = c.text, fontSize = 15.sp)
                            Text("Opens a new note here", fontSize = 12.sp, color = c.text2)
                        }
                        Text("›", fontSize = 20.sp, color = c.text2)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    newName, { newName = it },
                    label = { Text("New notebook") }, singleLine = true,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                ClayPrimaryButton("Add", enabled = newName.isNotBlank()) {
                    scope.launch {
                        val color = (notebooks.size) % CardColors.size
                        app.notebooks.create(newName.trim(), "book", color)
                        newName = ""
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSheet(app: NotelyApp, initialNotebook: Long?, onDismiss: () -> Unit, onCreated: (Long) -> Unit) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val notebooks by app.notebooks.observeActive().collectAsState(initial = emptyList())
    val templates by app.templates.observeAll().collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var notebookId by remember(initialNotebook, notebooks) {
        mutableStateOf(initialNotebook ?: notebooks.firstOrNull()?.id)
    }
    var templateJson by remember { mutableStateOf<String?>(null) }
    var templateName by remember { mutableStateOf<String?>(null) }

    fun ensureNotebook(): Long? = notebookId

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            SheetHeader("New note", "Saved locally · works offline", onDismiss)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                title, { title = it }, label = { Text("Title") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel("Notebook")
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notebooks) { nb ->
                    ClayPill(nb.name, notebookId == nb.id) { notebookId = nb.id }
                }
                item {
                    ClayPill("+ New", false) {
                        scope.launch {
                            val id = app.notebooks.create("Notebook ${notebooks.size + 1}", "book", notebooks.size % CardColors.size)
                            notebookId = id
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            SectionLabel("Start from a template")
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ClayPill("Blank", templateJson == null) { templateJson = null; templateName = null } }
                items(templates) { t ->
                    ClayPill(t.name, templateJson == t.documentJson) {
                        templateJson = t.documentJson; templateName = t.name
                        if (title.isBlank()) title = t.name
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            ClayPrimaryButton("Create note") {
                scope.launch {
                    val nb = ensureNotebook() ?: app.notebooks.create("Personal")
                    val id = app.notes.create(
                        nb,
                        title.ifBlank { templateName ?: "Untitled" },
                        templateJson ?: ""
                    )
                    onCreated(id)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
