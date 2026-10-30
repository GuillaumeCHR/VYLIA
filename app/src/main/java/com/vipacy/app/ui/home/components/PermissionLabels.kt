package com.vipacy.app.ui.home.components

import androidx.annotation.StringRes
import com.vipacy.app.R
import com.vipacy.app.domain.model.ScoreRating

@get:StringRes
internal val ScoreRating.labelRes: Int
    get() = when (this) {
        ScoreRating.GOOD -> R.string.risk_low
        ScoreRating.FAIR -> R.string.risk_medium
        ScoreRating.POOR -> R.string.risk_high
    }
