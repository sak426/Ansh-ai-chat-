package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.AiStudyRepository
import com.example.data.ChatMessage
import com.example.data.StudySparkRepository
import com.example.data.UiState
import com.example.util.ImageUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatComposerState(
    val pendingBitmap: Bitmap? = null,
    val isGenerating: Boolean = false,
    val streamingReply: String = "",
    val errorMessage: String? = null
)

class ChatViewModel(
    private val repository: StudySparkRepository,
    private val aiRepository: AiStudyRepository,
    private val currentUserId: String
) : ViewModel() {

    val messagesState: StateFlow<UiState<List<ChatMessage>>> = repository.observeChatMessages(currentUserId)
        .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing chat history", root)
            emit(UiState.Error(root.message ?: "Failed to load chat history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    private val _composerState = MutableStateFlow(ChatComposerState())
    val composerState: StateFlow<ChatComposerState> = _composerState.asStateFlow()

    fun attachHomeworkPhoto(bitmap: Bitmap) {
        _composerState.update { it.copy(pendingBitmap = bitmap, errorMessage = null) }
    }

    fun clearAttachedPhoto() {
        _composerState.update { it.copy(pendingBitmap = null) }
    }

    fun dismissError() {
        _composerState.update { it.copy(errorMessage = null) }
    }

    fun sendQuestion(rawPrompt: String, dailyChatLimit: Int) {
        val current = _composerState.value
        if (current.isGenerating) return

        val bitmap = current.pendingBitmap
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() && bitmap == null) return

        val displayPrompt = prompt.ifBlank {
            "Solve this textbook question step-by-step."
        }
        val historySnapshot = (messagesState.value as? UiState.Success)?.data.orEmpty()

        viewModelScope.launch {
            _composerState.update {
                it.copy(
                    pendingBitmap = null,
                    isGenerating = true,
                    streamingReply = "",
                    errorMessage = null
                )
            }

            try {
                // Atomically verify & consume 1 daily study question credit in Firestore (resets at midnight)
                val quotaResult = repository.consumeDailyUsageCredit(
                    userId = currentUserId,
                    isChat = true,
                    dailyLimit = dailyChatLimit
                )
                if (quotaResult.isFailure) {
                    throw quotaResult.exceptionOrNull()
                        ?: IllegalStateException("Daily study question limit reached. Come back tomorrow!")
                }

                val previewBase64 = if (bitmap != null) {
                    withContext(Dispatchers.Default) {
                        ImageUtils.bitmapToBase64(bitmap, maxDimension = 360, quality = 72)
                    }
                } else {
                    null
                }

                // Persist student message to Firestore
                val saveUserResult = repository.addChatMessage(
                    userId = currentUserId,
                    role = "user",
                    text = displayPrompt,
                    hasImage = bitmap != null,
                    imagePreviewBase64 = previewBase64
                )
                if (saveUserResult.isFailure) {
                    throw saveUserResult.exceptionOrNull()
                        ?: IllegalStateException("Could not save your message.")
                }

                // Stream Gemini answer
                var finalAnswer = ""
                aiRepository.streamStudyAnswer(
                    recentHistory = historySnapshot,
                    userPrompt = displayPrompt,
                    homeworkBitmap = bitmap
                ).collect { partial ->
                    finalAnswer = partial
                    _composerState.update { it.copy(streamingReply = partial) }
                }

                if (finalAnswer.isBlank()) {
                    throw IllegalStateException("Gemini returned an empty response. Please try again.")
                }

                repository.addChatMessage(
                    userId = currentUserId,
                    role = "model",
                    text = finalAnswer,
                    hasImage = false
                )

                _composerState.update {
                    it.copy(isGenerating = false, streamingReply = "", errorMessage = null)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini study response timed out", e)
                _composerState.update {
                    it.copy(
                        isGenerating = false,
                        streamingReply = "",
                        errorMessage = "Response timed out after 60 seconds. Please try again."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Study chat failed", e)
                _composerState.update {
                    it.copy(
                        isGenerating = false,
                        streamingReply = "",
                        errorMessage = e.localizedMessage ?: "Failed to get AI explanation."
                    )
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val result = repository.clearChatHistory(currentUserId)
            if (result.isFailure) {
                _composerState.update {
                    it.copy(errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Unable to clear chat.")
                }
            }
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val STOP_TIMEOUT_MS = 5000L

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                ChatViewModel(StudySparkRepository(db), AiStudyRepository(), currentUserId)
            }
        }
    }
}
