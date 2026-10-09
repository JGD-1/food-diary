package nl.guido.foodtracker.feature.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.guido.foodtracker.core.data.repo.CurrentUser
import nl.guido.foodtracker.core.data.repo.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton

/** STAND-IN: one fixed local user, no sign-in. Stream 7 replaces it with Google sign-in via Supabase. */
@Singleton
internal class StandInSessionRepository @Inject constructor() : SessionRepository {
    override val currentUser: StateFlow<CurrentUser> =
        MutableStateFlow(CurrentUser(userId = "local-user", householdId = "local-household", displayName = "Me"))
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncModule {
    @Binds abstract fun session(impl: StandInSessionRepository): SessionRepository
}
