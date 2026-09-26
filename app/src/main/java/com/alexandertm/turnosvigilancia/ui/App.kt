package com.alexandertm.turnosvigilancia.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alexandertm.turnosvigilancia.AppViewModel
import com.alexandertm.turnosvigilancia.data.*
import com.alexandertm.turnosvigilancia.util.ExportUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private val Es = Locale("es", "PE")

@Composable
fun TurnosApp(vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var editing by remember { mutableStateOf<Pair<String, LocalDate>?>(null) }

    Scaffold(
        containerColor = AppBg,
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple("Calendario", Icons.Default.CalendarMonth, 0),
                    Triple("Guardias", Icons.Default.Groups, 1),
                    Triple("Reportes", Icons.Default.Description, 2),
                    Triple("Configuración", Icons.Default.Settings, 3)
                ).forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.third,
                        onClick = { tab = item.third; selectedDate = null },
                        icon = { Icon(item.second, null) },
                        label = { Text(item.first) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                editing != null -> AssignmentEditor(vm, editing!!.first, editing!!.second) { editing = null }
                selectedDate != null -> DayScreen(vm, selectedDate!!, onBack = { selectedDate = null }, onEdit = { id -> editing = id to selectedDate!! })
                tab == 0 -> CalendarScreen(vm) { selectedDate = it }
                tab == 1 -> GuardsScreen(vm)
                tab == 2 -> ReportsScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)? = null) {
    Surface(color = AppBlue) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = if (onBack == null) 12.dp else 0.dp))
        }
    }
}

@Composable
private fun CalendarScreen(vm: AppViewModel, onDay: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    Column(Modifier.fillMaxSize()) {
        Header("Turnos de Vigilancia")
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, null) }
                Text(month.month.getDisplayName(TextStyle.FULL, Es).replaceFirstChar { it.uppercase() } + " " + month.year, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Lun","Mar","Mié","Jue","Vie","Sáb","Dom").forEachIndexed { i, s ->
                    Text(s, Modifier.weight(1f), textAlign = TextAlign.Center, color = if (i >= 5) Color(0xFFDC2626) else AppMuted)
                }
            }
            Spacer(Modifier.height(6.dp))
            val offset = month.atDay(1).dayOfWeek.value - 1
            val total = offset + month.lengthOfMonth()
            repeat((total + 6) / 7) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val day = row * 7 + col - offset + 1
                        if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(62.dp))
                        else {
                            val date = month.atDay(day)
                            val count = vm.snapshot.assignments.count { it.date == date.toString() }
                            val holiday = vm.holiday(date) != null
                            Card(
                                modifier = Modifier.weight(1f).height(62.dp).padding(2.dp).clickable { onDay(date) },
                                colors = CardDefaults.cardColors(containerColor = if (col >= 5 || holiday) Color(0xFFFFF2F2) else Color.White)
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    Text(day.toString(), Modifier.align(Alignment.TopCenter).padding(top = 7.dp), color = if (col >= 5 || holiday) Color(0xFFDC2626) else AppText)
                                    if (count > 0) Text(count.toString(), Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), color = AppBlue2, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Toca un día para ver o registrar los turnos.", color = AppMuted)
        }
    }
}

