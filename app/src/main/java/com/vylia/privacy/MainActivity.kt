package com.vylia.privacy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vylia.privacy.ui.home.HomeViewModel
import com.vylia.privacy.ui.main.MainScreen
import com.vylia.privacy.ui.splash.VyliaSplashScreen
import com.vylia.privacy.ui.theme.VyliaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VyliaTheme {
                // Instancié dès le splash : le scan démarre pendant l'animation. Même instance que l'écran Scan.
                viewModel<HomeViewModel>(factory = HomeViewModel.Factory)
                var showSplash by rememberSaveable { mutableStateOf(true) }
                Crossfade(targetState = showSplash, animationSpec = tween(500), label = "splash") { splash ->
                    if (splash) VyliaSplashScreen(onFinished = { showSplash = false }) else MainScreen()
                }
            }
        }
    }
}
