package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ImageAspectOption
import com.example.ui.components.DailyCreditsPill
import com.example.ui.components.DailyLimitReachedCard
import com.example.viewmodel.ImageGenUiState
import com.example.viewmodel.ImageGenViewModel

private data class StylePreset(val label: String, val suffix: String)

private val EDUCATIONAL_STYLES = listOf(
    StylePreset("Science Diagram", "Clean labeled-style textbook scientific diagram, crisp vector lines, high contrast"),
    StylePreset("Illustrated Art", "Editorial educational illustration, rich harmonious colors, studio quality"),
    StylePreset("3D Model", "Clean isometric 3D educational render on soft neutral studio backdrop"),
    StylePreset("Minimal Sketch", "Clean minimalist academic blueprint drawing with clear geometry")
)

private val SAMPLE_IMAGE_PROMPTS = listOf(
    "Cross-section of a plant cell showing nucleus, chloroplasts, and cell wall",
    "The solar system planets orbiting the sun in an educational poster style",
    "Water cycle showing evaporation, condensation, and precipitation over mountains",
    "Structure of a DNA double helix in vivid academic illustration style"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenScreen(
    viewModel: ImageGenViewModel,
    remainingImages: Int,
    dailyImageLimit: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedAspect by viewModel.selectedAspect.collectAsStateWithLifecycle()
    val saveFeedback by viewModel.saveFeedback.collectAsStateWithLifecycle()

    var promptText by remember { mutableStateOf("") }
    var selectedStyleIndex by remember { mutableStateOf(0) }
    val isLimitReached = remainingImages <= 0

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "AI Image Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Create educational diagrams & project visuals",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                DailyCreditsPill(
                    remaining = remainingImages,
                    limit = dailyImageLimit,
                    unitLabel = "left",
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .testTag("image_daily_credits_pill")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isLimitReached) {
                DailyLimitReachedCard(
                    title = "Daily Image Limit Reached",
                    message = "You've used all $dailyImageLimit AI image generations today. Come back tomorrow after midnight when your fresh credits reset!",
                    modifier = Modifier.testTag("image_limit_reached_card")
                )
            }

            // Prompt composer card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("Describe your diagram or illustration") },
                        placeholder = { Text("e.g., Diagram of human heart chambers and blood flow") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("image_prompt_input")
                    )

                    Text(
                        text = "Visual Style",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EDUCATIONAL_STYLES.forEachIndexed { idx, preset ->
                            FilterChip(
                                selected = selectedStyleIndex == idx,
                                onClick = { selectedStyleIndex = idx },
                                label = { Text(preset.label) }
                            )
                        }
                    }

                    Text(
                        text = "Aspect Ratio",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageAspectOption.entries.forEach { option ->
                            FilterChip(
                                selected = selectedAspect == option,
                                onClick = { viewModel.selectAspect(option) },
                                label = { Text(option.label) }
                            )
                        }
                    }

                    Text(
                        text = if (isLimitReached) {
                            "0 of $dailyImageLimit image credits left today • Resets at midnight"
                        } else {
                            "$remainingImages of $dailyImageLimit image credits remaining today • Resets at midnight"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLimitReached) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Button(
                        onClick = {
                            viewModel.generateImage(
                                prompt = promptText,
                                styleSuffix = EDUCATIONAL_STYLES[selectedStyleIndex].suffix,
                                dailyImageLimit = dailyImageLimit
                            )
                        },
                        enabled = !isLimitReached && promptText.isNotBlank() && uiState !is ImageGenUiState.Loading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("generate_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                uiState is ImageGenUiState.Loading -> "Synthesizing..."
                                isLimitReached -> "Daily Limit Reached"
                                else -> "Generate Image"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Output stage
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (val state = uiState) {
                        is ImageGenUiState.Idle -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Ready to illustrate your ideas",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Try one of these study prompts:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SAMPLE_IMAGE_PROMPTS.forEach { sample ->
                                        OutlinedButton(
                                            onClick = { promptText = sample },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = sample,
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = TextAlign.Start,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is ImageGenUiState.Loading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(20.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(44.dp),
                                    strokeWidth = 3.5.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Creating your educational visual...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "High-resolution generation typically takes 20–60 seconds.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is ImageGenUiState.Error -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = "Generation error",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Image Generation Failed",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is ImageGenUiState.Success -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val aspectRatio = state.bitmap.width.toFloat() / state.bitmap.height.coerceAtLeast(1).toFloat()
                                Image(
                                    bitmap = state.bitmap.asImageBitmap(),
                                    contentDescription = "Generated educational image: ${state.prompt}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(aspectRatio.coerceIn(0.55f, 1.85f))
                                        .clip(RoundedCornerShape(16.dp))
                                )

                                Text(
                                    text = state.prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = { viewModel.saveCurrentImageToGallery(context) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("save_gallery_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save to Gallery")
                                }

                                if (saveFeedback != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = saveFeedback.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
