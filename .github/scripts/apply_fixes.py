from pathlib import Path
p = Path("TurnosVigilancia/app/src/main/java/com/alexandertm/turnosvigilancia/ui/App.kt")
s = p.read_text()

if "import androidx.compose.foundation.rememberScrollState" not in s:
    s = s.replace(
        "import androidx.compose.foundation.layout.*\n",
        "import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\n"
    )
if "import androidx.compose.foundation.text.KeyboardActions" not in s:
    s = s.replace(
        "import androidx.compose.foundation.verticalScroll\n",
        "import androidx.compose.foundation.verticalScroll\nimport androidx.compose.foundation.text.KeyboardActions\nimport androidx.compose.foundation.text.KeyboardOptions\n"
    )
if "import androidx.compose.ui.focus.FocusDirection" not in s:
    s = s.replace(
        "import androidx.compose.ui.graphics.Color\n",
        "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.focus.FocusDirection\n"
    )
if "import androidx.compose.ui.platform.LocalFocusManager" not in s:
    s = s.replace(
        "import androidx.compose.ui.platform.LocalContext\n",
        "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalFocusManager\nimport androidx.compose.ui.platform.LocalSoftwareKeyboardController\n"
    )
elif "import androidx.compose.ui.platform.LocalSoftwareKeyboardController" not in s:
    s = s.replace(
        "import androidx.compose.ui.platform.LocalFocusManager\n",
        "import androidx.compose.ui.platform.LocalFocusManager\nimport androidx.compose.ui.platform.LocalSoftwareKeyboardController\n"
    )
if "import androidx.compose.ui.text.input.ImeAction" not in s:
    s = s.replace(
        "import androidx.compose.ui.text.style.TextAlign\n",
        "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.input.ImeAction\n"
    )

s = s.replace(
    "Column(Modifier.fillMaxSize().padding(16.dp)) {",
    "Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {",
    1
)
s = s.replace(
    "LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.height(360.dp), userScrollEnabled = false)",
    "LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.height(320.dp), userScrollEnabled = false)"
)
s = s.replace(
    "modifier = Modifier.padding(2.dp).aspectRatio(1f).clickable { selected = date },",
    """modifier = Modifier.padding(2.dp).aspectRatio(1f).clickable {
                                selected = date
                                nav.navigate("day/$date")
                            },"""
)

old = """    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (existing == null) "Nuevo vigilante" else "Editar vigilante", onBack = { nav.popBackStack() })
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre completo *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(document, { document = it }, label = { Text("Documento (opcional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono (opcional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Observación (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Activo", Modifier.weight(1f)); Switch(active, { active = it }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Administrador", Modifier.weight(1f)); Switch(isAdmin, { isAdmin = it }) }
            Spacer(Modifier.weight(1f))
            Button(enabled = name.isNotBlank(), onClick = { vm.saveGuard(Guard(id = existing?.id ?: UUID.randomUUID().toString(), name = name.trim(), document = document.trim(), phone = phone.trim(), active = active, notes = notes.trim(), isAdmin = isAdmin)); nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
        }
    }"""

new = """    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (existing == null) "Nuevo vigilante" else "Editar vigilante", onBack = { nav.popBackStack() })

        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Nombre completo *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )
                OutlinedTextField(
                    document,
                    { document = it },
                    label = { Text("Documento (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )
                OutlinedTextField(
                    phone,
                    { phone = it },
                    label = { Text("Teléfono (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )
                OutlinedTextField(
                    notes,
                    { notes = it },
                    label = { Text("Observación (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Activo", Modifier.weight(1f))
                    Switch(active, { active = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Administrador", Modifier.weight(1f))
                    Switch(isAdmin, { isAdmin = it })
                }
            }

            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        vm.saveGuard(
                            Guard(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                document = document.trim(),
                                phone = phone.trim(),
                                active = active,
                                notes = notes.trim(),
                                isAdmin = isAdmin
                            )
                        )
                        nav.popBackStack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Guardar")
                }
            }
        }
    }"""

if old not in s:
    raise SystemExit("GuardEditScreen original target block not found")

s = s.replace(old, new, 1)
p.write_text(s)