@Composable
private fun DayScreen(vm: AppViewModel, date: LocalDate, onBack: () -> Unit, onEdit: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header("Detalle del día", onBack)
        Column(Modifier.padding(14.dp)) {
            Text(date.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            vm.holiday(date)?.let { Text("Feriado: " + it.name, color = Color(0xFFDC2626)) }
            Spacer(Modifier.height(8.dp))
            if (vm.guardsForDate(date).isEmpty()) {
                Text("No hay vigilantes activos. Agrégalos desde Guardias.", color = AppMuted)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(vm.guardsForDate(date), key = { it.id }) { g ->
                        val a = vm.assignment(g.id, date)
                        Card(Modifier.fillMaxWidth().clickable { onEdit(g.id) }) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(g.name, fontWeight = FontWeight.SemiBold)
                                    Text(if (a == null) "Sin registro" else a.summary(), color = AppMuted)
                                }
                                Icon(Icons.Default.ChevronRight, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignmentEditor(vm: AppViewModel, guardId: String, date: LocalDate, onBack: () -> Unit) {
    val guard = vm.guard(guardId) ?: return
    val existing = vm.assignment(guardId, date)
    var primary by remember { mutableStateOf(existing?.primaryShift) }
    var second by remember { mutableStateOf(existing?.secondShift) }
    var situation by remember { mutableStateOf(existing?.situation) }
    var replacement by remember { mutableStateOf(existing?.replacementForGuardId) }
    var reason by remember { mutableStateOf(existing?.replacementReason ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Header("Asignar turno", onBack)
        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(guard.name + " · " + date, fontWeight = FontWeight.Bold) }
            item { Text("Turno principal", fontWeight = FontWeight.SemiBold) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Día", primary == ShiftType.DAY, Modifier.weight(1f)) { primary = if (primary == ShiftType.DAY) null else ShiftType.DAY }
                    Choice("Noche", primary == ShiftType.NIGHT, Modifier.weight(1f)) { primary = if (primary == ShiftType.NIGHT) null else ShiftType.NIGHT }
                }
            }
            item { Text("Segundo turno (opcional)", fontWeight = FontWeight.SemiBold) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Día", second == ShiftType.DAY, Modifier.weight(1f)) { second = if (second == ShiftType.DAY) null else ShiftType.DAY }
                    Choice("Noche", second == ShiftType.NIGHT, Modifier.weight(1f)) { second = if (second == ShiftType.NIGHT) null else ShiftType.NIGHT }
                }
            }
            item { Text("Situación", fontWeight = FontWeight.SemiBold) }
            items(Situation.entries) { s ->
                Choice(s.label + if (s == Situation.REST) " (10 días)" else "", situation == s, Modifier.fillMaxWidth()) { situation = if (situation == s) null else s }
            }
            if (situation == Situation.REPLACEMENT) {
                item { Text("Persona reemplazada", fontWeight = FontWeight.SemiBold) }
                items(vm.activeGuards().filter { it.id != guardId }) { g ->
                    Choice(g.name, replacement == g.id, Modifier.fillMaxWidth()) { replacement = g.id }
                }
                item { OutlinedTextField(reason, { reason = it }, label = { Text("Motivo (opcional)") }, modifier = Modifier.fillMaxWidth()) }
            }
            item { OutlinedTextField(note, { note = it }, label = { Text("Observación (opcional)") }, modifier = Modifier.fillMaxWidth()) }
            if (primary != null && second != null) item { AssistChip(onClick = {}, label = { Text("Alerta: dos turnos el mismo día") }, leadingIcon = { Icon(Icons.Default.Warning, null) }) }
            item {
                Button(onClick = {
                    vm.saveAssignment(guardId,date,primary,second,situation,replacement,reason,note)
                    Toast.makeText(context,"Registro guardado",Toast.LENGTH_SHORT).show()
                    onBack()
                }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            }
            if (existing != null) item {
                OutlinedButton(onClick = { vm.clearAssignment(guardId,date); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar registro") }
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFDCEEFF) else Color.White)
    ) { Text(label, Modifier.padding(14.dp), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun GuardsScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Guard?>(null) }
    var addNew by remember { mutableStateOf(false) }
    if (editing != null || addNew) {
        GuardForm(vm, editing) { editing = null; addNew = false }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Header("Guardias")
        Column(Modifier.padding(14.dp)) {
            Button(onClick = { addNew = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add,null); Spacer(Modifier.width(6.dp)); Text("Agregar vigilante") }
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vm.snapshot.guards.sortedBy { it.name }) { g ->
                    Card(Modifier.fillMaxWidth().clickable { editing = g }) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(g.name, fontWeight = FontWeight.SemiBold)
                                Text((if (g.active) "Activo" else "Inactivo") + if (g.isAdmin) " · Administrador" else "", color = if (g.active) Color(0xFF16A34A) else AppMuted)
                            }
                            Switch(g.active, onCheckedChange = { vm.setGuardActive(g.id,it) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardForm(vm: AppViewModel, original: Guard?, done: () -> Unit) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var doc by remember { mutableStateOf(original?.document ?: "") }
    var phone by remember { mutableStateOf(original?.phone ?: "") }
    var notes by remember { mutableStateOf(original?.notes ?: "") }
    var active by remember { mutableStateOf(original?.active ?: true) }
    var admin by remember { mutableStateOf(original?.isAdmin ?: false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Header(if (original == null) "Nuevo vigilante" else "Editar vigilante", done)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name,{name=it},label={Text("Nombre completo *")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(doc,{doc=it},label={Text("Documento (opcional)")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(phone,{phone=it},label={Text("Teléfono (opcional)")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(notes,{notes=it},label={Text("Observación (opcional)")},modifier=Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Activo",Modifier.weight(1f)); Switch(active,{active=it}) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Administrador",Modifier.weight(1f)); Switch(admin,{admin=it}) }
            Button(onClick={
                if(name.isBlank()) Toast.makeText(context,"Ingresa un nombre",Toast.LENGTH_SHORT).show()
                else { vm.saveGuard(Guard(original?.id ?: UUID.randomUUID().toString(),name.trim(),doc,phone,active,notes,admin)); done() }
            },modifier=Modifier.fillMaxWidth()){Text("Guardar")}
        }
    }
}

@Composable
private fun ReportsScreen(vm: AppViewModel) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Header("Reportes")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                IconButton({month=month.minusMonths(1)}){Icon(Icons.Default.ChevronLeft,null)}
                Text(month.month.getDisplayName(TextStyle.FULL,Es)+" "+month.year,fontWeight=FontWeight.Bold)
                IconButton({month=month.plusMonths(1)}){Icon(Icons.Default.ChevronRight,null)}
            }
            ExportButton("Exportar a PDF",Icons.Default.PictureAsPdf) { ExportUtils.shareFile(context,ExportUtils.exportPdf(context,vm.snapshot,month),"application/pdf") }
            ExportButton("Exportar a Excel",Icons.Default.TableChart) { ExportUtils.shareFile(context,ExportUtils.exportXlsx(context,vm.snapshot,month),"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }
            ExportButton("Exportar como imagen",Icons.Default.Image) { ExportUtils.shareFile(context,ExportUtils.exportPng(context,vm.snapshot,month),"image/png") }
        }
    }
}

@Composable
private fun ExportButton(text:String, icon: androidx.compose.ui.graphics.vector.ImageVector, action:()->Unit) {
    Card(Modifier.fillMaxWidth().clickable { runCatching(action) }) {
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=AppBlue2);Spacer(Modifier.width(12.dp));Text(text,fontWeight=FontWeight.Bold)}
    }
}

@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val s=vm.snapshot.settings
    var holidayDate by remember { mutableStateOf("") }
    var holidayName by remember { mutableStateOf("") }
    val context=LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Header("Configuración")
        LazyColumn(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {
                Card { Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Duración del descanso",Modifier.weight(1f)); IconButton({vm.updateSettings(s.copy(restDays=(s.restDays-1).coerceAtLeast(1)))}){Icon(Icons.Default.Remove,null)}
                    Text(s.restDays.toString()+" días",fontWeight=FontWeight.Bold); IconButton({vm.updateSettings(s.copy(restDays=(s.restDays+1).coerceAtMost(31)))}){Icon(Icons.Default.Add,null)}
                }}
            }
            item { Row(verticalAlignment=Alignment.CenterVertically){Text("Mostrar alertas",Modifier.weight(1f));Switch(s.showAlerts,{vm.updateSettings(s.copy(showAlerts=it))})} }
            item { Text("Feriados manuales",fontWeight=FontWeight.Bold) }
            item { OutlinedTextField(holidayDate,{holidayDate=it},label={Text("Fecha AAAA-MM-DD")},modifier=Modifier.fillMaxWidth()) }
            item { OutlinedTextField(holidayName,{holidayName=it},label={Text("Nombre")},modifier=Modifier.fillMaxWidth()) }
            item { Button(onClick={
                val d=runCatching{LocalDate.parse(holidayDate)}.getOrNull()
                if(d!=null && holidayName.isNotBlank()){vm.addHoliday(d,holidayName);holidayDate="";holidayName=""} else Toast.makeText(context,"Fecha o nombre inválido",Toast.LENGTH_SHORT).show()
            },modifier=Modifier.fillMaxWidth()){Text("Agregar feriado")} }
            items(vm.snapshot.holidays.sortedBy{it.date}) { h ->
                Card { Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(h.date,fontWeight=FontWeight.SemiBold);Text(h.name,color=AppMuted)};IconButton({vm.removeHoliday(LocalDate.parse(h.date))}){Icon(Icons.Default.Delete,null,tint=Color(0xFFDC2626))}}}
            }
            item { Text("Copia de seguridad",fontWeight=FontWeight.Bold) }
            item { Button(onClick={val f=ExportUtils.createBackupFile(context,vm.exportBackupJson());ExportUtils.shareFile(context,f,"application/json")},modifier=Modifier.fillMaxWidth()){Text("Crear y compartir copia")} }
        }
    }
}
