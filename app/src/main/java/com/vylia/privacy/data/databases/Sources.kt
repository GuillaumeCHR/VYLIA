package com.vylia.privacy.data.databases

import com.vylia.privacy.data.trackers.ExodusCategoryOverrides
import com.vylia.privacy.data.trackers.TrackerUpdater
import com.vylia.privacy.domain.databases.DatabaseGroup
import com.vylia.privacy.domain.databases.DatabaseSource
import com.vylia.privacy.domain.databases.Fetch
import com.vylia.privacy.domain.databases.Outcome

/** Base Exodus Privacy (signatures de traqueurs), ODbL 1.0. */
class ExodusSource(private val updater: TrackerUpdater, private val count: () -> Int?) : DatabaseSource {
    override val id = "exodus"
    override val group = DatabaseGroup.SCORE
    override val name = "Exodus Privacy"
    override val attribution = "Exodus Privacy · ODbL 1.0"
    override val checkUrl = TrackerUpdater.UPDATE_URL
    override val affectsScore = true
    override fun entries(): Int? = count()
    override suspend fun update(fetch: Fetch): Outcome = updater.update(fetch)
}

/** Catégories ajoutées aux traqueurs qu'Exodus ne catégorise pas, révisées dans le dépôt VYLIA. */
class ExodusCategoriesSource(private val overrides: ExodusCategoryOverrides) : DatabaseSource {
    override val id = "exodus_categories"
    override val group = DatabaseGroup.SCORE
    override val name = "Catégories Exodus (VYLIA)"
    override val attribution = "VYLIA · GPL v3"
    override val checkUrl = ExodusCategoryOverrides.REMOTE_URL
    override val affectsScore = true
    override fun entries(): Int = overrides.size
    override suspend fun update(fetch: Fetch): Outcome = overrides.update(fetch)
}

/** Stalkerwares connus d'Echap (`ioc.yaml`), CC BY 4.0. */
class EchapSource(private val repository: com.vylia.privacy.data.security.StalkerwareRepository) : DatabaseSource {
    override val id = "echap"
    override val group = DatabaseGroup.SCORE
    override val name = "Echap · stalkerware-indicators"
    override val attribution = "Echap · CC BY 4.0"
    override val checkUrl = com.vylia.privacy.data.security.StalkerwareRepository.UPDATE_URL
    override val affectsScore = true
    override fun entries(): Int = repository.current.value.database.apps
    override suspend fun update(fetch: Fetch): Outcome = repository.update(fetch)
}

/** Indicateurs MVT des logiciels espions de surveillance ciblée, MIT. */
class MvtSource(private val repository: com.vylia.privacy.data.security.MvtRepository) : DatabaseSource {
    override val id = "mvt"
    override val group = DatabaseGroup.SCORE
    override val name = "MVT · mvt-indicators"
    override val attribution = "MVT · MIT"
    override val checkUrl = com.vylia.privacy.domain.security.MvtIndicators.INDEX_URL
    override val affectsScore = true
    override fun entries(): Int = repository.current.value.database.total
    override suspend fun update(fetch: Fetch): Outcome = repository.update(fetch)
}

/** Règles de score publiées dans le dépôt VYLIA. */
class RulesSource(private val repository: com.vylia.privacy.data.rules.RuleMatrixRepository) : DatabaseSource {
    override val id = "rules"
    override val group = DatabaseGroup.SCORE
    override val name = "Règles de score VYLIA"
    override val attribution = "VYLIA · GPL v3"
    override val checkUrl = com.vylia.privacy.data.rules.RuleMatrixRepository.REMOTE_URL
    override val affectsScore = true
    override fun entries(): Int = repository.current.value.rules.categories.size
    override suspend fun update(fetch: Fetch): Outcome = repository.update(fetch)
}

