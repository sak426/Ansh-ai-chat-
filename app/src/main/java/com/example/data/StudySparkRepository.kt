package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudySparkRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    // --- USER PROFILE & USAGE STATS ---

    suspend fun syncUserProfileOnSignIn(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            require(uid.isNotBlank()) { "Authenticated user ID is blank" }
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val safeName = (user.displayName?.trim()?.takeIf { it.isNotBlank() } ?: "Student").take(100)
            val safeEmail = (user.email?.trim()?.takeIf { it.length >= 3 } ?: "student@anshaichat.app").take(200)
            val safePhoto = user.photoUrl?.toString()?.take(1000)
            val todayKey = todayUsageDateKey()

            if (!snapshot.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    put("email", safeEmail)
                    if (!safePhoto.isNullOrBlank()) {
                        put("photoUrl", safePhoto)
                    }
                    put("blocked", false)
                    put("chatCount", 0L)
                    put("imageGenCount", 0L)
                    put("noteCount", 0L)
                    put("dailyChatCount", 0L)
                    put("dailyImageCount", 0L)
                    put("usageDate", todayKey)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                    put("lastActiveAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val isBlocked = snapshot.getBoolean("blocked") == true
                if (!isBlocked) {
                    val storedDate = snapshot.getString("usageDate")
                    val updates = buildMap<String, Any> {
                        put("displayName", safeName)
                        put("email", safeEmail)
                        if (!safePhoto.isNullOrBlank()) {
                            put("photoUrl", safePhoto)
                        }
                        if (storedDate != todayKey) {
                            put("dailyChatCount", 0L)
                            put("dailyImageCount", 0L)
                            put("usageDate", todayKey)
                        }
                        put("updatedAt", FieldValue.serverTimestamp())
                        put("lastActiveAt", FieldValue.serverTimestamp())
                    }
                    docRef.update(updates).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    suspend fun incrementUsageCounter(userId: String, counterField: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user usage update." }
            require(counterField in setOf("chatCount", "imageGenCount", "noteCount")) {
                "Unsupported usage field: $counterField"
            }
            db.collection("users").document(userId).update(
                mapOf(
                    counterField to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "lastActiveAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically verifies and consumes 1 daily AI credit (chat question or image generation)
     * in Firestore. Automatically resets daily counters when the local calendar date passes midnight.
     */
    suspend fun consumeDailyUsageCredit(
        userId: String,
        isChat: Boolean,
        dailyLimit: Int
    ): Result<Int> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user quota write." }
            val todayKey = todayUsageDateKey()
            val userRef = db.collection("users").document(userId)

            val newUsedCount = db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Profile is still syncing. Please try again in a moment.")
                }
                val storedDate = snapshot.getString("usageDate").orEmpty()
                val isNewDay = storedDate != todayKey
                val currentDailyChat = if (isNewDay) 0L else (snapshot.getLong("dailyChatCount") ?: 0L)
                val currentDailyImage = if (isNewDay) 0L else (snapshot.getLong("dailyImageCount") ?: 0L)
                val currentTotalChat = snapshot.getLong("chatCount") ?: 0L
                val currentTotalImage = snapshot.getLong("imageGenCount") ?: 0L

                val currentUsed = if (isChat) currentDailyChat.toInt() else currentDailyImage.toInt()
                if (currentUsed >= dailyLimit) {
                    val itemLabel = if (isChat) "study questions" else "AI images"
                    throw DailyLimitReachedException(
                        "You've reached today's limit of $dailyLimit $itemLabel. Please come back tomorrow after midnight!"
                    )
                }

                val nextDailyChat = if (isChat) currentDailyChat + 1L else currentDailyChat
                val nextDailyImage = if (isChat) currentDailyImage else currentDailyImage + 1L
                val updates = mutableMapOf<String, Any>(
                    "dailyChatCount" to nextDailyChat,
                    "dailyImageCount" to nextDailyImage,
                    "usageDate" to todayKey,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "lastActiveAt" to FieldValue.serverTimestamp()
                )
                if (isChat) {
                    updates["chatCount"] = currentTotalChat + 1L
                } else {
                    updates["imageGenCount"] = currentTotalImage + 1L
                }
                transaction.update(userRef, updates)
                if (isChat) nextDailyChat.toInt() else nextDailyImage.toInt()
            }.await()

            Result.success(newUsedCount)
        } catch (e: DailyLimitReachedException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun resetDailyUsageIfNewDay(userId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user daily reset." }
            val todayKey = todayUsageDateKey()
            val userRef = db.collection("users").document(userId)
            val snapshot = userRef.get().await()
            if (snapshot.exists() && snapshot.getBoolean("blocked") != true && snapshot.getString("usageDate") != todayKey) {
                userRef.update(
                    mapOf(
                        "dailyChatCount" to 0L,
                        "dailyImageCount" to 0L,
                        "usageDate" to todayKey,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- CHAT HISTORY ---

    fun observeChatMessages(userId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$userId/chatMessages"
        emitAll(
            db.collection("users").document(userId).collection("chatMessages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun addChatMessage(
        userId: String,
        role: String,
        text: String,
        hasImage: Boolean = false,
        imagePreviewBase64: String? = null
    ): Result<String> {
        var path = "users/$userId/chatMessages"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user chat write." }
            require(role == "user" || role == "model") { "Invalid chat role" }
            val trimmedText = text.trim().take(20000)
            require(trimmedText.isNotEmpty()) { "Message cannot be empty" }

            val docRef = db.collection("users").document(userId)
                .collection("chatMessages").document()
            path = docRef.path

            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("role", role)
                put("text", trimmedText)
                put("hasImage", hasImage)
                val safePreview = imagePreviewBase64?.takeIf { it.isNotBlank() && it.length <= 120000 }
                if (safePreview != null) {
                    put("imagePreviewBase64", safePreview)
                }
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun clearChatHistory(userId: String): Result<Unit> {
        val path = "users/$userId/chatMessages"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user chat clear." }
            val docs = db.collection("users").document(userId)
                .collection("chatMessages").get().await().documents
            docs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- STUDY NOTES ---

    fun observeNotes(userId: String): Flow<List<StudyNote>> = flow {
        val path = "users/$userId/studyNotes"
        emitAll(
            db.collection("users").document(userId).collection("studyNotes")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudyNote::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun addNote(
        userId: String,
        title: String,
        subject: String,
        content: String,
        summary: String = ""
    ): Result<String> {
        var path = "users/$userId/studyNotes"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user note write." }
            val cleanTitle = title.trim().take(150)
            val cleanContent = content.trim().take(25000)
            require(cleanTitle.isNotEmpty()) { "Note title cannot be empty" }
            require(cleanContent.isNotEmpty()) { "Note content cannot be empty" }

            val docRef = db.collection("users").document(userId)
                .collection("studyNotes").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "subject" to subject.trim().take(60),
                "content" to cleanContent,
                "summary" to summary.trim().take(8000),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            incrementUsageCounter(uid, "noteCount")
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateNote(
        userId: String,
        noteId: String,
        title: String,
        subject: String,
        content: String,
        summary: String
    ): Result<Unit> {
        val path = "users/$userId/studyNotes/$noteId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user note update." }
            require(noteId.isNotBlank()) { "Note ID must not be blank" }
            val cleanTitle = title.trim().take(150)
            val cleanContent = content.trim().take(25000)
            require(cleanTitle.isNotEmpty()) { "Note title cannot be empty" }
            require(cleanContent.isNotEmpty()) { "Note content cannot be empty" }

            db.collection("users").document(userId)
                .collection("studyNotes").document(noteId)
                .update(
                    mapOf(
                        "title" to cleanTitle,
                        "subject" to subject.trim().take(60),
                        "content" to cleanContent,
                        "summary" to summary.trim().take(8000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateNoteSummary(userId: String, noteId: String, summary: String): Result<Unit> {
        val path = "users/$userId/studyNotes/$noteId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user summary update." }
            require(noteId.isNotBlank()) { "Note ID must not be blank" }
            db.collection("users").document(userId)
                .collection("studyNotes").document(noteId)
                .update(
                    mapOf(
                        "summary" to summary.trim().take(8000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(userId: String, noteId: String): Result<Unit> {
        val path = "users/$userId/studyNotes/$noteId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user note delete." }
            require(noteId.isNotBlank()) { "Note ID must not be blank" }
            db.collection("users").document(userId)
                .collection("studyNotes").document(noteId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- STUDY PLANNER TASKS ---

    fun observeTasks(userId: String): Flow<List<StudyTask>> = flow {
        val path = "users/$userId/studyTasks"
        emitAll(
            db.collection("users").document(userId).collection("studyTasks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudyTask::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun addTask(
        userId: String,
        title: String,
        category: TaskCategory,
        dueDateLabel: String,
        priority: TaskPriority
    ): Result<String> {
        var path = "users/$userId/studyTasks"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user task write." }
            val cleanTitle = title.trim().take(160)
            require(cleanTitle.isNotEmpty()) { "Task title cannot be empty" }

            val docRef = db.collection("users").document(userId)
                .collection("studyTasks").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "category" to category.name,
                "dueDateLabel" to dueDateLabel.trim().take(40),
                "priority" to priority.name,
                "completed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompleted(userId: String, taskId: String, completed: Boolean): Result<Unit> {
        val path = "users/$userId/studyTasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user task update." }
            require(taskId.isNotBlank()) { "Task ID must not be blank" }
            db.collection("users").document(userId)
                .collection("studyTasks").document(taskId)
                .update(
                    mapOf(
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        val path = "users/$userId/studyTasks/$taskId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user task delete." }
            require(taskId.isNotBlank()) { "Task ID must not be blank" }
            db.collection("users").document(userId)
                .collection("studyTasks").document(taskId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- GLOBAL BRANDING & ANNOUNCEMENTS (SINGLETON GET) ---

    fun observeBranding(userId: String): Flow<AppBranding> = flow {
        val path = "appBranding/current"
        emitAll(
            db.collection("appBranding").document("current")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            AppBranding::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: AppBranding()
                    } else {
                        AppBranding()
                    }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeAnnouncement(userId: String): Flow<Announcement?> = flow {
        val path = "announcements/current"
        emitAll(
            db.collection("announcements").document("current")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            Announcement::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeUsageLimits(userId: String): Flow<UsageLimits> = flow {
        val path = "usageLimits/current"
        emitAll(
            db.collection("usageLimits").document("current")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UsageLimits::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: UsageLimits()
                    } else {
                        UsageLimits()
                    }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    // --- OWNER ADMIN OPERATIONS ---

    fun observeAllUsers(adminUid: String): Flow<List<UserProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection("users")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, adminUid)
                    throw error
                }
        )
    }

    suspend fun setUserBlocked(targetUserId: String, blocked: Boolean): Result<Unit> {
        val path = "users/$targetUserId"
        return try {
            val adminUid = requireUserId()
            require(targetUserId.isNotBlank()) { "Target user ID cannot be blank" }
            require(targetUserId != adminUid) { "Admin cannot block their own account" }
            db.collection("users").document(targetUserId)
                .update(
                    mapOf(
                        "blocked" to blocked,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveBranding(
        appName: String,
        logoBase64: String,
        bannerBase64: String,
        bannerTitle: String,
        bannerSubtitle: String
    ): Result<Unit> {
        val path = "appBranding/current"
        return try {
            val adminUid = requireUserId()
            val cleanAppName = appName.trim().ifBlank { DEFAULT_APP_NAME }.take(60)
            require(logoBase64.length <= 250000) { "Logo image is too large. Choose a smaller image." }
            require(bannerBase64.length <= 450000) { "Banner image is too large. Choose a smaller image." }

            val payload = mapOf(
                "appName" to cleanAppName,
                "logoBase64" to logoBase64,
                "bannerBase64" to bannerBase64,
                "bannerTitle" to bannerTitle.trim().take(100),
                "bannerSubtitle" to bannerSubtitle.trim().take(200),
                "updatedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("appBranding").document("current").set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveUsageLimits(
        dailyChatLimit: Int,
        dailyImageLimit: Int
    ): Result<Unit> {
        val path = "usageLimits/current"
        return try {
            val adminUid = requireUserId()
            require(dailyChatLimit in 1..500) { "Daily question limit must be between 1 and 500." }
            require(dailyImageLimit in 1..100) { "Daily image limit must be between 1 and 100." }

            val payload = mapOf(
                "dailyChatLimit" to dailyChatLimit.toLong(),
                "dailyImageLimit" to dailyImageLimit.toLong(),
                "updatedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("usageLimits").document("current").set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveAnnouncement(
        title: String,
        message: String,
        active: Boolean
    ): Result<Unit> {
        val path = "announcements/current"
        return try {
            val adminUid = requireUserId()
            val cleanTitle = title.trim().ifBlank { "Announcement" }.take(100)
            val cleanMessage = message.trim().take(600)
            require(cleanMessage.isNotEmpty()) { "Announcement message cannot be empty" }

            val payload = mapOf(
                "title" to cleanTitle,
                "message" to cleanMessage,
                "active" to active,
                "updatedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("announcements").document("current").set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
