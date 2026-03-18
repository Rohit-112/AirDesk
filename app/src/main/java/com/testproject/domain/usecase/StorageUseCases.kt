package com.testproject.domain.usecase

import android.content.Context
import android.net.Uri
import com.testproject.domain.repository.IStorageRepository
import javax.inject.Inject

class IsFileSizeValidUseCase @Inject constructor(private val repository: IStorageRepository) {
    operator fun invoke(context: Context, uri: Uri) = repository.isFileSizeValid(context, uri)
}

class UploadFileUseCase @Inject constructor(private val repository: IStorageRepository) {
    suspend operator fun invoke(
        sessionCode: String,
        fileUri: Uri,
        fileName: String,
        onProgress: (Int) -> Unit
    ) = repository.uploadFile(sessionCode, fileUri, fileName, onProgress)
}

class DownloadFileBytesUseCase @Inject constructor(private val repository: IStorageRepository) {
    suspend operator fun invoke(url: String) = repository.downloadFileBytes(url)
}

class DeleteFileByUrlUseCase @Inject constructor(private val repository: IStorageRepository) {
    suspend operator fun invoke(url: String) = repository.deleteFileByUrl(url)
}

class DeleteSessionStorageUseCase @Inject constructor(private val repository: IStorageRepository) {
    suspend operator fun invoke(sessionCode: String) = repository.deleteSessionStorage(sessionCode)
}
