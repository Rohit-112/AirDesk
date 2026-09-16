package com.share.app.data.file

import com.share.app.domain.media.FileTypes
import com.share.app.domain.model.OutgoingFile
import com.share.app.domain.repository.FileSystemRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileKitFileSystemRepository : FileSystemRepository {

    override suspend fun pickFile(imagesOnly: Boolean): OutgoingFile? {
        // Extensions rather than the platform's photo picker: that one is free
        // to hand back a JPEG in place of the HEIC the user actually picked,
        // which would make converting it pointless.
        val type = if (imagesOnly) FileKitType.File(extensions = IMAGE_EXTENSIONS) else FileKitType.File()
        val file = withContext(Dispatchers.Main) { FileKit.openFilePicker(type = type) }
        return file?.toOutgoingFile()
    }

    override suspend fun saveFile(name: String, bytes: ByteArray): Boolean {
        val extension = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 10 }
        val baseName = if (extension != null) name.removeSuffix(".$extension") else name
        val target = withContext(Dispatchers.Main) {
            FileKit.openFileSaver(suggestedName = baseName, defaultExtension = extension)
        } ?: return false
        target.write(bytes)
        return true
    }

    private companion object {
        /** What the web converter's picker accepts: any image type, plus HEIC and HEIF by name. */
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic", "heif", "ico", "svg")
    }
}

/** The type is only the name's claim here; the bytes are read when the file is sent. */
fun PlatformFile.toOutgoingFile(): OutgoingFile = OutgoingFile(
    name = name,
    size = size(),
    contentType = FileTypes.detect(name).mimeType,
    readBytes = { readBytes() },
)
