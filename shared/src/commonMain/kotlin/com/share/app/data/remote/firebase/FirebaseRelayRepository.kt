package com.share.app.data.remote.firebase

import com.share.app.domain.repository.RelayRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.storage
import dev.gitlive.firebase.storage.storageMetadata

class FirebaseRelayRepository : RelayRepository {

    override suspend fun upload(path: String, bytes: ByteArray, contentType: String, onProgress: (Long) -> Unit) {
        val metadata = storageMetadata { this.contentType = contentType }
        Firebase.storage.reference(path)
            .putDataResumable(bytes.toStorageData(), metadata)
            .collect { progress -> onProgress(progress.bytesTransferred.toLong()) }
        onProgress(bytes.size.toLong())
    }

    override suspend fun download(path: String, maxBytes: Long): ByteArray =
        Firebase.storage.reference(path).getData(maxBytes).toByteArray()

    override suspend fun delete(path: String) {
        Firebase.storage.reference(path).delete()
    }
}

internal expect fun ByteArray.toStorageData(): Data

internal expect fun Data.toByteArray(): ByteArray
