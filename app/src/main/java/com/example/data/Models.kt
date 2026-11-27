package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DEFAULT_APP_NAME = "Ansh AI Chat"
const val DEFAULT_DAILY_CHAT_LIMIT = 20
const val DEFAULT_DAILY_IMAGE_LIMIT = 4

fun todayUsageDateKey(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

class DailyLimitReachedException(message: String) : IllegalStateException(message)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class TaskCategory(val label: String) {
    HOMEWORK("Homework"),
    EXAM("Exam"),
    DAILY("Daily");

    companion object {
        fun fromRaw(raw: String?): TaskCategory =
            entries.firstOrNull { it.name == raw } ?: DAILY
    }
}

enum class TaskPriority(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val blocked: Boolean = false,
    val chatCount: Long = 0L,
    val imageGenCount: Long = 0L,
    val noteCount: Long = 0L,
    val dailyChatCount: Long = 0L,
    val dailyImageCount: Long = 0L,
    val usageDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @ServerTimestamp val lastActiveAt: Timestamp? = null
) {
    fun usedChatsToday(todayKey: String = todayUsageDateKey()): Int =
        if (usageDate == todayKey) dailyChatCount.toInt().coerceAtLeast(0) else 0

    fun usedImagesToday(todayKey: String = todayUsageDateKey()): Int =
        if (usageDate == todayKey) dailyImageCount.toInt().coerceAtLeast(0) else 0
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val text: String = "",
    val hasImage: Boolean = false,
    val imagePreviewBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudyNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "",
    val content: String = "",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudyTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = TaskCategory.HOMEWORK.name,
    val dueDateLabel: String = "",
    val priority: String = TaskPriority.MEDIUM.name,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppBranding(
    @DocumentId val id: String = "",
    val appName: String = DEFAULT_APP_NAME,
    val logoBase64: String = "",
    val bannerBase64: String = "",
    val bannerTitle: String = "Snap. Ask. Master.",
    val bannerSubtitle: String = "Upload any homework question for step-by-step Gemini tutoring.",
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedAppName(): String =
        if (appName.isBlank() || appName.equals("StudySpark", ignoreCase = true)) {
            DEFAULT_APP_NAME
        } else {
            appName
        }
}

@Keep
@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val active: Boolean = false,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UsageLimits(
    @DocumentId val id: String = "",
    val dailyChatLimit: Long = DEFAULT_DAILY_CHAT_LIMIT.toLong(),
    val dailyImageLimit: Long = DEFAULT_DAILY_IMAGE_LIMIT.toLong(),
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun chatLimitInt(): Int = dailyChatLimit.toInt().coerceIn(1, 500)
    fun imageLimitInt(): Int = dailyImageLimit.toInt().coerceIn(1, 100)
}
