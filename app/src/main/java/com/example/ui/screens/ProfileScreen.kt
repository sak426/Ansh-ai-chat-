package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.ThemeMode
import com.example.data.UserProfile
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    firebaseUser: FirebaseUser,
    userProfile: UserProfile?,
    isOwnerAdmin: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Identity card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(18.dp)
                ) {
                    val photoUrl = firebaseUser.photoUrl?.toString()
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profile avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = "Profile avatar",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile?.displayName?.ifBlank { null }
                                ?: firebaseUser.displayName?.ifBlank { null }
                                ?: "Student",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = firebaseUser.email.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isOwnerAdmin) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Owner Admin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Personal study stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileStatTile(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "AI Chats",
                    value = (userProfile?.chatCount ?: 0L).toString(),
                    modifier = Modifier.weight(1f)
                )
                ProfileStatTile(
                    icon = Icons.Rounded.Palette,
                    label = "Images",
                    value = (userProfile?.imageGenCount ?: 0L).toString(),
                    modifier = Modifier.weight(1f)
                )
                ProfileStatTile(
                    icon = Icons.Rounded.Description,
                    label = "Notes",
                    value = (userProfile?.noteCount ?: 0L).toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            // Appearance (Light / Dark / System)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size
                                ),
                                modifier = Modifier.testTag("theme_${mode.name.lowercase()}")
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }

            // Owner-only Admin Panel entry (strictly hidden for non-owner accounts)
            if (isOwnerAdmin) {
                Surface(
                    onClick = onOpenAdminDashboard,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_admin_dashboard_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = "Admin dashboard",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Owner Admin Dashboard",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Manage users, upload logo & banner, post announcements",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onSignOut,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ProfileStatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
