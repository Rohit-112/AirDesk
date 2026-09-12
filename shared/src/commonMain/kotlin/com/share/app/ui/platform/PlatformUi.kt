package com.share.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.share.app.domain.model.OutgoingFile

/** Whether this device has a camera scanner. Desktop does not. */
expect val isQrScanSupported: Boolean

/**
 * A live camera view that reports every QR it reads. Return true from
 * [onScanned] to stop scanning, false to keep going.
 */
@Composable
expect fun QrScannerView(onScanned: (String) -> Boolean, modifier: Modifier)

/**
 * Dropping files from the operating system onto the window sends them.
 * A no-op on platforms without window drag and drop.
 */
expect fun Modifier.fileDropTarget(
    onDragStateChange: (Boolean) -> Unit,
    onFilesDropped: (List<OutgoingFile>) -> Unit,
): Modifier

/** Ctrl on Windows and Linux, Cmd on macOS, shown in shortcut hints. Null on touch devices. */
expect val sendShortcutHint: String?
