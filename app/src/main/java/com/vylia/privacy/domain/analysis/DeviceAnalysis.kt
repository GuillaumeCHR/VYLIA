package com.vylia.privacy.domain.analysis

import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.ScoreRating
import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.scoring.Context
import com.vylia.privacy.domain.scoring.PermissionFinding
import com.vylia.privacy.domain.usecase.RiskFirstOrder

/** Permission sensible dont on liste les apps qui la demandent (onglet Analyse). */
enum class WatchedPermission(val permission: String) {
    MICROPHONE("RECORD_AUDIO"),
    CAMERA("CAMERA"),
    BACKGROUND_LOCATION("ACCESS_BACKGROUND_LOCATION"),
    FINE_LOCATION("ACCESS_FINE_LOCATION"),
    CONTACTS("READ_CONTACTS"),
    SMS("READ_SMS"),
    CALL_LOG("READ_CALL_LOG"),
    CALENDAR("READ_CALENDAR"),
    ACCESSIBILITY("BIND_ACCESSIBILITY_SERVICE"),
    NOTIFICATIONS("BIND_NOTIFICATION_LISTENER_SERVICE"),
    OVERLAY("SYSTEM_ALERT_WINDOW"),
    USAGE_STATS("PACKAGE_USAGE_STATS"),
    ALL_FILES("MANAGE_EXTERNAL_STORAGE"),
    BODY_SENSORS("BODY_SENSORS"),
}

/** App dont des permissions ne correspondent pas à la finalité de sa catégorie. */
data class PurposeAnomaly(val app: AuditedApp, val findings: List<PermissionFinding>) {
    val penalty: Double get() = findings.sumOf { it.penalty }
}

data class PermissionUsage(
    val permission: WatchedPermission,
    val granted: List<AuditedApp>,
    val declaredOnly: List<AuditedApp>,
) {
    val total: Int get() = granted.size + declaredOnly.size
}

data class TrackerUsage(val tracker: Tracker, val apps: List<AuditedApp>)

/**
 * Vues transversales de l'onglet Analyse, calculées à partir du dernier scan :
 * zones de risque, anomalies de finalité, apps par permission sensible, cartographie des traqueurs.
 */
data class DeviceAnalysis(
    val zones: Map<ScoreRating, List<AuditedApp>>,
    val anomalies: List<PurposeAnomaly>,
    /** Permissions demandées par au moins une app, de la plus répandue à la moins répandue. */
    val permissions: List<PermissionUsage>,
    /** Traqueurs détectés, du plus répandu au moins répandu. */
    val trackers: List<TrackerUsage>,
    /** Nombre d'apps embarquant au moins un traqueur de chaque catégorie Exodus. */
    val trackerCategories: List<Pair<String, Int>>,
    val analyzedAppCount: Int,
    val appsWithTrackers: Int,
) {
    companion object {
        const val UNCATEGORIZED = "Sans catégorie"

        fun from(apps: List<AuditedApp>): DeviceAnalysis {
            val sorted = apps.sortedWith(RiskFirstOrder)
            val analyzed = sorted.mapNotNull { app -> (app.trackerAnalysis as? TrackerAnalysis.Analyzed)?.let { app to it.trackers } }

            val trackers = analyzed
                .flatMap { (app, trackers) -> trackers.map { it to app } }
                .groupBy({ it.first.id }, { it })
                .values
                .map { pairs -> TrackerUsage(pairs.first().first, pairs.map { it.second }) }
                .sortedWith(compareByDescending<TrackerUsage> { it.apps.size }.thenBy { it.tracker.name })

            val trackerCategories = analyzed
                .flatMap { (app, trackers) ->
                    trackers.flatMap { it.categories.ifEmpty { listOf(UNCATEGORIZED) } }.distinct().map { it to app }
                }
                .groupingBy { it.first }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }

            return DeviceAnalysis(
                zones = ScoreRating.entries.associateWith { rating -> sorted.filter { it.score.rating == rating } },
                anomalies = sorted
                    .mapNotNull { app -> app.purposeFindings().takeIf { it.isNotEmpty() }?.let { PurposeAnomaly(app, it) } }
                    .sortedByDescending { it.penalty },
                permissions = WatchedPermission.entries
                    .map { watched ->
                        val requesting = sorted.filter { watched.permission in it.app.declaredPermissions }
                        val (granted, declaredOnly) = requesting.partition { watched.permission in it.app.grantedPermissions }
                        PermissionUsage(watched, granted, declaredOnly)
                    }
                    .filter { it.total > 0 }
                    .sortedByDescending { it.total },
                trackers = trackers,
                trackerCategories = trackerCategories,
                analyzedAppCount = analyzed.size,
                appsWithTrackers = analyzed.count { it.second.isNotEmpty() },
            )
        }

        /**
         * Permissions interdites pour la catégorie, et inattendues quand la catégorie est connue :
         * sans catégorie, toute permission sensible est "inattendue", ce qui ne dit rien de la finalité.
         */
        private fun AuditedApp.purposeFindings(): List<PermissionFinding> =
            result.permissionFindings.filter {
                it.context == Context.FORBIDDEN || (it.context == Context.UNEXPECTED && result.categoryId != "DEFAULT")
            }
    }
}
