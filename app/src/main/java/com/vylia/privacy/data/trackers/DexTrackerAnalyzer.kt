package com.vylia.privacy.data.trackers

import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.repository.TrackerAnalyzer
import com.vylia.privacy.domain.repository.TrackerSignatureRepository
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Méthode Exodus, exécutée localement : on liste les classes de chaque `classes*.dex` des APK
 * de l'app (base + splits), puis on y cherche les signatures de code des traqueurs.
 *
 * Mémoire : chaque DEX est copié en flux dans un fichier temporaire puis mappé en lecture seule,
 * et les noms de classes sont passés au [TrackerMatcher] au fil de l'eau, sans être accumulés.
 */
class DexTrackerAnalyzer(
    private val signatureRepository: TrackerSignatureRepository,
    private val cache: TrackerScanCache,
    private val tempDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TrackerAnalyzer {

    override suspend fun isAvailable(): Boolean = signatureRepository.getSignatures().isNotEmpty()

    override suspend fun findTrackers(app: InstalledApp): List<Tracker> {
        val matcher = signatureRepository.getMatcher()
        cache.get(app, matcher.version)?.let { return matcher.trackersById(it) }

        val trackers = withContext(ioDispatcher) { scan(app.apkPaths, matcher) }
        cache.put(app, matcher.version, trackers.map { it.id })
        return trackers
    }

    private suspend fun scan(apkPaths: List<String>, matcher: TrackerMatcher): List<Tracker> {
        val scan = matcher.newScan()
        for (path in apkPaths) {
            ZipFile(path).use { zip ->
                for (entry in zip.entries()) {
                    if (!DEX_ENTRY.matches(entry.name)) continue
                    currentCoroutineContext().ensureActive()
                    val tmp = File.createTempFile("dex", ".tmp", tempDir)
                    try {
                        zip.getInputStream(entry).use { input -> tmp.outputStream().use { input.copyTo(it, COPY_BUFFER) } }
                        RandomAccessFile(tmp, "r").use { file ->
                            val dex = file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
                            DexClassNameReader.forEachClassName(dex, scan::accept)
                        }
                    } finally {
                        tmp.delete()
                    }
                }
            }
        }
        return scan.result()
    }

    private companion object {
        val DEX_ENTRY = Regex("classes\\d*\\.dex")
        const val COPY_BUFFER = 64 * 1024
    }
}
