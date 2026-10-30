package com.vylia.privacy.domain.scoring

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CategoryRule(
    val category: String,
    @SerialName("allowed_permissions") val allowedPermissions: List<String> = emptyList(),
    @SerialName("disallowed_permissions") val disallowedPermissions: List<String> = emptyList(),
    val penalty: Int = 15
)

@Serializable
data class RuleMatrix(
    val rules: List<CategoryRule>
) {
    companion object {
        fun fromJson(jsonString: String): RuleMatrix {
            val json = Json { ignoreUnknownKeys = true }
            return json.decodeFromString(jsonString)
        }
    }
}
