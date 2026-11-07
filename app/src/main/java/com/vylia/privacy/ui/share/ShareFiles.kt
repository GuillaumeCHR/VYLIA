package com.vylia.privacy.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * Fichiers de partage : uniquement dans `cache/share` (seul dossier exposé par le FileProvider), effacés au bout
 * d'une heure. Images et clips sont dessinés de zéro : aucune métadonnée EXIF ni localisation n'y figure.
 */
object ShareFiles {
    private const val DIR = "share"
    private const val MAX_AGE_MS = 60 * 60 * 1000L
    const val WHATSAPP = "com.whatsapp"

    private fun directory(context: Context): File = File(context.cacheDir, DIR).apply { mkdirs() }

    /** Supprime les fichiers de plus d'une heure (l'app destinataire peut encore lire les récents). */
    private fun clean(context: Context, now: Long = System.currentTimeMillis()) {
        directory(context).listFiles()?.filter { now - it.lastModified() > MAX_AGE_MS }?.forEach { it.delete() }
    }

    fun newFile(context: Context, extension: String): File {
        clean(context)
        return File(directory(context), "${fileName()}.$extension")
    }

    fun writePng(context: Context, bitmap: Bitmap): File =
        newFile(context, "png").also { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

    /** Feuille de partage Android avec le texte d'accompagnement ; [target] force une app (WhatsApp). */
    fun shareIntent(uri: Uri, mime: String, text: String, title: String, target: String? = null): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return if (target != null) send.setPackage(target) else Intent.createChooser(send, title)
    }

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** La galerie sans permission n'existe qu'à partir d'Android 10 (MediaStore). */
    val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Enregistre le fichier dans Images/VYLIA (Android 10+, aucune permission demandée). */
    fun saveToGallery(context: Context, file: File, mime: String): Boolean {
        if (!canSaveToGallery) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VYLIA")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: IOException) {
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun fileName(): String = "vylia-${System.currentTimeMillis()}"
}
