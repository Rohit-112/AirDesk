package com.share.app.ui.platform

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import com.share.app.domain.media.FileTypes
import com.share.app.domain.model.OutgoingFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.datatransfer.DataFlavor
import java.io.File

actual val isQrScanSupported: Boolean = false

@Composable
actual fun QrScannerView(onScanned: (String) -> Boolean, modifier: Modifier) = Unit

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.fileDropTarget(
    onDragStateChange: (Boolean) -> Unit,
    onFilesDropped: (List<OutgoingFile>) -> Unit,
): Modifier = composed {
    val target = remember(onDragStateChange, onFilesDropped) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = onDragStateChange(true)

            override fun onExited(event: DragAndDropEvent) = onDragStateChange(false)

            override fun onEnded(event: DragAndDropEvent) = onDragStateChange(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                onDragStateChange(false)
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                val files = (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                    ?.filterIsInstance<File>()
                    ?.filter { it.isFile }
                    .orEmpty()
                if (files.isEmpty()) return false
                onFilesDropped(files.map { it.toOutgoingFile() })
                return true
            }
        }
    }
    dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        },
        target = target,
    )
}

private fun File.toOutgoingFile() = OutgoingFile(
    name = name,
    size = length(),
    contentType = FileTypes.detect(name).mimeType,
    readBytes = { withContext(Dispatchers.IO) { readBytes() } },
)

actual val sendShortcutHint: String? =
    if (System.getProperty("os.name").orEmpty().lowercase().contains("mac")) "⌘ Enter to send" else "Ctrl+Enter to send"
