package io.github.szlynn.scanmyweight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.szlynn.scanmyweight.data.SettingsRepository
import io.github.szlynn.scanmyweight.ui.ScanMyWeightNavHost
import io.github.szlynn.scanmyweight.ui.theme.ScanMyWeightTheme

class MainActivity : ComponentActivity() {

    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanMyWeightTheme {
                ScanMyWeightNavHost(settingsRepository = settingsRepository)
            }
        }
    }
}
