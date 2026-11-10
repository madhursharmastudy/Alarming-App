package com.example

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.QrCodeScanner
import com.example.challenge.QrScannerView
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.challenge.ComboCatalog
import android.app.Activity
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.DisposableEffect
import com.example.challenge.BaseChallengeActivity
import com.example.challenge.CardArrangementChallengeActivity
import com.example.challenge.ChargerChallengeActivity
import com.example.challenge.ImageArrangementChallengeActivity
import com.example.challenge.MathChallengeActivity
import com.example.challenge.OddOneOutChallengeActivity
import com.example.challenge.PatternLockChallengeActivity
import com.example.challenge.QrChallengeActivity
import com.example.challenge.ReactionGameChallengeActivity
import com.example.challenge.ShakeChallengeActivity
import com.example.challenge.StepsChallengeActivity
import com.example.challenge.TypingChallengeActivity
import com.example.data.AlarmEntity
import com.example.data.AlarmRepository
import com.example.data.AppDatabase
import com.example.data.ChallengeType
import com.example.service.AlarmReceiver
import com.example.service.AlarmRingingService
import com.example.service.AlarmScheduler
import com.example.ui.AlarmRingingActivity
import com.example.ui.PermissionSetupCard
import com.example.ui.WakeStatsCard
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ThemePreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import com.example.ui.theme.OceanBrush
import com.example.ui.theme.OceanCyan
import com.example.ui.theme.OceanGradientButton
import com.example.ui.theme.OceanIconTile
import com.example.ui.theme.oceanSwitchColors

class MainActivity : ComponentActivity() {

    private lateinit var repository: AlarmRepository

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notifications permission needed for alarm alerts", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getDatabase(this)
        repository = AlarmRepository(db.alarmDao())

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MyApplicationTheme {
                MainAppScreen(
                    repository = repository,
                    onTestChallenge = { type -> launchChallengeDirectly(type) },
                    onTestCombo = { id -> launchChallengeDirectly(ChallengeType.COMBO, id) },
                    onTestAlarmRing = { alarm -> triggerAlarmRingNow(alarm) }
                )
            }
        }
    }

    private fun launchChallengeDirectly(type: ChallengeType, comboId: Int = 0) {
        val combo = if (type == ChallengeType.COMBO) ComboCatalog.byId(comboId) ?: ComboCatalog.byId(1) else null
        val firstType = combo?.steps?.first()?.type ?: type
        val intent = Intent(this, ComboCatalog.activityClass(firstType)).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, "Test ${combo?.name ?: type.displayName}")
            putExtra(AlarmReceiver.EXTRA_CHALLENGE_TYPE, type.name)
            putExtra(AlarmReceiver.EXTRA_REF_LABELS, "Cup, Bottle, Chair")
            if (combo != null) {
                putExtra(BaseChallengeActivity.EXTRA_COMBO_ID, combo.id)
                putExtra(BaseChallengeActivity.EXTRA_FORCE_STEP, 1)
            }
        }
        startActivity(intent)
    }

    private fun triggerAlarmRingNow(alarm: AlarmEntity) {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
            putExtra(AlarmReceiver.EXTRA_CHALLENGE_TYPE, alarm.challengeType.name)
            putExtra(AlarmReceiver.EXTRA_REF_LABELS, alarm.referenceLabels)
        }
        sendBroadcast(intent)
        Toast.makeText(this, "Triggering alarm immediately!", Toast.LENGTH_SHORT).show()
    }
}

