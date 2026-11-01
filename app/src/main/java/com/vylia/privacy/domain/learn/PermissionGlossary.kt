package com.vylia.privacy.domain.learn

import androidx.annotation.StringRes
import com.vylia.privacy.R

/** Comment Android accorde une permission : détermine si vous avez eu votre mot à dire. */
enum class GrantMode(@param:StringRes val label: Int, @param:StringRes val explanation: Int) {
    RUNTIME(R.string.grant_runtime, R.string.grant_runtime_explanation),
    BACKGROUND(R.string.grant_background, R.string.grant_background_explanation),
    WITH_MEDIA(R.string.grant_with_media, R.string.grant_with_media_explanation),
    SPECIAL(R.string.grant_special, R.string.grant_special_explanation),
    INSTALL(R.string.grant_install, R.string.grant_install_explanation),
}

enum class PermissionTheme(@param:StringRes val title: Int) {
    LOCATION(R.string.perm_theme_location),
    MESSAGES_CALLS(R.string.perm_theme_messages_calls),
    CONTACTS(R.string.perm_theme_contacts),
    MIC_CAMERA(R.string.perm_theme_mic_camera),
    CALENDAR(R.string.perm_theme_calendar),
    HEALTH(R.string.perm_theme_health),
    FILES(R.string.perm_theme_files),
    NEARBY(R.string.perm_theme_nearby),
    APPS(R.string.perm_theme_apps),
    CONTROL(R.string.perm_theme_control),
    NETWORK(R.string.perm_theme_network),
    ADS(R.string.perm_theme_ads),
    SYSTEM(R.string.perm_theme_system),
    SCREEN(R.string.perm_theme_screen),
    OTHER(R.string.perm_theme_other),
}

/** Explication grand public d'une permission (textes en ressources, traduits). */
data class PermissionInfo(
    /** Nom court, sans "android.permission." (nom complet pour une permission d'un autre espace, ex. AD_ID). */
    val permission: String,
    @param:StringRes val title: Int,
    val theme: PermissionTheme,
    @param:StringRes val allows: Int,
    @param:StringRes val whySensitive: Int,
    val grant: GrantMode,
)

/**
 * Lexique des permissions : une entrée pour chaque permission notée dans vylia_rules_v2_1.json, plus les
 * permissions courantes non notées qu'il faut comprendre (Internet, identifiant publicitaire, démarrage au boot…).
 */
object PermissionGlossary {

