package com.example.viewmodel

import android.content.Context
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
import com.example.data.ImageAspectOption
import com.example.data.StudySparkRepository
import com.example.util.ImageUtils
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data class Loading(val prompt: String) : ImageGenUiState
    data class Success(val bitmap: Bitmap, val prompt: String) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

class ImageGenViewModel(
    private val aiRepository: AiStudyRepository,
    private val repository: StudySparkRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val uiState: StateFlow<ImageGenUiState> = _uiState.asStateFlow()

    private val _selectedAspect = MutableStateFlow(ImageAspectOption.SQUARE)
    val selectedAspect: StateFlow<ImageAspectOption> = _selectedAspect.asStateFlow()

    private val _saveFeedback = MutableStateFlow<String?>(null)
    val saveFeedback: StateFlow<String?> = _saveFeedback.asStateFlow()

    fun selectAspect(option: ImageAspectOption) {
        _selectedAspect.value = option
    }

    fun clearSaveFeedback() {
        _saveFeedback.value = null
    }

    fun generateImage(prompt: String, styleSuffix: String, dailyImageLimit: Int) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return

        val finalPrompt = if (styleSuffix.isNotBlank()) {
            "$cleanPrompt. $styleSuffix"
        } else {
            cleanPrompt
        }

        viewModelScope.launch {
            _uiState.value = ImageGenUiState.Loading(cleanPrompt)
            _saveFeedback.value = null
            try {
                // Atomically verify & consume 1 daily image credit in Firestore (resets at midnight)
                val quotaResult = repository.consumeDailyUsageCredit(
                    userId = currentUserId,
                    isChat = false,
                    dailyLimit = dailyImageLimit
                )
                if (quotaResult.isFailure) {
                    throw quotaResult.exceptionOrNull()
                        ?: IllegalStateException("Daily image limit reached. Come back tomorrow!")
                }

                val bitmap = aiRepository.generateEducationalImage(
                    prompt = finalPrompt,
                    aspectOption = _selectedAspect.value
                )
                if (bitmap != null) {
                    _uiState.value = ImageGenUiState.Success(bitmap, cleanPrompt)
                } else {
                    _uiState.value = ImageGenUiState.Error(
                        "No image was returned by the model. Try refining your prompt."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _uiState.value = ImageGenUiState.Error(
                    "Image generation timed out after 60 seconds. Please try again."
                )
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Image generation quota exceeded", e)
                _uiState.value = ImageGenUiState.Error(
                    "Image generation rate limit or quota reached. Please try again later."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _uiState.value = ImageGenUiState.Error(
                    e.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    fun saveCurrentImageToGallery(context: Context) {
        val successState = _uiState.value as? ImageGenUiState.Success ?: return
        viewModelScope.launch {
            val result = ImageUtils.saveBitmapToGallery(
                context = context.applicationContext,
                bitmap = successState.bitmap,
                displayNamePrefix = "AnshAIChat"
            )
            _saveFeedback.value = result.fold(
                onSuccess = { filename -> "Saved to Pictures/AnshAIChat ($filename)" },
                onFailure = { err -> "Could not save image: ${err.localizedMessage ?: "Unknown error"}" }
            )
        }
    }

    companion object {
        private const val TAG = "ImageGenViewModel"

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                ImageGenViewModel(AiStudyRepository(), StudySparkRepository(db), currentUserId)
            }
        }
    }
}
