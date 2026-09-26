package com.alexandertm.turnosvigilancia.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class ShiftType(val label: String, val code: String) {
    DAY("Día", "D"),
    NIGHT("Noche", "N")
}

enum class Situation(val label: String, val code: String) {
    REST("Descanso", "DES"),
    VACATION("Vacaciones", "VAC"),
    PERMISSION("Permiso", "PER"),
    ABSENCE("Falta", "FAL"),
    REPLACEMENT("Reemplazo", "REP")
}

data class Guard(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val document: String = "",
    val phone: String = "",
    val active: Boolean = true,
    val notes: String = "",
    val isAdmin: Boolean = false
)

data class Assignment(
    val id: String = UUID.randomUUID().toString(),
    val guardId: String,
    val date: String,
    val primaryShift: ShiftType? = null,
    val secondShift: ShiftType? = null,
    val situation: Situation? = null,
    val replacementForGuardId: String? = null,
    val replacementReason: String = "",
    val note: String = "",
    val autoRestGroupId: String? = null,
    val generatedRest: Boolean = false,
    val manualOverride: Boolean = false
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        primaryShift?.let { parts += it.code }
        secondShift?.let { parts += it.code }
        situation?.let { parts += it.code }
        return parts.joinToString(" + ").ifBlank { "—" }
    }
}

data class Holiday(
    val date: String,
    val name: String
)

data class AppSettings(
    val restDays: Int = 10,
    val confirmImportantChanges: Boolean = true,
    val showAlerts: Boolean = true,
    val colors: Map<String, String> = defaultColors()
) {
    companion object {
        fun defaultColors() = mapOf(
            "DAY" to "#2F80ED",
            "NIGHT" to "#7C3AED",
            "REST" to "#9AA5B1",
            "VACATION" to "#16A66A",
            "PERMISSION" to "#F59E0B",
            "ABSENCE" to "#EF4444",
            "REPLACEMENT" to "#EC4899"
        )
    }
}

data class AppSnapshot(
    val guards: List<Guard> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val holidays: List<Holiday> = emptyList(),
    val settings: AppSettings = AppSettings()
)

class AppRepository(context: Context) {
    private val prefs = context.getSharedPreferences("turnos_vigilancia", Context.MODE_PRIVATE)

    fun load(): AppSnapshot {
        val raw = prefs.getString(KEY_DATA, null) ?: return AppSnapshot()
        return runCatching { fromJson(JSONObject(raw)) }.getOrElse { AppSnapshot() }
    }

    fun save(snapshot: AppSnapshot) {
        prefs.edit().putString(KEY_DATA, toJson(snapshot).toString()).apply()
    }

    fun exportJson(snapshot: AppSnapshot): String = toJson(snapshot).toString(2)

    fun importJson(raw: String): AppSnapshot = fromJson(JSONObject(raw))

    private fun toJson(snapshot: AppSnapshot): JSONObject = JSONObject().apply {
        put("version", 1)
        put("guards", JSONArray().apply {
            snapshot.guards.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id); put("name", g.name); put("document", g.document)
                    put("phone", g.phone); put("active", g.active); put("notes", g.notes)
                    put("isAdmin", g.isAdmin)
                })
            }
        })
        put("assignments", JSONArray().apply {
            snapshot.assignments.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id); put("guardId", a.guardId); put("date", a.date)
                    put("primaryShift", a.primaryShift?.name ?: JSONObject.NULL)
                    put("secondShift", a.secondShift?.name ?: JSONObject.NULL)
                    put("situation", a.situation?.name ?: JSONObject.NULL)
                    put("replacementForGuardId", a.replacementForGuardId ?: JSONObject.NULL)
                    put("replacementReason", a.replacementReason); put("note", a.note)
                    put("autoRestGroupId", a.autoRestGroupId ?: JSONObject.NULL)
                    put("generatedRest", a.generatedRest); put("manualOverride", a.manualOverride)
                })
            }
        })
        put("holidays", JSONArray().apply {
            snapshot.holidays.forEach { h ->
                put(JSONObject().apply { put("date", h.date); put("name", h.name) })
            }
        })
        put("settings", JSONObject().apply {
            put("restDays", snapshot.settings.restDays)
            put("confirmImportantChanges", snapshot.settings.confirmImportantChanges)
            put("showAlerts", snapshot.settings.showAlerts)
            put("colors", JSONObject(snapshot.settings.colors))
        })
    }

    private fun fromJson(root: JSONObject): AppSnapshot {
        val guards = root.optJSONArray("guards").orEmptyObjects().map { o ->
            Guard(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name"),
                document = o.optString("document"),
                phone = o.optString("phone"),
                active = o.optBoolean("active", true),
                notes = o.optString("notes"),
                isAdmin = o.optBoolean("isAdmin", false)
            )
        }
        val assignments = root.optJSONArray("assignments").orEmptyObjects().map { o ->
            Assignment(
                id = o.optString("id", UUID.randomUUID().toString()),
                guardId = o.optString("guardId"),
                date = o.optString("date"),
                primaryShift = o.optNullableString("primaryShift")?.let { runCatching { ShiftType.valueOf(it) }.getOrNull() },
                secondShift = o.optNullableString("secondShift")?.let { runCatching { ShiftType.valueOf(it) }.getOrNull() },
                situation = o.optNullableString("situation")?.let { runCatching { Situation.valueOf(it) }.getOrNull() },
                replacementForGuardId = o.optNullableString("replacementForGuardId"),
                replacementReason = o.optString("replacementReason"),
                note = o.optString("note"),
                autoRestGroupId = o.optNullableString("autoRestGroupId"),
                generatedRest = o.optBoolean("generatedRest", false),
                manualOverride = o.optBoolean("manualOverride", false)
            )
        }
        val holidays = root.optJSONArray("holidays").orEmptyObjects().map { o ->
            Holiday(date = o.optString("date"), name = o.optString("name"))
        }
        val settingsObj = root.optJSONObject("settings") ?: JSONObject()
        val colorsObj = settingsObj.optJSONObject("colors")
        val colors = if (colorsObj == null) AppSettings.defaultColors() else buildMap {
            AppSettings.defaultColors().forEach { (k, v) -> put(k, colorsObj.optString(k, v)) }
        }
        val settings = AppSettings(
            restDays = settingsObj.optInt("restDays", 10).coerceIn(1, 31),
            confirmImportantChanges = settingsObj.optBoolean("confirmImportantChanges", true),
            showAlerts = settingsObj.optBoolean("showAlerts", true),
            colors = colors
        )
        return AppSnapshot(guards, assignments, holidays, settings)
    }

    companion object { private const val KEY_DATA = "snapshot" }
}

private fun JSONArray?.orEmptyObjects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) optJSONObject(i)?.let(::add)
    }
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

fun monthAssignments(assignments: List<Assignment>, year: Int, month: Int): List<Assignment> =
    assignments.filter {
        runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> d.year == year && d.monthValue == month } == true
    }
