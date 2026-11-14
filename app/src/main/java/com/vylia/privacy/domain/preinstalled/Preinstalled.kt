package com.vylia.privacy.domain.preinstalled

import com.vylia.privacy.domain.model.InstalledApp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Qui a mis l'app sur le téléphone. */
enum class PreinstalledOrigin { OEM, GOOGLE, CARRIER, THIRD_PARTY, ANDROID }

/**
 * Risque à désactiver l'app, d'après UAD-ng : Recommended → [SAFE], Advanced → [CAUTION], Expert et Unsafe →
 * [ESSENTIAL]. [UNKNOWN] : absente de la liste.
 */
enum class DisableRisk { SAFE, CAUTION, ESSENTIAL, UNKNOWN }

/** Les trois groupes du filtre de Scanner en mode Avancé. */
enum class AppGroup { USER, PREINSTALLED, SYSTEM }

/** Une app de la liste UAD-ng : origine, risque, description (en anglais) et liens avec d'autres paquets. */
data class PreinstalledEntry(
    val origin: PreinstalledOrigin,
    val risk: DisableRisk,
    val description: String,
    /** Paquets dont elle a besoin. */
    val dependencies: List<String> = emptyList(),
    /** Paquets qui ont besoin d'elle. */
    val neededBy: List<String> = emptyList(),
)

/**
 * Une app système classée : son groupe, son entrée UAD-ng si elle y figure, la marque déduite du paquet, et le fabricant
 * de la puce si c'est un de ses services (Qualcomm, MediaTek…).
 */
data class PreinstalledInfo(
    val group: AppGroup,
    val entry: PreinstalledEntry?,
    val brand: String?,
    val chipset: String? = null,
    /** Description traduite par VYLIA (en français), `null` si elle manque ou ne correspond plus au texte d'UAD-ng. */
    val translatedDescription: String? = null,
) {
    val risk: DisableRisk get() = entry?.risk ?: DisableRisk.UNKNOWN

    /** Une action est proposée (fiche Android, commande ADB) : jamais pour une app indispensable ou inconnue. */
    val actionable: Boolean get() = group == AppGroup.PREINSTALLED && (risk == DisableRisk.SAFE || risk == DisableRisk.CAUTION)
}

/** La liste UAD-ng, réduite à ce que VYLIA affiche, avec les traductions de ses descriptions. */
class PreinstalledDatabase(
    private val entries: Map<String, PreinstalledEntry>,
    val translations: UadTranslations = UadTranslations.EMPTY,
) {
    val size: Int get() = entries.size

    fun withTranslations(translations: UadTranslations) = PreinstalledDatabase(entries, translations)

    operator fun get(packageName: String): PreinstalledEntry? = entries[packageName]

    /**
     * Groupe d'une app : une app utilisateur reste [AppGroup.USER]. Une app système de la liste est préinstallée si elle
     * ne vient pas d'Android lui-même et qu'on peut la désactiver (sans risque ou à vos risques) ; sinon c'est un
     * composant d'Android. Absente de la liste, une app avec une icône dans le tiroir d'apps est préinstallée. Un service
     * du fabricant de la puce (Qualcomm, MediaTek…) sans icône est un composant d'Android, quel que soit son niveau : il
     * fait marcher le matériel (appels 4G, empreinte digitale…), et UAD-ng en dit parfois « sans risque » à tort.
     */
    fun classify(app: InstalledApp): PreinstalledInfo {
        if (!app.isSystemApp) return PreinstalledInfo(AppGroup.USER, null, null)
        val entry = entries[app.packageName]
        val chipset = Chipsets.of(app.packageName)
        val group = when {
            chipset != null && !app.hasLauncherIcon -> AppGroup.SYSTEM
            entry != null -> if (entry.origin != PreinstalledOrigin.ANDROID && entry.risk in DISABLEABLE) AppGroup.PREINSTALLED else AppGroup.SYSTEM
            app.hasLauncherIcon -> AppGroup.PREINSTALLED
            else -> AppGroup.SYSTEM
        }
        val origin = entry?.origin
        val brand = if (origin == null || origin == PreinstalledOrigin.OEM || origin == PreinstalledOrigin.CARRIER) Brands.of(app.packageName) else null
        return PreinstalledInfo(group, entry, brand.takeIf { chipset == null }, chipset, entry?.description?.let(translations::of))
    }

    companion object {
        val EMPTY = PreinstalledDatabase(emptyMap())
        private val DISABLEABLE = setOf(DisableRisk.SAFE, DisableRisk.CAUTION)
    }
}

