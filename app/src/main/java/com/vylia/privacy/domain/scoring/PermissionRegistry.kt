package com.vylia.privacy.domain.scoring

import android.Manifest
import androidx.annotation.StringRes
import com.vylia.privacy.R

enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * Famille de permissions donnant accès aux mêmes données : une famille ne pénalise qu'une fois,
 * au niveau de son membre le plus intrusif détecté.
 */
enum class PermissionFamily {
    BACKGROUND_LOCATION, ACCESSIBILITY, CAMERA, MICROPHONE, SMS, LOCATION, CONTACTS, CALENDAR, STORAGE
}

data class PermissionRisk(
    val permission: String,
    val family: PermissionFamily,
    @param:StringRes val title: Int,
    val basePenalty: Int,
    val riskLevel: RiskLevel
)

object PermissionRegistry {
    private val registry = listOf(
        // --- CRITICAL (-25 à -30 points) ---
        PermissionRisk(
            permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            family = PermissionFamily.BACKGROUND_LOCATION,
            title = R.string.risk_access_background_location,
            basePenalty = 30,
            riskLevel = RiskLevel.CRITICAL
        ),
        PermissionRisk(
            permission = Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
            family = PermissionFamily.ACCESSIBILITY,
            title = R.string.risk_bind_accessibility_service,
            basePenalty = 30,
            riskLevel = RiskLevel.CRITICAL
        ),
        PermissionRisk(
            permission = Manifest.permission.CAMERA,
            family = PermissionFamily.CAMERA,
            title = R.string.risk_camera,
            basePenalty = 25,
            riskLevel = RiskLevel.CRITICAL
        ),
        PermissionRisk(
            permission = Manifest.permission.RECORD_AUDIO,
            family = PermissionFamily.MICROPHONE,
            title = R.string.risk_record_audio,
            basePenalty = 25,
            riskLevel = RiskLevel.CRITICAL
        ),
        PermissionRisk(
            permission = Manifest.permission.READ_SMS,
            family = PermissionFamily.SMS,
            title = R.string.risk_read_sms,
            basePenalty = 25,
            riskLevel = RiskLevel.CRITICAL
        ),
        PermissionRisk(
            permission = Manifest.permission.RECEIVE_SMS,
            family = PermissionFamily.SMS,
            title = R.string.risk_receive_sms,
            basePenalty = 25,
            riskLevel = RiskLevel.CRITICAL
        ),
        PermissionRisk(
            permission = Manifest.permission.SEND_SMS,
            family = PermissionFamily.SMS,
            title = R.string.risk_send_sms,
            basePenalty = 25,
            riskLevel = RiskLevel.CRITICAL
        ),

        // --- HIGH (-15 à -20 points) ---
        PermissionRisk(
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            family = PermissionFamily.LOCATION,
            title = R.string.risk_access_fine_location,
            basePenalty = 20,
            riskLevel = RiskLevel.HIGH
        ),
        PermissionRisk(
            permission = Manifest.permission.READ_CONTACTS,
            family = PermissionFamily.CONTACTS,
            title = R.string.risk_read_contacts,
            basePenalty = 20,
            riskLevel = RiskLevel.HIGH
        ),
        PermissionRisk(
            permission = Manifest.permission.WRITE_CONTACTS,
            family = PermissionFamily.CONTACTS,
            title = R.string.risk_write_contacts,
            basePenalty = 20,
            riskLevel = RiskLevel.HIGH
        ),
        PermissionRisk(
            permission = Manifest.permission.GET_ACCOUNTS,
            family = PermissionFamily.CONTACTS,
            title = R.string.risk_get_accounts,
            basePenalty = 20,
            riskLevel = RiskLevel.HIGH
        ),
        PermissionRisk(
            permission = Manifest.permission.READ_CALENDAR,
            family = PermissionFamily.CALENDAR,
            title = R.string.risk_read_calendar,
            basePenalty = 15,
            riskLevel = RiskLevel.HIGH
        ),
        PermissionRisk(
            permission = Manifest.permission.WRITE_CALENDAR,
            family = PermissionFamily.CALENDAR,
            title = R.string.risk_write_calendar,
            basePenalty = 15,
            riskLevel = RiskLevel.HIGH
        ),

        // --- MEDIUM (-10 points) ---
        PermissionRisk(
            permission = Manifest.permission.ACCESS_COARSE_LOCATION,
            family = PermissionFamily.LOCATION,
            title = R.string.risk_access_coarse_location,
            basePenalty = 10,
            riskLevel = RiskLevel.MEDIUM
        ),
        storage(Manifest.permission.READ_EXTERNAL_STORAGE, R.string.risk_read_external_storage),
        storage(Manifest.permission.WRITE_EXTERNAL_STORAGE, R.string.risk_write_external_storage),
        storage(Manifest.permission.MANAGE_EXTERNAL_STORAGE, R.string.risk_manage_external_storage),
        storage(Manifest.permission.READ_MEDIA_IMAGES, R.string.risk_read_media_images),
        storage(Manifest.permission.READ_MEDIA_VIDEO, R.string.risk_read_media_video),
        storage(Manifest.permission.READ_MEDIA_AUDIO, R.string.risk_read_media_audio),
        storage(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, R.string.risk_read_media_visual_user_selected),
    )

    val risks: Map<String, PermissionRisk> = registry.associateBy { it.permission }

    fun strongestByFamily(permissions: Collection<String>): List<PermissionRisk> {
        val declared = permissions.toSet()
        return registry
            .filter { it.permission in declared }
            .groupBy { it.family }
            .values
            .map { family -> family.maxBy { it.basePenalty } }
            .sortedByDescending { it.basePenalty }
    }

    private fun storage(permission: String, @StringRes title: Int) = PermissionRisk(
        permission = permission,
        family = PermissionFamily.STORAGE,
        title = title,
        basePenalty = 10,
        riskLevel = RiskLevel.MEDIUM
    )
}
