package com.vylia.privacy.data.rewards

import android.content.Context
import com.vylia.privacy.domain.rewards.RewardsState
import com.vylia.privacy.domain.rewards.RewardsStore
import kotlinx.serialization.json.Json

/** Récompenses stockées en JSON dans des SharedPreferences privées (jamais sauvegardées ni partagées). */
class PrefsRewardsStore(context: Context) : RewardsStore {
    private val prefs = context.getSharedPreferences("vylia_rewards", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): RewardsState? =
        prefs.getString(KEY_STATE, null)?.let { runCatching { json.decodeFromString<RewardsState>(it) }.getOrNull() }

    override fun save(state: RewardsState) {
        prefs.edit().putString(KEY_STATE, json.encodeToString(state)).apply()
    }

    private companion object {
        const val KEY_STATE = "rewards_state"
    }
}