    val entries: List<PermissionInfo> = listOf(
        info("ACCESS_BACKGROUND_LOCATION", PermissionTheme.LOCATION, GrantMode.BACKGROUND, R.string.perm_access_background_location_title, R.string.perm_access_background_location_allows, R.string.perm_access_background_location_why),
        info("ACCESS_FINE_LOCATION", PermissionTheme.LOCATION, GrantMode.RUNTIME, R.string.perm_access_fine_location_title, R.string.perm_access_fine_location_allows, R.string.perm_access_fine_location_why),
        info("ACCESS_COARSE_LOCATION", PermissionTheme.LOCATION, GrantMode.RUNTIME, R.string.perm_access_coarse_location_title, R.string.perm_access_coarse_location_allows, R.string.perm_access_coarse_location_why),
        info("ACCESS_MEDIA_LOCATION", PermissionTheme.LOCATION, GrantMode.WITH_MEDIA, R.string.perm_access_media_location_title, R.string.perm_access_media_location_allows, R.string.perm_access_media_location_why),
        info("READ_SMS", PermissionTheme.MESSAGES_CALLS, GrantMode.RUNTIME, R.string.perm_read_sms_title, R.string.perm_read_sms_allows, R.string.perm_read_sms_why),
        info("RECEIVE_SMS", PermissionTheme.MESSAGES_CALLS, GrantMode.RUNTIME, R.string.perm_receive_sms_title, R.string.perm_receive_sms_allows, R.string.perm_receive_sms_why),
        info("SEND_SMS", PermissionTheme.MESSAGES_CALLS, GrantMode.RUNTIME, R.string.perm_send_sms_title, R.string.perm_send_sms_allows, R.string.perm_send_sms_why),
        info("RECEIVE_MMS", PermissionTheme.MESSAGES_CALLS, GrantMode.RUNTIME, R.string.perm_receive_mms_title, R.string.perm_receive_mms_allows, R.string.perm_receive_mms_why),
        info("READ_CALL_LOG", PermissionTheme.MESSAGES_CALLS, GrantMode.RUNTIME, R.string.perm_read_call_log_title, R.string.perm_read_call_log_allows, R.string.perm_read_call_log_why),
        info("WRITE_CALL_LOG", PermissionTheme.MESSAGES_CALLS, GrantMode.RUNTIME, R.string.perm_write_call_log_title, R.string.perm_write_call_log_allows, R.string.perm_write_call_log_why),
        info("PROCESS_OUTGOING_CALLS", PermissionTheme.MESSAGES_CALLS, GrantMode.RUNTIME, R.string.perm_process_outgoing_calls_title, R.string.perm_process_outgoing_calls_allows, R.string.perm_process_outgoing_calls_why),
        info("CALL_PHONE", PermissionTheme.MESSAGES_CALLS, GrantMode.RUNTIME, R.string.perm_call_phone_title, R.string.perm_call_phone_allows, R.string.perm_call_phone_why),
        info("ANSWER_PHONE_CALLS", PermissionTheme.MESSAGES_CALLS, GrantMode.RUNTIME, R.string.perm_answer_phone_calls_title, R.string.perm_answer_phone_calls_allows, R.string.perm_answer_phone_calls_why),
        info("READ_PHONE_STATE", PermissionTheme.MESSAGES_CALLS, GrantMode.RUNTIME, R.string.perm_read_phone_state_title, R.string.perm_read_phone_state_allows, R.string.perm_read_phone_state_why),
        info("READ_PHONE_NUMBERS", PermissionTheme.MESSAGES_CALLS, GrantMode.RUNTIME, R.string.perm_read_phone_numbers_title, R.string.perm_read_phone_numbers_allows, R.string.perm_read_phone_numbers_why),
        info("READ_CONTACTS", PermissionTheme.CONTACTS, GrantMode.RUNTIME, R.string.perm_read_contacts_title, R.string.perm_read_contacts_allows, R.string.perm_read_contacts_why),
        info("WRITE_CONTACTS", PermissionTheme.CONTACTS, GrantMode.RUNTIME, R.string.perm_write_contacts_title, R.string.perm_write_contacts_allows, R.string.perm_write_contacts_why),
        info("GET_ACCOUNTS", PermissionTheme.CONTACTS, GrantMode.RUNTIME, R.string.perm_get_accounts_title, R.string.perm_get_accounts_allows, R.string.perm_get_accounts_why),
        info("RECORD_AUDIO", PermissionTheme.MIC_CAMERA, GrantMode.RUNTIME, R.string.perm_record_audio_title, R.string.perm_record_audio_allows, R.string.perm_record_audio_why),
        info("CAMERA", PermissionTheme.MIC_CAMERA, GrantMode.RUNTIME, R.string.perm_camera_title, R.string.perm_camera_allows, R.string.perm_camera_why),
        info("READ_CALENDAR", PermissionTheme.CALENDAR, GrantMode.RUNTIME, R.string.perm_read_calendar_title, R.string.perm_read_calendar_allows, R.string.perm_read_calendar_why),
        info("WRITE_CALENDAR", PermissionTheme.CALENDAR, GrantMode.RUNTIME, R.string.perm_write_calendar_title, R.string.perm_write_calendar_allows, R.string.perm_write_calendar_why),
        info("BODY_SENSORS", PermissionTheme.HEALTH, GrantMode.RUNTIME, R.string.perm_body_sensors_title, R.string.perm_body_sensors_allows, R.string.perm_body_sensors_why),
        info("BODY_SENSORS_BACKGROUND", PermissionTheme.HEALTH, GrantMode.BACKGROUND, R.string.perm_body_sensors_background_title, R.string.perm_body_sensors_background_allows, R.string.perm_body_sensors_background_why),
        info("ACTIVITY_RECOGNITION", PermissionTheme.HEALTH, GrantMode.RUNTIME, R.string.perm_activity_recognition_title, R.string.perm_activity_recognition_allows, R.string.perm_activity_recognition_why),
        info("READ_EXTERNAL_STORAGE", PermissionTheme.FILES, GrantMode.RUNTIME, R.string.perm_read_external_storage_title, R.string.perm_read_external_storage_allows, R.string.perm_read_external_storage_why),
        info("MANAGE_EXTERNAL_STORAGE", PermissionTheme.FILES, GrantMode.SPECIAL, R.string.perm_manage_external_storage_title, R.string.perm_manage_external_storage_allows, R.string.perm_manage_external_storage_why),
        info("READ_MEDIA_IMAGES", PermissionTheme.FILES, GrantMode.RUNTIME, R.string.perm_read_media_images_title, R.string.perm_read_media_images_allows, R.string.perm_read_media_images_why),
        info("READ_MEDIA_VIDEO", PermissionTheme.FILES, GrantMode.RUNTIME, R.string.perm_read_media_video_title, R.string.perm_read_media_video_allows, R.string.perm_read_media_video_why),
        info("READ_MEDIA_AUDIO", PermissionTheme.FILES, GrantMode.RUNTIME, R.string.perm_read_media_audio_title, R.string.perm_read_media_audio_allows, R.string.perm_read_media_audio_why),
        info("BLUETOOTH_SCAN", PermissionTheme.NEARBY, GrantMode.RUNTIME, R.string.perm_bluetooth_scan_title, R.string.perm_bluetooth_scan_allows, R.string.perm_bluetooth_scan_why),
        info("NEARBY_WIFI_DEVICES", PermissionTheme.NEARBY, GrantMode.RUNTIME, R.string.perm_nearby_wifi_devices_title, R.string.perm_nearby_wifi_devices_allows, R.string.perm_nearby_wifi_devices_why),
        info("QUERY_ALL_PACKAGES", PermissionTheme.APPS, GrantMode.INSTALL, R.string.perm_query_all_packages_title, R.string.perm_query_all_packages_allows, R.string.perm_query_all_packages_why),
        info("PACKAGE_USAGE_STATS", PermissionTheme.APPS, GrantMode.SPECIAL, R.string.perm_package_usage_stats_title, R.string.perm_package_usage_stats_allows, R.string.perm_package_usage_stats_why),
        info("REQUEST_INSTALL_PACKAGES", PermissionTheme.APPS, GrantMode.SPECIAL, R.string.perm_request_install_packages_title, R.string.perm_request_install_packages_allows, R.string.perm_request_install_packages_why),
        info("BIND_ACCESSIBILITY_SERVICE", PermissionTheme.CONTROL, GrantMode.SPECIAL, R.string.perm_bind_accessibility_service_title, R.string.perm_bind_accessibility_service_allows, R.string.perm_bind_accessibility_service_why),
        info("BIND_NOTIFICATION_LISTENER_SERVICE", PermissionTheme.CONTROL, GrantMode.SPECIAL, R.string.perm_bind_notification_listener_service_title, R.string.perm_bind_notification_listener_service_allows, R.string.perm_bind_notification_listener_service_why),
        info("BIND_DEVICE_ADMIN", PermissionTheme.CONTROL, GrantMode.SPECIAL, R.string.perm_bind_device_admin_title, R.string.perm_bind_device_admin_allows, R.string.perm_bind_device_admin_why),
        info("SYSTEM_ALERT_WINDOW", PermissionTheme.CONTROL, GrantMode.SPECIAL, R.string.perm_system_alert_window_title, R.string.perm_system_alert_window_allows, R.string.perm_system_alert_window_why),
        info("INTERNET", PermissionTheme.NETWORK, GrantMode.INSTALL, R.string.perm_internet_title, R.string.perm_internet_allows, R.string.perm_internet_why),
        info("ACCESS_NETWORK_STATE", PermissionTheme.NETWORK, GrantMode.INSTALL, R.string.perm_access_network_state_title, R.string.perm_access_network_state_allows, R.string.perm_access_network_state_why),
        info("ACCESS_WIFI_STATE", PermissionTheme.NETWORK, GrantMode.INSTALL, R.string.perm_access_wifi_state_title, R.string.perm_access_wifi_state_allows, R.string.perm_access_wifi_state_why),
        info("com.google.android.gms.permission.AD_ID", PermissionTheme.ADS, GrantMode.INSTALL, R.string.perm_ad_id_title, R.string.perm_ad_id_allows, R.string.perm_ad_id_why),
        info("com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE", PermissionTheme.ADS, GrantMode.INSTALL, R.string.perm_bind_get_install_referrer_service_title, R.string.perm_bind_get_install_referrer_service_allows, R.string.perm_bind_get_install_referrer_service_why),
        info("RECEIVE_BOOT_COMPLETED", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_receive_boot_completed_title, R.string.perm_receive_boot_completed_allows, R.string.perm_receive_boot_completed_why),
        info("FOREGROUND_SERVICE", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_foreground_service_title, R.string.perm_foreground_service_allows, R.string.perm_foreground_service_why),
        info("POST_NOTIFICATIONS", PermissionTheme.SYSTEM, GrantMode.RUNTIME, R.string.perm_post_notifications_title, R.string.perm_post_notifications_allows, R.string.perm_post_notifications_why),
        info("WAKE_LOCK", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_wake_lock_title, R.string.perm_wake_lock_allows, R.string.perm_wake_lock_why),
        info("BLUETOOTH_CONNECT", PermissionTheme.NEARBY, GrantMode.RUNTIME, R.string.perm_bluetooth_connect_title, R.string.perm_bluetooth_connect_allows, R.string.perm_bluetooth_connect_why),
        info("NFC", PermissionTheme.NEARBY, GrantMode.INSTALL, R.string.perm_nfc_title, R.string.perm_nfc_allows, R.string.perm_nfc_why),
        info("USE_BIOMETRIC", PermissionTheme.CONTACTS, GrantMode.INSTALL, R.string.perm_use_biometric_title, R.string.perm_use_biometric_allows, R.string.perm_use_biometric_why),
        info("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", PermissionTheme.SYSTEM, GrantMode.SPECIAL, R.string.perm_request_ignore_battery_optimizations_title, R.string.perm_request_ignore_battery_optimizations_allows, R.string.perm_request_ignore_battery_optimizations_why),
        info("ACCESS_ADSERVICES_AD_ID", PermissionTheme.ADS, GrantMode.INSTALL, R.string.perm_access_adservices_ad_id_title, R.string.perm_access_adservices_ad_id_allows, R.string.perm_access_adservices_ad_id_why),
        info("ACCESS_ADSERVICES_ATTRIBUTION", PermissionTheme.ADS, GrantMode.INSTALL, R.string.perm_access_adservices_attribution_title, R.string.perm_access_adservices_attribution_allows, R.string.perm_access_adservices_attribution_why),
        info("ACCESS_ADSERVICES_TOPICS", PermissionTheme.ADS, GrantMode.INSTALL, R.string.perm_access_adservices_topics_title, R.string.perm_access_adservices_topics_allows, R.string.perm_access_adservices_topics_why),
        info("ACCESS_ADSERVICES_CUSTOM_AUDIENCE", PermissionTheme.ADS, GrantMode.INSTALL, R.string.perm_access_adservices_custom_audience_title, R.string.perm_access_adservices_custom_audience_allows, R.string.perm_access_adservices_custom_audience_why),
        info("AD_SERVICES_CONFIG", PermissionTheme.ADS, GrantMode.INSTALL, R.string.perm_ad_services_config_title, R.string.perm_ad_services_config_allows, R.string.perm_ad_services_config_why),
        info("DETECT_SCREEN_CAPTURE", PermissionTheme.SCREEN, GrantMode.INSTALL, R.string.perm_detect_screen_capture_title, R.string.perm_detect_screen_capture_allows, R.string.perm_detect_screen_capture_why),
        info("DETECT_SCREEN_RECORDING", PermissionTheme.SCREEN, GrantMode.INSTALL, R.string.perm_detect_screen_recording_title, R.string.perm_detect_screen_recording_allows, R.string.perm_detect_screen_recording_why),
        info("FOREGROUND_SERVICE_MEDIA_PROJECTION", PermissionTheme.SCREEN, GrantMode.SPECIAL, R.string.perm_foreground_service_media_projection_title, R.string.perm_foreground_service_media_projection_allows, R.string.perm_foreground_service_media_projection_why),
        info("MEDIA_PROJECTION", PermissionTheme.SCREEN, GrantMode.SPECIAL, R.string.perm_media_projection_title, R.string.perm_media_projection_allows, R.string.perm_media_projection_why),
        info("HIDE_OVERLAY_WINDOWS", PermissionTheme.SCREEN, GrantMode.INSTALL, R.string.perm_hide_overlay_windows_title, R.string.perm_hide_overlay_windows_allows, R.string.perm_hide_overlay_windows_why),
        info("READ_CLIPBOARD", PermissionTheme.SCREEN, GrantMode.SPECIAL, R.string.perm_read_clipboard_title, R.string.perm_read_clipboard_allows, R.string.perm_read_clipboard_why),
        info("READ_ASSIST_STRUCTURE_SCREEN_CONTENT", PermissionTheme.SCREEN, GrantMode.SPECIAL, R.string.perm_read_assist_structure_screen_content_title, R.string.perm_read_assist_structure_screen_content_allows, R.string.perm_read_assist_structure_screen_content_why),
        info("FOREGROUND_SERVICE_MICROPHONE", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_foreground_service_microphone_title, R.string.perm_foreground_service_microphone_allows, R.string.perm_foreground_service_microphone_why),
        info("FOREGROUND_SERVICE_CAMERA", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_foreground_service_camera_title, R.string.perm_foreground_service_camera_allows, R.string.perm_foreground_service_camera_why),
        info("FOREGROUND_SERVICE_LOCATION", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_foreground_service_location_title, R.string.perm_foreground_service_location_allows, R.string.perm_foreground_service_location_why),
        info("FOREGROUND_SERVICE_DATA_SYNC", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_foreground_service_data_sync_title, R.string.perm_foreground_service_data_sync_allows, R.string.perm_foreground_service_data_sync_why),
        info("FOREGROUND_SERVICE_MEDIA_PLAYBACK", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_foreground_service_media_playback_title, R.string.perm_foreground_service_media_playback_allows, R.string.perm_foreground_service_media_playback_why),
        info("FOREGROUND_SERVICE_PHONE_CALL", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_foreground_service_phone_call_title, R.string.perm_foreground_service_phone_call_allows, R.string.perm_foreground_service_phone_call_why),
        info("FOREGROUND_SERVICE_CONNECTED_DEVICE", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_foreground_service_connected_device_title, R.string.perm_foreground_service_connected_device_allows, R.string.perm_foreground_service_connected_device_why),
        info("FOREGROUND_SERVICE_SPECIAL_USE", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_foreground_service_special_use_title, R.string.perm_foreground_service_special_use_allows, R.string.perm_foreground_service_special_use_why),
        info("SCHEDULE_EXACT_ALARM", PermissionTheme.SYSTEM, GrantMode.SPECIAL, R.string.perm_schedule_exact_alarm_title, R.string.perm_schedule_exact_alarm_allows, R.string.perm_schedule_exact_alarm_why),
        info("USE_EXACT_ALARM", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_use_exact_alarm_title, R.string.perm_use_exact_alarm_allows, R.string.perm_use_exact_alarm_why),
        info("USE_FULL_SCREEN_INTENT", PermissionTheme.SYSTEM, GrantMode.SPECIAL, R.string.perm_use_full_screen_intent_title, R.string.perm_use_full_screen_intent_allows, R.string.perm_use_full_screen_intent_why),
        info("RUN_USER_INITIATED_JOBS", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_run_user_initiated_jobs_title, R.string.perm_run_user_initiated_jobs_allows, R.string.perm_run_user_initiated_jobs_why),
        info("com.google.android.c2dm.permission.RECEIVE", PermissionTheme.SYSTEM, GrantMode.INSTALL, R.string.perm_receive_title, R.string.perm_receive_allows, R.string.perm_receive_why),
        info("com.android.vending.BILLING", PermissionTheme.OTHER, GrantMode.INSTALL, R.string.perm_billing_title, R.string.perm_billing_allows, R.string.perm_billing_why),
        info("USE_FINGERPRINT", PermissionTheme.CONTACTS, GrantMode.INSTALL, R.string.perm_use_fingerprint_title, R.string.perm_use_fingerprint_allows, R.string.perm_use_fingerprint_why),
        info("MANAGE_ACCOUNTS", PermissionTheme.CONTACTS, GrantMode.INSTALL, R.string.perm_manage_accounts_title, R.string.perm_manage_accounts_allows, R.string.perm_manage_accounts_why),
        info("CHANGE_WIFI_STATE", PermissionTheme.NETWORK, GrantMode.INSTALL, R.string.perm_change_wifi_state_title, R.string.perm_change_wifi_state_allows, R.string.perm_change_wifi_state_why),
        info("READ_LOGS", PermissionTheme.CONTROL, GrantMode.SPECIAL, R.string.perm_read_logs_title, R.string.perm_read_logs_allows, R.string.perm_read_logs_why),
        info("WRITE_SETTINGS", PermissionTheme.CONTROL, GrantMode.SPECIAL, R.string.perm_write_settings_title, R.string.perm_write_settings_allows, R.string.perm_write_settings_why),
        info("health.*", PermissionTheme.HEALTH, GrantMode.RUNTIME, R.string.perm_health_all_title, R.string.perm_health_all_allows, R.string.perm_health_all_why),
    )

    private val byPermission = entries.associateBy { it.permission }

    /** Fiche d'une permission (nom court ou complet) ; toutes les permissions Health Connect partagent une fiche. */
    fun find(permission: String): PermissionInfo? {
        val short = permission.removePrefix("android.permission.")
        return byPermission[short] ?: if (short.startsWith("health.")) byPermission[HEALTH_CONNECT] else null
    }

    private const val HEALTH_CONNECT = "health.*"

    private fun info(
        permission: String,
        theme: PermissionTheme,
        grant: GrantMode,
        @StringRes title: Int,
        @StringRes allows: Int,
        @StringRes whySensitive: Int,
    ) = PermissionInfo(permission, title, theme, allows, whySensitive, grant)
}
