package com.pocketshadow.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import com.pocketshadow.app.ui.screens.OnboardingScreen
import com.pocketshadow.app.ui.screens.PocketShadowChatScreen
import com.pocketshadow.app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"
private const val ACTION_NEW_CHAT = "com.pocketshadow.app.action.NEW_CHAT"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var appUpdateManager: AppUpdateManager

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Install splash screen FIRST — before super.onCreate and setContent.
        //    The OS will keep showing the splash until the first frame is drawn.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 2. Handle "New Chat" launcher shortcut intent
        handleIntent(intent)

        // 3. Set up Play In-App Update manager
        appUpdateManager = AppUpdateManagerFactory.create(this)

        // 4. Collect the in-app review trigger emitted by SettingsRepository
        //    after conversation milestones (5 / 15 / 30 completed chats).
        lifecycleScope.launch {
            mainViewModel.reviewTrigger.collect { launchReviewFlow() }
        }

        setContent {
            val onboardingDone by mainViewModel.hasCompletedOnboarding
                .collectAsStateWithLifecycle()

            AnimatedContent(
                targetState    = onboardingDone,
                transitionSpec = {
                    if (targetState) {
                        (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.95f))
                            .togetherWith(fadeOut(tween(250)))
                    } else {
                        fadeIn(tween(300)).togetherWith(fadeOut(tween(300)))
                    }
                },
                label = "root_nav"
            ) { done ->
                if (done) {
                    PocketShadowChatScreen(mainViewModel = mainViewModel)
                } else {
                    OnboardingScreen(onComplete = mainViewModel::completeOnboarding)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkForAppUpdate()
    }

    // Called when app is already open and the shortcut is tapped again
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // ── Launcher shortcut ─────────────────────────────────────────────────────

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_NEW_CHAT) {
            mainViewModel.requestNewChat()
        }
    }

    // ── In-App Update (Flexible) ──────────────────────────────────────────────
    // Flexible update: downloads in background, user can keep using the app.
    // We call completeUpdate() when download finishes so it installs on next restart.

    private fun checkForAppUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                // New update available — start flexible download
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    appUpdateManager.startUpdateFlow(
                        info,
                        this,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    )
                }
                // Update already downloaded (e.g., from a previous session) — install it
                info.installStatus() == InstallStatus.DOWNLOADED -> {
                    appUpdateManager.completeUpdate()
                }
            }
        }.addOnFailureListener { e ->
            // Non-fatal — Play Store may be unavailable (sideloaded build, no network)
            Log.d(TAG, "App update check skipped: ${e.message}")
        }
    }

    // ── In-App Review ─────────────────────────────────────────────────────────
    // Google Play enforces its own rate limit internally — it may silently
    // skip showing the dialog if the user has already rated or was prompted recently.

    private fun launchReviewFlow() {
        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(this, task.result)
                    .addOnCompleteListener {
                        // Flow complete — result is intentionally opaque (Play Store design)
                        Log.d(TAG, "Review flow completed")
                    }
            } else {
                Log.d(TAG, "Review request failed: ${task.exception?.message}")
            }
        }
    }
}
