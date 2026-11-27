package com.notely.app.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.notely.app.ui.components.ClayPrimaryButton
import com.notely.app.ui.components.ClaySoftButton
import com.notely.app.ui.theme.LocalNotelyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSheet(onDismiss: () -> Unit, onText: (String) -> Unit) {
    val c = LocalNotelyColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Pick an image to recognize text (offline ML Kit).") }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; status = "Reading image…"
        scope.launch(Dispatchers.IO) {
            try {
                // Photos from modern cameras are 12–48 MP; decode a bounded version so
                // OCR does not allocate hundreds of megabytes.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1600 && sample < 8) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap: Bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                } ?: throw IllegalStateException("Could not read image")
                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val text = try {
                    kotlinx.coroutines.tasks.await(recognizer.process(image))
                } finally {
                    // Release the native detector; holding it leaks model resources.
                    runCatching { recognizer.close() }
                    runCatching { bitmap.recycle() }
                }
                withContext(Dispatchers.Main) {
                    result = text.text
                    status = if (text.text.isBlank()) "No text found in this image." else "Recognized ${text.text.length} characters."
                    busy = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    status = "OCR failed: ${e.message}. You can retry with another image."
                    busy = false
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recognize text (OCR)", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.text)
                IconButton(onClick = onDismiss) { Text("✕") }
            }
            Text(status, fontSize = 13.sp, color = c.text2)
            Spacer(Modifier.height(8.dp))
            ClayPrimaryButton(if (busy) "Working…" else "Pick image & scan") {
                launcher.launch("image/*")
            }
            if (result.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text(result, fontSize = 14.sp, color = c.text)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClayPrimaryButton("Insert into note") { onText(result) }
                    ClaySoftButton("Copy") {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("ocr", result))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
