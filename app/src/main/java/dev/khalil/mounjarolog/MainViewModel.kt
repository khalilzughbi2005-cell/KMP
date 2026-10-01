package dev.khalil.mounjarolog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.khalil.mounjarolog.data.BackupCodec
import dev.khalil.mounjarolog.data.LoggerDatabase
import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.widget.HomeWidgetUpdater
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
        injectionSite: String = "",
        appetite: Int? = null,
        sideEffects: String = "",
        note: String = "",
        onDone: (Result<Unit>) -> Unit = {}
    ) {
        if (weightKg == null && doseMg == null && appetite == null && sideEffects.isBlank() && note.isBlank()) {
            onDone(Result.failure(IllegalArgumentException("Add at least one value.")))
            return
        }
        if (weightKg != null && weightKg !in 20.0..400.0) {
            onDone(Result.failure(IllegalArgumentException("Weight must be between 20 and 400 kg.")))
            return
        }
        runWrite(onDone) {
            database.addEntry(date, weightKg, doseMg, injectionSite, appetite, sideEffects, note)
        }
    }

    fun updateWeight(
        id: Long,
        date: LocalDate,
        weightKg: Double,
        note: String,
        onDone: (Result<Unit>) -> Unit = {}
    ) {
        if (weightKg !in 20.0..400.0) {
            onDone(Result.failure(IllegalArgumentException("Weight must be between 20 and 400 kg.")))
            return
        }
        runWrite(onDone) { database.updateWeight(id, date, weightKg, note) }
    }

    fun updateInjection(
        id: Long,
        date: LocalDate,
        doseMg: Double,
        injectionSite: String,
        note: String,
        onDone: (Result<Unit>) -> Unit = {}
    ) {
        runWrite(onDone) { database.updateInjection(id, date, doseMg, injectionSite, note) }
    }

    fun upsertDayLog(
        date: LocalDate,
        appetite: Int?,
        sideEffects: String,
        note: String,
        onDone: (Result<Unit>) -> Unit = {}
    ) {
        runWrite(onDone) { database.upsertDayLog(date, appetite, sideEffects, note) }
    }

    fun deleteWeight(id: Long, onDone: (Result<Unit>) -> Unit = {}) {
        runWrite(onDone) { database.deleteWeight(id) }
    }

    fun deleteInjection(id: Long, onDone: (Result<Unit>) -> Unit = {}) {
        runWrite(onDone) { database.deleteInjection(id) }
    }

    fun deleteDayLog(id: Long, onDone: (Result<Unit>) -> Unit = {}) {
        runWrite(onDone) { database.deleteDayLog(id) }
    }

    fun addMilestone(targetKg: Double, label: String = "", onDone: (Result<Unit>) -> Unit = {}) {
        if (targetKg !in 20.0..400.0) {
            onDone(Result.failure(IllegalArgumentException("Milestone must be between 20 and 400 kg.")))
            return
        }
        runWrite(onDone) { database.addMilestone(targetKg, label) }
    }

    fun updateMilestone(id: Long, targetKg: Double, label: String, onDone: (Result<Unit>) -> Unit = {}) {
        if (targetKg !in 20.0..400.0) {
            onDone(Result.failure(IllegalArgumentException("Milestone must be between 20 and 400 kg.")))
            return
        }
        runWrite(onDone) { database.updateMilestone(id, targetKg, label) }
    }

    fun deleteMilestone(id: Long, onDone: (Result<Unit>) -> Unit = {}) {
        runWrite(onDone) { database.deleteMilestone(id) }
    }

    fun restoreBackup(rawJson: String, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val restored = BackupCodec.fromJson(rawJson)
                    database.restoreData(restored)
                    database.loadAll()
                }
            }
            result.onSuccess {
                _data.value = it
                HomeWidgetUpdater.updateAll(getApplication())
            }
            _busy.value = false
            onDone(result.map { Unit })
        }
    }

    private fun runWrite(
        onDone: (Result<Unit>) -> Unit,
        block: () -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    block()
                    database.loadAll()
                }
            }
            result.onSuccess {
                _data.value = it
                HomeWidgetUpdater.updateAll(getApplication())
            }
            _busy.value = false
            onDone(result.map { Unit })
        }
    }
}
