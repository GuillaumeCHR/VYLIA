package com.vylia.privacy.data.trackers

import android.content.Context
import com.vylia.privacy.domain.model.TrackerSignature
import com.vylia.privacy.domain.repository.TrackerSignatureRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Charge la base Exodus en privilégiant la version téléchargée, puis la version assets/. */
class AssetTrackerSignatureRepository(
    private val context: Context,
    private val parser: ExodusTrackerParser = ExodusTrackerParser(),
    private val fileName: String = EXODUS_TRACKERS_ASSET,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TrackerSignatureRepository {

    private val mutex = Mutex()
    private var cached: List<TrackerSignature>? = null

    override suspend fun getSignatures(): List<TrackerSignature> = mutex.withLock {
        cached ?: withContext(ioDispatcher) {
            val downloadedFile = File(context.filesDir, TrackerUpdater.DOWNLOADED_FILE_NAME)
            val jsonContent = if (downloadedFile.exists()) {
                downloadedFile.readText()
            } else {
                context.assets.open(fileName).bufferedReader().use { it.readText() }
            }
            parser.parse(jsonContent)
        }.also { cached = it }
    }

    fun invalidateCache() {
        cached = null
    }

    companion object {
        const val EXODUS_TRACKERS_ASSET = "trackers.json"
    }
}
