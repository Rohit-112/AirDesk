package com.share.app.data.remote.firebase

import com.share.app.platform.toByteArray
import com.share.app.platform.toNSData
import dev.gitlive.firebase.storage.Data

internal actual fun ByteArray.toStorageData(): Data = Data(toNSData())

internal actual fun Data.toByteArray(): ByteArray = data.toByteArray()
