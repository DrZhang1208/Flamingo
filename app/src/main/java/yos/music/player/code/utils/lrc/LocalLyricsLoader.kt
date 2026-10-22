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
    private var prefetchJob: Job? = null

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
            val selected = selectLyricSource(trackKey, audioPath, reportFailure)
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

    /**
     * Warm the lyric cache for the next queue item so a switch renders lyrics
     * from memory. Single-flight: only the newest prefetch request runs.
     */
    fun prefetchNext(trackKey: String, audioPath: String, durationMs: Long) {
        if (LyricsController.isCached(trackKey)) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            if (LyricsController.isCached(trackKey)) return@launch
            val selected = selectLyricSource(
                trackKey,
                audioPath,
                { android.util.Log.w("LocalLyricsLoader", "预取歌词失败，忽略", it) }
            ) ?: return@launch
            LyricsController.cacheRawLyrics(trackKey, selected.first, selected.second, durationMs)
        }
    }

    fun cancel() {
        loadJob?.cancel()
        loadJob = null
        prefetchJob?.cancel()
        prefetchJob = null
    }

    private suspend fun selectLyricSource(
        trackKey: String,
        audioPath: String,
        reportFailure: (Exception) -> Unit
    ): Pair<String, LyricSource>? {
        val sidecarText = optionalPlaybackTask(reportFailure) { loadSidecar(audioPath) }
            ?: optionalPlaybackTask(reportFailure) { loadSafSidecar(trackKey) }
        val embedded = optionalPlaybackTask(reportFailure) { AudioMetadataUtils.extractEmbeddedLyrics(audioPath) }
        return chooseLyricCandidate(embedded, sidecarText)
    }

    private fun loadSidecar(audioPath: String): String? {
        val basePath = audioPath.substringBeforeLast('.', audioPath)
        for (extension in SIDECAR_EXTENSIONS) {
            val file = File("$basePath.$extension")
            if (!file.isFile) continue
            val content = readText(file)?.takeIf(String::isNotBlank) ?: continue
            return content
        }
        return null
    }

    private fun loadSafSidecar(trackKey: String): String? {
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

        findDocumentSibling(uri, desiredNames)?.let { return it }
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
                    readText(sibling)?.takeIf(String::isNotBlank)?.let { return it }
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
        return context.contentResolver.openInputStream(uri)?.use { LyricsTextDecoder.decode(it.readBytes()) }
    }

    private fun readText(file: File): String? = LyricsTextDecoder.decode(file.readBytes())

    private companion object {
        val SIDECAR_EXTENSIONS = listOf("ttml", "lrc")
    }
}
