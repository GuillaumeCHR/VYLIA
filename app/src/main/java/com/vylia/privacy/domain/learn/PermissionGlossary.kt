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
}

/** Explication grand public d'une permission notée par les règles de score (textes en ressources, traduits). */
data class PermissionInfo(
    /** Nom court, sans "android.permission.", tel qu'utilisé par les règles. */
    val permission: String,
    @param:StringRes val title: Int,
    val theme: PermissionTheme,
    @param:StringRes val allows: Int,
    @param:StringRes val whySensitive: Int,
    val grant: GrantMode,
)

/** Lexique des permissions sensibles : une entrée pour chaque permission notée dans vylia_rules_v2_1.json. */
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
    )

    private val byPermission = entries.associateBy { it.permission }

    fun find(permission: String): PermissionInfo? = byPermission[permission.removePrefix("android.permission.")]

    private fun info(
        permission: String,
        theme: PermissionTheme,
        grant: GrantMode,
        @StringRes title: Int,
        @StringRes allows: Int,
        @StringRes whySensitive: Int,
    ) = PermissionInfo(permission, title, theme, allows, whySensitive, grant)
}
