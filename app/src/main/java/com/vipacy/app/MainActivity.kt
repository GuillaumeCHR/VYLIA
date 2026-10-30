package com.vipacy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vipacy.app.ui.main.MainScreen
import com.vipacy.app.ui.theme.VipacyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VipacyTheme {
                MainScreen()
            }
        }
    }
}