private fun challengeIconFor(type: ChallengeType): ImageVector = when (type) {
    ChallengeType.TYPING -> Icons.Default.Keyboard
    ChallengeType.IMAGE_ARRANGEMENT -> Icons.Default.GridOn
    ChallengeType.CHARGER -> Icons.Default.Power
    ChallengeType.CARD_ARRANGEMENT -> Icons.Default.GridOn
    ChallengeType.PATTERN_LOCK -> Icons.Default.Lock
    ChallengeType.ODD_ONE_OUT -> Icons.Default.Visibility
    ChallengeType.REACTION_GAME -> Icons.Default.Speed
    ChallengeType.MATH -> Icons.Default.Science
    ChallengeType.SHAKE -> Icons.Default.PhoneAndroid
    ChallengeType.STEPS -> Icons.Default.Timer
    ChallengeType.QR -> Icons.Default.QrCode
    ChallengeType.WALK_MATH -> Icons.Default.DirectionsWalk
    ChallengeType.TURN_WALK -> Icons.Default.TurnLeft
    ChallengeType.FACE_CHECK -> Icons.Default.Face
    ChallengeType.SQUAT -> Icons.Default.FitnessCenter
    ChallengeType.VOICE_MATH -> Icons.Default.Mic
    ChallengeType.LIGHT -> Icons.Default.LightMode
    ChallengeType.OBJECT_PHOTO -> Icons.Default.CameraAlt
    ChallengeType.NFC_TAP -> Icons.Default.Nfc
    ChallengeType.COMBO -> Icons.Default.Bolt
}

private fun timeLabel(alarm: AlarmEntity): String {
    val h = if (alarm.hour == 0) 12 else if (alarm.hour > 12) alarm.hour - 12 else alarm.hour
    return String.format("%02d:%02d %s", h, alarm.minute, if (alarm.hour < 12) "AM" else "PM")
}

private fun taskName(alarm: AlarmEntity): String =
    if (alarm.challengeType == ChallengeType.COMBO) {
        ComboCatalog.byId(alarm.comboId)?.name ?: "Combo"
    } else {
        alarm.challengeType.displayName
    }

private fun shortDays(alarm: AlarmEntity): String = when (alarm.daysOfWeek) {
    127 -> "Every day"
    62 -> "Mon\u2013Fri"
    65 -> "Weekend"
    0 -> "Once"
    else -> {
        val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        (0..6).filter { (alarm.daysOfWeek and (1 shl it)) != 0 }.joinToString(", ") { names[it] }
    }
}

// Minutes from `now` until the next time this alarm rings (null if it never rings again)
private fun minutesUntilNext(alarm: AlarmEntity, now: Calendar): Long? {
    for (offset in 0..7) {
        val c = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, offset)
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now.timeInMillis) continue
        val dow = c.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
        if (alarm.daysOfWeek == 0 || (alarm.daysOfWeek and (1 shl dow)) != 0) {
            return (c.timeInMillis - now.timeInMillis) / 60000L
        }
    }
    return null
}

private fun formatTimeLeft(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h >= 24 -> "${h / 24}d ${h % 24}h left"
        h > 0 -> "${h}h ${m}m left"
        else -> "${m}m left"
    }
}

