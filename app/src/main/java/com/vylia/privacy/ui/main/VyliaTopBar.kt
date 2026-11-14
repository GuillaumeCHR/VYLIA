package com.vylia.privacy.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vylia.privacy.BuildConfig
import com.vylia.privacy.R
import com.vylia.privacy.ui.components.SearchField
import com.vylia.privacy.ui.components.VyliaLogotype
import com.vylia.privacy.ui.act.CleanupLauncher
import com.vylia.privacy.ui.shield.ShieldControl

/**
 * Hauteur de la barre : l'intro fait glisser le logotype à 16 dp du bord, centré sur cette hauteur
 * ([com.vylia.privacy.ui.splash.VyliaSplashScreen]).
 */
private val BarHeight = 64.dp

/** Icônes de la barre principale, un peu plus serrées que les 48 dp habituels. */
private val CompactIconSize = 42.dp

@Composable
private fun BarSurface(content: @Composable RowScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(BarHeight),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun BarIcon(@DrawableRes icon: Int, @StringRes description: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(icon), contentDescription = stringResource(description))
    }
}

/**
 * Barre des cinq écrans principaux : logotype, pastille du bouclier collée à sa droite, puis recherche, aide et
 * paramètres. La pastille occupe la place restante et passe en icône seule si elle ne tient pas.
 */
@Composable
fun MainTopBar(onSearch: () -> Unit, onHelp: () -> Unit, onSettings: () -> Unit, cleanup: CleanupLauncher? = null) {
    BarSurface {
        VyliaLogotype(modifier = Modifier.padding(start = 16.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) { if (BuildConfig.SHIELD_ENABLED) ShieldControl() }
        Spacer(Modifier.width(8.dp))
        // Quatre icônes serrées (42 dp au lieu de 48) : la pastille du bouclier garde sa place.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides CompactIconSize) {
            cleanup?.let { CleanupIcon(it) }
            BarIcon(R.drawable.ic_sym_search, R.string.home_search_hint, onSearch, Modifier.size(CompactIconSize))
            BarIcon(R.drawable.ic_sym_help, R.string.topbar_help, onHelp, Modifier.size(CompactIconSize))
            BarIcon(R.drawable.ic_sym_settings, R.string.settings_title, onSettings, Modifier.size(CompactIconSize))
        }
        Spacer(Modifier.width(4.dp))
    }
}

/** Le ménage guidé : un point quand il est proposé (jamais fait, ou score du téléphone en baisse). */
@Composable
private fun CleanupIcon(cleanup: CleanupLauncher) {
    IconButton(onClick = cleanup.start, enabled = cleanup.ready, modifier = Modifier.size(CompactIconSize)) {
        BadgedBox(badge = { if (cleanup.due) Badge() }) {
            Icon(painterResource(R.drawable.ic_sym_sparkles), contentDescription = stringResource(R.string.cleanup_title))
        }
    }
}

/** Barre d'un écran secondaire (fiche d'une app, aide, paramètres) : retour, titre, et la même pastille du bouclier. */
@Composable
fun BackTopBar(@StringRes title: Int, onBack: () -> Unit) {
    BarSurface {
        Spacer(Modifier.width(4.dp))
        BarIcon(R.drawable.ic_sym_arrow_back, R.string.back, onBack)
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp).semantics { heading() },
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { if (BuildConfig.SHIELD_ENABLED) ShieldControl() }
        Spacer(Modifier.width(8.dp))
    }
}

/** Barre de la recherche globale : retour, champ de saisie, effacer. */
@Composable
fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onBack: () -> Unit) {
    BarSurface {
        Spacer(Modifier.width(4.dp))
        BarIcon(R.drawable.ic_sym_arrow_back, R.string.back, onBack)
        Box(Modifier.weight(1f)) { SearchField(query, onQueryChange, stringResource(R.string.home_search_hint)) }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(painterResource(R.drawable.ic_sym_close), contentDescription = stringResource(R.string.home_search_clear))
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}
