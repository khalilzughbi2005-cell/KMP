package dev.khalil.mounjarolog.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.model.DayLog
import dev.khalil.mounjarolog.model.Injection
import dev.khalil.mounjarolog.model.Milestone
import dev.khalil.mounjarolog.model.WeightMeasurement
import java.io.File
import java.time.LocalDate

class LoggerDatabase(private val context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE weights(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                weight_kg REAL NOT NULL,
                note TEXT NOT NULL DEFAULT ''
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE injections(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                dose_mg REAL NOT NULL,
                injection_site TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT ''
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE day_logs(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL UNIQUE,
                appetite INTEGER,
                side_effects TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT ''
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE milestones(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                target_kg REAL NOT NULL,
                label TEXT NOT NULL DEFAULT ''
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX idx_weights_date ON weights(date)")
        db.execSQL("CREATE INDEX idx_injections_date ON injections(date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add future schema changes as migrations. Never drop the user's tables here.
    }

    fun loadAll(): AppData = readableDatabase.let { db ->
        val weights = mutableListOf<WeightMeasurement>()
        db.query("weights", null, null, null, null, null, "date ASC, id ASC").use { c ->
            while (c.moveToNext()) {
                weights += WeightMeasurement(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    date = LocalDate.parse(c.getString(c.getColumnIndexOrThrow("date"))),
                    weightKg = c.getDouble(c.getColumnIndexOrThrow("weight_kg")),
                    note = c.getString(c.getColumnIndexOrThrow("note"))
                )
            }
        }

        val injections = mutableListOf<Injection>()
        db.query("injections", null, null, null, null, null, "date ASC, id ASC").use { c ->
            while (c.moveToNext()) {
                injections += Injection(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    date = LocalDate.parse(c.getString(c.getColumnIndexOrThrow("date"))),
                    doseMg = c.getDouble(c.getColumnIndexOrThrow("dose_mg")),
                    injectionSite = c.getString(c.getColumnIndexOrThrow("injection_site")),
                    note = c.getString(c.getColumnIndexOrThrow("note"))
                )
            }
        }

        val dayLogs = mutableListOf<DayLog>()
        db.query("day_logs", null, null, null, null, null, "date ASC, id ASC").use { c ->
            while (c.moveToNext()) {
                val appetiteIndex = c.getColumnIndexOrThrow("appetite")
                dayLogs += DayLog(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    date = LocalDate.parse(c.getString(c.getColumnIndexOrThrow("date"))),
                    appetite = if (c.isNull(appetiteIndex)) null else c.getInt(appetiteIndex),
                    sideEffects = c.getString(c.getColumnIndexOrThrow("side_effects")),
                    note = c.getString(c.getColumnIndexOrThrow("note"))
                )
            }
        }

        val milestones = mutableListOf<Milestone>()
        db.query("milestones", null, null, null, null, null, "target_kg DESC, id ASC").use { c ->
            while (c.moveToNext()) {
                milestones += Milestone(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    targetKg = c.getDouble(c.getColumnIndexOrThrow("target_kg")),
                    label = c.getString(c.getColumnIndexOrThrow("label"))
                )
            }
        }

        AppData(weights, injections, dayLogs, milestones)
    }

    fun addEntry(
        date: LocalDate,
        weightKg: Double?,
        doseMg: Double?,
        injectionSite: String,
        appetite: Int?,
        sideEffects: String,
        note: String
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            weightKg?.let {
                db.insertOrThrow("weights", null, weightValues(date, it, note))
            }
            doseMg?.let {
                db.insertOrThrow("injections", null, injectionValues(date, it, injectionSite, note))
            }
            if (appetite != null || sideEffects.isNotBlank()) {
                upsertDayLogInTransaction(db, date, appetite, sideEffects, note)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        writeSnapshot()
    }

    fun updateWeight(id: Long, date: LocalDate, weightKg: Double, note: String) {
        require(id > 0)
        writableDatabase.update(
            "weights",
            weightValues(date, weightKg, note),
            "id=?",
            arrayOf(id.toString())
        )
        writeSnapshot()
    }

    fun updateInjection(
        id: Long,
        date: LocalDate,
        doseMg: Double,
        injectionSite: String,
        note: String
    ) {
        require(id > 0)
        writableDatabase.update(
            "injections",
            injectionValues(date, doseMg, injectionSite, note),
            "id=?",
            arrayOf(id.toString())
        )
        writeSnapshot()
    }

    fun upsertDayLog(
        date: LocalDate,
        appetite: Int?,
        sideEffects: String,
        note: String
    ) {
        val db = writableDatabase
        if (appetite == null && sideEffects.isBlank() && note.isBlank()) {
            db.delete("day_logs", "date=?", arrayOf(date.toString()))
        } else {
            upsertDayLogInTransaction(db, date, appetite, sideEffects, note)
        }
        writeSnapshot()
    }

    fun deleteWeight(id: Long) {
        writableDatabase.delete("weights", "id=?", arrayOf(id.toString()))
        writeSnapshot()
    }

    fun deleteInjection(id: Long) {
        writableDatabase.delete("injections", "id=?", arrayOf(id.toString()))
        writeSnapshot()
    }

    fun deleteDayLog(id: Long) {
        writableDatabase.delete("day_logs", "id=?", arrayOf(id.toString()))
        writeSnapshot()
    }

    fun addMilestone(targetKg: Double, label: String = "") {
        writableDatabase.insertOrThrow("milestones", null, milestoneValues(targetKg, label))
        writeSnapshot()
    }

    fun updateMilestone(id: Long, targetKg: Double, label: String) {
        writableDatabase.update(
            "milestones",
            milestoneValues(targetKg, label),
            "id=?",
            arrayOf(id.toString())
        )
        writeSnapshot()
    }

    fun deleteMilestone(id: Long) {
        writableDatabase.delete("milestones", "id=?", arrayOf(id.toString()))
        writeSnapshot()
    }

    fun restoreData(data: AppData) {
        // Preserve a recoverable copy of the current state before replacing anything.
        writeSnapshot()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("weights", null, null)
            db.delete("injections", null, null)
            db.delete("day_logs", null, null)
            db.delete("milestones", null, null)

            data.weights.forEach {
                db.insertOrThrow("weights", null, weightValues(it.date, it.weightKg, it.note).apply {
                    if (it.id > 0) put("id", it.id)
                })
            }
            data.injections.forEach {
                db.insertOrThrow("injections", null, injectionValues(it.date, it.doseMg, it.injectionSite, it.note).apply {
                    if (it.id > 0) put("id", it.id)
                })
            }
            data.dayLogs.forEach {
                db.insertOrThrow("day_logs", null, dayLogValues(it.date, it.appetite, it.sideEffects, it.note).apply {
                    if (it.id > 0) put("id", it.id)
                })
            }
            data.milestones.forEach {
                db.insertOrThrow("milestones", null, milestoneValues(it.targetKg, it.label).apply {
                    if (it.id > 0) put("id", it.id)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        writeSnapshot()
    }

    private fun upsertDayLogInTransaction(
        db: SQLiteDatabase,
        date: LocalDate,
        appetite: Int?,
        sideEffects: String,
        note: String
    ) {
        val values = dayLogValues(date, appetite, sideEffects, note)
        val updated = db.update("day_logs", values, "date=?", arrayOf(date.toString()))
        if (updated == 0) db.insertOrThrow("day_logs", null, values)
    }

    private fun weightValues(date: LocalDate, weightKg: Double, note: String) =
        ContentValues().apply {
            put("date", date.toString())
            put("weight_kg", weightKg)
            put("note", note.trim())
        }

    private fun injectionValues(
        date: LocalDate,
        doseMg: Double,
        injectionSite: String,
        note: String
    ) = ContentValues().apply {
        put("date", date.toString())
        put("dose_mg", doseMg)
        put("injection_site", injectionSite.trim())
        put("note", note.trim())
    }

    private fun dayLogValues(
        date: LocalDate,
        appetite: Int?,
        sideEffects: String,
        note: String
    ) = ContentValues().apply {
        put("date", date.toString())
        if (appetite == null) putNull("appetite") else put("appetite", appetite.coerceIn(1, 5))
        put("side_effects", sideEffects.trim())
        put("note", note.trim())
    }

    private fun milestoneValues(targetKg: Double, label: String) =
        ContentValues().apply {
            put("target_kg", targetKg)
            put("label", label.trim())
        }

    private fun writeSnapshot() {
        val dir = File(context.filesDir, "snapshots").apply { mkdirs() }
        val next = (dir.listFiles()?.mapNotNull {
            it.nameWithoutExtension.removePrefix("snapshot_").toIntOrNull()
        }?.maxOrNull() ?: 0) + 1
        File(dir, "snapshot_\${next}.json").writeText(BackupCodec.toJson(loadAll()))

        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_SNAPSHOTS)
            ?.forEach { it.delete() }
    }

    companion object {
        private const val DB_NAME = "mounjaro_log.db"
        private const val DB_VERSION = 1
        private const val MAX_SNAPSHOTS = 5
    }
}
