package com.testproject.domain.repository

interface IPreferenceRepository {
    suspend fun saveSessionCode(code: String)
    suspend fun getSessionCode(): String?
    suspend fun setIsHost(isHost: Boolean)
    suspend fun isHost(): Boolean
    suspend fun setLoggedIn(value: Boolean)
    suspend fun isLoggedIn(): Boolean
    suspend fun saveLastSentText(text: String)
    suspend fun getLastSentText(): String?
    suspend fun removeSession()
    suspend fun clearPreferences()
}
