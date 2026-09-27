from pathlib import Path
import re
base=Path('TurnosVigilancia/app/src/main')
p=base/'java/com/alexandertm/turnosvigilancia/ui/App.kt'
s=p.read_text()
if 'LocalNavigationLoading' not in s:
    s=s.replace('private val Es = Locale("es", "PE")\n','''private val Es = Locale("es", "PE")

private val LocalNavigationLoading = staticCompositionLocalOf<MutableState<Boolean>> { error("Navigation loading state not provided") }

@Composable private fun FullScreenLoadingOverlay(message: String = "Cargando...") {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)).clickable(enabled = true) {}, contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 8.dp) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp)); Text(message, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable private fun AppSplash() {
    Box(Modifier.fillMaxSize().background(AppBlue), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = 0.14f)) { Icon(Icons.Default.Security, null, tint = Color.White, modifier = Modifier.padding(24.dp).size(64.dp)) }
            Spacer(Modifier.height(18.dp)); Text("Turnos de Vigilancia", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp)); CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}
''')

start=s.index('@Composable\nfun TurnosApp')
end=s.index('@Composable\nprivate fun BottomNav', start)
turnos='''@Composable
fun TurnosApp(vm: AppViewModel) {
    var showSplash by remember { mutableStateOf(true) }; val navigationLoading = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(650); showSplash = false }
    if (showSplash) { AppSplash(); return }
    val nav = rememberNavController(); val backStack by nav.currentBackStackEntryAsState(); val route = backStack?.destination?.route.orEmpty()
    val showBottom = route in setOf("calendar", "guards", "reports", "settings")
    CompositionLocalProvider(LocalNavigationLoading provides navigationLoading) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(bottomBar = { if (showBottom) BottomNav(nav, route) }, containerColor = AppBg) { padding ->
                NavHost(navController = nav, startDestination = "calendar", modifier = Modifier.padding(padding)) {
                    composable("calendar") { CalendarScreen(vm, nav) }
                    composable("day/{date}", arguments = listOf(navArgument("date") { type = NavType.StringType })) { DayScreen(vm, nav, LocalDate.parse(it.arguments!!.getString("date")!!)) }
                    composable("assign/{guardId}/{date}", arguments = listOf(navArgument("guardId") { type = NavType.StringType }, navArgument("date") { type = NavType.StringType })) { QuickAssignmentScreen(vm, nav, it.arguments!!.getString("guardId")!!, LocalDate.parse(it.arguments!!.getString("date")!!)) }
                    composable("detail/{guardId}/{date}", arguments = listOf(navArgument("guardId") { type = NavType.StringType }, navArgument("date") { type = NavType.StringType })) { AssignmentDetailScreen(vm, nav, it.arguments!!.getString("guardId")!!, LocalDate.parse(it.arguments!!.getString("date")!!)) }
                    composable("guards") { GuardsScreen(vm, nav) }; composable("guard/new") { GuardEditScreen(vm, nav, null) }
                    composable("guard/{guardId}", arguments = listOf(navArgument("guardId") { type = NavType.StringType })) { GuardEditScreen(vm, nav, it.arguments!!.getString("guardId")) }
                    composable("history/{guardId}", arguments = listOf(navArgument("guardId") { type = NavType.StringType })) { HistoryScreen(vm, nav, it.arguments!!.getString("guardId")!!) }
                    composable("reports") { ReportsScreen(vm) }; composable("settings") { SettingsScreen(vm, nav) }; composable("holidays") { HolidaysScreen(vm, nav) }; composable("backup") { BackupScreen(vm, nav) }
                }
            }
            if (navigationLoading.value) FullScreenLoadingOverlay()
        }
    }
}

'''
s=s[:start]+turnos+s[end:]

