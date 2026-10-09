package nl.guido.foodtracker.feature.sync

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The browser comes back here after Google sign-in (nl.guido.foodtracker://auth-callback?code=…).
 * Hands the code to [SyncManager] and returns to the app where the person left it.
 */
class AuthCallbackActivity : Activity() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun syncManager(): SyncManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent?.data?.getQueryParameter("code")
        EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).syncManager().finishSignIn(code)
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }
        finish()
    }
}
