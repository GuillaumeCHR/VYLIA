package com.vylia.privacy.domain.analysis

import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp

/** Vos apps qui demandent une permission du lexique, et celles à qui elle est accordée. */
data class GlossaryUsage(val granted: List<AuditedApp>, val refused: List<AuditedApp>) {
    val declared: Int get() = granted.size + refused.size
}

object GlossaryUsages {
    /**
     * Pour chaque clé du lexique (nom court, ou famille « health.* »), les apps utilisateur qui la déclarent,
     * accordée d'abord, chacune triée par score croissant (les plus intrusives en tête).
     */
    fun of(keys: Collection<String>, apps: List<AuditedApp>): Map<String, GlossaryUsage> {
        val userApps = apps.filter { AppFilter.USER.matches(it.app) }.sortedBy { it.score.value }
        return keys.associateWith { key ->
            fun Set<String>.has() = if (key.endsWith(".*")) any { it.startsWith(key.dropLast(1)) } else key in this
            val declaring = userApps.filter { it.app.declaredPermissions.has() }
            val (granted, refused) = declaring.partition { it.app.grantedPermissions.has() }
            GlossaryUsage(granted, refused)
        }
    }
}
