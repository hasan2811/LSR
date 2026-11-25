package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object EvidencePhotoHelper {

    private const val TAG = "EvidencePhotoHelper"
    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 78

    /**
     * Compresses an image URI to <= 1280px longest edge JPEG inside app files/evidence/
     * to keep local storage and cellular uploads lightweight (~100-200 KB).
     */
    suspend fun compressAndSaveEvidence(
        context: Context,
        sourceUri: Uri,
        findingId: Int
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val evidenceDir = File(context.filesDir, "evidence").apply { mkdirs() }
            val outFile = File(evidenceDir, "finding_${findingId}_${System.currentTimeMillis()}.jpg")

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val srcWidth = boundsOptions.outWidth.coerceAtLeast(1)
            val srcHeight = boundsOptions.outHeight.coerceAtLeast(1)
            var inSampleSize = 1
            while (srcWidth / inSampleSize > MAX_DIMENSION * 2 || srcHeight / inSampleSize > MAX_DIMENSION * 2) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val decoded = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext Result.failure(IllegalStateException("Unable to decode selected image"))

            val scaled = scaleBitmapIfNeeded(decoded, MAX_DIMENSION)
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
            }
            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()

            Log.d(TAG, "Compressed evidence photo saved: ${outFile.name} (${outFile.length() / 1024} KB)")
            Result.success(outFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress evidence photo: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun uploadEvidenceToFirebaseStorage(
        localFilePath: String,
        projectId: String,
        findingId: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(localFilePath)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalStateException("Local photo file missing"))
            }
            val safeProj = projectId.ifBlank { "default_proj" }
            val ref = FirebaseStorage.getInstance()
                .reference
                .child("evidence/$safeProj/finding_${findingId}/${file.name}")

            ref.putFile(Uri.fromFile(file)).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Log.d(TAG, "Uploaded evidence to Firebase Storage: $downloadUrl")
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Storage upload skipped/failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return bitmap

        val ratio = maxEdge.toFloat() / longest.toFloat()
        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
