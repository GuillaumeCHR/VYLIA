package com.vylia.privacy.ui.scan.components

import androidx.annotation.StringRes
import com.vylia.privacy.R
import com.vylia.privacy.domain.model.ScoreRating

@get:StringRes
internal val ScoreRating.labelRes: Int
    get() = when (this) {
        ScoreRating.GOOD -> R.string.risk_low
        ScoreRating.FAIR -> R.string.risk_medium
        ScoreRating.POOR -> R.string.risk_high
    }