/** Catalogue des alternatives (swaps), dépôt VYLIA. */
class AlternativesSource(private val repository: com.vylia.privacy.data.alternatives.AlternativesRepository) : DatabaseSource {
    override val id = "alternatives"
    override val group = DatabaseGroup.ALTERNATIVES
    override val name = "Alternatives VYLIA"
    override val attribution = "VYLIA · GPL v3"
    override val checkUrl = com.vylia.privacy.data.alternatives.AlternativesRepository.REMOTE_URL
    override val affectsScore = false
    override fun entries(): Int = repository.size
    override suspend fun update(fetch: Fetch): Outcome = repository.update(fetch)
}

/** Services utilisables en version web, dépôt VYLIA. */
class WebServicesSource(private val repository: com.vylia.privacy.data.web.WebServicesRepository) : DatabaseSource {
    override val id = "web_services"
    override val group = DatabaseGroup.ALTERNATIVES
    override val name = "Services web VYLIA"
    override val attribution = "VYLIA · GPL v3"
    override val checkUrl = com.vylia.privacy.data.web.WebServicesRepository.REMOTE_URL
    override val affectsScore = false
    override fun entries(): Int = repository.size
    override suspend fun update(fetch: Fetch): Outcome = repository.update(fetch)
}

/** Services de DNS privé, dépôt VYLIA. */
class PrivateDnsSource(private val repository: com.vylia.privacy.data.dns.PrivateDnsRepository) : DatabaseSource {
    override val id = "private_dns"
    override val group = DatabaseGroup.ALTERNATIVES
    override val name = "DNS privés VYLIA"
    override val attribution = "VYLIA · GPL v3"
    override val checkUrl = com.vylia.privacy.data.dns.PrivateDnsRepository.REMOTE_URL
    override val affectsScore = false
    override fun entries(): Int = repository.size
    override suspend fun update(fetch: Fetch): Outcome = repository.update(fetch)
}

/** AdGuard companiesdb : nom de l'entreprise derrière un domaine bloqué. */
class CompaniesSource(private val repository: CompaniesRepository) : DatabaseSource {
    override val id = "companies"
    override val group = DatabaseGroup.ENRICHMENT
    override val name = "AdGuard companiesdb"
    override val attribution = "AdGuard · CC BY-SA 4.0"
    override val checkUrl = CompaniesRepository.TRACKERS_URL
    override val affectsScore = false
    override fun entries(): Int? = repository.size
    override suspend fun update(fetch: Fetch): Outcome = repository.update(fetch)
}

/** Une liste du bouclier (EasyList, AdGuard…), analysée et gardée compressée par le module du bouclier. */
class ShieldListSource(
    private val list: com.vylia.privacy.data.shield.ShieldList,
    private val controller: com.vylia.privacy.data.shield.ShieldController,
) : DatabaseSource {
    override val id = list.id
    override val group = DatabaseGroup.SHIELD
    override val name = list.name
    override val attribution = "${list.name.substringBefore(' ')} · ${list.license}"
    override val checkUrl = list.url
    override val affectsScore = false
    override val proposed = list.proposed
    override val downloadBytes = list.downloadBytes
    override val rawBytes = list.rawBytes
    override fun isEnabled(): Boolean = controller.isListEnabled(list)
    override fun entries(): Int? = null
    override suspend fun update(fetch: Fetch): Outcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val file = fetch.get(list.url, list.maxBytes, conditional = controller.lists.isDownloaded(list.id))
            ?: return@withContext Outcome.Unchanged
        Outcome.Installed(controller.lists.install(list, file).domains)
    }
}

/** Exemples de ressources d'aide en cas de surveillance, dépôt VYLIA. */
class SupportResourcesSource(private val repository: com.vylia.privacy.data.safety.SupportResourcesRepository) : DatabaseSource {
    override val id = "support_resources"
    override val group = DatabaseGroup.ALTERNATIVES
    override val name = "Ressources d'aide VYLIA"
    override val attribution = "VYLIA · GPL v3"
    override val checkUrl = com.vylia.privacy.data.safety.SupportResourcesRepository.REMOTE_URL
    override val affectsScore = false
    override fun entries(): Int = repository.size
    override suspend fun update(fetch: Fetch): Outcome = repository.update(fetch)
}