/**
 * Lecture du fichier `uad_lists.json` d'UAD-ng, le même pour la version embarquée et celle téléchargée. Le fichier est
 * traité comme hostile : une entrée au nom de paquet invalide (il finit dans une commande ADB), à la liste ou au
 * niveau inconnus est écartée ; descriptions et listes de dépendances sont bornées.
 */
object UadList {
    const val MAX_DESCRIPTION = 1_000
    private const val MAX_LINKS = 30
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): PreinstalledDatabase {
        val root = json.parseToJsonElement(text).jsonObject
        val entries = HashMap<String, PreinstalledEntry>(root.size * 2)
        root.forEach { (pkg, value) ->
            if (!PackageNames.isValid(pkg)) return@forEach
            val obj = value as? JsonObject ?: return@forEach
            val origin = origin(obj.string("list")) ?: return@forEach
            val risk = risk(obj.string("removal")) ?: return@forEach
            entries[pkg] = PreinstalledEntry(
                origin = origin,
                risk = risk,
                description = obj.string("description").orEmpty().trim().take(MAX_DESCRIPTION),
                dependencies = obj.packages("dependencies"),
                neededBy = obj.packages("neededBy"),
            )
        }
        return PreinstalledDatabase(entries)
    }

    /** Empreinte d'une description (telle que gardée : rognée et coupée), clé de sa traduction : 16 caractères hexadécimaux. */
    fun keyOf(description: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(description.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)

    /** UAD-ng recense plus de 5 000 paquets : beaucoup moins, c'est une liste tronquée ou falsifiée. */
    const val MIN_ENTRIES = 4_000

    /**
     * Une liste téléchargée remplace celle en service si elle a assez d'entrées valides et n'en a pas perdu plus de
     * 10 % : une liste vidée ou tronquée ne remplace pas la bonne.
     */
    fun acceptable(fresh: Int, inService: Int): Boolean = fresh >= MIN_ENTRIES && fresh * 10L >= inService * 9L

    private fun origin(list: String?) = when (list) {
        "Oem" -> PreinstalledOrigin.OEM
        "Google" -> PreinstalledOrigin.GOOGLE
        "Carrier" -> PreinstalledOrigin.CARRIER
        "Misc" -> PreinstalledOrigin.THIRD_PARTY
        "Aosp" -> PreinstalledOrigin.ANDROID
        else -> null
    }

    private fun risk(removal: String?) = when (removal) {
        "Recommended" -> DisableRisk.SAFE
        "Advanced" -> DisableRisk.CAUTION
        "Expert", "Unsafe" -> DisableRisk.ESSENTIAL
        else -> null
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.packages(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.takeIf(PackageNames::isValid) }
            .take(MAX_LINKS)
}

/**
 * Traductions des descriptions d'UAD-ng, publiées dans le dépôt VYLIA (GPL v3, comme la liste). Indexées par
 * l'empreinte du texte anglais ([UadList.keyOf]) : une description modifiée par UAD-ng n'a plus de traduction, et
 * l'app montre le texte anglais plutôt qu'une traduction dépassée. Le fichier est traité comme hostile.
 */
class UadTranslations(val language: String, private val byKey: Map<String, String>) {
    val size: Int get() = byKey.size

    fun of(description: String): String? = if (description.isBlank()) null else byKey[UadList.keyOf(description)]

    companion object {
        val EMPTY = UadTranslations("fr", emptyMap())
        const val MAX_TEXT = 1_500
        private const val MAX_ENTRIES = 20_000
        private val KEY = Regex("[0-9a-f]{16}")
        private val LANGUAGE = Regex("[a-z]{2}")
        /** Caractères de contrôle et de mise en forme invisibles (dont l'inversion du sens d'écriture), sauf le saut de ligne. */
        private val INVISIBLE = Regex("[\\p{Cc}\\p{Cf}&&[^\\n]]")
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): UadTranslations {
            val root = json.parseToJsonElement(text).jsonObject
            require((root["format"] as? JsonPrimitive)?.content == "1") { "format inconnu" }
            val language = (root["language"] as? JsonPrimitive)?.content.orEmpty()
            require(LANGUAGE.matches(language)) { "langue invalide" }
            val translations = root["translations"] as? JsonObject ?: error("traductions absentes")
            require(translations.size <= MAX_ENTRIES) { "trop de traductions" }
            val byKey = HashMap<String, String>(translations.size * 2)
            translations.forEach { (key, value) ->
                if (!KEY.matches(key)) return@forEach
                val fr = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.replace(INVISIBLE, "")?.trim()?.take(MAX_TEXT)
                if (!fr.isNullOrEmpty()) byKey[key] = fr
            }
            return UadTranslations(language, byKey)
        }
    }
}

