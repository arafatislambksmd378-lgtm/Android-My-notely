package com.notely.app.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional AI writing assistant.
 * - Device capability detection (RAM / storage / arch) for model guidance.
 * - Optional OpenAI-compatible HTTP provider configured by the user (no hardcoded keys).
 * - Core app never depends on this; all failures are surfaced as errors.
 */
object AiAssistant {

    data class DeviceCaps(
        val totalRamMb: Long, val freeStorageGb: Double, val arch: String,
        val recommendedModel: String, val canRunLocal: Boolean
    )

    fun deviceCaps(context: Context): DeviceCaps {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalRamMb = info.totalMem / (1024 * 1024)
        val stat = StatFs(File(context.filesDir.path).path)
        val freeGb = (stat.availableBytes.toDouble() / 1e9)
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val (model, canRun) = when {
            totalRamMb >= 6000 && freeGb >= 4 -> "3B-class quantized (e.g. Llama-3.2-3B Q4)" to true
            totalRamMb >= 3000 && freeGb >= 2 -> "1B-class quantized (e.g. SmolLM-1.7B Q4)" to true
            else -> "Cloud provider recommended (device too small for local model)" to false
        }
        return DeviceCaps(totalRamMb, freeGb, arch, model, canRun)
    }

    enum class Action { GENERATE, REWRITE, GRAMMAR, SUMMARIZE, EXPAND, TRANSLATE, TITLES, OUTLINE, EXPLAIN, IDEAS, REPORT }

    /** A downloadable on-device model the user may choose to install (never automatic). */
    data class LocalModel(val id: String, val label: String, val sizeGb: Double, val minRamMb: Long, val url: String)

