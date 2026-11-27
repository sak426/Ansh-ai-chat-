package com.example.data

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

enum class ImageAspectOption(val label: String) {
    SQUARE("1:1 Square"),
    LANDSCAPE("16:9 Wide"),
    PORTRAIT("9:16 Tall")
}

class AiStudyRepository {

    private val studyTutorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are Ansh AI Chat, a patient, encouraging, and crystal-clear AI tutor for students. " +
                        "Always explain answers step-by-step using simple language, clear numbered steps, " +
                        "key formulas or concepts, and a quick final takeaway. " +
                        "When given a photo of a textbook problem or homework, first transcribe the core question " +
                        "briefly and then walk through the exact solution step by step."
                )
            }
        )
    }

    private val summarizerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are an expert academic note summarizer. Convert student notes into crisp, " +
                        "memorable bullet points starting with '• '. Highlight key definitions, formulas, " +
                        "dates, and exam-ready takeaways. Keep it concise and easy to scan."
                )
            }
        )
    }

    private val squareImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val landscapeImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(AspectRatio.LANDSCAPE_16x9)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        )
    }

    private val portraitImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(AspectRatio.PORTRAIT_9x16)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        )
    }

    fun streamStudyAnswer(
        recentHistory: List<ChatMessage>,
        userPrompt: String,
        homeworkBitmap: Bitmap?
    ): Flow<String> = flow {
        val historyContent = recentHistory.takeLast(8).map { msg ->
            content(role = if (msg.role == "model") "model" else "user") {
                text(msg.text)
            }
        }
        val chat = studyTutorModel.startChat(history = historyContent)
        val inputContent = content("user") {
            if (homeworkBitmap != null) {
                image(homeworkBitmap)
            }
            val effectivePrompt = userPrompt.ifBlank {
                "Please read this homework or textbook question from the photo and solve it step-by-step."
            }
            text(effectivePrompt)
        }
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(inputContent).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
    }

    suspend fun summarizeNotes(title: String, noteContent: String): String {
        val prompt = buildString {
            if (title.isNotBlank()) {
                append("Topic: ").append(title.trim()).append("\n\n")
            }
            append("Summarize the following study notes into clear bullet points:\n\n")
            append(noteContent.trim())
        }
        val response = withTimeout(60_000L) {
            summarizerModel.generateContent(prompt)
        }
        return response.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("Gemini returned an empty summary.")
    }

    suspend fun generateEducationalImage(
        prompt: String,
        aspectOption: ImageAspectOption
    ): Bitmap? {
        val model = when (aspectOption) {
            ImageAspectOption.SQUARE -> squareImageModel
            ImageAspectOption.LANDSCAPE -> landscapeImageModel
            ImageAspectOption.PORTRAIT -> portraitImageModel
        }
        val response = withTimeout(60_000L) {
            model.generateContent(prompt)
        }
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
        return imagePart?.image
    }
}
