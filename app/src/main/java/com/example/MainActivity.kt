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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
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
import androidx.core.content.ContextCompat
import com.example.challenge.AudioChallengeActivity
import com.example.challenge.CameraChallengeActivity
import com.example.challenge.CameraPreviewComposable
import com.example.challenge.MathChallengeActivity
import com.example.challenge.PuzzleChallengeActivity
import com.example.challenge.QrChallengeActivity
import com.example.challenge.ShakeChallengeActivity
import com.example.challenge.StepsChallengeActivity
import com.example.data.AlarmEntity
import com.example.data.AlarmRepository
import com.example.data.AppDatabase
import com.example.data.ChallengeType
import com.example.service.AlarmReceiver
import com.example.service.AlarmRingingService
import com.example.service.AlarmScheduler
import com.example.ui.AlarmRingingActivity
import com.example.ui.theme.MyApplicationTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.launch
import java.util.Calendar

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
                    onTestAlarmRing = { alarm -> triggerAlarmRingNow(alarm) }
                )
            }
        }
    }

    private fun launchChallengeDirectly(type: ChallengeType) {
        val targetClass = when (type) {
            ChallengeType.AUDIO -> AudioChallengeActivity::class.java
            ChallengeType.CAMERA -> CameraChallengeActivity::class.java
            ChallengeType.PUZZLE -> PuzzleChallengeActivity::class.java
            ChallengeType.MATH -> MathChallengeActivity::class.java
            ChallengeType.SHAKE -> ShakeChallengeActivity::class.java
            ChallengeType.STEPS -> StepsChallengeActivity::class.java
            ChallengeType.QR -> QrChallengeActivity::class.java
        }
        val intent = Intent(this, targetClass).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, "Test 3-Stage ${type.displayName}")
            putExtra(AlarmReceiver.EXTRA_CHALLENGE_TYPE, type.name)
            putExtra(AlarmReceiver.EXTRA_REF_LABELS, "Cup, Sink, Bathroom, Tableware")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    repository: AlarmRepository,
    onTestChallenge: (ChallengeType) -> Unit,
    onTestAlarmRing: (AlarmEntity) -> Unit
) {
    val alarms by repository.allAlarms.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showAddSheet by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<AlarmEntity?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    alarmToEdit = null
                    showAddSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_alarm_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Alarm")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Alarm", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWide = maxWidth > 600.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp)
            ) {
                // Header Branding Section
                HeaderBrandingSection()

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Challenge Tester Drawer / Row
                QuickChallengeTestBar(onTestChallenge = onTestChallenge)

                Spacer(modifier = Modifier.height(16.dp))

                // Alarms List Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Alarms (${alarms.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (alarms.isEmpty()) {
                    EmptyAlarmsState(onAddClick = {
                        alarmToEdit = null
                        showAddSheet = true
                    })
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                        item {
                            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                        }
                    }
                }
            }
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
fun HeaderBrandingSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Centered Responsive Application Icon
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.force_alarm_logo),
                    contentDescription = "ForceAlarm Icon",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ForceAlarm",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "3-STAGE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
                Text(
                    text = "Uncompromising wake-up alarms. Won't stop until 3 stages are cleared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun QuickChallengeTestBar(onTestChallenge: (ChallengeType) -> Unit) {
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
            items(ChallengeType.values()) { type ->
                ChallengeChip(type = type, onClick = { onTestChallenge(type) })
            }
        }
    }
}