    val localModels = listOf(
        LocalModel("qwen2.5-0.5b-q4", "Qwen2.5 0.5B (Q4, fastest, 1 GB RAM)", 0.4, 2000,
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"),
        LocalModel("smollm2-1.7b-q4", "SmolLM2 1.7B (Q4, balanced, 3 GB RAM)", 1.1, 3000,
            "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf"),
        LocalModel("llama3.2-3b-q4", "Llama 3.2 3B (Q4, best quality, 6 GB RAM)", 2.0, 6000,
            "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf")
    )

    fun modelDir(context: Context): File = File(context.filesDir, "ai_models").apply { mkdirs() }

    fun installedModels(context: Context): List<File> =
        modelDir(context).listFiles { f -> f.isFile && f.length() > 0 }?.toList() ?: emptyList()

    fun modelsSizeBytes(context: Context): Long = installedModels(context).sumOf { it.length() }

    fun compatible(model: LocalModel, caps: DeviceCaps): Boolean =
        caps.totalRamMb >= model.minRamMb && caps.freeStorageGb >= model.sizeGb

    /**
     * Streams a model file onto internal storage with progress and a size check.
     * Called only after the user explicitly taps "Download" and confirms the size.
     */
    suspend fun downloadModel(
        context: Context,
        model: LocalModel,
        onProgress: suspend (downloadedMb: Long, totalMb: Long) -> Unit,
        isCancelled: () -> Boolean = { false }
    ): File = withContext(Dispatchers.IO) {
        val target = File(modelDir(context), model.id + ".gguf")
        if (target.exists() && target.length() > 0) return@withContext target
        val temp = File(target.absolutePath + ".part")
        val conn = (URL(model.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Notely/1.0 (Android)")
            // huggingface.co redirects to a CDN; advertising the binary type avoids
            // HTML error pages being saved as ".gguf".
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val total = conn.contentLengthLong
            if (total > 0 && total < 1_000_000L) {
                throw IllegalStateException("The server returned an invalid file (${total} bytes).")
            }
            val expectBytes = if (total > 0) total else (model.sizeGb * 1_000_000_000L).toLong()
            val freeBytes = StatFs(context.filesDir.path).availableBytes
            if (freeBytes < expectBytes + 100L * 1024 * 1024) {
                throw IllegalStateException(
                    "Not enough free storage: ${"%.1f".format(freeBytes / 1e9)} GB available, " +
                        "${"%.1f".format(expectBytes / 1e9)} GB needed."
                )
            }
            conn.inputStream.use { input ->
                FileOutputStream(temp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    var lastReport = 0L
                    while (true) {
                        if (isCancelled()) break
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        readTotal += n
                        // Throttle to one callback per 4 MB: Compose state recomposition
                        // on every 64 KB chunk would stall the download.
                        if (readTotal - lastReport >= 4L * 1024 * 1024) {
                            lastReport = readTotal
                            // Callbacks touch Compose state, so hop to the main thread.
                            withContext(Dispatchers.Main) {
                                onProgress(readTotal / (1024 * 1024), expectBytes / (1024 * 1024))
                            }
                        }
                    }
                }
            }
            if (isCancelled() || (total > 0 && temp.length() != total)) {
                temp.delete()
                throw IllegalStateException(
                    if (isCancelled()) "Download cancelled — the partial file was removed."
                    else "Download incomplete (${temp.length()} of $total bytes) — please retry."
                )
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IllegalStateException("Could not finalise the model file.")
            }
            target
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    fun deleteModel(model: LocalModel, context: Context): Boolean {
        val f = File(modelDir(context), model.id + ".gguf")
        return !f.exists() || f.delete()
    }


    fun promptFor(action: Action, text: String): String = when (action) {
        Action.GENERATE -> "Write a short, polished passage about: $text"
        Action.REWRITE -> "Rewrite the following text more clearly:\n$text"
        Action.GRAMMAR -> "Fix grammar and spelling in:\n$text"
        Action.SUMMARIZE -> "Summarize this document concisely:\n$text"
        Action.EXPAND -> "Expand this paragraph with useful detail:\n$text"
        Action.TRANSLATE -> "Translate the following text to English:\n$text"
        Action.TITLES -> "Suggest 5 good titles for:\n$text"
        Action.OUTLINE -> "Create a structured outline for:\n$text"
        Action.EXPLAIN -> "Explain the following text simply:\n$text"
        Action.IDEAS -> "Brainstorm 8 ideas related to:\n$text"
        Action.REPORT -> "Turn these notes into a short report:\n$text"
    }

    /** Calls an OpenAI-compatible chat completions endpoint. Throws on failure. */
    fun callProvider(baseUrl: String, model: String, apiKey: String, userPrompt: String): String {
        require(baseUrl.isNotBlank()) { "AI provider URL is not configured (Settings → AI)." }
        require(model.isNotBlank()) { "AI model is not configured (Settings → AI)." }
        val url = URL(baseUrl.trimEnd('/') + "/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("Content-Type", "application/json")
        if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
        val body = """{"model":"${model.replace("\"", "")}","messages":[{"role":"user","content":${jsonString(userPrompt)}}],"temperature":0.7}"""
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) throw IllegalStateException("AI provider returned HTTP $code")
        return extractContent(resp)
    }

    /**
     * Pulls `choices[0].message.content` out of a chat-completions response.
     * Uses the platform JSON parser instead of string searching, because content
     * routinely contains escaped quotes, `{`, `}` and unicode escapes; a naive
     * scan truncates prose or picks up the `content` of an earlier message.
     */
    private fun extractContent(json: String): String {
        val root = runCatching { org.json.JSONObject(json) }.getOrNull()
            ?: throw IllegalStateException("Unexpected AI response format")
        root.optJSONObject("error")?.let { err ->
            throw IllegalStateException(err.optString("message").ifBlank { "AI provider error" })
        }
        val choices = root.optJSONArray("choices")
            ?: throw IllegalStateException("Unexpected AI response format")
        if (choices.length() == 0) throw IllegalStateException("AI provider returned no choices")
        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw IllegalStateException("Unexpected AI response format")
        // Some providers return content as an array of parts instead of a string.
        val raw = message.opt("content")
        val text = when (raw) {
            is String -> raw
            is org.json.JSONArray -> (0 until raw.length()).joinToString("") { i ->
                when (val part = raw.opt(i)) {
                    is String -> part
                    is org.json.JSONObject -> part.optString("text")
                    else -> ""
                }
            }
            else -> ""
        }
        if (text.isBlank()) throw IllegalStateException("AI provider returned an empty response")
        return text.trim()
    }

    private fun jsonString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
}
