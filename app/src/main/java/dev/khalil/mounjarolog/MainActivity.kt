package dev.khalil.mounjarolog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.khalil.mounjarolog.ui.MounjaroLogApp
import dev.khalil.mounjarolog.ui.MounjaroLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MounjaroLogTheme {
                val vm: MainViewModel = viewModel()
                MounjaroLogApp(vm)
            }
        }
    }
}
