package com.share.app.ui.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes a preview the image processor produced. Null if it cannot be read,
 * which the caller treats like having no preview at all.
 */
expect fun decodePreview(bytes: ByteArray): ImageBitmap?
