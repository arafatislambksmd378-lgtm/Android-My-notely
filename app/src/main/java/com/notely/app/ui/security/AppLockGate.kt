package com.notely.app.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notely.app.NotelyApp
import com.notely.app.ui.components.ClayIconTile
import com.notely.app.ui.components.ClayPrimaryButton
import com.notely.app.ui.theme.LocalNotelyColors
import kotlinx.coroutines.launch

/**
 * Real app lock: when a PIN hash exists the app shell stays behind this gate until the
 * PIN verifies. There is no bypass — "Forgot PIN" explains the local-only recovery path.
 */
@Composable
fun AppLockGate(app: NotelyApp, onUnlocked: () -> Unit) {
    val c = LocalNotelyColors.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    fun attempt() {
        if (pin.length < 4) { error = "Enter your 4+ digit PIN."; return }
        checking = true
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = app.prefs.verifyPin(pin)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                checking = false
                if (ok) {
                    onUnlocked()
                } else {
                    attempts++
                    pin = ""
                    error = if (attempts >= 5)
                        "Too many attempts. Wait a moment, then try again."
                    else "Incorrect PIN. ${5 - attempts} attempt(s) left."
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ClayIconTile(c.primary, size = 64) { Text("🔒", fontSize = 26.sp) }
            Spacer(Modifier.height(18.dp))
            Text("Notely is locked", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.text)
            Text(
                "Enter your PIN to open your notes. Everything stays on this device.",
                fontSize = 14.sp, color = c.text2, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter { it.isDigit() }.take(12); error = "" },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontSize = 20.sp),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                enabled = attempts < 5
            )
            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            ClayPrimaryButton(if (checking) "Checking…" else "Unlock", onClick = { attempt() })
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { showHelp = true }) { Text("Forgot PIN?") }
        }
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("PIN recovery") },
            text = {
                Text(
                    "The PIN is stored only on this device as a salted hash — it can never be read back. " +
                        "To regain access, clear Notely's app data in Android Settings → Apps → Notely → Storage. " +
                        "That removes the lock and also erases local notes, so keep exports of important documents."
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } }
        )
    }
}
