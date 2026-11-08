package com.vylia.privacy.domain.usecase

import android.graphics.drawable.Drawable
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.Tracker
import com.vylia.privacy.domain.model.TrackerAnalysis
import com.vylia.privacy.domain.scoring.PrivacyScorer
import com.vylia.privacy.domain.scoring.ReferenceApps
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les apps de référence de :scoring, notées par le chemin de l'app (InstalledApp → scoreApp) : même note que dans
 * :scoring et que dans l'outil de l'annuaire.
 */
class ReferenceScoresAppTest {

    private val icon = object : Drawable() {
        override fun draw(canvas: android.graphics.Canvas) = Unit
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = 0
    }

    @Test
    fun `memes notes que le moteur et l annuaire`() {
        val scorer = PrivacyScorer(ReferenceApps.rules)
        ReferenceApps.apps.forEach { ref ->
            val permissions = ref.permissions.map(ReferenceApps::shortName).toSet()
            val app = InstalledApp(
                label = ref.packageName,
                packageName = ref.packageName,
                icon = icon,
                isSystemApp = false,
                category = null,
                apkPaths = emptyList(),
                lastUpdateTime = 0L,
                targetSdk = ref.targetSdk,
                appInfoCategory = "CATEGORY_UNDEFINED",
                declaredPermissions = permissions,
                grantedPermissions = permissions,
                signals = emptySet(),
                requestedPermissions = emptyList(),
                permissions = emptyList(),
            )
            val analysis = TrackerAnalysis.Analyzed(ref.trackers.mapIndexed { i, t -> Tracker(i, t.name, null, t.categories) })
            val result = scorer.scoreApp(app, analysis).result
            assertEquals(ref.packageName, ref.expected, ReferenceApps.summary(result))
        }
    }
}
