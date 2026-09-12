package com.share.app.data.remote.firebase

import com.share.app.domain.repository.AuthRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FirebaseAuthRepository : AuthRepository {
    override fun observeUserId(): Flow<String?> =
        Firebase.auth.authStateChanged.map { it?.uid }.distinctUntilChanged()

    override suspend fun signInAnonymously() {
        Firebase.auth.signInAnonymously()
    }
}
