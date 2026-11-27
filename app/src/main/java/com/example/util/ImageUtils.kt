package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {

    fun createTempCameraUri(context: Context): Uri {
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val photoFile = File(cameraDir, "homework_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            photoFile
        )
    }

    suspend fun decodeUriToBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return@withContext null

            var inSampleSize = 1
            val longest = max(srcWidth, srcHeight)
            while (longest / inSampleSize > maxDimension * 2) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            scaleBitmapDown(rawBitmap, maxDimension)
        } catch (_: Exception) {
            null
        }
    }

    fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longest = max(width, height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest.toFloat()
        val targetW = (width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    fun bitmapToBase64(
        bitmap: Bitmap,
        maxDimension: Int,
        quality: Int = 80
    ): String {
        val scaled = scaleBitmapDown(bitmap, maxDimension)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun base64ToBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        displayNamePrefix: String = "AnshAIChat"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val filename = "${displayNamePrefix}_${System.currentTimeMillis()}.png"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/AnshAIChat"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val imageUri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IOException("Could not create MediaStore entry for image.")

            resolver.openOutputStream(imageUri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                    throw IOException("Failed to encode image bitmap.")
                }
            } ?: throw IOException("Could not open gallery output stream.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            Result.success(filename)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
