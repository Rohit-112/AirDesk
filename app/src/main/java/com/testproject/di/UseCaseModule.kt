package com.testproject.di

import com.testproject.domain.repository.IHistoryRepository
import com.testproject.domain.repository.ISessionRepository
import com.testproject.domain.repository.IStorageRepository
import com.testproject.domain.usecase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideGetHistoryUseCase(repository: IHistoryRepository) = GetHistoryUseCase(repository)

    @Provides
    @Singleton
    fun provideGetQueuedItemsUseCase(repository: IHistoryRepository) = GetQueuedItemsUseCase(repository)

    @Provides
    @Singleton
    fun provideInsertHistoryUseCase(repository: IHistoryRepository) = InsertHistoryUseCase(repository)

    @Provides
    @Singleton
    fun provideMarkAsNotQueuedUseCase(repository: IHistoryRepository) = MarkAsNotQueuedUseCase(repository)

    @Provides
    @Singleton
    fun provideCreateSessionUseCase(repository: ISessionRepository) = CreateSessionUseCase(repository)

    @Provides
    @Singleton
    fun provideJoinSessionUseCase(repository: ISessionRepository) = JoinSessionUseCase(repository)

    @Provides
    @Singleton
    fun provideDeleteSessionUseCase(repository: ISessionRepository) = DeleteSessionUseCase(repository)

    @Provides
    @Singleton
    fun provideObserveClipboardUseCase(repository: ISessionRepository) = ObserveClipboardUseCase(repository)

    @Provides
    @Singleton
    fun provideObservePeerPresenceUseCase(repository: ISessionRepository) = ObservePeerPresenceUseCase(repository)

    @Provides
    @Singleton
    fun provideWriteClipboardUseCase(repository: ISessionRepository) = WriteClipboardUseCase(repository)

    @Provides
    @Singleton
    fun provideRemoveSessionListenerUseCase(repository: ISessionRepository) = RemoveSessionListenerUseCase(repository)

    @Provides
    @Singleton
    fun provideIsFileSizeValidUseCase(repository: IStorageRepository) = IsFileSizeValidUseCase(repository)

    @Provides
    @Singleton
    fun provideUploadFileUseCase(repository: IStorageRepository) = UploadFileUseCase(repository)

    @Provides
    @Singleton
    fun provideDownloadFileBytesUseCase(repository: IStorageRepository) = DownloadFileBytesUseCase(repository)

    @Provides
    @Singleton
    fun provideDeleteFileByUrlUseCase(repository: IStorageRepository) = DeleteFileByUrlUseCase(repository)

    @Provides
    @Singleton
    fun provideDeleteSessionStorageUseCase(repository: IStorageRepository) = DeleteSessionStorageUseCase(repository)
}