private fun greetingFor(hour: Int): String = when {
    hour < 12 -> "Good morning"
    hour < 17 -> "Good afternoon"
    else -> "Good evening"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabsScreen(
    repository: AlarmRepository,
    onTestChallenge: (ChallengeType) -> Unit,
    onTestCombo: (Int) -> Unit = {},
    onTestAlarmRing: (AlarmEntity) -> Unit
) {
    val alarms by repository.allAlarms.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showAddSheet by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<AlarmEntity?>(null) }
    var tab by remember { mutableStateOf(0) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val nextAlarm = remember(alarms, nowMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        alarms
            .filter { it.isEnabled }
            .mapNotNull { a -> minutesUntilNext(a, cal)?.let { left -> a to left } }
            .minByOrNull { it.second }
    }
    val greeting = remember(nowMillis) {
        greetingFor(Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY))
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (tab == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        OceanGradientButton(
                            text = "New alarm",
                            icon = Icons.Default.Add,
                            onClick = {
                                alarmToEdit = null
                                showAddSheet = true
                            },
                            modifier = Modifier.testTag("add_alarm_fab")
                        )
                    }
                }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    listOf(
                        Triple("Home", Icons.Default.Home, 0),
                        Triple("Stats", Icons.Default.BarChart, 1),
                        Triple("Settings", Icons.Default.Settings, 2)
                    ).forEach { (name, icon, index) ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(imageVector = icon, contentDescription = name) },
                            label = { Text(name) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = OceanCyan,
                                selectedTextColor = OceanCyan,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_$name")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (tab == 1) {
            StatsTab(padding = innerPadding)
        } else if (tab == 2) {
            SettingsTab(padding = innerPadding, onTestChallenge = onTestChallenge, onTestCombo = onTestCombo)
        } else LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                    Text(
                        text = greeting,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Next alarm",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            item { NextAlarmHeroCard(next = nextAlarm) }

            // Shows only the permissions/settings that still need attention
            item { PermissionSetupCard() }

            if (alarms.isEmpty()) {
                item { EmptyAlarmsState(onAddClick = { alarmToEdit = null; showAddSheet = true }) }
            } else {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCardItem(
                        alarm = alarm,
                        onToggle = { enabled ->
                            coroutineScope.launch {
                                val updated = alarm.copy(isEnabled = enabled)
                                repository.updateAlarm(updated)
                                AlarmScheduler.scheduleAlarm(context, updated)
                            }
                        },
                        onDelete = {
                            coroutineScope.launch {
                                AlarmScheduler.cancelAlarm(context, alarm.id)
                                repository.deleteAlarm(alarm)
                            }
                        },
                        onTestRing = { onTestAlarmRing(alarm) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showAddSheet) {
        AddEditAlarmSheet(
            initialAlarm = alarmToEdit,
            onDismiss = { showAddSheet = false },
            onSave = { newAlarm ->
                coroutineScope.launch {
                    val id = repository.insertAlarm(newAlarm)
                    val created = newAlarm.copy(id = id.toInt())
                    AlarmScheduler.scheduleAlarm(context, created)
                    showAddSheet = false
                    Toast.makeText(context, "Alarm set for ${created.formatTime()}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun MainAppScreen(
    repository: AlarmRepository,
    onTestChallenge: (ChallengeType) -> Unit,
    onTestCombo: (Int) -> Unit = {},
    onTestAlarmRing: (AlarmEntity) -> Unit
) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("force_alarm_ui", android.content.Context.MODE_PRIVATE) }
    var showSplash by remember { mutableStateOf(!uiPrefs.getBoolean("splash_seen", false)) }

    if (showSplash) {
        SplashScreen(
            onStart = {
                uiPrefs.edit().putBoolean("splash_seen", true).apply()
                showSplash = false
            }
        )
    } else {
        MainTabsScreen(
            repository = repository,
            onTestChallenge = onTestChallenge,
            onTestCombo = onTestCombo,
            onTestAlarmRing = onTestAlarmRing
        )
    }
}

@Composable
fun SplashScreen(onStart: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(1.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0D2442)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(196.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0A3A58)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(156.dp)
                                .clip(CircleShape)
                                .background(OceanBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Force Alarm",
                                tint = Color.White,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Force Alarm",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You have to get up. No snooze.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            OceanGradientButton(
                text = "Get started",
                onClick = onStart,
                modifier = Modifier.testTag("splash_start_button")
            )
        }
    }
}

@Composable
private fun StatsTab(padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Stats",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }
        item { WakeStatsCard() }
    }
}

@Composable
private fun SettingsTab(
    padding: PaddingValues,
    onTestChallenge: (ChallengeType) -> Unit,
    onTestCombo: (Int) -> Unit
) {
    val context = LocalContext.current
    val themeMode by ThemePreferences.themeModeFlow.collectAsState()
    var showTestTools by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Settings",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
        }
        item {
            Text(
                text = "Appearance",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(AppThemeMode.values().toList()) { mode ->
            val selected = mode == themeMode
            val icon = when (mode) {
                AppThemeMode.SYSTEM -> Icons.Default.PhoneAndroid
                AppThemeMode.DARK -> Icons.Default.Bedtime
                AppThemeMode.LIGHT -> Icons.Default.LightMode
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .then(
                        if (selected) Modifier.background(OceanBrush)
                        else Modifier.background(MaterialTheme.colorScheme.surface)
                    )
                    .clickable { ThemePreferences.setThemeMode(context, mode) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("theme_${mode.name}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OceanIconTile(icon = icon, onGradient = selected)
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = mode.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White
                    )
                }
            }
        }

        item {
            Text(
                text = "Setup",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item { PermissionSetupCard() }

        item {
            Column {
                TextButton(onClick = { showTestTools = !showTestTools }) {
                    Text(
                        text = if (showTestTools) "Hide test tools" else "Show test tools",
                        color = OceanCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (showTestTools) {
                    QuickChallengeTestBar(onTestChallenge = onTestChallenge, onTestCombo = onTestCombo)
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun NextAlarmHeroCard(next: Pair<AlarmEntity, Long>?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(OceanBrush)
            .padding(horizontal = 24.dp, vertical = 26.dp)
    ) {
        if (next != null) {
            Column {
                Text(
                    text = timeLabel(next.first),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatTimeLeft(next.second)} \u00B7 ${taskName(next.first)}",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        } else {
            Column {
                Text(
                    text = "No alarm set",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap New alarm to create one",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }
    }
}

@Composable
fun QuickChallengeTestBar(onTestChallenge: (ChallengeType) -> Unit, onTestCombo: (Int) -> Unit = {}) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚡ Test Any 3-Stage Challenge",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ChallengeType.values().filter { it != ChallengeType.COMBO }) { type ->
                ChallengeChip(type = type, onClick = { onTestChallenge(type) })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Test a combo",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ComboCatalog.all) { combo ->
                Surface(
                    onClick = { onTestCombo(combo.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.testTag("test_combo_${combo.id}")
                ) {
                    Text(
                        text = "C${combo.id} ${combo.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChallengeChip(type: ChallengeType, onClick: () -> Unit) {
    val icon = when (type) {
        ChallengeType.TYPING -> Icons.Default.Keyboard
        ChallengeType.IMAGE_ARRANGEMENT -> Icons.Default.GridOn
        ChallengeType.CHARGER -> Icons.Default.Power
        ChallengeType.CARD_ARRANGEMENT -> Icons.Default.GridOn
        ChallengeType.PATTERN_LOCK -> Icons.Default.Lock
        ChallengeType.ODD_ONE_OUT -> Icons.Default.Visibility
        ChallengeType.REACTION_GAME -> Icons.Default.Speed
        ChallengeType.MATH -> Icons.Default.Science
        ChallengeType.SHAKE -> Icons.Default.PhoneAndroid
        ChallengeType.STEPS -> Icons.Default.Timer
        ChallengeType.QR -> Icons.Default.QrCode
        ChallengeType.WALK_MATH -> Icons.Default.DirectionsWalk
        ChallengeType.TURN_WALK -> Icons.Default.TurnLeft
        ChallengeType.FACE_CHECK -> Icons.Default.Face
        ChallengeType.SQUAT -> Icons.Default.FitnessCenter
        ChallengeType.VOICE_MATH -> Icons.Default.Mic
        ChallengeType.LIGHT -> Icons.Default.LightMode
        ChallengeType.OBJECT_PHOTO -> Icons.Default.CameraAlt
        ChallengeType.NFC_TAP -> Icons.Default.Nfc
        ChallengeType.COMBO -> Icons.Default.Bolt
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.testTag("test_chip_${type.name}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = type.displayName, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = type.displayName.split(" ").first(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AlarmCardItem(
    alarm: AlarmEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onTestRing: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val dim = if (alarm.isEnabled) 1f else 0.55f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OceanIconTile(icon = challengeIconFor(alarm.challengeType))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timeLabel(alarm),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim)
            )
            Text(
                text = "${shortDays(alarm)} \u00B7 ${taskName(alarm)}",
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim)
            )
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Test ring") },
                    onClick = {
                        menuOpen = false
                        onTestRing()
                    },
                    modifier = Modifier.testTag("test_ring_btn_${alarm.id}")
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("delete_alarm_btn_${alarm.id}")
                )
            }
        }
        Switch(
            checked = alarm.isEnabled,
            onCheckedChange = onToggle,
            colors = oceanSwitchColors(),
            modifier = Modifier.testTag("alarm_switch_${alarm.id}")
        )
    }
}

@Composable
fun EmptyAlarmsState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OceanIconTile(icon = Icons.Default.Alarm, size = 72.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No alarms yet",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Create an alarm that only stops when you are really awake",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAlarmSheet(
    initialAlarm: AlarmEntity?,
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit
) {
    val cal = Calendar.getInstance()
    var selectedHour by remember { mutableIntStateOf(initialAlarm?.hour ?: cal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(initialAlarm?.minute ?: ((cal.get(Calendar.MINUTE) + 2) % 60)) }
    var label by remember { mutableStateOf(initialAlarm?.label ?: "Wake Up Routine") }
    var selectedChallengeType by remember { mutableStateOf(initialAlarm?.challengeType ?: ChallengeType.MATH) }
    var comboId by remember { mutableIntStateOf(initialAlarm?.comboId?.takeIf { it > 0 } ?: 1) }
    val selectedCombo = ComboCatalog.byId(comboId)
    val needsQr = selectedChallengeType == ChallengeType.QR ||
        (selectedChallengeType == ChallengeType.COMBO && selectedCombo?.needsQr == true)
    val needsNfc = selectedChallengeType == ChallengeType.NFC_TAP ||
        (selectedChallengeType == ChallengeType.COMBO && selectedCombo?.needsNfc == true)
    val needsObject = selectedChallengeType == ChallengeType.OBJECT_PHOTO ||
        (selectedChallengeType == ChallengeType.COMBO && selectedCombo?.needsObject == true)
    var nfcTag by remember { mutableStateOf(initialAlarm?.nfcTag) }
    var showNfcDialog by remember { mutableStateOf(false) }
    val sheetScroll = rememberScrollState()
    var selectedDays by remember { mutableIntStateOf(initialAlarm?.daysOfWeek ?: 127) }
    var refLabels by remember { mutableStateOf(initialAlarm?.referenceLabels ?: "") }
    val editorContext = LocalContext.current
    var qrCode by remember { mutableStateOf(initialAlarm?.qrCode) }
    var showQrScanner by remember { mutableStateOf(false) }
    val qrPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            Toast.makeText(editorContext, "Camera permission is needed to scan your QR", Toast.LENGTH_LONG).show()
        }
    }

    if (showNfcDialog) {
        val nfcActivity = editorContext.findActivity()
        DisposableEffect(Unit) {
            val nfcAdapter = NfcAdapter.getDefaultAdapter(editorContext)
            if (nfcActivity != null && nfcAdapter != null) {
                nfcAdapter.enableReaderMode(
                    nfcActivity,
                    NfcAdapter.ReaderCallback { tag ->
                        val id = tag.id.joinToString("") { "%02X".format(it) }
                        nfcActivity.runOnUiThread {
                            nfcTag = id
                            showNfcDialog = false
                        }
                    },
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                    null
                )
            }
            onDispose {
                if (nfcActivity != null && nfcAdapter != null) nfcAdapter.disableReaderMode(nfcActivity)
            }
        }
        AlertDialog(
            onDismissRequest = { showNfcDialog = false },
            title = { Text("Register your NFC tag") },
            text = { Text("Hold the back of your phone against the NFC tag now.") },
            confirmButton = { TextButton(onClick = { showNfcDialog = false }) { Text("Cancel") } }
        )
    }

    if (showQrScanner) {
        Dialog(
            onDismissRequest = { showQrScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                Box(modifier = Modifier.fillMaxSize()) {
                    QrScannerView(
                        modifier = Modifier.fillMaxSize(),
                        onCodeScanned = { value ->
                            qrCode = value
                            showQrScanner = false
                        }
                    )
                    Text(
                        text = "Point the camera at your QR / barcode",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(24.dp)
                    )
                    TextButton(
                        onClick = { showQrScanner = false },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(24.dp)
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                }
            }
        }
    }

    var showPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        if (showPicker) {
            ChallengePickerContent(
                selected = selectedChallengeType,
                onSelect = { selectedChallengeType = it },
                onConfirm = { showPicker = false }
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(sheetScroll)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = if (initialAlarm == null) "Set alarm" else "Edit alarm",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Big time picker: hour, minute and AM / PM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayHour = if (selectedHour == 0) 12 else if (selectedHour > 12) selectedHour - 12 else selectedHour
                val isAm = selectedHour < 12

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                        Text("\u25B2", color = OceanCyan, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = String.format("%02d", displayHour),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = { selectedHour = if (selectedHour == 0) 23 else selectedHour - 1 }) {
                        Text("\u25BC", color = OceanCyan, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = ":",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { selectedMinute = (selectedMinute + 1) % 60 }) {
                        Text("\u25B2", color = OceanCyan, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = String.format("%02d", selectedMinute),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = { selectedMinute = if (selectedMinute == 0) 59 else selectedMinute - 1 }) {
                        Text("\u25BC", color = OceanCyan, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("AM", "PM").forEach { period ->
                        val active = (period == "AM") == isAm
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (active) Modifier.background(OceanBrush)
                                    else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                .clickable {
                                    if (period == "AM" && !isAm) selectedHour -= 12
                                    if (period == "PM" && isAm) selectedHour += 12
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Repeat days (Mon..Sun). Bit 0 = Sunday, bit 1 = Monday ... bit 6 = Saturday
            Text(
                text = "Repeat",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
                val dayBits = listOf(1, 2, 3, 4, 5, 6, 0)
                dayLetters.forEachIndexed { index, letter ->
                    val bit = dayBits[index]
                    val on = (selectedDays and (1 shl bit)) != 0
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .then(
                                if (on) Modifier.background(OceanBrush)
                                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            .clickable { selectedDays = selectedDays xor (1 shl bit) }
                            .testTag("day_chip_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            fontWeight = FontWeight.Bold,
                            color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Label
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Alarm label") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alarm_label_input"),
                shape = RoundedCornerShape(20.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Selected challenge row, tap to change
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { showPicker = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("open_challenge_picker"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OceanIconTile(icon = challengeIconFor(selectedChallengeType))
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = if (selectedChallengeType == ChallengeType.COMBO) "Combos" else selectedChallengeType.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Change",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OceanCyan
                )
            }

            if (selectedChallengeType == ChallengeType.COMBO) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Choose a combo (the tasks run one after another)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                ComboCatalog.all.forEach { combo ->
                    val selected = combo.id == comboId
                    Surface(
                        onClick = { comboId = combo.id },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("select_combo_${combo.id}")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(combo.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(combo.level, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = combo.steps.joinToString("  \u2192  ") { it.type.displayName },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Force ${combo.force}/5  \u2022  ${combo.note}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (needsQr) {
                val registeredQr = qrCode
                Spacer(modifier = Modifier.height(12.dp))
                Text("Your QR / Barcode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = if (registeredQr.isNullOrBlank()) {
                        "Not registered yet. Keep a QR or barcode in another room (like the bathroom) and scan it here once."
                    } else {
                        "Registered: ${registeredQr.take(24)}"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            editorContext, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) showQrScanner = true
                        else qrPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_qr_button")
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (registeredQr.isNullOrBlank()) "Scan & Register QR" else "Scan Again to Change")
                }
            }

            if (needsNfc) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Your NFC tag", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = if (nfcTag.isNullOrBlank()) {
                        "Not registered yet. Stick an NFC tag in another room (like the bathroom) and touch it here once."
                    } else {
                        "Registered tag: ${nfcTag}"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        val nfcAdapter = NfcAdapter.getDefaultAdapter(editorContext)
                        if (nfcAdapter == null) {
                            Toast.makeText(editorContext, "This phone has no NFC", Toast.LENGTH_LONG).show()
                        } else if (!nfcAdapter.isEnabled) {
                            Toast.makeText(editorContext, "Switch NFC on in the phone settings first", Toast.LENGTH_LONG).show()
                        } else {
                            showNfcDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_nfc_button")
                ) {
                    Text(if (nfcTag.isNullOrBlank()) "Touch & Register NFC Tag" else "Touch Again to Change")
                }
            }

            if (needsObject) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = refLabels,
                    onValueChange = { refLabels = it },
                    label = { Text("Object(s) to show the camera") },
                    placeholder = { Text("bottle, cup, shoe, plant, chair, book, bag") },
                    supportingText = { Text("Everyday objects work best. Separate several with commas.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("object_labels_input")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save action
            OceanGradientButton(
                text = "Save alarm",
                icon = Icons.Default.Check,
                onClick = {
                    if (needsQr && qrCode.isNullOrBlank()) {
                        Toast.makeText(editorContext, "Please register your QR first", Toast.LENGTH_LONG).show()
                        return@OceanGradientButton
                    }
                    if (needsNfc && nfcTag.isNullOrBlank()) {
                        Toast.makeText(editorContext, "Please register your NFC tag first", Toast.LENGTH_LONG).show()
                        return@OceanGradientButton
                    }
                    if (needsObject && refLabels.isBlank()) {
                        Toast.makeText(editorContext, "Please type which object to show", Toast.LENGTH_LONG).show()
                        return@OceanGradientButton
                    }
                    val entity = AlarmEntity(
                        id = initialAlarm?.id ?: 0,
                        hour = selectedHour,
                        minute = selectedMinute,
                        label = label.ifBlank { "Force Alarm" },
                        isEnabled = true,
                        daysOfWeek = selectedDays,
                        challengeType = selectedChallengeType,
                        referenceLabels = refLabels,
                        qrCode = qrCode,
                        nfcTag = nfcTag,
                        comboId = if (selectedChallengeType == ChallengeType.COMBO) comboId else 0
                    )
                    onSave(entity)
                },
                modifier = Modifier.testTag("save_alarm_button")
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        }
    }
}

@Composable
private fun ChallengePickerContent(
    selected: ChallengeType,
    onSelect: (ChallengeType) -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Pick a challenge",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "It only stops once you are awake",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        ChallengeType.values().forEach { type ->
            val isSelected = type == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .then(
                        if (isSelected) Modifier.background(OceanBrush)
                        else Modifier.background(MaterialTheme.colorScheme.surface)
                    )
                    .clickable { onSelect(type) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("select_challenge_${type.name}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OceanIconTile(icon = challengeIconFor(type), onGradient = isSelected)
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = if (type == ChallengeType.COMBO) "Combos" else type.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OceanGradientButton(text = "Confirm", onClick = onConfirm, modifier = Modifier.testTag("confirm_challenge"))
        Spacer(modifier = Modifier.height(12.dp))
    }
}

// Finds the Activity behind a Context (needed for NFC reader mode inside the bottom sheet)
private fun android.content.Context.findActivity(): Activity? {
    var current: android.content.Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
