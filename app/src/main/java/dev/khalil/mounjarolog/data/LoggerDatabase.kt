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
import org.json.JSONArray
import org.json.JSONObject
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
        // Future schema changes go here as additive migrations. Never drop user data.
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
        db.query("day_logs", null, null, null, null, null, "date ASC").use { c ->
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
        db.query("milestones", null, null, null, null, null, "target_kg DESC").use { c ->
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

    fun addEntry(date: LocalDate, weightKg: Double?, doseMg: Double?, note: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            weightKg?.let {
                db.insertOrThrow("weights", null, ContentValues().apply {
                    put("date", date.toString())
                    put("weight_kg", it)
                    put("note", note)
                })
            }
            doseMg?.let {
                db.insertOrThrow("injections", null, ContentValues().apply {
                    put("date", date.toString())
                    put("dose_mg", it)
                    put("note", note)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
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

    fun addMilestone(targetKg: Double, label: String = "") {
        writableDatabase.insertOrThrow("milestones", null, ContentValues().apply {
            put("target_kg", targetKg)
            put("label", label)
        })
        writeSnapshot()
    }

    private fun writeSnapshot() {
        val data = loadAll()
        val root = JSONObject()
        root.put("schemaVersion", DB_VERSION)
        root.put("weights", JSONArray().apply {
            data.weights.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("date", it.date.toString())
                    put("weightKg", it.weightKg)
                    put("note", it.note)
                })
            }
        })
        root.put("injections", JSONArray().apply {
            data.injections.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("date", it.date.toString())
                    put("doseMg", it.doseMg)
                    put("injectionSite", it.injectionSite)
                    put("note", it.note)
                })
            }
        })
        root.put("dayLogs", JSONArray().apply {
            data.dayLogs.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("date", it.date.toString())
                    if (it.appetite != null) put("appetite", it.appetite)
                    put("sideEffects", it.sideEffects)
                    put("note", it.note)
                })
            }
        })
        root.put("milestones", JSONArray().apply {
            data.milestones.forEach {
                put(JSONObject().apply {
                    put("id", it.id)
                    put("targetKg", it.targetKg)
                    put("label", it.label)
                })
            }
        })

        val dir = File(context.filesDir, "snapshots").apply { mkdirs() }
        val next = (dir.listFiles()?.mapNotNull {
            it.nameWithoutExtension.removePrefix("snapshot_").toIntOrNull()
        }?.maxOrNull() ?: 0) + 1
        File(dir, "snapshot_${next}.json").writeText(root.toString(2))

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
