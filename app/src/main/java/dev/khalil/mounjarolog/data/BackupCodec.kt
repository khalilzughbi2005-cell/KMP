package dev.khalil.mounjarolog.data

import dev.khalil.mounjarolog.model.AppData
import dev.khalil.mounjarolog.model.DayLog
import dev.khalil.mounjarolog.model.Injection
import dev.khalil.mounjarolog.model.Milestone
import dev.khalil.mounjarolog.model.WeightMeasurement
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object BackupCodec {
    const val SCHEMA_VERSION = 1

    fun toJson(data: AppData): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
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
        return root.toString(2)
    }

    fun fromJson(raw: String): AppData {
        val root = JSONObject(raw)
        val version = root.optInt("schemaVersion", 1)
        require(version in 1..SCHEMA_VERSION) { "Unsupported backup version: $version" }

        val weights = root.optJSONArray("weights").toList { obj ->
            WeightMeasurement(
                id = obj.optLong("id", 0L),
                date = LocalDate.parse(obj.getString("date")),
                weightKg = obj.getDouble("weightKg"),
                note = obj.optString("note")
            )
        }
        val injections = root.optJSONArray("injections").toList { obj ->
            Injection(
                id = obj.optLong("id", 0L),
                date = LocalDate.parse(obj.getString("date")),
                doseMg = obj.getDouble("doseMg"),
                injectionSite = obj.optString("injectionSite"),
                note = obj.optString("note")
            )
        }
        val dayLogs = root.optJSONArray("dayLogs").toList { obj ->
            DayLog(
                id = obj.optLong("id", 0L),
                date = LocalDate.parse(obj.getString("date")),
                appetite = if (obj.has("appetite") && !obj.isNull("appetite")) obj.getInt("appetite") else null,
                sideEffects = obj.optString("sideEffects"),
                note = obj.optString("note")
            )
        }
        val milestones = root.optJSONArray("milestones").toList { obj ->
            Milestone(
                id = obj.optLong("id", 0L),
                targetKg = obj.getDouble("targetKg"),
                label = obj.optString("label")
            )
        }
        return AppData(
            weights = weights.sortedBy { it.date },
            injections = injections.sortedBy { it.date },
            dayLogs = dayLogs.sortedBy { it.date },
            milestones = milestones.sortedByDescending { it.targetKg }
        )
    }

    fun toCsv(data: AppData): String {
        val lines = mutableListOf("type,date,weight_kg,dose_mg,injection_site,appetite,side_effects,note")
        data.weights.forEach {
            lines += listOf(
                "weight", it.date, it.weightKg, "", "", "", "", it.note
            ).joinToString(",") { value -> csv(value.toString()) }
        }
        data.injections.forEach {
            lines += listOf(
                "injection", it.date, "", it.doseMg, it.injectionSite, "", "", it.note
            ).joinToString(",") { value -> csv(value.toString()) }
        }
        data.dayLogs.forEach {
            lines += listOf(
                "day_log", it.date, "", "", "", it.appetite ?: "", it.sideEffects, it.note
            ).joinToString(",") { value -> csv(value.toString()) }
        }
        return lines.joinToString("\n")
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\\"" + value.replace("\\"", "\\"\\\"") + "\\""
        } else value

    private fun <T> JSONArray?.toList(block: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) add(block(getJSONObject(i)))
        }
    }
}
