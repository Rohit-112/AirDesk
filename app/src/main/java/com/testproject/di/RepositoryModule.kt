package com.testproject.di

import com.testproject.data.SessionRepositoryImpl
import com.testproject.data.StorageRepositoryImpl
import com.testproject.data.local.HistoryRepositoryImpl
import com.testproject.domain.repository.IHistoryRepository
import com.testproject.domain.repository.ISessionRepository
import com.testproject.domain.repository.IStorageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        historyRepositoryImpl: HistoryRepositoryImpl
    ): IHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        sessionRepositoryImpl: SessionRepositoryImpl
    ): ISessionRepository

    @Binds
    @Singleton
    abstract fun bindStorageRepository(
        storageRepositoryImpl: StorageRepositoryImpl
    ): IStorageRepository
}
