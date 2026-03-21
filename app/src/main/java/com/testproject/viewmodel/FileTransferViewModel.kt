package com.testproject.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.testproject.domain.usecase.DeleteFileByUrlUseCase
import com.testproject.domain.usecase.DownloadFileBytesUseCase
import com.testproject.domain.usecase.IsFileSizeValidUseCase
import com.testproject.domain.usecase.UploadFileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FileTransferViewModel @Inject constructor(
    private val isFileSizeValidUseCase: IsFileSizeValidUseCase,
    private val uploadFileUseCase: UploadFileUseCase,
    private val downloadFileBytesUseCase: DownloadFileBytesUseCase,
    private val deleteFileByUrlUseCase: DeleteFileByUrlUseCase
) : ViewModel() {

    private val _uploadProgress = MutableLiveData<Int>()
    val uploadProgress: LiveData<Int> = _uploadProgress

    fun isFileSizeValid(context: Context, uri: Uri): Boolean {
        return isFileSizeValidUseCase(context, uri)
    }

    suspend fun uploadFile(sessionCode: String, uri: Uri, fileName: String): String? {
        return uploadFileUseCase(sessionCode, uri, fileName) { progress ->
            _uploadProgress.postValue(progress)
        }
    }

    suspend fun downloadFileBytes(url: String): ByteArray? {
        return downloadFileBytesUseCase(url)
    }

    suspend fun deleteFileByUrl(url: String) {
        deleteFileByUrlUseCase(url)
    }
}
