package com.example.anki.ui.screens.more

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.anki.data.model.NoteType
import com.example.anki.data.model.SchedulerMode
import com.example.anki.data.model.ThemeMode
import com.example.anki.data.preferences.PreferencesRepository
import com.example.anki.data.preferences.UserPreferences
import com.example.anki.data.repository.AnkiRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    repository: AnkiRepository,
    preferencesRepository: PreferencesRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val prefs by preferencesRepository.userPreferences.collectAsStateWithLifecycle(initialValue = UserPreferences())

    var showAccountDialog by remember { mutableStateOf(false) }
    var showSchedulerDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showNoteTypesDialog by remember { mutableStateOf(false) }
    var exportPreviewJson by remember { mutableStateOf<String?>(null) }

    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings & Tools", style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("AnkiWeb Account & Sync", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        SettingsActionRow(
                            icon = Icons.Filled.AccountCircle,
                            title = if (prefs.ankiWebAccount.isBlank()) "AnkiWeb Account" else prefs.ankiWebAccount,
                            subtitle = if (prefs.ankiWebAccount.isBlank()) "Tap to configure sync profile" else "Signed in",
                            onClick = { showAccountDialog = true },
                            testTag = "more_account_row",
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsActionRow(
                            icon = Icons.Filled.CloudSync,
                            title = "Sync Collection",
                            subtitle = prefs.lastSyncAtMillis?.let { "Last synced: ${dateFmt.format(Date(it))}" } ?: "Not synced yet",
                            onClick = {
                                scope.launch {
                                    preferencesRepository.recordSyncNow()
                                    snackbarHostState.showSnackbar("Collection synced")
                                }
                            },
                            testTag = "more_sync_row",
                        )
                    }
                }
            }

            item {
                Text("Reviewing & Scheduling", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        SettingsActionRow(
                            icon = Icons.Filled.Psychology,
                            title = "Spaced Repetition Algorithm",
                            subtitle = prefs.schedulerMode.displayName,
                            onClick = { showSchedulerDialog = true },
                            testTag = "more_scheduler_row",
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsSwitchRow(
                            icon = Icons.Filled.Timer,
                            title = "Show answer timer",
                            subtitle = "Display seconds spent on current card",
                            checked = prefs.showStudyTimer,
                            onCheckedChange = { scope.launch { preferencesRepository.setShowStudyTimer(it) } },
                            testTag = "more_timer_switch",
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsSwitchRow(
                            icon = Icons.Filled.Info,
                            title = "Show next review intervals",
                            subtitle = "Display interval labels on Again / Hard / Good / Easy",
                            checked = prefs.showNextTimes,
                            onCheckedChange = { scope.launch { preferencesRepository.setShowNextTimes(it) } },
                            testTag = "more_intervals_switch",
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsSwitchRow(
                            icon = Icons.Filled.Notifications,
                            title = "Daily study reminder",
                            subtitle = if (prefs.reminderEnabled) "Scheduled at 20:00" else "Off",
                            checked = prefs.reminderEnabled,
                            onCheckedChange = {
                                scope.launch { preferencesRepository.setReminder(it, 20, 0) }
                            },
                            testTag = "more_reminder_switch",
                        )
                    }
                }
            }

            item {
                Text("Appearance & Collection", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        SettingsActionRow(
                            icon = Icons.Filled.Brightness6,
                            title = "Theme",
                            subtitle = prefs.themeMode.displayName,
                            onClick = { showThemeDialog = true },
                            testTag = "more_theme_row",
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsActionRow(
                            icon = Icons.Filled.Style,
                            title = "Manage Note Types",
                            subtitle = "${NoteType.entries.size} built-in card templates (Basic, Reversed, Cloze, Type Answer)",
                            onClick = { showNoteTypesDialog = true },
                            testTag = "more_notetypes_row",
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsActionRow(
                            icon = Icons.Filled.Download,
                            title = "Export Collection Summary",
                            subtitle = "View & share JSON backup summary",
                            onClick = {
                                scope.launch {
                                    exportPreviewJson = repository.exportCollectionSummaryJson()
                                }
                            },
                            testTag = "more_export_row",
                        )
                    }
                }
            }
        }
    }

    if (showAccountDialog) {
        var email by remember(prefs.ankiWebAccount) { mutableStateOf(prefs.ankiWebAccount) }
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("AnkiWeb Account") },
            text = {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { preferencesRepository.setAnkiWebAccount(email) }
                        showAccountDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                if (prefs.ankiWebAccount.isNotBlank()) {
                    TextButton(
                        onClick = {
                            scope.launch { preferencesRepository.setAnkiWebAccount("") }
                            showAccountDialog = false
                        },
                    ) {
                        Text("Sign out")
                    }
                } else {
                    TextButton(onClick = { showAccountDialog = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (showSchedulerDialog) {
        AlertDialog(
            onDismissRequest = { showSchedulerDialog = false },
            title = { Text("Select Scheduler") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SchedulerMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { preferencesRepository.setSchedulerMode(mode) }
                                    showSchedulerDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = prefs.schedulerMode == mode,
                                onClick = {
                                    scope.launch { preferencesRepository.setSchedulerMode(mode) }
                                    showSchedulerDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(mode.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSchedulerDialog = false }) { Text("Close") }
            },
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("App Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { preferencesRepository.setThemeMode(mode) }
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = prefs.themeMode == mode,
                                onClick = {
                                    scope.launch { preferencesRepository.setThemeMode(mode) }
                                    showThemeDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Close") }
            },
        )
    }

    if (showNoteTypesDialog) {
        AlertDialog(
            onDismissRequest = { showNoteTypesDialog = false },
            title = { Text("Note Types") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NoteType.entries.forEach { t ->
                        Column {
                            Text(t.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNoteTypesDialog = false }) { Text("Done") }
            },
        )
    }

    exportPreviewJson?.let { json ->
        AlertDialog(
            onDismissRequest = { exportPreviewJson = null },
            title = { Text("Collection Export") },
            text = {
                Text(json, style = MaterialTheme.typography.labelMedium)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, json)
                            type = "application/json"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Export AnkiDroid Collection"))
                        exportPreviewJson = null
                    },
                ) {
                    Text("Share")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { exportPreviewJson = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
