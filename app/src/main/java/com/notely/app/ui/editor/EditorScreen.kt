package com.notely.app.ui.editor

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notely.app.NotelyApp
import com.notely.app.doc.*
import com.notely.app.ui.components.*
import com.notely.app.ui.theme.CardColors
import com.notely.app.ui.theme.LocalNotelyColors
import com.notely.app.util.ImageOps
import com.notely.app.util.TimeFmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val STYLES = listOf(
    "Normal", "Title", "Subtitle",
    "Heading 1", "Heading 2", "Heading 3", "Heading 4",
    "Heading 5", "Heading 6", "Heading 7", "Heading 8"
)

private fun styleHeadingLevel(style: String): Int = when (style) {
    "Normal" -> 0
    "Title" -> 1
    "Subtitle" -> 2
    else -> style.removePrefix("Heading ").toIntOrNull() ?: 0
}

private fun headingSize(level: Int): Float = when (level) {
    1 -> 30f; 2 -> 24f; 3 -> 21f; 4 -> 19f
    else -> (17.5f - (level - 5) * 0.5f).coerceAtLeast(14f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    app: NotelyApp,
    noteId: Long,
    readOnly: Boolean = false,
    onBack: () -> Unit
) {
    val c = LocalNotelyColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val autosave by app.prefs.autosave.collectAsStateWithLifecycle(initialValue = true)
    val spellcheck by app.prefs.spellcheck.collectAsStateWithLifecycle(initialValue = true)
    val noteFlow = remember(noteId) { app.notes.observeById(noteId) }
    val note by noteFlow.collectAsStateWithLifecycle(initialValue = null)
    val notebooks by app.notebooks.observeActive().collectAsStateWithLifecycle(initialValue = emptyList())

    var blocks by remember { mutableStateOf(listOf(DocumentBlock())) }
    var pageSetup by remember { mutableStateOf(PageSetup()) }
    var title by remember { mutableStateOf("Untitled") }
    var cardColorIndex by remember { mutableStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var editRevision by remember { mutableStateOf(0L) }
    val saveMutex = remember(noteId) { Mutex() }
    var leaving by remember { mutableStateOf(false) }

    var saveStatus by remember { mutableStateOf("Saved") }
    var dirty by remember { mutableStateOf(false) }
    var focusedBlockId by remember { mutableStateOf<String?>(null) }
    var showFormat by remember { mutableStateOf(true) }
    var showStyleRow by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showPageSetup by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showVersions by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showWordCount by remember { mutableStateOf(false) }
    var showChartDialog by remember { mutableStateOf(false) }
    var showTableDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showFootnoteDialog by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showDrawing by remember { mutableStateOf(false) }
    var showOcr by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showHighlightPicker by remember { mutableStateOf(false) }
    var showNav by remember { mutableStateOf(false) }
    var showShapePicker by remember { mutableStateOf(false) }
    var showSignPad by remember { mutableStateOf(false) }
    var showInsertFromBottom by remember { mutableStateOf(false) }
    var showFontSizeMenu by remember { mutableStateOf(false) }
    var showMultipage by remember { mutableStateOf(readOnly) }
    var undoStack by remember { mutableStateOf<List<List<DocumentBlock>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<DocumentBlock>>>(emptyList()) }
    var saveJob by remember { mutableStateOf<Job?>(null) }
    val listState = rememberLazyListState()
    var pendingSelection by remember { mutableStateOf<Pair<Int, IntRange>?>(null) }

    // A failed read is not an empty document. Keep the original bytes untouched.
    LaunchedEffect(noteId) {
        runCatching {
            val n = app.notes.getById(noteId) ?: error("Document no longer exists")
            val (b, p) = withContext(Dispatchers.Default) {
                DocumentSerializer.deserializeForEditing(n.documentJson)
            }
            blocks = b.ifEmpty { listOf(DocumentBlock()) }
            pageSetup = p
            title = n.title
            cardColorIndex = n.cardColorIndex
            loaded = true
        }.onFailure { loadError = "This document could not be opened safely. Its stored content has not been changed." }
    }

    fun serialize() = DocumentSerializer.serialize(blocks, pageSetup)

    fun saveNow(): Job? {
        if (!loaded || readOnly) return null
        val revision = editRevision
        val snapshot = blocks
        val page = pageSetup
        val t = title.ifBlank { "Untitled" }
        saveStatus = "Saving…"
        // Application-owned write survives removal of the editor composition. Start on
        // Main.immediate to enqueue snapshots in edit order; do JSON and Room work off-main.
        return app.scope.launch(Dispatchers.Main.immediate) {
            saveMutex.withLock {
                try {
                    withContext(Dispatchers.IO) {
                        app.notes.updateContent(noteId, t, DocumentSerializer.serialize(snapshot, page))
                    }
                    if (editRevision == revision) {
                        dirty = false
                        saveStatus = TimeFmt.savedLine(System.currentTimeMillis())
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    saveStatus = "Save failed — tap Save to retry"
                }
            }
        }
    }

    fun scheduleSave() {
        editRevision++
        dirty = true
        if (readOnly) return
        if (!autosave) {
            saveStatus = "Unsaved changes"
            return
        }
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(700)
            saveNow()
        }
    }

    fun pushUndo() {
        undoStack = (undoStack + listOf(blocks)).takeLast(60)
        redoStack = emptyList()
    }

    fun updateBlocks(newBlocks: List<DocumentBlock>) {
        blocks = newBlocks
        scheduleSave()
    }

    fun focusedIndex(): Int {
        val idx = blocks.indexOfFirst { it.id == focusedBlockId }
        return if (idx >= 0) idx else blocks.indexOfFirst { it.type != BlockType.DIVIDER && it.type != BlockType.PAGE_BREAK }.coerceAtLeast(0)
    }

    fun applyToFocused(transform: (DocumentBlock) -> DocumentBlock) {
        if (readOnly || blocks.isEmpty()) return
        pushUndo()
        val idx = focusedIndex()
        val newBlocks = blocks.toMutableList()
        newBlocks[idx] = transform(newBlocks[idx])
        updateBlocks(newBlocks)
    }

    fun insertBlock(block: DocumentBlock, focusIt: Boolean = true) {
        if (readOnly) return
        pushUndo()
        val idx = (focusedIndex() + 1).coerceIn(0, blocks.size)
        updateBlocks(blocks.toMutableList().apply { add(idx, block) })
        if (focusIt) focusedBlockId = block.id
    }

    fun appendBlock(block: DocumentBlock) {
        if (readOnly) return
        pushUndo()
        updateBlocks(blocks + block)
    }

    fun openUrl(url: String) {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) url
        else "https://$url"
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized))) }
    }

    // ---- Launchers ----
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val path = ImageOps.import(context, uri)
                withContext(Dispatchers.Main) {
                    if (path != null) {
                        appendBlock(DocumentBlock(type = BlockType.IMAGE, imagePath = path))
                    } else {
                        saveStatus = "Could not import that image"
                    }
                }
            }
        }
    }
    val multiImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val paths = uris.mapNotNull { ImageOps.import(context, it) }
                withContext(Dispatchers.Main) {
                    if (paths.isNotEmpty()) {
                        pushUndo()
                        // Read `blocks` on the main thread: the captured value from the
                        // launcher's composition is stale by the time IO finishes.
                        updateBlocks(blocks + paths.map { DocumentBlock(type = BlockType.IMAGE, imagePath = it) })
                        saveStatus = "Inserted ${paths.size} image(s)"
                    }
                }
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            scope.launch(Dispatchers.IO) {
                val path = ImageOps.save(context, bitmap)
                withContext(Dispatchers.Main) {
                    appendBlock(DocumentBlock(type = BlockType.IMAGE, imagePath = path))
                }
            }
        }
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                appendBlock(DocumentBlock(runs = listOf(TextRun(text))))
            }
        }
    }
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val path = ImageOps.importBytes(context, uri, "attachments")
                val mime = context.contentResolver.getType(uri) ?: ""
                val kind = when {
                    mime.startsWith("video") -> BlockType.VIDEO
                    mime.startsWith("audio") -> BlockType.AUDIO
                    else -> BlockType.ATTACHMENT
                }
                withContext(Dispatchers.Main) {
                    if (path != null) {
                        appendBlock(DocumentBlock(type = kind, extra = path))
                    } else {
                        saveStatus = "Attachment failed"
                    }
                }
            }
        }
    }

    val stats = remember(blocks, pageSetup) { DocumentStats.compute(blocks, pageSetup) }
    val (pageWmm, pageHmm) = pageSetup.sizeMm()
    val pageAspect = pageWmm / pageHmm

    // Leave the editor: always flush pending changes first.
    fun leave() {
        if (leaving) return
        leaving = true
        saveJob?.cancel()
        scope.launch {
            if (!readOnly && dirty) saveNow()?.join()
            leaving = false
            // Keep the editor open when a flush failed (or a newer edit arrived).
            if (!dirty || readOnly || !loaded) onBack()
        }
    }

    BackHandler { leave() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val flushLatest by rememberUpdatedState(newValue = {
        if (dirty && loaded && !readOnly) {
            saveJob?.cancel()
            saveNow()
        }
        Unit
    })
    DisposableEffect(lifecycleOwner, noteId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) flushLatest()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            flushLatest()
        }
    }

    if (loadError != null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(loadError!!)
            TextButton(onClick = onBack) { Text("Back to notebooks") }
        }
        return
    }
    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // ---------- Top bar (reference design) ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { leave() }) { Icon(Icons.Filled.ArrowBack, "Back", tint = c.text) }
            Column(Modifier.weight(1f)) {
                BasicTextField(
                    value = title,
                    onValueChange = { if (!readOnly) { title = it; scheduleSave() } },
                    textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.text),
                    singleLine = true,
                    enabled = !readOnly,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveNow(); focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (readOnly) "Read-only preview · ${stats.pages} page(s)"
                    else buildString {
                        append(if (dirty && !autosave) "Unsaved" else saveStatus)
                        append(" · ${stats.words} words")
                    },
                    fontSize = 11.sp, color = if (dirty && !autosave) c.primary else c.text2,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (readOnly) {
                IconButton(onClick = { showMultipage = !showMultipage }) {
                    Icon(if (showMultipage) Icons.Filled.ViewAgenda else Icons.Outlined.Article, "Pages", tint = c.text)
                }
                IconButton(onClick = { showExport = true }) { Icon(Icons.Outlined.Share, "Export", tint = c.text) }
            } else {
                if (!autosave) {
                    IconButton(onClick = { saveNow() }) { Icon(Icons.Filled.Check, "Save now", tint = c.primary) }
                }
                IconButton(onClick = { showComments = true }) {
                    Icon(Icons.Outlined.ChatBubbleOutline, "Comments", tint = c.text)
                }
                IconButton(onClick = { showBookmarks = true }) {
                    Icon(Icons.Outlined.BookmarkBorder, "Bookmarks", tint = c.text)
                }
                IconButton(onClick = { showOptions = true }) {
                    Icon(Icons.Filled.MoreVert, "Note options", tint = c.text)
                }
            }
        }

        // ---------- Formatting toolbar (floating, horizontally scrollable) ----------
        if (showFormat && !readOnly) {
            Surface(color = c.surface, shadowElevation = 2.dp) {
                Column(Modifier.fillMaxWidth()) {
                    if (showStyleRow) {
                        LazyRow(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(STYLES.size) { i ->
                                val s = STYLES[i]
                                ClayPill(s, false) {
                                    val level = styleHeadingLevel(s)
                                    applyToFocused { b ->
                                        b.copy(
                                            type = if (level == 0) BlockType.PARAGRAPH else BlockType.HEADING,
                                            headingLevel = level,
                                            runs = b.runs.map { r ->
                                                r.copy(
                                                    fontSizeSp = if (level == 0) 16f else headingSize(level),
                                                    bold = level in 1..4
                                                )
                                            }
                                        )
                                    }
                                    showStyleRow = false
                                }
                            }
                        }
                    }
                    LazyRow(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        item {
                            TextButton(onClick = { showStyleRow = !showStyleRow }) {
                                Text("Style ▾", fontSize = 13.sp, color = c.primary)
                            }
                        }
                        item { FmtButton(Icons.Filled.FormatBold, "Bold") { bold -> applyToFocused { b -> b.copy(runs = b.runs.map { it.copy(bold = bold) }) } } }
                        item { FmtButton(Icons.Filled.FormatItalic, "Italic") { on -> applyToFocused { b -> b.copy(runs = b.runs.map { it.copy(italic = on) }) } } }
                        item { FmtButton(Icons.Filled.FormatUnderlined, "Underline") { on -> applyToFocused { b -> b.copy(runs = b.runs.map { it.copy(underline = on) }) } } }
                        item { FmtButton(Icons.Filled.FormatStrikethrough, "Strikethrough") { on -> applyToFocused { b -> b.copy(runs = b.runs.map { it.copy(strikethrough = on) }) } } }
                        item { IconButton(onClick = { showColorPicker = true }) { Icon(Icons.Filled.FormatColorText, "Text color", tint = c.text) } }
                        item { IconButton(onClick = { showHighlightPicker = true }) { Icon(Icons.Filled.FormatColorFill, "Highlight", tint = c.text) } }
                        item { IconButton(onClick = { showFontSizeMenu = true }) { Text("Aa", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(align = 0) } }) { Icon(Icons.Filled.FormatAlignLeft, "Left", tint = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(align = 1) } }) { Icon(Icons.Filled.FormatAlignCenter, "Center", tint = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(align = 2) } }) { Icon(Icons.Filled.FormatAlignRight, "Right", tint = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(align = 3) } }) { Icon(Icons.Filled.FormatAlignJustify, "Justify", tint = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(type = BlockType.BULLET) } }) { Icon(Icons.Filled.FormatListBulleted, "Bullets", tint = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(type = BlockType.NUMBERED) } }) { Icon(Icons.Filled.FormatListNumbered, "Numbers", tint = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(type = BlockType.CHECKLIST) } }) { Icon(Icons.Filled.Checklist, "Checklist", tint = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(indentLevel = (b.indentLevel + 1).coerceAtMost(6)) } }) { Icon(Icons.Filled.FormatIndentIncrease, "Indent", tint = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(indentLevel = (b.indentLevel - 1).coerceAtLeast(0)) } }) { Icon(Icons.Filled.FormatIndentDecrease, "Outdent", tint = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(runs = b.runs.map { it.copy(superscript = !it.superscript, subscript = false) }) } }) { Icon(Icons.Filled.Superscript, "Superscript", tint = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(runs = b.runs.map { it.copy(subscript = !it.subscript, superscript = false) }) } }) { Icon(Icons.Filled.Subscript, "Subscript", tint = c.text) } }
                        item {
                            IconButton(onClick = {
                                if (undoStack.isNotEmpty()) {
                                    redoStack = redoStack + listOf(blocks)
                                    updateBlocks(undoStack.last())
                                    undoStack = undoStack.dropLast(1)
                                }
                            }) { Icon(Icons.Filled.Undo, "Undo", tint = c.text) }
                        }
                        item {
                            IconButton(onClick = {
                                if (redoStack.isNotEmpty()) {
                                    undoStack = undoStack + listOf(blocks)
                                    updateBlocks(redoStack.last())
                                    redoStack = redoStack.dropLast(1)
                                }
                            }) { Icon(Icons.Filled.Redo, "Redo", tint = c.text) }
                        }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(runs = b.runs.map { TextRun(it.text) }) } }) { Icon(Icons.Filled.FormatClear, "Clear formatting", tint = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(runs = b.runs.map { it.copy(text = it.text.uppercase()) }) } }) { Text("AA", fontSize = 12.sp, color = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(runs = b.runs.map { it.copy(text = it.text.lowercase()) }) } }) { Text("aa", fontSize = 12.sp, color = c.text) } }
                        item { IconButton(onClick = { applyToFocused { b -> b.copy(runs = b.runs.map { it.copy(text = titleCase(it.text)) }) } }) { Text("Aa", fontSize = 12.sp, color = c.text) } }
                        item { IconButton(onClick = { showNav = true }) { Icon(Icons.Filled.Menu, "Headings", tint = c.text) } }
                    }
                }
            }
        }

        // ---------- Document body ----------
        Box(
            Modifier.weight(1f).fillMaxWidth()
        ) {
            if (readOnly && showMultipage) {
                MultiPageView(blocks, pageSetup, pageAspect)
            } else if (!readOnly && showMultipage) {
                // Reading preview from the note options: pages + a way back to editing.
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Reading view · ${blocks.count { it.type == BlockType.PAGE_BREAK } + 1}+ page(s)",
                            fontSize = 12.sp, color = c.text2, modifier = Modifier.weight(1f)
                        )
                        ClaySoftButton("Back to editing") { showMultipage = false }
                        Spacer(Modifier.width(8.dp))
                        ClaySoftButton("Print / PDF") { showExport = true }
                    }
                    Box(Modifier.weight(1f)) { MultiPageView(blocks, pageSetup, pageAspect) }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(pageSetup.pageColorArgb))
                        .border(1.dp, c.border, RoundedCornerShape(20.dp))
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                            .padding(
                                start = (pageSetup.marginLeft / 3).dp,
                                end = (pageSetup.marginRight / 3).dp,
                                top = (pageSetup.marginTop / 3).dp,
                                bottom = (pageSetup.marginBottom / 3).dp
                            ),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        itemsIndexed(blocks, key = { _, b -> b.id }) { index, block ->
                            // BoxScope.alignment keeps this legal at the LazyColumn item
                            // level (Modifier.align is only resolvable inside a Box).
                            val boxAlign = when (block.align) {
                                1 -> Alignment.TopCenter
                                2 -> Alignment.TopEnd
                                else -> Alignment.TopStart
                            }
                            Box(Modifier.fillMaxWidth(), contentAlignment = boxAlign) {
                                Box {
                                    BlockEditor(
                                        block = block,
                                        index = index,
                                        readOnly = readOnly,
                                        focused = focusedBlockId == block.id,
                                        spellEnabled = spellcheck && !readOnly,
                                        allBlocks = blocks,
                                        onFocus = { focusedBlockId = block.id },
                                        selection = pendingSelection
                                            ?.takeIf { it.first == index }
                                            ?.second,
                                        onSelectionConsumed = { pendingSelection = null },
                                        onChange = { newBlock ->
                                            if (!readOnly) {
                                                val newBlocks = blocks.toMutableList()
                                                newBlocks[blocks.indexOfFirst { it.id == block.id }.coerceAtLeast(0)] = newBlock
                                                updateBlocks(newBlocks)
                                            }
                                        },
                                        onAddBelow = {
                                            if (!readOnly) {
                                                pushUndo()
                                                val nb = DocumentBlock()
                                                updateBlocks(blocks.toMutableList().apply {
                                                    add(blocks.indexOfFirst { it.id == block.id }.coerceAtLeast(0) + 1, nb)
                                                })
                                                focusedBlockId = nb.id
                                            }
                                        },
                                        onDelete = {
                                            if (!readOnly && blocks.size > 1) {
                                                pushUndo()
                                                updateBlocks(blocks.toMutableList().apply {
                                                    removeAt(blocks.indexOfFirst { it.id == block.id }.coerceAtLeast(0))
                                                })
                                            }
                                        },
                                        onOpenUrl = { openUrl(it) }
                                    )
                                }
                            }
                        }
                        item {
                            if (!readOnly) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    TextButton(onClick = {
                                        pushUndo()
                                        val nb = DocumentBlock()
                                        updateBlocks(blocks + nb)
                                        focusedBlockId = nb.id
                                    }) { Text("+ Add paragraph", fontSize = 12.sp, color = c.text2) }
                                }
                            }
                        }
                    }
                    if (pageSetup.watermark.isNotBlank()) {
                        Text(
                            pageSetup.watermark,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.primary.copy(alpha = 0.10f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    if (pageSetup.header.isNotBlank()) {
                        Text(pageSetup.header, fontSize = 10.sp, color = c.text2, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
                    }
                    if (pageSetup.footer.isNotBlank() || pageSetup.pageNumbers) {
                        Text(
                            listOfNotNull(pageSetup.footer.ifBlank { null }, if (pageSetup.pageNumbers) "1 / ${stats.pages}" else null).joinToString("    "),
                            fontSize = 10.sp, color = c.text2,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        )
                    }
                }
            }
        }

        // ---------- Bottom floating insert toolbar (reference design) ----------
        if (!readOnly) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(c.primary)
                            .clayPress { showOptions = false; showInsertFromBottom = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Add, "Insert", tint = c.onPrimary) }
                }
                item { MiniFab("Aa") { showFormat = !showFormat } }
                item { MiniFab("🖼") { galleryLauncher.launch("image/*") } }
                item { MiniFab("🎤") { launchVoice(voiceLauncher, context) { saveStatus = "Voice input unavailable" } } }
                item { MiniFab("🔍") { showFind = true } }
                item { MiniFab("📄") { showNav = true } }
                item { MiniFab("📊") { showChartDialog = true } }
                item { MiniFab("▦") { showTableDialog = true } }
                item { MiniFab("✏") { showDrawing = true } }
                item { MiniFab("…") { showOptions = true } }
            }
        }
    }

    // ---------- Sheets & dialogs ----------
    if (showOptions) {
        NoteOptionsSheet(
            app = app, noteId = noteId,
            onDismiss = { showOptions = false },
            onPageSetup = { showOptions = false; showPageSetup = true },
            onFind = { showOptions = false; showFind = true },
            onWordCount = { showOptions = false; showWordCount = true },
            onComments = { showOptions = false; showComments = true },
            onBookmarks = { showOptions = false; showBookmarks = true },
            onTags = { showOptions = false; showTags = true },
            onExport = { showOptions = false; showExport = true },
            onVersions = { showOptions = false; showVersions = true },
            onAi = { showOptions = false; showAi = true },
            onTemplates = { showOptions = false; showTemplates = true },
            onMove = { nbId -> scope.launch { app.notes.move(noteId, nbId); onBack() } },
            onDelete = { showOptions = false; showDeleteConfirm = true },
            onColor = { idx -> cardColorIndex = idx; scope.launch { app.notes.setColor(noteId, idx) } },
            onPin = { scope.launch { app.notes.setPinned(noteId, !(note?.pinned ?: false)) } },
            onFavorite = { scope.launch { app.notes.setFavorite(noteId, !(note?.favorite ?: false)) } },
            onArchive = { scope.launch { app.notes.setArchived(noteId, true); onBack() } },
            onCopy = {
                scope.launch {
                    // Abort any queued debounce first: its delayed save would otherwise
                    // overwrite the copy's content with this (correct) document a moment
                    // after duplication, or race the read below.
                    saveJob?.cancel()
                    val t = title.ifBlank { "Untitled" }
                    runCatching { app.notes.updateContent(noteId, t, serialize()) }
                    val copyId = app.notes.duplicate(noteId)
                    if (copyId > 0) {
                        val tagsOfNote = runCatching { app.tags.tagsForNote(noteId).first() }.getOrDefault(emptyList())
                        app.tags.copyTags(noteId, copyId, tagsOfNote)
                        onBack()
                    }
                }
            },
            onReadOnly = {
                showOptions = false
                showMultipage = true
            }
        )
    }
    if (showInsertFromBottom) {
        InsertSheet(
            app = app,
            onDismiss = { showInsertFromBottom = false },
            onImageGallery = { galleryLauncher.launch("image/*") },
            onImageMulti = { multiImageLauncher.launch("image/*") },
            onImageCamera = { cameraLauncher.launch(null) },
            onTable = { showTableDialog = true },
            onDrawing = { showDrawing = true },
            onChart = { showChartDialog = true },
            onLink = { showLinkDialog = true },
            onComment = { showComments = true },
            onDivider = { appendBlock(DocumentBlock(type = BlockType.DIVIDER)) },
            onFootnote = { showFootnoteDialog = true },
            onContents = {
                appendBlock(DocumentBlock(type = BlockType.TOC))
            },
            onBookmark = { showBookmarkDialog = true },
            onPageBreak = { appendBlock(DocumentBlock(type = BlockType.PAGE_BREAK)) },
            onDateTime = {
                appendBlock(DocumentBlock(runs = listOf(TextRun(TimeFmt.todayLine() + " · " + TimeFmt.savedLine(System.currentTimeMillis()).removePrefix("Saved · ")))))
            },
            onAttachment = { attachLauncher.launch("*/*") },
            onShape = { showShapePicker = true },
            onTextBox = { appendBlock(DocumentBlock(type = BlockType.TEXTBOX, runs = listOf(TextRun("Text box")))) },
            onSignature = { showSignPad = true },
            onWatermark = { pageSetup = pageSetup.copy(watermark = "DRAFT"); scheduleSave() },
            onHeader = { showPageSetup = true },
            onFooter = { showPageSetup = true },
            onAudio = { attachLauncher.launch("audio/*") },
            onVideo = { attachLauncher.launch("video/*") },
            onSymbols = { appendBlock(DocumentBlock(runs = listOf(TextRun("π © ® ™ → ← ↔ ∀ ∃ Σ ∞ √ ∫ ≠ ≤ ≥ ± × ÷ • …")))) },
            onEquation = { appendBlock(DocumentBlock(runs = listOf(TextRun("E = mc²")))) },
            onHandwriting = { showDrawing = true },
            onOcr = { showOcr = true },
            onScan = { showOcr = true }
        )
    }
    if (showPageSetup) {
        PageSetupSheet(
            initial = pageSetup,
            onDismiss = { showPageSetup = false },
            onSave = { pageSetup = it; scheduleSave(); showPageSetup = false }
        )
    }
    if (showFind) {
        FindReplaceSheet(
            blocks = blocks,
            onDismiss = { showFind = false },
            onReplaceAll = { newBlocks -> pushUndo(); updateBlocks(newBlocks) },
            onJump = { blockIndex, start, end ->
                val target = blocks.getOrNull(blockIndex)
                if (target != null) {
                    pendingSelection = blockIndex to (start until end)
                    focusedBlockId = target.id
                    showFind = false
                    scope.launch {
                        runCatching { listState.animateScrollToItem(blockIndex) }
                    }
                }
            }
        )
    }
    if (showComments) CommentsSheet(app, noteId, onDismiss = { showComments = false })
    if (showBookmarks) BookmarksSheet(
        app, noteId,
        blocks = blocks,
        onJump = { id ->
            focusedBlockId = id
            val idx = blocks.indexOfFirst { it.id == id }
            if (idx >= 0) scope.launch { runCatching { listState.animateScrollToItem(idx) } }
            showBookmarks = false
        },
        onDismiss = { showBookmarks = false }
    )
    if (showTags) TagsSheet(app, noteId, onDismiss = { showTags = false })
    if (showExport) {
        ExportSheet(
            app = app, noteId = noteId, title = title, blocks = blocks, pageSetup = pageSetup,
            onDismiss = { showExport = false }
        )
    }
    if (showVersions) {
        VersionsSheet(
            app = app, noteId = noteId,
            onDismiss = { showVersions = false },
            onRestored = {
                scope.launch {
                    val n = app.notes.getById(noteId)
                    if (n != null) {
                        val (b, p) = DocumentSerializer.deserializeForEditing(n.documentJson)
                        blocks = b.ifEmpty { listOf(DocumentBlock()) }
                        pageSetup = p
                    }
                }
            }
        )
    }
    if (showAi) {
        AiSheet(
            app = app,
            selectedText = blocks.filter { it.isTextBlock() }.joinToString("\n") { it.plainText() },
            onDismiss = { showAi = false },
            onInsert = { text ->
                appendBlock(DocumentBlock(runs = listOf(TextRun(text))))
                showAi = false
            },
            onReplace = { text ->
                pushUndo()
                updateBlocks(replaceTextBlocks(blocks, text))
                showAi = false
            }
        )
    }
    if (showTemplates) {
        TemplatesSheet(
            app = app,
            onDismiss = { showTemplates = false },
            onUse = { json ->
                pushUndo()
                val (b, p) = DocumentSerializer.deserializeForEditing(json)
                updateBlocks(b.ifEmpty { listOf(DocumentBlock()) })
                pageSetup = p
                showTemplates = false
            },
            onSaveCurrent = {
                scope.launch { app.templates.save(title.ifBlank { "Custom template" }, serialize()) }
            }
        )
    }
    if (showWordCount) {
        AlertDialog(
            onDismissRequest = { showWordCount = false },
            title = { Text("Document statistics") },
            text = {
                Column {
                    StatRow("Words", stats.words.toString())
                    StatRow("Characters", stats.chars.toString())
                    StatRow("Characters (no spaces)", stats.charsNoSpaces.toString())
                    StatRow("Sentences", stats.sentences.toString())
                    StatRow("Paragraphs", stats.paragraphs.toString())
                    StatRow("Pages (approx.)", stats.pages.toString())
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pages are estimated from ${pageSetup.paperSize} ${pageSetup.orientation} paper, " +
                            "your margins and line spacing. PDF export paginates precisely.",
                        fontSize = 11.sp, color = c.text2
                    )
                }
            },
            confirmButton = { ClayPrimaryButton("Close") { showWordCount = false } }
        )
    }
    if (showTableDialog) {
        TableDialog(onDismiss = { showTableDialog = false }, onInsert = { rows, cols, header ->
            appendBlock(
                DocumentBlock(
                    type = BlockType.TABLE,
                    table = TableData(rows, cols, header, List(rows * cols) { "" })
                )
            )
            showTableDialog = false
        })
    }
    if (showChartDialog) {
        ChartDialog(
            initial = null,
            onDismiss = { showChartDialog = false },
            onInsert = { kind, titleC, labels, values ->
                appendBlock(DocumentBlock(type = BlockType.CHART, chart = ChartData(kind, titleC, labels, values)))
                showChartDialog = false
            }
        )
    }
    if (showLinkDialog) {
        LinkDialog(onDismiss = { showLinkDialog = false }, onInsert = { url, text ->
            pushUndo()
            val linkBlock = DocumentBlock(runs = listOf(TextRun(text, link = url)))
            updateBlocks(blocks + linkBlock)
            showLinkDialog = false
        })
    }
    if (showFootnoteDialog) {
        FootnoteDialog(onDismiss = { showFootnoteDialog = false }, onInsert = { text ->
            scope.launch {
                val ref = runCatching {
                    withContext(Dispatchers.IO) { app.footnotes.add(noteId, text) }
                }.getOrDefault(System.currentTimeMillis() % 1000)
                appendBlock(
                    DocumentBlock(
                        type = BlockType.FOOTNOTE,
                        footnoteText = text,
                        footnoteRef = (ref % 1000).toInt().let { if (it == 0) 1 else it }
                    )
                )
            }
            showFootnoteDialog = false
        })
    }
    if (showBookmarkDialog) {
        BookmarkDialog(onDismiss = { showBookmarkDialog = false }, onSave = { name ->
            scope.launch { app.bookmarks.add(noteId, name, focusedBlockId ?: "") }
            showBookmarkDialog = false
        })
    }
    if (showDrawing) {
        DrawingScreen(
            onDismiss = { showDrawing = false },
            onSave = { path ->
                appendBlock(DocumentBlock(type = BlockType.DRAWING, drawingPath = path))
                showDrawing = false
            }
        )
    }
    if (showSignPad) {
        DrawingScreen(
            onDismiss = { showSignPad = false },
            onSave = { path ->
                appendBlock(DocumentBlock(type = BlockType.SIGNATURE, drawingPath = path, imageWidthPercent = 50))
                showSignPad = false
            }
        )
    }
    if (showOcr) {
        OcrSheet(
            onDismiss = { showOcr = false },
            onText = { text ->
                appendBlock(DocumentBlock(runs = listOf(TextRun(text))))
                showOcr = false
            }
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Move note to trash?") },
            text = { Text("The note moves to Trash and can be restored later. You can empty the trash from the library screen.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.notes.setTrashed(noteId, true); showDeleteConfirm = false; onBack() }
                }) { Text("Move to trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
    if (showColorPicker) {
        ColorPickerDialog(onDismiss = { showColorPicker = false }, onPick = { argb ->
            applyToFocused { b -> b.copy(runs = b.runs.map { it.copy(colorArgb = argb) }) }
            showColorPicker = false
        })
    }
    if (showHighlightPicker) {
        ColorPickerDialog(onDismiss = { showHighlightPicker = false }, onPick = { argb ->
            applyToFocused { b -> b.copy(runs = b.runs.map { it.copy(highlightArgb = argb) }) }
            showHighlightPicker = false
        })
    }
    if (showFontSizeMenu) {
        FontSizeDialog(
            onDismiss = { showFontSizeMenu = false },
            onPick = { size ->
                applyToFocused { b -> b.copy(runs = b.runs.map { it.copy(fontSizeSp = size) }) }
                showFontSizeMenu = false
            }
        )
    }
    if (showNav) {
        HeadingsSheet(
            blocks = blocks,
            onDismiss = { showNav = false },
            onJump = { idx -> showNav = false; scope.launch { runCatching { listState.animateScrollToItem(idx) } } }
        )
    }
    if (showShapePicker) {
        ShapePicker { shape ->
            appendBlock(DocumentBlock(type = BlockType.SHAPE, extra = shape))
            showShapePicker = false
        }
    }
}

// ---------------- small helpers/composables ----------------

@Composable
private fun MiniFab(label: String, onClick: () -> Unit) {
    val c = LocalNotelyColors.current
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(17.dp))
            .clayPress(onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 17.sp, color = c.text) }
}

@Composable
private fun FmtButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onToggle: (Boolean) -> Unit) {
    val c = LocalNotelyColors.current
    var on by remember { mutableStateOf(false) }
    IconButton(onClick = { on = !on; onToggle(on) }) {
        Icon(icon, desc, tint = if (on) c.primary else c.text)
    }
}

