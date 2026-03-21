package com.testproject.di

import com.testproject.data.webrtc.WebRTCRepositoryImpl
import com.testproject.domain.webrtc.WebRTCRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WebRTCModule {

    @Binds
    @Singleton
    abstract fun bindWebRTCRepository(
        impl: WebRTCRepositoryImpl
    ): WebRTCRepository
}