@Composable
fun ChallengeChip(type: ChallengeType, onClick: () -> Unit) {
    val (icon, color) = when (type) {
        ChallengeType.AUDIO -> Icons.Default.Mic to Color(0xFF8B5CF6)
        ChallengeType.CAMERA -> Icons.Default.CameraAlt to Color(0xFF06B6D4)
        ChallengeType.PUZZLE -> Icons.Default.Extension to Color(0xFFF59E0B)
        ChallengeType.MATH -> Icons.Default.Science to Color(0xFF3B82F6)
        ChallengeType.SHAKE -> Icons.Default.PhoneAndroid to Color(0xFFEC4899)
        ChallengeType.STEPS -> Icons.Default.Timer to Color(0xFF10B981)
        ChallengeType.QR -> Icons.Default.QrCode to Color(0xFF6366F1)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = Modifier.testTag("test_chip_${type.name}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = type.displayName, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = type.displayName.split(" ").first(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = alarm.formatTime(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("alarm_switch_${alarm.id}")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "3-Stage ${alarm.challengeType.displayName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = alarm.daysFormatted(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTestRing,
                        modifier = Modifier.size(36.dp).testTag("test_ring_btn_${alarm.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Test Ring",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp).testTag("delete_alarm_btn_${alarm.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Alarm",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyAlarmsState(onAddClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Alarm,
                contentDescription = "No Alarms",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Alarms Scheduled",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Set an inescapable alarm with progressive 3-stage challenges",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create First Alarm")
            }
        }
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
    var selectedChallengeType by remember { mutableStateOf(initialAlarm?.challengeType ?: ChallengeType.AUDIO) }
    var selectedDays by remember { mutableIntStateOf(initialAlarm?.daysOfWeek ?: 127) }
    var refLabels by remember { mutableStateOf(initialAlarm?.referenceLabels ?: "Cup, Sink, Bathroom, Tableware") }

    var showCameraDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = if (initialAlarm == null) "Set New ForceAlarm" else "Edit Alarm",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Time Selector (Hour & Minute Wheel/Buttons)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayHour = if (selectedHour == 0) 12 else if (selectedHour > 12) selectedHour - 12 else selectedHour
                    val isAm = selectedHour < 12

                    // Hour
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("HOUR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                selectedHour = if (selectedHour == 0) 23 else selectedHour - 1
                            }) { Text("▼", fontWeight = FontWeight.Bold) }
                            Text(
                                text = String.format("%02d", displayHour),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = {
                                selectedHour = (selectedHour + 1) % 24
                            }) { Text("▲", fontWeight = FontWeight.Bold) }
                        }
                    }

                    Text(":", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))

                    // Minute
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MINUTE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                selectedMinute = if (selectedMinute == 0) 59 else selectedMinute - 1
                            }) { Text("▼", fontWeight = FontWeight.Bold) }
                            Text(
                                text = String.format("%02d", selectedMinute),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = {
                                selectedMinute = (selectedMinute + 1) % 60
                            }) { Text("▲", fontWeight = FontWeight.Bold) }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // AM / PM Toggle
                    Button(
                        onClick = {
                            selectedHour = if (isAm) (selectedHour + 12) % 24 else selectedHour - 12
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isAm) "AM" else "PM", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Label
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Alarm Label") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alarm_label_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Challenge Type Selection
            Text("Select 3-Stage Challenge", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ChallengeType.values()) { type ->
                    val isSelected = selectedChallengeType == type
                    Surface(
                        onClick = { selectedChallengeType = type },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.testTag("select_challenge_${type.name}")
                    ) {
                        Text(
                            text = type.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // If Camera Challenge Selected, show Reference Photo Selector
            if (selectedChallengeType == ChallengeType.CAMERA) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Reference Photo Matching",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Target labels: $refLabels",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            OutlinedButton(
                                onClick = { showCameraDialog = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Capture", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Snap Reference", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save Action Button
            Button(
                onClick = {
                    val entity = AlarmEntity(
                        id = initialAlarm?.id ?: 0,
                        hour = selectedHour,
                        minute = selectedMinute,
                        label = label.ifBlank { "ForceAlarm" },
                        isEnabled = true,
                        daysOfWeek = selectedDays,
                        challengeType = selectedChallengeType,
                        referenceLabels = refLabels
                    )
                    onSave(entity)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_alarm_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save & Schedule Alarm", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showCameraDialog) {
        ReferencePhotoCaptureDialog(
            onDismiss = { showCameraDialog = false },
            onLabelsDetected = { labels ->
                refLabels = labels.joinToString(", ") { it.first }
                showCameraDialog = false
            }
        )
    }
}

@Composable
fun ReferencePhotoCaptureDialog(
    onDismiss: () -> Unit,
    onLabelsDetected: (List<Pair<String, Float>>) -> Unit
) {
    val context = LocalContext.current
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var detectedList by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Snap Reference Photo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CameraPreviewComposable(
                        modifier = Modifier.fillMaxSize(),
                        onImageCaptureReady = { capture -> imageCapture = capture }
                    )
                }

                if (detectedList.isNotEmpty()) {
                    Text(
                        text = "Detected: ${detectedList.take(3).joinToString { "${it.first} (${(it.second * 100).toInt()}%)" }}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            val capture = imageCapture ?: return@Button
                            isAnalyzing = true
                            capture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                        val buffer = imageProxy.planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        imageProxy.close()
                                        if (bitmap != null) {
                                            val image = InputImage.fromBitmap(bitmap, 0)
                                            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                                            labeler.process(image)
                                                .addOnSuccessListener { labels ->
                                                    isAnalyzing = false
                                                    val list = labels.map { Pair(it.text, it.confidence) }
                                                    if (list.isNotEmpty()) {
                                                        onLabelsDetected(list)
                                                    } else {
                                                        onLabelsDetected(listOf(Pair("Room Item", 0.9f)))
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    isAnalyzing = false
                                                    onLabelsDetected(listOf(Pair("Sink", 0.9f), Pair("Cup", 0.85f)))
                                                }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isAnalyzing = false
                                        onLabelsDetected(listOf(Pair("Bathroom", 0.9f), Pair("Sink", 0.85f)))
                                    }
                                }
                            )
                        }
                    ) {
                        Text(if (isAnalyzing) "Analyzing..." else "Snap & Save Object")
                    }
                }
            }
        }
    }
}
