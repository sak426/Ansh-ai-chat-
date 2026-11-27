package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.Announcement
import com.example.data.AppBranding
import com.example.data.PreferencesRepository
import com.example.data.StudySparkRepository
import com.example.data.ThemeMode
import com.example.data.UiState
import com.example.data.UsageLimits
import com.example.data.UserProfile
import com.example.data.todayUsageDateKey
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(
    private val repository: StudySparkRepository,
    private val preferencesRepository: PreferencesRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _todayKey = MutableStateFlow(todayUsageDateKey())
    val todayKey: StateFlow<String> = _todayKey.asStateFlow()

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing user profile", root)
            emit(UiState.Error(root.message ?: "Unable to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val branding: StateFlow<AppBranding> = repository.observeBranding(currentUserId)
        .onEach { branding ->
            preferencesRepository.cacheBranding(branding.resolvedAppName(), branding.logoBase64)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing branding", firestoreError ?: error)
            emit(AppBranding())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppBranding()
        )

    val announcement: StateFlow<Announcement?> = repository.observeAnnouncement(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing announcement", firestoreError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val usageLimits: StateFlow<UsageLimits> = repository.observeUsageLimits(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing usage limits", firestoreError ?: error)
            emit(UsageLimits())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UsageLimits()
        )

    init {
        viewModelScope.launch {
            val currentUser = Firebase.auth.currentUser
            if (currentUser != null && currentUser.uid == currentUserId) {
                repository.syncUserProfileOnSignIn(currentUser)
            }
        }
        // Automatically reset daily usage counts at local midnight even if the app remains open
        viewModelScope.launch {
            while (isActive) {
                val delayMs = millisUntilNextMidnight()
                delay(delayMs)
                val newKey = todayUsageDateKey()
                if (_todayKey.value != newKey) {
                    _todayKey.value = newKey
                    repository.resetDailyUsageIfNewDay(currentUserId)
                }
            }
        }
    }

    private fun millisUntilNextMidnight(): Long {
        val now = Calendar.getInstance()
        val nextMidnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 2)
            set(Calendar.MILLISECOND, 0)
        }
        return (nextMidnight.timeInMillis - now.timeInMillis).coerceAtLeast(5_000L)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
        private const val STOP_TIMEOUT_MS = 5000L

        fun provideFactory(
            currentUserId: String,
            preferencesRepository: PreferencesRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                MainViewModel(StudySparkRepository(db), preferencesRepository, currentUserId)
            }
        }
    }
}
