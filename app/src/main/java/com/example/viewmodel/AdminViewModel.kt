package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.StudySparkRepository
import com.example.data.UiState
import com.example.data.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminFeedbackState(
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class AdminViewModel(
    private val repository: StudySparkRepository,
    private val adminUid: String
) : ViewModel() {

    val usersState: StateFlow<UiState<List<UserProfile>>> = repository.observeAllUsers(adminUid)
        .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing registered users", root)
            emit(UiState.Error(root.message ?: "Unable to load users list"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    private val _feedbackState = MutableStateFlow(AdminFeedbackState())
    val feedbackState: StateFlow<AdminFeedbackState> = _feedbackState.asStateFlow()

    fun clearFeedback() {
        _feedbackState.update { it.copy(message = null, error = null) }
    }

    fun toggleUserBlocked(user: UserProfile) {
        if (user.userId == adminUid) {
            _feedbackState.update { it.copy(error = "You cannot block your own owner account.") }
            return
        }
        viewModelScope.launch {
            val targetBlocked = !user.blocked
            repository.setUserBlocked(user.userId, targetBlocked).fold(
                onSuccess = {
                    val status = if (targetBlocked) "blocked" else "unblocked"
                    _feedbackState.update {
                        it.copy(message = "${user.displayName} has been $status.", error = null)
                    }
                },
                onFailure = { err ->
                    _feedbackState.update {
                        it.copy(error = err.localizedMessage ?: "Failed to update user status.")
                    }
                }
            )
        }
    }

    fun saveBranding(
        appName: String,
        logoBase64: String,
        bannerBase64: String,
        bannerTitle: String,
        bannerSubtitle: String
    ) {
        viewModelScope.launch {
            _feedbackState.update { it.copy(isSaving = true, message = null, error = null) }
            repository.saveBranding(
                appName = appName,
                logoBase64 = logoBase64,
                bannerBase64 = bannerBase64,
                bannerTitle = bannerTitle,
                bannerSubtitle = bannerSubtitle
            ).fold(
                onSuccess = {
                    _feedbackState.update {
                        it.copy(
                            isSaving = false,
                            message = "Branding updated live for all students!"
                        )
                    }
                },
                onFailure = { err ->
                    _feedbackState.update {
                        it.copy(
                            isSaving = false,
                            error = err.localizedMessage ?: "Failed to save branding."
                        )
                    }
                }
            )
        }
    }

    fun saveAnnouncement(title: String, message: String, active: Boolean) {
        viewModelScope.launch {
            _feedbackState.update { it.copy(isSaving = true, message = null, error = null) }
            repository.saveAnnouncement(title, message, active).fold(
                onSuccess = {
                    val msg = if (active) "Announcement published to all students!" else "Announcement hidden."
                    _feedbackState.update { it.copy(isSaving = false, message = msg) }
                },
                onFailure = { err ->
                    _feedbackState.update {
                        it.copy(
                            isSaving = false,
                            error = err.localizedMessage ?: "Failed to save announcement."
                        )
                    }
                }
            )
        }
    }

    fun saveUsageLimits(dailyChatLimit: Int, dailyImageLimit: Int) {
        viewModelScope.launch {
            _feedbackState.update { it.copy(isSaving = true, message = null, error = null) }
            repository.saveUsageLimits(dailyChatLimit, dailyImageLimit).fold(
                onSuccess = {
                    _feedbackState.update {
                        it.copy(
                            isSaving = false,
                            message = "Daily limits updated ($dailyChatLimit questions, $dailyImageLimit images/day)!"
                        )
                    }
                },
                onFailure = { err ->
                    _feedbackState.update {
                        it.copy(
                            isSaving = false,
                            error = err.localizedMessage ?: "Failed to save daily limits."
                        )
                    }
                }
            )
        }
    }

    companion object {
        private const val TAG = "AdminViewModel"
        private const val STOP_TIMEOUT_MS = 5000L

        fun provideFactory(adminUid: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                AdminViewModel(StudySparkRepository(db), adminUid)
            }
        }
    }
}