private fun titleCase(s: String): String =
    s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

private fun launchVoice(
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    context: android.content.Context,
    onError: () -> Unit
) {
    try {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
        }
        launcher.launch(intent)
    } catch (e: Exception) {
        onError()
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val c = LocalNotelyColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = c.text2)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
    }
}

/**
 * Replaces the document's prose with [text] while keeping every non-text block
 * (images, tables, charts, drawings, page breaks…) where it was. The AI result
 * becomes one paragraph anchored at the position of the first text block that
 * was removed — previously the new paragraph was appended at a stale index.
 */
internal fun replaceTextBlocks(blocks: List<DocumentBlock>, text: String): List<DocumentBlock> {
    val firstText = blocks.indexOfFirst { it.isTextBlock() }
    val out = mutableListOf<DocumentBlock>()
    var inserted = false
    blocks.forEachIndexed { i, b ->
        if (b.isTextBlock()) {
            if (!inserted && (firstText < 0 || i == firstText)) {
                out.add(DocumentBlock(runs = listOf(TextRun(text))))
                inserted = true
            }
        } else {
            out.add(b)
        }
    }
    if (!inserted) out.add(0, DocumentBlock(runs = listOf(TextRun(text))))
    return out.ifEmpty { listOf(DocumentBlock(runs = listOf(TextRun(text)))) }
}

