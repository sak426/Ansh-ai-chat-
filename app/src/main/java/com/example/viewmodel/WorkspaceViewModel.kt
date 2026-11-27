package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.AiStudyRepository
import com.example.data.StudyNote
import com.example.data.StudySparkRepository
import com.example.data.StudyTask
import com.example.data.TaskCategory
import com.example.data.TaskPriority
import com.example.data.UiState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
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

data class WorkspaceActionState(
    val summarizingNoteId: String? = null,
    val isSummarizingDraft: Boolean = false,
    val draftSummary: String = "",
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class WorkspaceViewModel(
    private val repository: StudySparkRepository,
    private val aiRepository: AiStudyRepository,
    private val currentUserId: String
) : ViewModel() {

    val notesState: StateFlow<UiState<List<StudyNote>>> = repository.observeNotes(currentUserId)
        .map<List<StudyNote>, UiState<List<StudyNote>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing notes", root)
            emit(UiState.Error(root.message ?: "Unable to load study notes"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val tasksState: StateFlow<UiState<List<StudyTask>>> = repository.observeTasks(currentUserId)
        .map<List<StudyTask>, UiState<List<StudyTask>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing tasks", root)
            emit(UiState.Error(root.message ?: "Unable to load study planner"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    private val _actionState = MutableStateFlow(WorkspaceActionState())
    val actionState: StateFlow<WorkspaceActionState> = _actionState.asStateFlow()

    fun clearMessages() {
        _actionState.update { it.copy(feedbackMessage = null, errorMessage = null) }
    }

    fun resetDraftSummary(initialSummary: String = "") {
        _actionState.update { it.copy(draftSummary = initialSummary, isSummarizingDraft = false) }
    }

    fun saveNote(
        noteId: String?,
        title: String,
        subject: String,
        content: String,
        summary: String,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            val result = if (noteId.isNullOrBlank()) {
                repository.addNote(currentUserId, title, subject, content, summary).map { Unit }
            } else {
                repository.updateNote(currentUserId, noteId, title, subject, content, summary)
            }
            result.fold(
                onSuccess = {
                    _actionState.update { it.copy(feedbackMessage = "Note saved", errorMessage = null) }
                    onSaved()
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(errorMessage = err.localizedMessage ?: "Failed to save note")
                    }
                }
            )
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(currentUserId, noteId).onFailure { err ->
                _actionState.update {
                    it.copy(errorMessage = err.localizedMessage ?: "Failed to delete note")
                }
            }
        }
    }

    fun summarizeExistingNote(note: StudyNote) {
        if (_actionState.value.summarizingNoteId != null) return
        viewModelScope.launch {
            _actionState.update { it.copy(summarizingNoteId = note.id, errorMessage = null) }
            try {
                val bulletSummary = aiRepository.summarizeNotes(note.title, note.content)
                repository.updateNoteSummary(currentUserId, note.id, bulletSummary).onFailure { err ->
                    throw err
                }
                _actionState.update {
                    it.copy(summarizingNoteId = null, feedbackMessage = "AI summary updated")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Note summarization timed out", e)
                _actionState.update {
                    it.copy(
                        summarizingNoteId = null,
                        errorMessage = "Summarization timed out after 60 seconds."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Note summarization failed", e)
                _actionState.update {
                    it.copy(
                        summarizingNoteId = null,
                        errorMessage = e.localizedMessage ?: "Could not summarize note."
                    )
                }
            }
        }
    }

    fun summarizeDraftText(title: String, content: String, onSummaryReady: (String) -> Unit) {
        if (content.isBlank() || _actionState.value.isSummarizingDraft) return
        viewModelScope.launch {
            _actionState.update { it.copy(isSummarizingDraft = true, errorMessage = null) }
            try {
                val summary = aiRepository.summarizeNotes(title, content)
                _actionState.update { it.copy(isSummarizingDraft = false, draftSummary = summary) }
                onSummaryReady(summary)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Draft summarization timed out", e)
                _actionState.update {
                    it.copy(
                        isSummarizingDraft = false,
                        errorMessage = "Summarization timed out after 60 seconds."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Draft summarization failed", e)
                _actionState.update {
                    it.copy(
                        isSummarizingDraft = false,
                        errorMessage = e.localizedMessage ?: "Failed to summarize text."
                    )
                }
            }
        }
    }

    fun addTask(
        title: String,
        category: TaskCategory,
        dueDateLabel: String,
        priority: TaskPriority,
        onAdded: () -> Unit
    ) {
        viewModelScope.launch {
            repository.addTask(currentUserId, title, category, dueDateLabel, priority).fold(
                onSuccess = {
                    _actionState.update { it.copy(feedbackMessage = "Task added", errorMessage = null) }
                    onAdded()
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(errorMessage = err.localizedMessage ?: "Failed to add task")
                    }
                }
            )
        }
    }

    fun toggleTaskCompleted(task: StudyTask) {
        viewModelScope.launch {
            repository.toggleTaskCompleted(currentUserId, task.id, !task.completed).onFailure { err ->
                _actionState.update {
                    it.copy(errorMessage = err.localizedMessage ?: "Failed to update task")
                }
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(currentUserId, taskId).onFailure { err ->
                _actionState.update {
                    it.copy(errorMessage = err.localizedMessage ?: "Failed to delete task")
                }
            }
        }
    }

    companion object {
        private const val TAG = "WorkspaceViewModel"
        private const val STOP_TIMEOUT_MS = 5000L

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                WorkspaceViewModel(StudySparkRepository(db), AiStudyRepository(), currentUserId)
            }
        }
    }
}
