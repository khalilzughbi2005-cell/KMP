package dev.khalil.mounjarolog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.khalil.mounjarolog.data.LoggerDatabase
import dev.khalil.mounjarolog.model.AppData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = LoggerDatabase(application)

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _data.value = withContext(Dispatchers.IO) { database.loadAll() }
        }
    }

    fun addEntry(
        date: LocalDate,
        weightKg: Double?,
        doseMg: Double?,
        note: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        if (weightKg == null && doseMg == null) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    database.addEntry(date, weightKg, doseMg, note.trim())
                    database.loadAll()
                }
            }.onSuccess { _data.value = it }.isSuccess
            _busy.value = false
            onDone(ok)
        }
    }

    fun deleteWeight(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { database.deleteWeight(id) }
            refresh()
        }
    }

    fun deleteInjection(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { database.deleteInjection(id) }
            refresh()
        }
    }

    fun addMilestone(targetKg: Double, label: String = "") {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { database.addMilestone(targetKg, label) }
            refresh()
        }
    }
}