/** Paginated, read-only preview (Multiple Pages). Splits on explicit page breaks first. */
@Composable
private fun MultiPageView(blocks: List<DocumentBlock>, pageSetup: PageSetup, aspect: Float) {
    val c = LocalNotelyColors.current
    val pages = remember(blocks) {
        val out = mutableListOf<List<DocumentBlock>>()
        var current = mutableListOf<DocumentBlock>()
        blocks.forEach { b ->
            if (b.type == BlockType.PAGE_BREAK) {
                if (current.isNotEmpty()) { out.add(current); current = mutableListOf() }
            } else {
                current.add(b)
                // Soft cap so a long document still paginates sensibly without layout measurement.
                if (current.size >= 14) { out.add(current); current = mutableListOf() }
            }
        }
        if (current.isNotEmpty()) out.add(current)
        out.ifEmpty { listOf(emptyList()) }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        itemsIndexed(pages) { pi, pageBlocks ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(pageSetup.pageColorArgb))
                    .border(1.dp, c.border, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                if (pageSetup.header.isNotBlank()) Text(pageSetup.header, fontSize = 9.sp, color = c.text2)
                if (pi == 0 && pageSetup.watermark.isNotBlank()) {
                    Text(pageSetup.watermark, color = c.primary.copy(alpha = 0.12f), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    pageBlocks.forEach { b ->
                        when (b.type) {
                            BlockType.IMAGE, BlockType.DRAWING, BlockType.SIGNATURE -> {
                                val path = b.imagePath ?: b.drawingPath ?: b.extra
                                val bmp = remember(path) { ImageOps.decode(path, 700) }
                                val local = bmp
                                if (local != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = local.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
                                    )
                                }
                            }
                            BlockType.CHART -> ChartView(
                                chart = b.chart ?: ChartData("bar", "", emptyList(), emptyList()),
                                modifier = Modifier.fillMaxWidth()
                            )
                            BlockType.TABLE -> b.table?.let { t ->
                                Text(
                                    (0 until t.rows).joinToString("\n") { r ->
                                        (0 until t.cols).joinToString(" | ") { c2 -> t.cells.getOrNull(r * t.cols + c2) ?: "" }
                                    },
                                    fontSize = 9.sp, color = c.text
                                )
                            }
                            BlockType.DIVIDER -> HorizontalDivider(color = c.border)
                            BlockType.PAGE_BREAK -> Text("— page break —", fontSize = 8.sp, color = c.text2)
                            BlockType.FOOTNOTE -> Text("[${b.footnoteRef}] ${b.footnoteText}", fontSize = 8.sp, color = c.text2)
                            else -> {
                                if (b.plainText().isNotBlank()) {
                                    androidx.compose.foundation.text.BasicText(
                                        blockToAnnotated(b),
                                        style = TextStyle(
                                            fontSize = (b.runs.firstOrNull()?.fontSizeSp ?: 14f).sp * 0.72f,
                                            color = c.text,
                                            lineHeight = ((b.runs.firstOrNull()?.fontSizeSp ?: 14f) * b.lineSpacing * 0.72f).sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    buildString {
                        if (pageSetup.footer.isNotBlank()) append(pageSetup.footer).append("   ")
                        append("Page ${pi + 1} / ${pages.size}")
                    },
                    fontSize = 8.sp, color = c.text2
                )
            }
        }
    }
}

@Composable
private fun ColorPickerDialog(onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val palette = CardColors + listOf(Color.Black, Color(0xFF92909D), Color(0xFFD32F2F), Color.White)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a color") },
        text = {
            Column {
                palette.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        row.forEach { col -> ColorDot(col, false) { onPick(col.toArgb()) } }
                    }
                }
                Text("Highlight colors use the same palette.", fontSize = 11.sp, color = LocalNotelyColors.current.text2)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FontSizeDialog(onDismiss: () -> Unit, onPick: (Float) -> Unit) {
    val sizes = listOf(10f, 12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f, 40f, 48f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font size") },
        text = {
            Column {
                sizes.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { s -> ClayPill("${s.toInt()}", false) { onPick(s) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeadingsSheet(blocks: List<DocumentBlock>, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    val c = LocalNotelyColors.current
    val headings = blocks.mapIndexedNotNull { i, b ->
        if (b.type == BlockType.HEADING && b.plainText().isNotBlank()) Triple(i, b.headingLevel, b.plainText()) else null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            SheetHeader("Navigate", "${headings.size} heading(s) · tap to jump", onDismiss)
            Spacer(Modifier.height(8.dp))
            if (headings.isEmpty()) Text("Add Heading 1–8 styles and they appear here.", fontSize = 13.sp, color = c.text2)
            headings.forEach { (index, level, text) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp)
                        .clickable { onJump(index) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(((level - 1) * 12).dp))
                    Text(
                        "H$level", fontSize = 10.sp, color = c.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(text, fontSize = 14.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShapePicker(onPick: (String) -> Unit) {
    val c = LocalNotelyColors.current
    ModalBottomSheet(onDismissRequest = { onPick("") }, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Insert shape", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = c.text)
            Spacer(Modifier.height(10.dp))
            val shapes = listOf("rectangle" to "▭", "circle" to "◯", "triangle" to "△", "diamond" to "◇", "arrow" to "➜", "callout" to "💬")
            shapes.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    row.forEach { (name, glyph) ->
                        ClayCard(Modifier.weight(1f), onClick = { onPick(name) }) {
                            Text(glyph, fontSize = 22.sp, color = c.primary)
                            Text(name.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = c.text)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
