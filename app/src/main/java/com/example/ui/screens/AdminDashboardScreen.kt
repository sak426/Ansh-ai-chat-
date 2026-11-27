package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Announcement
import com.example.data.AppBranding
import com.example.data.DEFAULT_DAILY_CHAT_LIMIT
import com.example.data.DEFAULT_DAILY_IMAGE_LIMIT
import com.example.data.UiState
import com.example.data.UsageLimits
import com.example.data.UserProfile
import com.example.ui.components.AnnouncementBanner
import com.example.ui.components.BrandLogoImage
import com.example.ui.components.PromotionalBannerCard
import com.example.util.ImageUtils
import com.example.viewmodel.AdminViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    adminUid: String,
    currentBranding: AppBranding,
    currentAnnouncement: Announcement?,
    currentUsageLimits: UsageLimits,
    todayKey: String,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val usersState by viewModel.usersState.collectAsStateWithLifecycle()
    val feedbackState by viewModel.feedbackState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Users, 1 = Brand, 2 = Alerts, 3 = Limits

    LaunchedEffect(feedbackState.message, feedbackState.error) {
        val msg = feedbackState.error ?: feedbackState.message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Owner Admin Panel",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Users, Branding, Alerts & Daily Limits",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Profile"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                val tabs = listOf(
                    "Users" to "users",
                    "Brand" to "branding",
                    "Alerts" to "alerts",
                    "Limits" to "limits"
                )
                tabs.forEachIndexed { idx, (label, tagSuffix) ->
                    SegmentedButton(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                        modifier = Modifier.testTag("admin_tab_$tagSuffix")
                    ) {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> AdminUsersTab(
                    usersState = usersState,
                    adminUid = adminUid,
                    currentUsageLimits = currentUsageLimits,
                    todayKey = todayKey,
                    onToggleBlocked = { viewModel.toggleUserBlocked(it) }
                )

                1 -> AdminBrandingTab(
                    currentBranding = currentBranding,
                    isSaving = feedbackState.isSaving,
                    onSaveBranding = { appName, logoB64, bannerB64, bannerTitle, bannerSub ->
                        viewModel.saveBranding(appName, logoB64, bannerB64, bannerTitle, bannerSub)
                    }
                )

                2 -> AdminAnnouncementTab(
                    currentAnnouncement = currentAnnouncement,
                    isSaving = feedbackState.isSaving,
                    onPublish = { title, message, active ->
                        viewModel.saveAnnouncement(title, message, active)
                    }
                )

                3 -> AdminUsageLimitsTab(
                    currentUsageLimits = currentUsageLimits,
                    isSaving = feedbackState.isSaving,
                    onSaveLimits = { chatLimit, imageLimit ->
                        viewModel.saveUsageLimits(chatLimit, imageLimit)
                    }
                )
            }
        }
    }
}

