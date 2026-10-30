package com.vipacy.app.domain.model

import com.vipacy.app.domain.scoring.PermissionRisk

/**
 * Famille de permissions à risque trouvée dans une application, représentée par son membre
 * le plus intrusif ([risk]).
 *
 * @property isGranted `true` si au moins une permission de la famille est accordée (ou, pour
 * l'accessibilité, si le service est activé), `false` si elles sont seulement demandées.
 */
data class DetectedPermission(
    val risk: PermissionRisk,
    val isGranted: Boolean,
)
