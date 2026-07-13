package com.pocketshadow.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.pocketshadow.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Thin ViewModel owned by MainActivity.
 * - Exposes the one-time onboarding gate.
 * - Routes the "New Chat" launcher shortcut intent to the chat screen.
 * - Passes through the in-app review trigger from SettingsRepository.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    /** True once the user has accepted T&C and tapped "Get Started". */
    val hasCompletedOnboarding: StateFlow<Boolean> = settingsRepo.hasCompletedOnboarding

    fun completeOnboarding() = settingsRepo.setOnboardingCompleted()

    // ── New-Chat shortcut ─────────────────────────────────────────────────────
    // MainActivity sets this when launched via the launcher shortcut.
    // PocketShadowChatScreen observes it and calls chatViewModel.newChat().

    private val _newChatRequested = MutableStateFlow(false)
    val newChatRequested: StateFlow<Boolean> = _newChatRequested.asStateFlow()

    fun requestNewChat()       { _newChatRequested.value = true }
    fun consumeNewChatRequest(){ _newChatRequested.value = false }

    // ── In-App Review trigger ─────────────────────────────────────────────────
    // Emits Unit after conversation milestones (5, 15, 30 completions).
    // MainActivity collects this and launches the Play review dialog.

    val reviewTrigger: SharedFlow<Unit> = settingsRepo.reviewTrigger

    fun requestReview() = settingsRepo.requestReview()
}
