package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Announcement
import com.example.data.AppBranding
import com.example.data.ChatMessage
import com.example.data.UiState
import com.example.ui.components.AnnouncementBanner
import com.example.ui.components.BrandLogoImage
import com.example.ui.components.DailyCreditsPill
import com.example.ui.components.DailyLimitReachedCard
import com.example.ui.components.PromotionalBannerCard
import com.example.util.ImageUtils
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    branding: AppBranding,
    announcement: Announcement?,
    announcementDismissed: Boolean,
    onDismissAnnouncement: () -> Unit,
    remainingQuestions: Int,
    dailyChatLimit: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val composerState by viewModel.composerState.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var cameraPermissionMessage by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = ImageUtils.decodeUriToBitmap(context, uri, maxDimension = 1280)
                if (bitmap != null) {
                    viewModel.attachHomeworkPhoto(bitmap)
                }
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch {
                val bitmap = ImageUtils.decodeUriToBitmap(context, uri, maxDimension = 1280)
                if (bitmap != null) {
                    viewModel.attachHomeworkPhoto(bitmap)
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = ImageUtils.createTempCameraUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionMessage = "Camera permission is needed to snap homework photos. You can still upload from your gallery."
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size, composerState.streamingReply.length) {
        val totalItems = messages.size + (if (composerState.isGenerating) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems + 1)
        }
    }

    val displayAppName = branding.resolvedAppName()
    val isLimitReached = remainingQuestions <= 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandLogoImage(
                        logoBase64 = branding.logoBase64,
                        contentDescription = "$displayAppName logo",
                        size = 36.dp,
                        cornerRadius = 10.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = displayAppName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI Study Tutor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                DailyCreditsPill(
                    remaining = remainingQuestions,
                    limit = dailyChatLimit,
                    unitLabel = "left",
                    modifier = Modifier
                        .padding(end = if (messages.isNotEmpty()) 2.dp else 12.dp)
                        .testTag("chat_daily_credits_pill")
                )
                if (messages.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear chat history"
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "announcement_header") {
                AnnouncementBanner(
                    announcement = announcement,
                    dismissed = announcementDismissed,
                    onDismiss = onDismissAnnouncement
                )
            }

            item(key = "promo_banner") {
                PromotionalBannerCard(branding = branding)
            }

            when (val state = messagesState) {
                is UiState.Loading -> {
                    item(key = "chat_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "chat_error") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (state.data.isEmpty() && !composerState.isGenerating) {
                        item(key = "chat_empty_prompts") {
                            EmptyStudyStarterCard(
                                enabled = !isLimitReached,
                                onPromptPicked = { starter ->
                                    viewModel.sendQuestion(starter, dailyChatLimit)
                                }
                            )
                        }
                    } else {
                        items(state.data, key = { it.id }) { msg ->
                            ChatMessageBubble(message = msg)
                        }
                    }
                }
            }

            if (composerState.isGenerating) {
                item(key = "streaming_reply") {
                    StreamingAiBubble(text = composerState.streamingReply)
                }
            }
        }

        // Inline errors
        val bannerError = composerState.errorMessage ?: cameraPermissionMessage
        if (bannerError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bannerError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            viewModel.dismissError()
                            cameraPermissionMessage = null
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (isLimitReached) {
            DailyLimitReachedCard(
                title = "Daily Question Limit Reached",
                message = "You've used all $dailyChatLimit AI study questions today. Great effort! Come back tomorrow after midnight when your daily credits reset.",
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("chat_limit_reached_card")
            )
        }

        // Composer dock
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (composerState.pendingBitmap != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = composerState.pendingBitmap!!.asImageBitmap(),
                            contentDescription = "Attached homework photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Homework photo ready",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Ask a question or tap Send to solve step-by-step",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.clearAttachedPhoto() }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove attached photo"
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            cameraPermissionMessage = null
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                val uri = ImageUtils.createTempCameraUri(context)
                                pendingCameraUri = uri
                                takePictureLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = !composerState.isGenerating && !isLimitReached,
                        modifier = Modifier.testTag("chat_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "Take photo of homework",
                            tint = if (isLimitReached) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !composerState.isGenerating && !isLimitReached,
                        modifier = Modifier.testTag("chat_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = "Upload textbook photo from gallery",
                            tint = if (isLimitReached) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        enabled = !isLimitReached,
                        placeholder = {
                            Text(
                                if (isLimitReached) {
                                    "Come back tomorrow after midnight..."
                                } else {
                                    "Ask any study question..."
                                }
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val canSend = !isLimitReached &&
                        !composerState.isGenerating &&
                        (inputText.isNotBlank() || composerState.pendingBitmap != null)

                    FilledIconButton(
                        onClick = {
                            val toSend = inputText
                            inputText = ""
                            viewModel.sendQuestion(toSend, dailyChatLimit)
                        },
                        enabled = canSend,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_send_button")
                    ) {
                        if (composerState.isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send study question"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear chat history?") },
            text = { Text("This deletes all saved study conversations from your account.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearHistory()
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyStudyStarterCard(
    enabled: Boolean,
    onPromptPicked: (String) -> Unit
) {
    val quickPrompts = listOf(
        "Explain photosynthesis in 4 simple steps",
        "Solve 2x² - 5x - 3 = 0 step-by-step",
        "Summarize causes of World War I",
        "How does Newton's 2nd law work?"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start a study session",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Type any question, tap a starter below, or snap a photo of your textbook.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEach { prompt ->
                    SuggestionChip(
                        onClick = { onPromptPicked(prompt) },
                        enabled = enabled,
                        label = { Text(prompt, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val previewBitmap = remember(message.imagePreviewBase64) {
        ImageUtils.base64ToBitmap(message.imagePreviewBase64)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, top = 2.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "AI Tutor",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 18.dp else 6.dp,
                topEnd = if (isUser) 6.dp else 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            tonalElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Uploaded homework image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun StreamingAiBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp, top = 2.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
        Surface(
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Text(
                text = text.ifBlank { "Thinking step-by-step..." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}
