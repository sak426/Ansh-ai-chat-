package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.PreferencesRepository
import com.example.data.ThemeMode
import com.example.data.UiState
import com.example.firebase.isOwnerAdmin
import com.example.firebase.signOutGoogle
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.ImageGenViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.WorkspaceViewModel
import com.google.firebase.auth.FirebaseUser

private enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    CHAT("Chat", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_chat"),
    IMAGE_GEN("Image Gen", Icons.Rounded.Palette, Icons.Outlined.Palette, "nav_image_gen"),
    WORKSPACE("Workspace", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard, "nav_workspace"),
    PROFILE("Profile", Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile")
}

@Composable
fun StudySparkApp(
    firebaseUser: FirebaseUser,
    preferencesRepository: PreferencesRepository,
    themeMode: ThemeMode
) {
    val uid = firebaseUser.uid
    val isOwnerAdmin = firebaseUser.isOwnerAdmin()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val mainViewModel: MainViewModel = viewModel(
        key = "main_$uid",
        factory = MainViewModel.provideFactory(uid, preferencesRepository)
    )
    val chatViewModel: ChatViewModel = viewModel(
        key = "chat_$uid",
        factory = ChatViewModel.provideFactory(uid)
    )
    val imageGenViewModel: ImageGenViewModel = viewModel(
        key = "img_$uid",
        factory = ImageGenViewModel.provideFactory(uid)
    )
    val workspaceViewModel: WorkspaceViewModel = viewModel(
        key = "ws_$uid",
        factory = WorkspaceViewModel.provideFactory(uid)
    )

    val userProfileState by mainViewModel.userProfileState.collectAsStateWithLifecycle()
    val branding by mainViewModel.branding.collectAsStateWithLifecycle()
    val announcement by mainViewModel.announcement.collectAsStateWithLifecycle()
    val usageLimits by mainViewModel.usageLimits.collectAsStateWithLifecycle()
    val todayKey by mainViewModel.todayKey.collectAsStateWithLifecycle()

    val userProfile = (userProfileState as? UiState.Success)?.data
    val isBlocked = userProfile?.blocked == true && !isOwnerAdmin

    val dailyChatLimit = usageLimits.chatLimitInt()
    val dailyImageLimit = usageLimits.imageLimitInt()
    val remainingQuestions = (dailyChatLimit - (userProfile?.usedChatsToday(todayKey) ?: 0)).coerceAtLeast(0)
    val remainingImages = (dailyImageLimit - (userProfile?.usedImagesToday(todayKey) ?: 0)).coerceAtLeast(0)

    val onSignOut: () -> Unit = {
        signOutGoogle(credentialManager = credentialManager, scope = scope)
    }

    if (isBlocked) {
        BlockedAccountScreen(
            userEmail = firebaseUser.email.orEmpty(),
            onSignOut = onSignOut
        )
        return
    }

    var currentTab by rememberSaveable { mutableStateOf(AppTab.CHAT) }
    var showAdminDashboard by rememberSaveable { mutableStateOf(false) }
    var dismissedAnnouncementId by rememberSaveable { mutableStateOf<String?>(null) }

    val currentAnnouncementKey = announcement?.let { "${it.title}|${it.message}|${it.updatedAt?.seconds}" }
    val announcementDismissed = currentAnnouncementKey != null && dismissedAnnouncementId == currentAnnouncementKey

    if (showAdminDashboard && isOwnerAdmin) {
        val adminViewModel: AdminViewModel = viewModel(
            key = "admin_$uid",
            factory = AdminViewModel.provideFactory(uid)
        )
        AdminDashboardScreen(
            viewModel = adminViewModel,
            adminUid = uid,
            currentBranding = branding,
            currentAnnouncement = announcement,
            currentUsageLimits = usageLimits,
            todayKey = todayKey,
            onBack = { showAdminDashboard = false }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 840.dp

        if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    AppTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                        TabBody(
                            currentTab = currentTab,
                            firebaseUser = firebaseUser,
                            userProfile = userProfile,
                            isOwnerAdmin = isOwnerAdmin,
                            branding = branding,
                            announcement = announcement,
                            announcementDismissed = announcementDismissed,
                            onDismissAnnouncement = { dismissedAnnouncementId = currentAnnouncementKey },
                            remainingQuestions = remainingQuestions,
                            dailyChatLimit = dailyChatLimit,
                            remainingImages = remainingImages,
                            dailyImageLimit = dailyImageLimit,
                            themeMode = themeMode,
                            onThemeModeChange = { mainViewModel.setThemeMode(it) },
                            onOpenAdminDashboard = { showAdminDashboard = true },
                            onSignOut = onSignOut,
                            chatViewModel = chatViewModel,
                            imageGenViewModel = imageGenViewModel,
                            workspaceViewModel = workspaceViewModel
                        )
                    }
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    TabBody(
                        currentTab = currentTab,
                        firebaseUser = firebaseUser,
                        userProfile = userProfile,
                        isOwnerAdmin = isOwnerAdmin,
                        branding = branding,
                        announcement = announcement,
                        announcementDismissed = announcementDismissed,
                        onDismissAnnouncement = { dismissedAnnouncementId = currentAnnouncementKey },
                        remainingQuestions = remainingQuestions,
                        dailyChatLimit = dailyChatLimit,
                        remainingImages = remainingImages,
                        dailyImageLimit = dailyImageLimit,
                        themeMode = themeMode,
                        onThemeModeChange = { mainViewModel.setThemeMode(it) },
                        onOpenAdminDashboard = { showAdminDashboard = true },
                        onSignOut = onSignOut,
                        chatViewModel = chatViewModel,
                        imageGenViewModel = imageGenViewModel,
                        workspaceViewModel = workspaceViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBody(
    currentTab: AppTab,
    firebaseUser: FirebaseUser,
    userProfile: com.example.data.UserProfile?,
    isOwnerAdmin: Boolean,
    branding: com.example.data.AppBranding,
    announcement: com.example.data.Announcement?,
    announcementDismissed: Boolean,
    onDismissAnnouncement: () -> Unit,
    remainingQuestions: Int,
    dailyChatLimit: Int,
    remainingImages: Int,
    dailyImageLimit: Int,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onSignOut: () -> Unit,
    chatViewModel: ChatViewModel,
    imageGenViewModel: ImageGenViewModel,
    workspaceViewModel: WorkspaceViewModel
) {
    when (currentTab) {
        AppTab.CHAT -> ChatScreen(
            viewModel = chatViewModel,
            branding = branding,
            announcement = announcement,
            announcementDismissed = announcementDismissed,
            onDismissAnnouncement = onDismissAnnouncement,
            remainingQuestions = remainingQuestions,
            dailyChatLimit = dailyChatLimit
        )

        AppTab.IMAGE_GEN -> ImageGenScreen(
            viewModel = imageGenViewModel,
            remainingImages = remainingImages,
            dailyImageLimit = dailyImageLimit
        )

        AppTab.WORKSPACE -> WorkspaceScreen(
            viewModel = workspaceViewModel
        )

        AppTab.PROFILE -> ProfileScreen(
            firebaseUser = firebaseUser,
            userProfile = userProfile,
            isOwnerAdmin = isOwnerAdmin,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onOpenAdminDashboard = onOpenAdminDashboard,
            onSignOut = onSignOut
        )
    }
}