object PackageNames {
    /** Lettres, chiffres, `_` et points, comme Android l'impose : rien qu'un shell puisse interpréter. */
    private val VALID = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*")

    fun isValid(name: String): Boolean = name.length <= 255 && VALID.matches(name)
}

/** Commandes ADB à copier : `disable-user` plutôt que `uninstall`, réversible sans réinstaller. */
object AdbCommands {
    fun disable(packageName: String): String = "adb shell pm disable-user --user 0 ${checked(packageName)}"

    fun enable(packageName: String): String = "adb shell pm enable --user 0 ${checked(packageName)}"

    /** Une commande par ligne, dans l'ordre donné. */
    fun disableAll(packages: List<String>): String = packages.joinToString("\n", transform = ::disable)

    private fun checked(packageName: String): String {
        require(PackageNames.isValid(packageName)) { "nom de paquet invalide" }
        return packageName
    }
}

/** Fabricant de la puce dont l'app est un service, d'après le début de son nom de paquet. */
object Chipsets {
    private val PREFIXES = listOf(
        "com.qualcomm." to "Qualcomm", "com.qti." to "Qualcomm", "vendor.qti." to "Qualcomm", "com.quicinc." to "Qualcomm",
        "org.codeaurora." to "Qualcomm",
        "com.mediatek." to "MediaTek", "com.mtk." to "MediaTek", "vendor.mediatek." to "MediaTek",
        "com.unisoc." to "Unisoc", "com.sprd." to "Unisoc", "com.spreadtrum." to "Unisoc",
    )

    fun of(packageName: String): String? = PREFIXES.firstOrNull { packageName.startsWith(it.first) }?.second
}

/** Marque d'une app de constructeur ou d'opérateur, déduite du début de son nom de paquet (UAD-ng ne la donne pas). */
object Brands {
    private val PREFIXES = listOf(
        "com.samsung." to "Samsung", "com.sec." to "Samsung", "com.sem." to "Samsung",
        "com.miui." to "Xiaomi", "com.xiaomi." to "Xiaomi", "com.mi." to "Xiaomi",
        "com.huawei." to "Huawei", "com.hihonor." to "Honor",
        "com.oneplus." to "OnePlus", "com.oplus." to "Oppo", "com.coloros." to "Oppo", "com.oppo." to "Oppo",
        "com.heytap." to "Oppo", "com.realme." to "Realme",
        "com.vivo." to "Vivo", "com.bbk." to "Vivo",
        "com.motorola." to "Motorola", "com.lge." to "LG", "com.sonyericsson." to "Sony", "com.sony." to "Sony",
        "com.asus." to "Asus", "com.hmdglobal." to "Nokia", "com.evenwell." to "Nokia",
        "com.fairphone." to "Fairphone", "com.nothing." to "Nothing", "com.zte." to "ZTE", "com.tcl." to "TCL",
        "com.lenovo." to "Lenovo", "com.htc." to "HTC", "com.transsion." to "Transsion",
        "com.orange." to "Orange", "com.sfr." to "SFR", "fr.bouyguestelecom." to "Bouygues Telecom",
        "com.vodafone." to "Vodafone", "com.tmobile." to "T-Mobile", "com.verizon." to "Verizon", "com.att." to "AT&T",
    )

    fun of(packageName: String): String? = PREFIXES.firstOrNull { packageName.startsWith(it.first) }?.second
}
