package com.share.app.di

import com.share.app.config.AppConfig
import com.share.app.data.crypto.CryptographySessionCipher
import com.share.app.data.file.FileKitFileSystemRepository
import com.share.app.data.local.DataStorePreferencesRepository
import com.share.app.data.local.InMemoryHistoryRepository
import com.share.app.data.remote.firebase.FirebaseAuthRepository
import com.share.app.data.remote.firebase.FirebaseRelayRepository
import com.share.app.data.remote.firebase.FirebaseSessionRepository
import com.share.app.data.remote.firebase.FirebaseSignalingRepository
import com.share.app.domain.crypto.SessionCipher
import com.share.app.domain.repository.AuthRepository
import com.share.app.domain.repository.FileSystemRepository
import com.share.app.domain.repository.HistoryRepository
import com.share.app.domain.repository.PreferencesRepository
import com.share.app.domain.repository.RelayRepository
import com.share.app.domain.repository.SessionRemoteRepository
import com.share.app.domain.repository.SignalingRepository
import com.share.app.domain.session.SessionEngine
import com.share.app.domain.usecase.PairingUseCase
import com.share.app.domain.usecase.PreferencesUseCase
import com.share.app.domain.usecase.TransferUseCase
import com.share.app.ui.home.HomeViewModel
import com.share.app.ui.root.RootViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

val dataModule = module {
    singleOf(::FirebaseAuthRepository) bind AuthRepository::class
    singleOf(::FirebaseSessionRepository) bind SessionRemoteRepository::class
    singleOf(::FirebaseSignalingRepository) bind SignalingRepository::class
    singleOf(::FirebaseRelayRepository) bind RelayRepository::class
    single<SessionCipher> { CryptographySessionCipher() }
    singleOf(::DataStorePreferencesRepository) bind PreferencesRepository::class
    singleOf(::InMemoryHistoryRepository) bind HistoryRepository::class
    singleOf(::FileKitFileSystemRepository) bind FileSystemRepository::class
}

val domainModule = module {
    single { AppConfig() }
    single {
        SessionEngine(
            config = get(),
            authRepository = get(),
            sessionRepository = get(),
            signalingRepository = get(),
            relayRepository = get(),
            cipher = get(),
            preferencesRepository = get(),
            historyRepository = get(),
            fileSystemRepository = get(),
            peerConnectionFactory = get(),
        )
    }
    factoryOf(::PairingUseCase)
    factoryOf(::TransferUseCase)
    factoryOf(::PreferencesUseCase)
}

val presentationModule = module {
    viewModelOf(::RootViewModel)
    viewModelOf(::HomeViewModel)
}

/** DataStore, clipboard and the WebRTC implementation for the current platform. */
expect val platformModule: Module

/**
 * Starts DI and the session engine. Call once, before any UI, from each
 * platform's entry point. Firebase must already be initialised.
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication {
    val application = startKoin {
        appDeclaration()
        modules(platformModule, dataModule, domainModule, presentationModule)
    }
    application.koin.get<SessionEngine>().start()
    return application
}

/** Hands a scanned or tapped `?code=` link to the running session engine. */
fun handleIncomingLink(raw: String) {
    KoinPlatform.getKoinOrNull()?.get<SessionEngine>()?.handleDeepLink(raw)
}
