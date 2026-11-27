package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Announcement
import com.example.data.AppBranding
import com.example.util.ImageUtils

@Composable
fun BrandLogoImage(
    logoBase64: String,
    contentDescription: String,
    size: Dp = 40.dp,
    cornerRadius: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    val customBitmap = remember(logoBase64) {
        ImageUtils.base64ToBitmap(logoBase64)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(cornerRadius)
            )
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_studyspark_logo),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PromotionalBannerCard(
    branding: AppBranding,
    modifier: Modifier = Modifier
) {
    val customBanner = remember(branding.bannerBase64) {
        ImageUtils.base64ToBitmap(branding.bannerBase64)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .testTag("promotional_banner_card"),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (customBanner != null) {
                Image(
                    bitmap = customBanner.asImageBitmap(),
                    contentDescription = branding.bannerTitle.ifBlank { "Promotional banner" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.img_default_banner),
                    contentDescription = "Ansh AI Chat banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Editorial scrim so text stays sharp over any admin-uploaded image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = branding.bannerTitle.ifBlank { "Snap. Ask. Master." },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (branding.bannerSubtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = branding.bannerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun AnnouncementBanner(
    announcement: Announcement?,
    dismissed: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = announcement != null && announcement.active && announcement.message.isNotBlank() && !dismissed
    AnimatedVisibility(visible = visible, modifier = modifier) {
        if (announcement != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("announcement_banner"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Campaign,
                            contentDescription = "Announcement",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = announcement.title.ifBlank { "Announcement" },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = announcement.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dismiss_announcement_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss announcement",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyCreditsPill(
    remaining: Int,
    limit: Int,
    unitLabel: String,
    modifier: Modifier = Modifier
) {
    val isExhausted = remaining <= 0
    val isLow = !isExhausted && remaining <= (limit / 5).coerceAtLeast(1)

    val containerColor = when {
        isExhausted -> MaterialTheme.colorScheme.errorContainer
        isLow -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when {
        isExhausted -> MaterialTheme.colorScheme.onErrorContainer
        isLow -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (isExhausted) Icons.Rounded.Schedule else Icons.Rounded.Bolt,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$remaining/$limit $unitLabel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DailyLimitReachedCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = "Daily limit reached",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
