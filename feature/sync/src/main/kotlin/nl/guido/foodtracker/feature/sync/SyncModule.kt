package nl.guido.foodtracker.feature.sync

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.guido.foodtracker.core.data.repo.CurrentUser
import nl.guido.foodtracker.core.data.repo.SessionRepository
import nl.guido.foodtracker.core.data.repo.SyncEntry
import nl.guido.foodtracker.feature.sync.local.StoredAccount
import nl.guido.foodtracker.feature.sync.remote.Http
import nl.guido.foodtracker.feature.sync.remote.SupabaseApi
import nl.guido.foodtracker.feature.sync.remote.SupabaseConfig
import nl.guido.foodtracker.feature.sync.remote.UrlConnectionHttp
import javax.inject.Inject
import javax.inject.Singleton

/** The current user: the Google account once signed in, a local user before that. */
@Singleton
internal class SyncSessionRepository @Inject constructor(manager: SyncManager) : SessionRepository {
    override val currentUser: StateFlow<CurrentUser> = manager.state
        .map { it.account.toCurrentUser() }
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, manager.state.value.account.toCurrentUser())

    init {
        // Every screen asks for the current user, so this is the earliest place to start syncing.
        manager.start()
    }
}

internal fun StoredAccount?.toCurrentUser(): CurrentUser =
    if (this == null) LOCAL_USER else CurrentUser(userId, householdId, displayName ?: email ?: LOCAL_USER.displayName)

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncModule {
    @Binds abstract fun session(impl: SyncSessionRepository): SessionRepository
    @Binds abstract fun http(impl: UrlConnectionHttp): Http
    @Binds abstract fun entry(impl: SignInOffer): SyncEntry

    companion object {
        @Provides fun config() = SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY)

        @Provides @Singleton fun api(config: SupabaseConfig, http: Http) = SupabaseApi(config, http)
    }
}