bs=s.index('@Composable\nprivate fun BottomNav')
be=s.index('@Composable\nprivate fun ScreenHeader',bs)
bottom='''@Composable
private fun BottomNav(nav: NavHostController, route: String) {
    val items=listOf(Triple("calendar","Calendario",Icons.Default.CalendarMonth),Triple("guards","Guardias",Icons.Default.Groups),Triple("reports","Reportes",Icons.Default.Description),Triple("settings","Configuración",Icons.Default.Settings))
    val loading=LocalNavigationLoading.current; val scope=rememberCoroutineScope()
    NavigationBar { items.forEach { (r,label,icon) -> NavigationBarItem(selected=route==r, enabled=!loading.value, onClick={ if(route!=r&&!loading.value){ loading.value=true; scope.launch { delay(120); nav.navigate(r){popUpTo("calendar"){saveState=true};launchSingleTop=true;restoreState=true}; delay(220); loading.value=false } } }, icon={ if(loading.value&&route!=r) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Icon(icon,null)}, label={Text(label)}) } }
}

'''
s=s[:bs]+bottom+s[be:]

hs=s.index('@Composable\nprivate fun ScreenHeader')
he=s.index('@Composable\nprivate fun LoadingActionButton',hs)
header='''@Composable
private fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, action: (@Composable (() -> Unit))? = null) {
    val loading=LocalNavigationLoading.current; val scope=rememberCoroutineScope()
    Surface(color=AppBlue,shadowElevation=2.dp){ Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
        if(onBack!=null) IconButton(enabled=!loading.value,onClick={if(!loading.value){loading.value=true;scope.launch{delay(100);onBack();delay(220);loading.value=false}}}){if(loading.value) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=Color.White) else Icon(Icons.Default.ArrowBack,null,tint=Color.White)}
        Text(title,color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f).padding(start=if(onBack==null)12.dp else 0.dp)); action?.invoke()
    }}
}

'''
s=s[:hs]+header+s[he:]
p.write_text(s)

m=base/'AndroidManifest.xml'; ms=m.read_text()
if 'android:icon=' not in ms: ms=ms.replace('android:label="Turnos de Vigilancia"','android:label="Turnos de Vigilancia"\n        android:icon="@mipmap/ic_launcher"\n        android:roundIcon="@mipmap/ic_launcher_round"')
m.write_text(ms)

(base/'drawable').mkdir(exist_ok=True); (base/'mipmap-anydpi-v26').mkdir(exist_ok=True); (base/'mipmap-anydpi').mkdir(exist_ok=True)
fg='''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><path android:fillColor="#FFFFFF" android:pathData="M54,16 L82,27 L82,49 C82,69 70,84 54,92 C38,84 26,69 26,49 L26,27 Z"/><path android:fillColor="#0B5CAD" android:pathData="M38,38 L70,38 L70,67 L38,67 Z"/><path android:fillColor="#FFFFFF" android:pathData="M42,45 L66,45 L66,63 L42,63 Z M45,34 L49,34 L49,42 L45,42 Z M59,34 L63,34 L63,42 L59,42 Z"/><path android:fillColor="#0B5CAD" android:pathData="M46,49 L52,49 L52,55 L46,55 Z M56,49 L62,49 L62,55 L56,55 Z M46,57 L52,57 L52,61 L46,61 Z"/></vector>'''
bg='''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><path android:fillColor="#0B5CAD" android:pathData="M0,0h108v108h-108z"/></vector>'''
ad='''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/ic_launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/></adaptive-icon>'''
legacy='''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="48dp" android:height="48dp" android:viewportWidth="108" android:viewportHeight="108"><path android:fillColor="#0B5CAD" android:pathData="M0,0h108v108h-108z"/><path android:fillColor="#FFFFFF" android:pathData="M54,16 L82,27 L82,49 C82,69 70,84 54,92 C38,84 26,69 26,49 L26,27 Z"/><path android:fillColor="#0B5CAD" android:pathData="M38,38 L70,38 L70,67 L38,67 Z"/><path android:fillColor="#FFFFFF" android:pathData="M42,45 L66,45 L66,63 L42,63 Z M45,34 L49,34 L49,42 L45,42 Z M59,34 L63,34 L63,42 L59,42 Z"/></vector>'''
(base/'drawable/ic_launcher_foreground.xml').write_text(fg);(base/'drawable/ic_launcher_background.xml').write_text(bg)
(base/'mipmap-anydpi-v26/ic_launcher.xml').write_text(ad);(base/'mipmap-anydpi-v26/ic_launcher_round.xml').write_text(ad)
(base/'mipmap-anydpi/ic_launcher.xml').write_text(legacy);(base/'mipmap-anydpi/ic_launcher_round.xml').write_text(legacy)
