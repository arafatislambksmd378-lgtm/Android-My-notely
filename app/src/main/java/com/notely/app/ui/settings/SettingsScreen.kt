package com.notely.app.ui.settings

import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notely.app.NotelyApp
import com.notely.app.ai.AiAssistant
import com.notely.app.ui.components.*
import com.notely.app.ui.theme.*
import com.notely.app.util.Security
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(app: NotelyApp) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val mode by app.prefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val accent by app.prefs.accent.collectAsStateWithLifecycle(initialValue = Accent.PURPLE)
    val wsName by app.prefs.workspaceName.collectAsStateWithLifecycle(initialValue = "Writer")
    val wsIcon by app.prefs.workspaceIcon.collectAsStateWithLifecycle(initialValue = "W")
    val autosave by app.prefs.autosave.collectAsStateWithLifecycle(initialValue = true)
    val spell by app.prefs.spellcheck.collectAsStateWithLifecycle(initialValue = true)
    val font by app.prefs.defaultFont.collectAsStateWithLifecycle(initialValue = "Default")
    val fontSize by app.prefs.defaultFontSize.collectAsStateWithLifecycle(initialValue = 16)
    val lineSpacing by app.prefs.defaultLineSpacing.collectAsStateWithLifecycle(initialValue = 1.55f)
    val appLock by app.prefs.appLock.collectAsStateWithLifecycle(initialValue = false)
    val pinHash by app.prefs.pinHash.collectAsStateWithLifecycle(initialValue = "")
    val noteCount by app.notes.countActive().collectAsStateWithLifecycle(initialValue = 0)
    val nbCount by app.notebooks.countActive().collectAsStateWithLifecycle(initialValue = 0)
    val aiUrl by app.prefs.aiBaseUrl.collectAsStateWithLifecycle(initialValue = "")
    val aiModel by app.prefs.aiModel.collectAsStateWithLifecycle(initialValue = "")
    val aiEnabled by app.prefs.aiEnabled.collectAsStateWithLifecycle(initialValue = false)

    var editWs by remember { mutableStateOf(false) }
    var pinDraft by remember { mutableStateOf("") }
    var pinMsg by remember { mutableStateOf("") }
    var aiUrlDraft by remember(aiUrl) { mutableStateOf(aiUrl) }
    var aiModelDraft by remember(aiModel) { mutableStateOf(aiModel) }
    var aiKeyDraft by remember { mutableStateOf("") }
    var aiMsg by remember { mutableStateOf("") }
    var showStorage by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var caps by remember { mutableStateOf<AiAssistant.DeviceCaps?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = c.text)
        }
        item {
            // Workspace profile card (matches the reference's purple profile block)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.primary)
                    .clayPress { editWs = true }
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.onPrimary.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) { Text(wsIcon.take(1).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.onPrimary) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(wsName, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.onPrimary)
                        Text("Personal workspace · local-first", fontSize = 12.sp, color = c.onPrimary.copy(alpha = 0.85f))
                    }
                    Text("✎", fontSize = 17.sp, color = c.onPrimary)
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)); SectionLabel("Appearance") }
        item {
            ClayCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClayIconTile(c.primary) { Text("🌙", fontSize = 17.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Theme", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.text)
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = c.text2)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        ClayPill(m.name.lowercase().replaceFirstChar { it.uppercase() }, mode == m) {
                            scope.launch { app.prefs.setThemeMode(m) }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClayIconTile(AccentGreen) { Text("🎨", fontSize = 17.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Accent", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.text)
                        Text("Purple · green · blue · yellow", fontSize = 12.sp, color = c.text2)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Accent.entries.forEach { a ->
                        Box(
                            Modifier.size(38.dp).clip(CircleShape)
                                .background(accentColor(a.ordinal))
                                .clayPress { scope.launch { app.prefs.setAccent(a) } },
                            contentAlignment = Alignment.Center
                        ) {
                            if (accent == a) Text("✓", color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        item { SectionLabel("Security") }
        item {
            ClayCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ClayIconTile(AccentBlue) { Text("🔒", fontSize = 17.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("App lock", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.text)
                        Text(
                            if (appLock && pinHash.isNotBlank()) "PIN required to open Notely"
                            else "Require a PIN to open Notely",
                            fontSize = 12.sp, color = c.text2
                        )
                    }
                    Switch(checked = appLock, onCheckedChange = { scope.launch { app.prefs.setAppLock(it) } })
                }
                if (appLock) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            pinDraft, { pinDraft = it.filter { ch -> ch.isDigit() }.take(12) },
                            label = { Text(if (pinHash.isBlank()) "Set PIN" else "Change PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        ClayPrimaryButton("Save") {
                            scope.launch {
                                if (pinDraft.length >= 4) {
                                    val newHash = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        Security.hashPin(pinDraft)
                                    }
                                    app.prefs.setPinHash(newHash)
                                    pinMsg = "PIN saved — it will be required next time the app opens."
                                    pinDraft = ""
                                } else pinMsg = "PIN must be at least 4 digits."
                            }
                        }
                    }
                    if (pinMsg.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(pinMsg, fontSize = 12.sp, color = c.text2)
                    }
                    Text(
                        "The PIN is stored as a salted PBKDF2 verifier on this device and is never transmitted.",
                        fontSize = 11.sp, color = c.text2
                    )
                }
            }
        }

        item { SectionLabel("Editor") }
        item {
            ClayCard {
                SwitchRow("Auto-save", "Saves as you type", autosave) { scope.launch { app.prefs.setAutosave(it) } }
                SwitchRow("Spelling & grammar", "Flags likely typos while reviewing", spell) { scope.launch { app.prefs.setSpellcheck(it) } }
                Spacer(Modifier.height(8.dp))
                Text("Default font", fontSize = 13.sp, color = c.text2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Default", "Serif", "Mono").forEach { f ->
                        ClayPill(f, font == f) { scope.launch { app.prefs.setDefaultFont(f) } }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Default font size: $fontSize", fontSize = 13.sp, color = c.text2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClaySoftButton("−") { scope.launch { app.prefs.setDefaultFontSize((fontSize - 1).coerceAtLeast(10)) } }
                    Text("$fontSize", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    ClaySoftButton("+") { scope.launch { app.prefs.setDefaultFontSize((fontSize + 1).coerceAtMost(32)) } }
                }
                Spacer(Modifier.height(10.dp))
                Text("Default line spacing", fontSize = 13.sp, color = c.text2)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(1.0f, 1.15f, 1.3f, 1.55f, 1.8f, 2.0f)) { v ->
                        ClayPill("${v}×", lineSpacing == v) { scope.launch { app.prefs.setDefaultLineSpacing(v) } }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "New notes start with these defaults. Each note keeps its own page setup and formatting.",
                    fontSize = 11.sp, color = c.text2
                )
            }
        }

        item { SectionLabel("AI assistant (optional)") }
        item {
            ClayCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ClayIconTile(AccentYellow) { Text("✨", fontSize = 17.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Enable AI", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.text)
                        Text("Off by default · core app works fully offline", fontSize = 12.sp, color = c.text2)
                    }
                    Switch(checked = aiEnabled, onCheckedChange = { on ->
                        scope.launch { app.prefs.setAi(aiUrlDraft, aiModelDraft, null, on) }
                    })
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Online mode sends the text you choose to your own OpenAI-compatible provider. " +
                        "Nothing is sent unless you press Generate, and nothing is shared with Notely.",
                    fontSize = 11.sp, color = c.text2
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    aiUrlDraft, { aiUrlDraft = it },
                    label = { Text("Provider base URL (https://api.openai.com/v1)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    aiModelDraft, { aiModelDraft = it }, label = { Text("Model (e.g. gpt-4o-mini)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    aiKeyDraft, { aiKeyDraft = it },
                    label = { Text(if (aiKeyDraft.isBlank()) "API key (stored encrypted on device)" else "API key") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClayPrimaryButton("Save AI settings") {
                        scope.launch {
                            try {
                                app.prefs.setAi(aiUrlDraft, aiModelDraft, aiKeyDraft.takeIf { it.isNotBlank() }, aiEnabled)
                                aiKeyDraft = ""
                                aiMsg = "Saved securely."
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                aiMsg = "Could not save securely. Existing settings were not changed."
                            }
                        }
                    }
                    ClaySoftButton("Remove key") {
                        scope.launch {
                            app.prefs.clearAiKey()
                            aiKeyDraft = ""
                            aiMsg = "API key removed."
                        }
                    }
                    ClaySoftButton("Device check") { caps = AiAssistant.deviceCaps(app) }
                }
                caps?.let { capsInfo ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "RAM: ${capsInfo.totalRamMb} MB · free: ${"%.1f".format(capsInfo.freeStorageGb)} GB · ${capsInfo.arch}\n" +
                            "Recommended: ${capsInfo.recommendedModel}",
                        fontSize = 12.sp, color = c.text2
                    )
                }
                if (aiMsg.isNotBlank()) Text(aiMsg, fontSize = 12.sp, color = AccentGreen)
                Spacer(Modifier.height(8.dp))
                ClaySoftButton("On-device models (download)") {
                    caps = caps ?: AiAssistant.deviceCaps(app)
                    showModels = true
                }
            }
        }

        item { SectionLabel("About") }
        item {
            ClayCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClayIconTile(c.primary) { Text("📓", fontSize = 17.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Notely", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                        Text("Version 1.0.0 · Premium notes", fontSize = 12.sp, color = c.text2)
                    }
                    Text("$noteCount notes", fontSize = 12.sp, color = c.text2)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClayIconTile(AccentBlue) { Text("🗄", fontSize = 17.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Library", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.text)
                        Text("$nbCount notebooks · local storage", fontSize = 12.sp, color = c.text2)
                    }
                    TextButton(onClick = { showLibrary = true }) { Text("Open") }
                }
                Spacer(Modifier.height(6.dp))
                ClaySoftButton("Storage usage") { showStorage = true }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (editWs) {
        var wsNameDraft by remember { mutableStateOf(wsName) }
        var wsIconDraft by remember { mutableStateOf(wsIcon) }
        AlertDialog(
            onDismissRequest = { editWs = false },
            title = { Text("Edit workspace") },
            text = {
                Column {
                    OutlinedTextField(wsNameDraft, { wsNameDraft = it }, label = { Text("Workspace name") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(wsIconDraft, { wsIconDraft = it.take(2) }, label = { Text("Icon letter") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                }
            },
            confirmButton = {
                ClayPrimaryButton("Save") {
                    scope.launch {
                        app.prefs.setWorkspace(wsNameDraft.ifBlank { "Writer" }, wsIconDraft.ifBlank { "W" })
                        editWs = false
                    }
                }
            },
            dismissButton = { ClaySoftButton("Cancel") { editWs = false } }
        )
    }
    if (showStorage) {
        val stat = StatFs(app.filesDir.path)
        fun dirSize(f: File): Long = if (f.exists()) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
        var cacheCleared by remember { mutableStateOf("") }
        val sizes = remember(showStorage, cacheCleared) {
            mapOf(
                "Database" to File(app.filesDir, "notely.db").length(),
                "Exports" to dirSize(File(app.filesDir, "exports")),
                "Images" to dirSize(File(app.filesDir, "images")),
                "Drawings" to dirSize(File(app.filesDir, "drawings")),
                "Attachments" to dirSize(File(app.filesDir, "attachments")),
                "Cache" to dirSize(app.cacheDir)
            )
        }
        AlertDialog(
            onDismissRequest = { showStorage = false },
            title = { Text("Storage usage") },
            text = {
                Column {
                    sizes.forEach { (label, bytes) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, fontSize = 14.sp, color = c.text2)
                            Text(formatBytes(bytes), fontSize = 14.sp, color = c.text, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Free device storage: ${"%.1f".format(stat.availableBytes / 1e9)} GB of " +
                            "${"%.1f".format(stat.totalBytes / 1e9)} GB",
                        fontSize = 13.sp, color = c.text2
                    )
                    if (cacheCleared.isNotBlank()) Text(cacheCleared, fontSize = 12.sp, color = AccentGreen)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClaySoftButton("Clear cache") {
                        app.cacheDir.deleteRecursively()
                        cacheCleared = "Cache cleared."
                    }
                    ClayPrimaryButton("Close") { showStorage = false }
                }
            }
        )
    }
    if (showModels) {
        LocalModelsDialog(app, onDismiss = { showModels = false })
    }
    if (showLibrary) {
        val templates by app.templates.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
        val trashed by app.notes.observeTrashed().collectAsStateWithLifecycle(initialValue = emptyList())
        val archived by app.notes.observeArchived().collectAsStateWithLifecycle(initialValue = emptyList())
        AlertDialog(
            onDismissRequest = { showLibrary = false },
            title = { Text("Library") },
            text = {
                Column {
                    Text("Notes: $noteCount active · ${archived.size} archived · ${trashed.size} in trash", fontSize = 13.sp, color = c.text2)
                    Text("Notebooks: $nbCount", fontSize = 13.sp, color = c.text2)
                    Spacer(Modifier.height(8.dp))
                    Text("Templates (${templates.size})", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.text)
                    Column(Modifier.heightIn(max = 260.dp)) {
                        templates.forEach { t ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(t.name, modifier = Modifier.weight(1f), fontSize = 13.sp, color = c.text)
                                if (!t.builtin) {
                                    TextButton(onClick = { scope.launch { app.templates.delete(t.id) } }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { ClayPrimaryButton("Close") { showLibrary = false } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalModelsDialog(app: NotelyApp, onDismiss: () -> Unit) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    val caps = remember { AiAssistant.deviceCaps(app) }
    var installed by remember { mutableStateOf(AiAssistant.installedModels(app).map { it.name }) }
    var progress by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var progressTotalMb by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf("") }
    var cancelRequested by remember { mutableStateOf(false) }
    val totalSize = remember(installed) { AiAssistant.modelsSizeBytes(app) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            SheetHeader("On-device AI models", "Nothing downloads unless you tap Download", onDismiss)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your device: ${caps.totalRamMb} MB RAM · ${"%.1f".format(caps.freeStorageGb)} GB free · ${caps.arch}",
                fontSize = 12.sp, color = c.text2
            )
            Text(
                "These are open GGUF models. Downloads go to app-private storage and can be removed at any time. " +
                    "A local inference runtime (llama.cpp / MediaPipe) is not bundled with this build, so the online " +
                    "provider section above remains the way to generate text until such a runtime is added — the " +
                    "download screen exists so models are never fetched automatically or silently.",
                fontSize = 11.sp, color = c.text2
            )
            Spacer(Modifier.height(10.dp))
            Text("Installed: ${if (installed.isEmpty()) "none" else installed.joinToString()}", fontSize = 12.sp, color = c.text)
            Text("Model storage: ${"%.1f".format(totalSize / (1024.0 * 1024.0))} MB", fontSize = 12.sp, color = c.text2)
            Spacer(Modifier.height(10.dp))
            AiAssistant.localModels.forEach { model ->
                val compatible = AiAssistant.compatible(model, caps)
                ClayCard(Modifier.padding(bottom = 8.dp)) {
                    Text(model.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Text(
                        "${model.sizeGb} GB · needs ${model.minRamMb / 1000} GB RAM" +
                            if (compatible) " · compatible" else " · not recommended for this device",
                        fontSize = 11.sp, color = if (compatible) c.text2 else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isInstalled = installed.any { it.startsWith(model.id) }
                        if (isInstalled) {
                            ClaySoftButton("Remove") {
                                AiAssistant.deleteModel(model, app)
                                installed = AiAssistant.installedModels(app).map { it.name }
                                message = "Removed ${model.label}."
                            }
                        } else {
                            ClayPrimaryButton("Download", enabled = compatible && progress == null) {
                                cancelRequested = false
                                progress = model.id to 0
                                progressTotalMb = 0
                                message = ""
                                scope.launch {
                                    val result = runCatching {
                                        AiAssistant.downloadModel(
                                            app, model,
                                            onProgress = { done, total ->
                                                progress = model.id to done
                                                progressTotalMb = total
                                            },
                                            isCancelled = { cancelRequested }
                                        )
                                    }
                                    progress = null
                                    result.onSuccess {
                                        installed = AiAssistant.installedModels(app).map { f -> f.name }
                                        message = "Downloaded ${model.label}."
                                    }.onFailure {
                                        message = it.message ?: "Download failed"
                                    }
                                }
                            }
                            // `progress` is a plain var captured by this lambda; reading it
                            // through a local keeps the smart cast valid.
                            val active = progress
                            if (active != null && active.first == model.id) {
                                val doneMb = active.second
                                Text(
                                    if (progressTotalMb > 0) "Downloading: $doneMb / $progressTotalMb MB"
                                    else "Downloading: $doneMb MB",
                                    fontSize = 11.sp, color = c.text2
                                )
                                if (progressTotalMb > 0) {
                                    LinearProgressIndicator(
                                        progress = { (doneMb.toFloat() / progressTotalMb).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    )
                                } else {
                                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                                }
                                ClaySoftButton("Cancel") { cancelRequested = true }
                            }
                        }
                    }
                }
            }
            if (message.isNotBlank()) {
                Text(message, fontSize = 12.sp, color = if (message.contains("fail", true)) MaterialTheme.colorScheme.error else c.text2)
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalNotelyColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.text)
            Text(subtitle, fontSize = 12.sp, color = c.text2)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
