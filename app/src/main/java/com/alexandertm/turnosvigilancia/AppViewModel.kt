package com.alexandertm.turnosvigilancia

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.alexandertm.turnosvigilancia.data.*
import java.time.LocalDate
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)

    var snapshot by mutableStateOf(repository.load())
        private set

    fun guard(id: String): Guard? = snapshot.guards.firstOrNull { it.id == id }

    fun activeGuards(): List<Guard> = snapshot.guards.filter { it.active }.sortedBy { it.name.lowercase() }

    fun guardsForDate(date: LocalDate): List<Guard> {
        val assignedIds = snapshot.assignments.filter { it.date == date.toString() }.map { it.guardId }.toSet()
        return snapshot.guards.filter { it.active || it.id in assignedIds }.sortedBy { it.name.lowercase() }
    }

    fun assignment(guardId: String, date: LocalDate): Assignment? =
        snapshot.assignments.firstOrNull { it.guardId == guardId && it.date == date.toString() }

    fun saveGuard(guard: Guard) {
        val list = snapshot.guards.toMutableList()
        val index = list.indexOfFirst { it.id == guard.id }
        if (guard.isAdmin) {
            for (i in list.indices) list[i] = list[i].copy(isAdmin = false)
        }
        if (index >= 0) list[index] = guard else list += guard
        update(snapshot.copy(guards = list))
    }

    fun setGuardActive(id: String, active: Boolean) {
        update(snapshot.copy(guards = snapshot.guards.map { if (it.id == id) it.copy(active = active) else it }))
    }

    fun saveAssignment(
        guardId: String,
        date: LocalDate,
        primary: ShiftType?,
        second: ShiftType?,
        situation: Situation?,
        replacementFor: String?,
        replacementReason: String,
        note: String
    ) {
        if (situation == Situation.REST) {
            createRestPeriod(guardId, date, primary, second, note)
            return
        }
        val existing = assignment(guardId, date)
        val updated = Assignment(
            id = existing?.id ?: UUID.randomUUID().toString(),
            guardId = guardId,
            date = date.toString(),
            primaryShift = primary,
            secondShift = second,
            situation = situation,
            replacementForGuardId = if (situation == Situation.REPLACEMENT) replacementFor else null,
            replacementReason = if (situation == Situation.REPLACEMENT) replacementReason else "",
            note = note,
            autoRestGroupId = existing?.autoRestGroupId,
            generatedRest = existing?.generatedRest ?: false,
            manualOverride = (existing?.generatedRest == true && existing.situation == Situation.REST)
        )
        upsertAssignment(updated)
    }

    fun clearAssignment(guardId: String, date: LocalDate) {
        update(snapshot.copy(assignments = snapshot.assignments.filterNot { it.guardId == guardId && it.date == date.toString() }))
    }

    private fun createRestPeriod(guardId: String, start: LocalDate, primary: ShiftType?, second: ShiftType?, note: String) {
        val groupId = UUID.randomUUID().toString()
        var items = snapshot.assignments.toMutableList()
        repeat(snapshot.settings.restDays) { offset ->
            val date = start.plusDays(offset.toLong())
            val oldIndex = items.indexOfFirst { it.guardId == guardId && it.date == date.toString() }
            val old = items.getOrNull(oldIndex)
            val merged = Assignment(
                id = old?.id ?: UUID.randomUUID().toString(),
                guardId = guardId,
                date = date.toString(),
                primaryShift = if (offset == 0) primary ?: old?.primaryShift else old?.primaryShift,
                secondShift = if (offset == 0) second ?: old?.secondShift else old?.secondShift,
                situation = Situation.REST,
                replacementForGuardId = null,
                replacementReason = "",
                note = if (offset == 0 && note.isNotBlank()) note else old?.note.orEmpty(),
                autoRestGroupId = groupId,
                generatedRest = true,
                manualOverride = false
            )
            if (oldIndex >= 0) items[oldIndex] = merged else items += merged
        }
        update(snapshot.copy(assignments = items))
    }

    private fun upsertAssignment(item: Assignment) {
        val list = snapshot.assignments.toMutableList()
        val index = list.indexOfFirst { it.guardId == item.guardId && it.date == item.date }
        if (item.primaryShift == null && item.secondShift == null && item.situation == null && item.note.isBlank()) {
            if (index >= 0) list.removeAt(index)
        } else if (index >= 0) list[index] = item else list += item
        update(snapshot.copy(assignments = list))
    }

    fun addHoliday(date: LocalDate, name: String) {
        val list = snapshot.holidays.filterNot { it.date == date.toString() } + Holiday(date.toString(), name)
        update(snapshot.copy(holidays = list.sortedBy { it.date }))
    }

    fun removeHoliday(date: LocalDate) {
        update(snapshot.copy(holidays = snapshot.holidays.filterNot { it.date == date.toString() }))
    }

    fun holiday(date: LocalDate): Holiday? = snapshot.holidays.firstOrNull { it.date == date.toString() }

    fun updateSettings(settings: AppSettings) = update(snapshot.copy(settings = settings))

    fun cycleColor(key: String) {
        val palette = listOf("#2F80ED", "#7C3AED", "#9AA5B1", "#16A66A", "#F59E0B", "#EF4444", "#EC4899", "#0891B2")
        val current = snapshot.settings.colors[key] ?: palette.first()
        val next = palette[(palette.indexOf(current).let { if (it < 0) 0 else it + 1 }) % palette.size]
        updateSettings(snapshot.settings.copy(colors = snapshot.settings.colors + (key to next)))
    }

    fun exportBackupJson(): String = repository.exportJson(snapshot)

    fun importBackupJson(raw: String): Result<Unit> = runCatching {
        val imported = repository.importJson(raw)
        snapshot = imported
        repository.save(imported)
    }

    fun alertsFor(assignment: Assignment?): List<String> {
        if (assignment == null || !snapshot.settings.showAlerts) return emptyList()
        return buildList {
            if (assignment.primaryShift != null && assignment.secondShift != null) add("Este vigilante tiene dos turnos el mismo día.")
            if (assignment.situation == Situation.REPLACEMENT && assignment.replacementForGuardId == null) add("El reemplazo no tiene una persona reemplazada seleccionada.")
            if (assignment.generatedRest && assignment.manualOverride) add("Este día pertenece a un descanso generado automáticamente y fue modificado manualmente.")
        }
    }

    private fun update(newSnapshot: AppSnapshot) {
        snapshot = newSnapshot
        repository.save(newSnapshot)
    }
}
