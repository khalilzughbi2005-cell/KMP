package dev.khalil.mounjarolog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.khalil.mounjarolog.ui.MounjaroLogApp
import dev.khalil.mounjarolog.ui.MounjaroLogTheme
import dev.khalil.mounjarolog.widget.WidgetActions

class MainActivity : ComponentActivity() {
    private var widgetQuickAddMode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetQuickAddMode = intent.getStringExtra(WidgetActions.EXTRA_QUICK_ADD_MODE)
        enableEdgeToEdge()
        setContent {
            MounjaroLogTheme {
                val vm: MainViewModel = viewModel()
                MounjaroLogApp(
                    viewModel = vm,
                    quickAddMode = widgetQuickAddMode,
                    onQuickAddConsumed = { widgetQuickAddMode = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetQuickAddMode = intent.getStringExtra(WidgetActions.EXTRA_QUICK_ADD_MODE)
    }
}
