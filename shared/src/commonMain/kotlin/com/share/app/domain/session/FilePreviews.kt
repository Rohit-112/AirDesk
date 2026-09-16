package com.share.app.domain.session

import com.share.app.domain.media.FileKind
import com.share.app.domain.media.FileTypes
import com.share.app.domain.media.ImagePreview
import com.share.app.domain.media.SerialImageProcessor
import com.share.app.domain.repository.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Gives an activity row its picture, and the inbox its preview, once they have
 * been drawn.
 *
 * Fire and forget: the row is already on screen with its type icon and simply
 * upgrades, and a file that cannot be drawn keeps the icon. Runs its callbacks
 * on [scope], which is the engine's own thread.
 */
internal class FilePreviews(
    private val scope: CoroutineScope,
    private val processor: SerialImageProcessor,
    private val history: HistoryRepository,
    /** A received file's preview is ready; [localFileId] says which file it belongs to. */
    private val onInboxPreview: (localFileId: String, preview: ImagePreview) -> Unit,
) {
    fun attach(historyId: String, bytes: ByteArray, mimeType: String, forInbox: Boolean) {
        val kind = FileTypes.kindOf(mimeType)
        if (kind != FileKind.IMAGE && kind != FileKind.VIDEO) return

        scope.launch {
            processor.preview(bytes, mimeType, SerialImageProcessor.THUMBNAIL_MAX_DIMENSION)
                ?.let { history.setThumbnail(historyId, it) }
            if (forInbox) {
                processor.preview(bytes, mimeType, SerialImageProcessor.PREVIEW_MAX_DIMENSION)
                    ?.let { onInboxPreview(historyId, it) }
            }
        }
    }
}
