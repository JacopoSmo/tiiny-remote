package app.tiinyremote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/** A picture picked from the phone, scaled down and encoded for the Tiiny. */
class PickedImage(val bytes: ByteArray, val mime: String, val preview: Bitmap) {
    val dataUri get() = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

    /** Already on a transparent background (over 5% see-through), so no cut-out is needed. */
    val transparent: Boolean by lazy {
        if (!preview.hasAlpha()) return@lazy false
        val k = minOf(1.0, 256.0 / maxOf(preview.width, preview.height))
        val small = Bitmap.createScaledBitmap(preview, maxOf(1, (preview.width * k).toInt()), maxOf(1, (preview.height * k).toInt()), false)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        px.count { (it ushr 24) < 16 } > px.size / 20
    }
}

/** An audio clip picked or recorded on the phone. */
class PickedAudio(val bytes: ByteArray, val mime: String, val name: String, val seconds: Double? = null)

object Inputs {
    /** Loads a picture, turned upright and at most maxSide pixels on its long side. PNG if it has transparency, else JPEG. */
    fun image(ctx: Context, uri: Uri, maxSide: Int = 1536): PickedImage {
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1.0, maxSide.toDouble() / maxOf(w, h))
                decoder.setTargetSize(maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw MediaException("Couldn't read that picture.")
            val scale = minOf(1.0, maxSide.toDouble() / maxOf(b.width, b.height))
            if (scale < 1.0) Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true) else b
        }
        val out = ByteArrayOutputStream()
        val png = bmp.hasAlpha()
        bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, out)
        return PickedImage(out.toByteArray(), if (png) "image/png" else "image/jpeg", bmp)
    }

    fun audio(ctx: Context, uri: Uri): PickedAudio {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw MediaException("Couldn't read that audio file.")
        var name = "audio"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val mime = ctx.contentResolver.getType(uri) ?: "audio/mpeg"
        val secs = try {
            val r = android.media.MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it / 1000.0 }
            } finally {
                r.release()
            }
        } catch (e: Exception) {
            null
        }
        return PickedAudio(bytes, mime, name, secs)
    }

    /** A picture from the app's own results, to build on (e.g. a 3D model from a generated image). */
    fun fromFile(file: File): PickedImage? {
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            return null
        }
        val preview = thumbnail(file, 1536) ?: return null
        val mime = if (file.name.endsWith(".png")) "image/png" else "image/jpeg"
        return PickedImage(bytes, mime, preview)
    }

    /** A small preview of a result picture, without loading the full size into memory. */
    fun thumbnail(file: File, maxSide: Int = 1024): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
