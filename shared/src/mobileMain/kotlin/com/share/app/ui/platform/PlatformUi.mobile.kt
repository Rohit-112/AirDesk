package com.share.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.share.app.domain.model.OutgoingFile
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions

actual val isQrScanSupported: Boolean = true

@Composable
actual fun QrScannerView(onScanned: (String) -> Boolean, modifier: Modifier) {
    ScannerWithPermissions(
        modifier = modifier,
        onScanned = onScanned,
        types = listOf(CodeType.QR),
        enableTorch = false,
        permissionText = "Camera access is needed to scan a pairing code. You can type the code instead.",
        openSettingsLabel = "Open settings",
    )
}

actual fun Modifier.fileDropTarget(
    onDragStateChange: (Boolean) -> Unit,
    onFilesDropped: (List<OutgoingFile>) -> Unit,
): Modifier = this

actual val sendShortcutHint: String? = null
