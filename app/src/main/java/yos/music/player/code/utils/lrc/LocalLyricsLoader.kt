package yos.music.player.code.utils.lrc

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.code.AudioMetadataUtils
import yos.music.player.data.libraries.SettingsLibrary
import java.io.File
import yos.music.player.code.utils.player.optionalPlaybackTask

/**
 * The sole local-file lyric acquisition job. A track change cancels the previous
 * read, then resolves the APK source order before handing one immutable document
 * to [LyricsController].
 */
class LocalLyricsLoader(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var loadJob: Job? = null

    fun load(
        trackKey: String,
        audioPath: String,
        durationMs: Long,
        currentPositionMs: Int
    ) {
        loadJob?.cancel()
        LyricsController.activateTrack(trackKey, currentPositionMs, durationMs)
        loadJob = scope.launch(Dispatchers.IO) {
            val reportFailure: (Exception) -> Unit = {
                android.util.Log.w("LocalLyricsLoader", "可选歌词读取失败，继续播放", it)
            }
            val sidecar = optionalPlaybackTask(reportFailure) { loadSidecar(audioPath) }
                ?: optionalPlaybackTask(reportFailure) { loadSafSidecar(trackKey) }
            val embedded = optionalPlaybackTask(reportFailure) { AudioMetadataUtils.extractEmbeddedLyrics(audioPath) }
            val selected = if (SettingsLibrary.LyricPreferEmbedded) {
                embedded?.takeIf(String::isNotBlank)?.let { it to LyricSource.EMBEDDED_TAG }
                    ?: sidecar
            } else {
                sidecar ?: embedded?.takeIf(String::isNotBlank)?.let { it to LyricSource.EMBEDDED_TAG }
            }
            if (selected != null) {
                withContext(Dispatchers.Main.immediate) {
                    LyricsController.submitRawLyrics(
                        trackKey = trackKey,
                        rawLyrics = selected.first,
                        currentPositionMs = currentPositionMs,
                        source = selected.second,
                        durationMs = durationMs
                    )
                }
            }
        }
    }

    fun cancel() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun loadSidecar(audioPath: String): Pair<String, LyricSource>? {
        val basePath = audioPath.substringBeforeLast('.', audioPath)
        for (extension in SIDECAR_EXTENSIONS) {
            val file = File("$basePath.$extension")
            if (!file.isFile) continue
            val content = readText(file)?.takeIf(String::isNotBlank) ?: continue
            return content to LyricSource.SIDECAR_FILE
        }
        return null
    }

    private fun loadSafSidecar(trackKey: String): Pair<String, LyricSource>? {
        val uri = runCatching { Uri.parse(trackKey) }.getOrNull() ?: return null
        if (uri.scheme != "content") return null
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: return null
        val desiredNames = SIDECAR_EXTENSIONS.map { "${displayName.substringBeforeLast('.', displayName)}.$it" }

        findDocumentSibling(uri, desiredNames)?.let { return it to LyricSource.SIDECAR_FILE }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
                null,
                null,
                null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            if (relativePath != null) {
                val volume = runCatching { MediaStore.getVolumeName(uri) }.getOrDefault(MediaStore.VOLUME_EXTERNAL)
                for (name in desiredNames) {
                    val sibling = context.contentResolver.query(
                        MediaStore.Files.getContentUri(volume),
                        arrayOf(MediaStore.MediaColumns._ID),
                        "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                        arrayOf(relativePath, name),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            Uri.withAppendedPath(MediaStore.Files.getContentUri(volume), cursor.getLong(0).toString())
                        } else null
                    }
                    readText(sibling)?.takeIf(String::isNotBlank)?.let { return it to LyricSource.SIDECAR_FILE }
                }
            }
        }
        return null
    }

    private fun findDocumentSibling(uri: Uri, desiredNames: List<String>): String? = runCatching {
        if (!DocumentsContract.isDocumentUri(context, uri)) return@runCatching null
        val documentId = DocumentsContract.getDocumentId(uri)
        val parentId = documentId.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentId.isBlank()) return@runCatching null
        val children = DocumentsContract.buildChildDocumentsUri(uri.authority, parentId)
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) in desiredNames) {
                    val sibling = DocumentsContract.buildDocumentUri(uri.authority, cursor.getString(0))
                    return@use readText(sibling)
                }
            }
            null
        }
    }.getOrNull()

    private fun readText(uri: Uri?): String? {
        if (uri == null) return null
        return context.contentResolver.openInputStream(uri)?.use { decodeText(it.readBytes()) }
    }

    private fun readText(file: File): String? = decodeText(file.readBytes())

    private fun decodeText(bytes: ByteArray): String? {
        if (looksLikeAudio(bytes)) return null
        return when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
            else -> bytes.toString(Charsets.UTF_8)
        }
    }

    private fun looksLikeAudio(bytes: ByteArray): Boolean {
        val head = bytes.take(12).toByteArray().toString(Charsets.ISO_8859_1)
        return head.startsWith("ID3") || head.startsWith("fLaC") || head.startsWith("OggS") ||
            head.startsWith("RIFF") || head.contains("ftyp")
    }

    private companion object {
        val SIDECAR_EXTENSIONS = listOf("ttml", "lrc")
    }
}
