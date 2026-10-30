package com.vylia.privacy.ui.home

import android.Manifest
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import java.text.Normalizer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.R
import com.vylia.privacy.domain.model.AppFilter
import com.vylia.privacy.domain.model.AuditedApp
import com.vylia.privacy.domain.model.DetectedPermission
import com.vylia.privacy.domain.model.InstalledApp
import com.vylia.privacy.domain.model.PrivacyScore
import com.vylia.privacy.domain.scoring.PermissionRegistry
import com.vylia.privacy.domain.scoring.Confidence
import com.vylia.privacy.domain.scoring.ScoreResult
import com.vylia.privacy.ui.components.TransparencyBadge
import com.vylia.privacy.ui.home.components.AppCard
import com.vylia.privacy.ui.theme.VyliaTheme

private val Tabs = listOf(AppFilter.USER, AppFilter.SYSTEM)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onAppClick: (AuditedApp) -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    HomeContent(
        modifier = modifier,
        state = state,
        query = query,
        onQueryChange = { query = it },
        onFilterSelected = viewModel::selectFilter,
        onRefresh = viewModel::refresh,
        onAppClick = onAppClick,
    )
}

/** Version sans ViewModel, pour les previews et les tests d'UI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    state: HomeUiState,
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onFilterSelected: (AppFilter) -> Unit,
    onRefresh: () -> Unit,
    onAppClick: (AuditedApp) -> Unit = {},
) {
    Scaffold(
        modifier = Modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.home_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(top = innerPadding.calculateTopPadding())) {
            AppSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            TransparencyBadge(
                text = stringResource(if (state.advancedMode) R.string.mode_badge_advanced else R.string.mode_badge_basic),
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // Mode Basique : apps utilisateur uniquement, pas d'onglet Système.
            if (state.advancedMode) {
                PrimaryTabRow(selectedTabIndex = Tabs.indexOf(state.selectedFilter)) {
                    Tabs.forEach { filter ->
                        Tab(
                            selected = filter == state.selectedFilter,
                            onClick = { onFilterSelected(filter) },
                            text = {
                                Text(
                                    text = when (filter) {
                                        AppFilter.SYSTEM -> stringResource(R.string.tab_system_apps, state.systemAppCount)
                                        else -> stringResource(R.string.tab_user_apps, state.userAppCount)
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            },
                        )
                    }
                }
            }

            val bottomPadding = innerPadding.calculateBottomPadding()
            val results = remember(state.apps, query) { state.apps.filter { it.matches(query) } }
            when {
                state.isLoading -> CenteredMessage(stringResource(R.string.home_scanning), showProgress = true)
                state.errorMessage != null -> CenteredMessage(
                    message = stringResource(R.string.home_error, state.errorMessage),
                    actionLabel = stringResource(R.string.home_retry),
                    onAction = onRefresh,
                )
                state.apps.isEmpty() -> CenteredMessage(stringResource(R.string.home_empty))
                results.isEmpty() -> CenteredMessage(stringResource(R.string.home_search_empty, query.trim()))
                else -> AppList(results, onAppClick, PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = bottomPadding + 16.dp))
            }
        }
    }
}

/** Recherche par nom ou par paquet, sans tenir compte de la casse ni des accents. */
@Composable
private fun AppSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.home_search_clear))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

private fun AuditedApp.matches(query: String): Boolean {
    val needle = query.trim().normalizedForSearch()
    return needle.isEmpty() || needle in app.label.normalizedForSearch() || needle in app.packageName.lowercase()
}

private fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(DiacriticalMarks, "").lowercase()

private val DiacriticalMarks = Regex("\\p{Mn}+")

@Composable
private fun AppList(apps: List<AuditedApp>, onAppClick: (AuditedApp) -> Unit, contentPadding: PaddingValues) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(apps, key = { it.app.packageName }) { AppCard(it, onClick = { onAppClick(it) }) }
    }
}

@Composable
private fun CenteredMessage(
    message: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showProgress) CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    fun fakeApp(label: String, color: Int, score: Int, vararg permissions: Pair<String, Boolean>): AuditedApp {
        val app = InstalledApp(
            label = label,
            packageName = "com.example.${label.lowercase()}",
            icon = ColorDrawable(color),
            isSystemApp = false,
            category = null,
            apkPaths = emptyList(),
            lastUpdateTime = 0L,
            requestedPermissions = permissions.map { it.first },
            permissions = PermissionRegistry.strongestByFamily(permissions.map { it.first }).map { risk ->
                DetectedPermission(risk, isGranted = permissions.any { (name, granted) -> granted && name == risk.permission })
            },
        )
        val result = ScoreResult(score, "?", Confidence.PARTIAL, "DEFAULT", 0.0, 0, emptyList(), emptyList())
        return AuditedApp(app, PrivacyScore(score), result = result)
    }
    val apps = listOf(
        fakeApp(
            "Messagerie", AndroidColor.BLUE, 41,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION to true,
            Manifest.permission.READ_SMS to false,
            Manifest.permission.READ_CONTACTS to true,
        ),
        fakeApp("Photo", AndroidColor.MAGENTA, 74, Manifest.permission.CAMERA to true, Manifest.permission.ACCESS_FINE_LOCATION to false),
        fakeApp("Calculatrice", AndroidColor.GRAY, 88),
    ).sortedBy { it.score.value }

    VyliaTheme(dynamicColor = false) {
        HomeContent(
        modifier = Modifier,
            state = HomeUiState(
                isLoading = false,
                advancedMode = true,
                apps = apps,
                averageScore = PrivacyScore.average(apps.map { it.score }),
                userAppCount = apps.size,
                systemAppCount = 42,
            ),
            onFilterSelected = {},
            onRefresh = {},
        )
    }
}
