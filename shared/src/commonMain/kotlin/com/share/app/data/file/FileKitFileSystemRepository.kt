package com.share.app.data.file

import com.share.app.domain.model.OutgoingFile
import com.share.app.domain.policy.SessionLimits
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

    override suspend fun pickFile(): OutgoingFile? {
        val file = withContext(Dispatchers.Main) { FileKit.openFilePicker(type = FileKitType.File()) }
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
}

fun PlatformFile.toOutgoingFile(): OutgoingFile = OutgoingFile(
    name = name,
    size = size(),
    contentType = SessionLimits.guessContentType(name),
    readBytes = { readBytes() },
)