@Composable
private fun AdminUsersTab(
    usersState: UiState<List<UserProfile>>,
    adminUid: String,
    currentUsageLimits: UsageLimits,
    todayKey: String,
    onToggleBlocked: (UserProfile) -> Unit
) {
    when (usersState) {
        is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    text = usersState.message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        is UiState.Success -> {
            val users = usersState.data
            val totalChats = users.sumOf { it.chatCount }
            val totalImages = users.sumOf { it.imageGenCount }
            val totalNotes = users.sumOf { it.noteCount }

            LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "admin_metrics") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AdminMetricCard("Users", users.size.toString(), Modifier.weight(1f))
                        AdminMetricCard("Chats", totalChats.toString(), Modifier.weight(1f))
                        AdminMetricCard("Images", totalImages.toString(), Modifier.weight(1f))
                        AdminMetricCard("Notes", totalNotes.toString(), Modifier.weight(1f))
                    }
                }

                items(users, key = { it.userId }) { user ->
                    val isSelf = user.userId == adminUid
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.displayName.ifBlank { "Student" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isSelf) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Owner",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (user.blocked) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Blocked",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Today: ${user.usedChatsToday(todayKey)}/${currentUsageLimits.chatLimitInt()} Q • ${user.usedImagesToday(todayKey)}/${currentUsageLimits.imageLimitInt()} Img",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Total: ${user.chatCount} chats • ${user.imageGenCount} images • ${user.noteCount} notes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (!isSelf) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Switch(
                                        checked = user.blocked,
                                        onCheckedChange = { onToggleBlocked(user) }
                                    )
                                    Text(
                                        text = if (user.blocked) "Blocked" else "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (user.blocked) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun AdminBrandingTab(
    currentBranding: AppBranding,
    isSaving: Boolean,
    onSaveBranding: (String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var appName by remember(currentBranding) { mutableStateOf(currentBranding.resolvedAppName()) }
    var logoBase64 by remember(currentBranding) { mutableStateOf(currentBranding.logoBase64) }
    var bannerBase64 by remember(currentBranding) { mutableStateOf(currentBranding.bannerBase64) }
    var bannerTitle by remember(currentBranding) { mutableStateOf(currentBranding.bannerTitle) }
    var bannerSubtitle by remember(currentBranding) { mutableStateOf(currentBranding.bannerSubtitle) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = ImageUtils.decodeUriToBitmap(context, uri, maxDimension = 320)
                if (bmp != null) {
                    logoBase64 = withContext(Dispatchers.Default) {
                        ImageUtils.bitmapToBase64(bmp, maxDimension = 256, quality = 80)
                    }
                }
            }
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = ImageUtils.decodeUriToBitmap(context, uri, maxDimension = 960)
                if (bmp != null) {
                    bannerBase64 = withContext(Dispatchers.Default) {
                        ImageUtils.bitmapToBase64(bmp, maxDimension = 840, quality = 76)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Logo section
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "App Identity & Logo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BrandLogoImage(
                        logoBase64 = logoBase64,
                        contentDescription = "Preview app logo",
                        size = 72.dp,
                        cornerRadius = 18.dp
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                logoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_upload_logo_button")
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload App Logo")
                        }
                        if (logoBase64.isNotBlank()) {
                            OutlinedButton(
                                onClick = { logoBase64 = "" },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Default Logo")
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Display Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Promotional Banner section
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Student Home Banner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                PromotionalBannerCard(
                    branding = AppBranding(
                        appName = appName,
                        logoBase64 = logoBase64,
                        bannerBase64 = bannerBase64,
                        bannerTitle = bannerTitle,
                        bannerSubtitle = bannerSubtitle
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            bannerPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_upload_banner_button")
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Banner")
                    }
                    if (bannerBase64.isNotBlank()) {
                        OutlinedButton(onClick = { bannerBase64 = "" }) {
                            Text("Reset")
                        }
                    }
                }

                OutlinedTextField(
                    value = bannerTitle,
                    onValueChange = { bannerTitle = it },
                    label = { Text("Banner Headline") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bannerSubtitle,
                    onValueChange = { bannerSubtitle = it },
                    label = { Text("Banner Subtitle") },
                    maxLines = 2,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = {
                onSaveBranding(appName, logoBase64, bannerBase64, bannerTitle, bannerSubtitle)
            },
            enabled = !isSaving && appName.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("admin_save_branding_button")
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Save & Push Branding Live", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AdminAnnouncementTab(
    currentAnnouncement: Announcement?,
    isSaving: Boolean,
    onPublish: (String, String, Boolean) -> Unit
) {
    var title by remember(currentAnnouncement) {
        mutableStateOf(currentAnnouncement?.title.orEmpty().ifBlank { "Study Notice" })
    }
    var message by remember(currentAnnouncement) {
        mutableStateOf(currentAnnouncement?.message.orEmpty())
    }
    var active by remember(currentAnnouncement) {
        mutableStateOf(currentAnnouncement?.active ?: true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Student Announcement Banner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = active,
                        onCheckedChange = { active = it }
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Announcement Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_announcement_title_input")
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Announcement Message") },
                    placeholder = { Text("e.g., Midterm revision sessions start Monday! Try the AI Notes Summarizer.") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_announcement_message_input")
                )
            }
        }

        if (message.isNotBlank() && active) {
            Text(
                text = "Live Student Preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnnouncementBanner(
                announcement = Announcement(title = title, message = message, active = true),
                dismissed = false,
                onDismiss = {}
            )
        }

        Button(
            onClick = { onPublish(title, message, active) },
            enabled = !isSaving && message.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("admin_publish_announcement_button")
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (active) "Publish Announcement" else "Save as Hidden",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AdminUsageLimitsTab(
    currentUsageLimits: UsageLimits,
    isSaving: Boolean,
    onSaveLimits: (Int, Int) -> Unit
) {
    var chatLimitText by remember(currentUsageLimits) {
        mutableStateOf(currentUsageLimits.chatLimitInt().toString())
    }
    var imageLimitText by remember(currentUsageLimits) {
        mutableStateOf(currentUsageLimits.imageLimitInt().toString())
    }

    val parsedChatLimit = chatLimitText.toIntOrNull()
    val parsedImageLimit = imageLimitText.toIntOrNull()
    val isChatValid = parsedChatLimit != null && parsedChatLimit in 1..500
    val isImageValid = parsedImageLimit != null && parsedImageLimit in 1..100

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Student Daily Usage Limits",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Applies live to all students and resets every day at midnight.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Daily Questions Limit stepper + input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "AI Study Questions / Day (1–500)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val next = ((parsedChatLimit ?: DEFAULT_DAILY_CHAT_LIMIT) - 1).coerceAtLeast(1)
                                chatLimitText = next.toString()
                            },
                            modifier = Modifier.size(48.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Decrease daily question limit"
                            )
                        }

                        OutlinedTextField(
                            value = chatLimitText,
                            onValueChange = { raw ->
                                chatLimitText = raw.filter { it.isDigit() }.take(3)
                            },
                            singleLine = true,
                            isError = !isChatValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Questions per day") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_chat_limit_input")
                        )

                        FilledTonalButton(
                            onClick = {
                                val next = ((parsedChatLimit ?: DEFAULT_DAILY_CHAT_LIMIT) + 1).coerceAtMost(500)
                                chatLimitText = next.toString()
                            },
                            modifier = Modifier.size(48.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Increase daily question limit"
                            )
                        }
                    }
                    if (!isChatValid) {
                        Text(
                            text = "Enter a number between 1 and 500.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Daily Image Limit stepper + input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "AI Image Generations / Day (1–100)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val next = ((parsedImageLimit ?: DEFAULT_DAILY_IMAGE_LIMIT) - 1).coerceAtLeast(1)
                                imageLimitText = next.toString()
                            },
                            modifier = Modifier.size(48.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Decrease daily image limit"
                            )
                        }

                        OutlinedTextField(
                            value = imageLimitText,
                            onValueChange = { raw ->
                                imageLimitText = raw.filter { it.isDigit() }.take(3)
                            },
                            singleLine = true,
                            isError = !isImageValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Images per day") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_image_limit_input")
                        )

                        FilledTonalButton(
                            onClick = {
                                val next = ((parsedImageLimit ?: DEFAULT_DAILY_IMAGE_LIMIT) + 1).coerceAtMost(100)
                                imageLimitText = next.toString()
                            },
                            modifier = Modifier.size(48.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Increase daily image limit"
                            )
                        }
                    }
                    if (!isImageValid) {
                        Text(
                            text = "Enter a number between 1 and 100.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        chatLimitText = DEFAULT_DAILY_CHAT_LIMIT.toString()
                        imageLimitText = DEFAULT_DAILY_IMAGE_LIMIT.toString()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_reset_limits_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset to Defaults ($DEFAULT_DAILY_CHAT_LIMIT questions / $DEFAULT_DAILY_IMAGE_LIMIT images)")
                }
            }
        }

        Button(
            onClick = {
                if (parsedChatLimit != null && parsedImageLimit != null) {
                    onSaveLimits(parsedChatLimit, parsedImageLimit)
                }
            },
            enabled = !isSaving && isChatValid && isImageValid,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("admin_save_limits_button")
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Save Daily Limits", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
