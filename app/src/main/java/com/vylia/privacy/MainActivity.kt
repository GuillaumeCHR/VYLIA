package com.vylia.privacy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vylia.privacy.ui.main.MainScreen
import com.vylia.privacy.ui.theme.VyliaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VyliaTheme {
                MainScreen()
            }
        }
    }
}
