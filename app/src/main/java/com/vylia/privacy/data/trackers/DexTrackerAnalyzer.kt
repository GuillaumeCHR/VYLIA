package com.vylia.privacy.data.trackers

import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.repository.TrackerAnalyzer
import com.vylia.privacy.domain.repository.TrackerSignatureRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Méthode Exodus, exécutée localement : on liste les classes de chaque `classes*.dex` des APK
 * de l'app, puis on y cherche les signatures de code des traqueurs.
 *
 * Les signatures Exodus étant des préfixes de paquets (`com.facebook.ads.`), la recherche se fait
 * sur l'ensemble dédoublonné des paquets (`com.foo.`) plutôt que sur chaque classe : quelques
 * milliers de chaînes au lieu de centaines de milliers.
 */
class DexTrackerAnalyzer(
    private val signatureRepository: TrackerSignatureRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TrackerAnalyzer {

    /** Clé `package@lastUpdateTime` : une app mise à jour est ré-analysée. */
    private val cache = ConcurrentHashMap<String, List<Tracker>>()

    override suspend fun isAvailable(): Boolean = signatureRepository.getSignatures().isNotEmpty()

    override suspend fun findTrackers(app: InstalledApp): List<Tracker> {
        val key = "${app.packageName}@${app.lastUpdateTime}"
        cache[key]?.let { return it }

        val signatures = signatureRepository.getSignatures()
        val trackers = withContext(ioDispatcher) {
            val packages = collectPackageNames(app.apkPaths)
            signatures
                .filter { signature -> packages.any(signature.codeSignature::containsMatchIn) }
                .map { it.tracker }
                .distinctBy { it.id }
        }
        return trackers.also { cache[key] = it }
    }

    private suspend fun collectPackageNames(apkPaths: List<String>): Set<String> = withContext(ioDispatcher) {
        val packages = HashSet<String>()
        for (path in apkPaths) {
            ZipFile(path).use { zip ->
                for (entry in zip.entries()) {
                    if (!DEX_ENTRY.matches(entry.name)) continue
                    ensureActive()
                    // Un DEX fait au plus quelques dizaines de Mo : lu d'un bloc puis libéré.
                    val dex = zip.getInputStream(entry).use { it.readBytes() }
                    DexClassNameReader.readClassNames(dex).forEach { className ->
                        packages += className.substringBeforeLast('.', missingDelimiterValue = "") + "."
                    }
                }
            }
        }
        packages
    }

    private companion object {
        val DEX_ENTRY = Regex("classes\\d*\\.dex")
    }
}
