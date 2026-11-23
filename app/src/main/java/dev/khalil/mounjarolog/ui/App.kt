package dev.khalil.mounjarolog.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khalil.mounjarolog.MainViewModel
import dev.khalil.mounjarolog.widget.WidgetActions
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Heute", Icons.Default.Home),
    HISTORY("Verlauf", Icons.AutoMirrored.Filled.List),
    INSIGHTS("Auswertung", Icons.Default.ShowChart),
    MORE("Mehr", Icons.Default.MoreHoriz)
}

@Composable
fun MounjaroLogApp(
    viewModel: MainViewModel,
    quickAddMode: String? = null,
    onQuickAddConsumed: () -> Unit = {}
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var entry by remember { mutableStateOf<EntryRequest?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(quickAddMode) {
        if (quickAddMode != null) {
            entry = when (quickAddMode) {
                WidgetActions.MODE_DOSE -> EntryRequest(LocalDate.now(), EntryTab.DOSE)
                WidgetActions.MODE_BOTH -> EntryRequest(LocalDate.now(), EntryTab.WEIGHT, withDose = true)
                else -> EntryRequest(LocalDate.now(), EntryTab.WEIGHT)
            }
            onQuickAddConsumed()
        }
    }

    fun notify(result: Result<Unit>, success: String) {
        scope.launch {
            snackbar.showSnackbar(result.fold(onSuccess = { success }, onFailure = { it.message ?: "Etwas ist schiefgelaufen." }))
        }
    }

    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Paper) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Paper,
                            indicatorColor = Ink,
                            selectedTextColor = Ink,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AppTab.HOME -> HomeScreen(data) { entry = EntryRequest(LocalDate.now(), it) }
                AppTab.HISTORY -> HistoryScreen(data, viewModel, ::notify) { entry = EntryRequest(it, EntryTab.WEIGHT) }
                AppTab.INSIGHTS -> InsightsScreen(data)
                AppTab.MORE -> MoreScreen(data, viewModel, ::notify)
            }
        }
    }

    entry?.let { request ->
        EntrySheet(request, data, busy, viewModel, ::notify) { entry = null }
    }
}
