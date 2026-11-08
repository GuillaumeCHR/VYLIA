package com.vylia.privacy.domain.databases

/** Une ligne de la carte « Bases de données ». */
data class DatabaseRow(
    val id: String,
    val group: DatabaseGroup,
    val name: String,
    val attribution: String,
    val status: DatabaseStatus,
    /** Date de la version téléchargée en service ; `null` : version de l'app. */
    val updatedAt: Long?,
    val entries: Int?,
    val lastError: String?,
    val consecutiveFailures: Int,
    val proposed: Boolean,
    val enabled: Boolean,
    val downloadBytes: Long,
    val rawBytes: Long,
)

/** État agrégé d'un groupe de bases, ou de la carte entière, visible carte repliée. */
data class DatabaseAggregate(
    val total: Int,
    val updating: Int,
    val failed: Int,
    val updateAvailable: Int,
    /** Date de la mise à jour la plus ancienne parmi les bases téléchargées, `null` si aucune ne l'est. */
    val oldestUpdate: Long?,
    /** Une base a échoué trois fois de suite ou plus (mise à jour automatique comprise). */
    val repeatedFailure: Boolean,
)

object DatabaseRows {
    /** Échecs de suite pour une même base à partir desquels le résumé de la carte le signale. */
    const val REPEATED_FAILURES = 3

    fun rows(sources: List<DatabaseSource>, meta: Map<String, DatabaseMeta>, running: Set<String>): List<DatabaseRow> =
        sources.map { source ->
            val m = meta[source.id] ?: DatabaseMeta()
            val enabled = source.isEnabled()
            val status = when {
                !enabled -> DatabaseStatus.NOT_ADDED
                source.id in running -> DatabaseStatus.UPDATING
                m.lastError != null -> DatabaseStatus.FAILED
                m.updateAvailable -> DatabaseStatus.UPDATE_AVAILABLE
                m.updatedAt == null && m.checkedAt == null -> DatabaseStatus.EMBEDDED
                else -> DatabaseStatus.UP_TO_DATE
            }
            DatabaseRow(
                id = source.id,
                group = source.group,
                name = source.name,
                attribution = source.attribution,
                status = status,
                updatedAt = m.updatedAt,
                entries = if (enabled) source.entries() ?: m.entries else null,
                lastError = m.lastError,
                consecutiveFailures = m.consecutiveFailures,
                proposed = source.proposed,
                enabled = enabled,
                downloadBytes = m.transferred?.takeIf { it > 0 } ?: source.downloadBytes,
                rawBytes = source.rawBytes,
            )
        }

    /** Les bases proposées que l'utilisateur n'a pas ajoutées ne comptent pas. */
    fun aggregate(rows: List<DatabaseRow>): DatabaseAggregate {
        val active = rows.filter { it.enabled }
        return DatabaseAggregate(
            total = active.size,
            updating = active.count { it.status == DatabaseStatus.UPDATING },
            failed = active.count { it.status == DatabaseStatus.FAILED },
            updateAvailable = active.count { it.status == DatabaseStatus.UPDATE_AVAILABLE },
            oldestUpdate = active.mapNotNull { it.updatedAt }.minOrNull(),
            repeatedFailure = active.any { it.consecutiveFailures >= REPEATED_FAILURES },
        )
    }
}
