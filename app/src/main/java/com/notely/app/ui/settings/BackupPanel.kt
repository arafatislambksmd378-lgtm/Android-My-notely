package com.notely.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notely.app.NotelyApp
import com.notely.app.backup.LocalBackup
import kotlinx.coroutines.*

/** Storage Access Framework: user controls where an unencrypted local backup lives. */
@Composable
fun BackupPanel(app: NotelyApp) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    fun run(uri: Uri, restore: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val service = LocalBackup(app, app.db)
                    val progress: suspend (String) -> Unit = { value -> withContext(Dispatchers.Main) { message = value } }
                    if (restore) {
                        app.contentResolver.openInputStream(uri)?.use { service.restore(it, progress) }
                            ?: error("Could not open backup")
                    } else {
                        app.contentResolver.openOutputStream(uri, "wt")?.use { service.export(it, progress) }
                            ?: error("Could not write backup")
                        withContext(Dispatchers.Main) { message = "Backup saved. Keep it in a private location." }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                message = error.message?.take(180) ?: "Backup operation failed. Existing notes were not replaced."
            } finally { busy = false }
        }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        if (it != null) run(it, false)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) pendingRestore = it
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Local backup & restore", style = MaterialTheme.typography.titleMedium)
        Text("Includes notebooks, documents, recovery copies, versions and private media. Excludes PIN, API keys and AI models. Backups are not encrypted; choose private storage.")
        Button(enabled = !busy, onClick = { create.launch("Notely-${System.currentTimeMillis()}.notely.zip") }) { Text("Create full backup") }
        OutlinedButton(enabled = !busy, onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Restore backup as copies") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message)
    }
    pendingRestore?.let { uri ->
        AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("Restore as new copies?") },
            text = { Text("Existing data will not be overwritten. Notebooks and documents receive new IDs. Matching tags are reused. Future reminders are preserved. Large or incompatible backups will be rejected safely.") },
            confirmButton = { TextButton(onClick = { pendingRestore = null; run(uri, true) }) { Text("Restore copies") } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } })
    }
}
