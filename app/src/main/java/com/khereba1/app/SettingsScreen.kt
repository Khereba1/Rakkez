package com.khereba1.app

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: MainViewModel, onLanguage: (String) -> Unit, onFont: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val st = vm.settings
    val lang = remember { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", "system") ?: "system" }
    val font = remember { fontKey(ctx) }
    val theme = remember { themeKey(ctx) }
    var showLog by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }
    var bkBasic by remember { mutableStateOf(true) }
    var bkLedger by remember { mutableStateOf(true) }
    var bkNotes by remember { mutableStateOf(true) }
    var bkMedia by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Throwable) {
            ""
        }
    }
    val basicSize = remember(vm.backupMsg) { Backup.fmtSize(Backup.basicSize(ctx)) }
    val ledgerSize = remember(vm.backupMsg, vm.ledgers) { Backup.fmtSize(Backup.ledgerSize(ctx)) }
    val notesSize = remember(vm.backupMsg, vm.notes) { Backup.fmtSize(Backup.notesSize(ctx)) }
    val mediaSize = remember(vm.backupMsg, vm.notes) { Backup.fmtSize(Backup.mediaSize(ctx)) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareRestore(uri)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackChevron { vm.closeOverlay() }
            Text(stringResource(R.string.tab_settings), color = cs.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // language
            Column(Modifier.fillMaxWidth().panel(cs.surface, 20.dp).padding(16.dp)) {
                Text(stringResource(R.string.language_label), color = cs.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SegPill(stringResource(R.string.lang_system), lang == "system", { onLanguage("system") })
                    SegPill(stringResource(R.string.lang_arabic), lang == "ar", { onLanguage("ar") })
                    SegPill(stringResource(R.string.lang_english), lang == "en", { onLanguage("en") })
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.font_label), color = cs.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SegPill(stringResource(R.string.font_cairo), font == "cairo", { onFont("cairo") })
                    SegPill(stringResource(R.string.font_plex), font == "plex", { onFont("plex") })
                    SegPill(stringResource(R.string.font_system), font == "system", { onFont("system") })
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.theme_label), color = cs.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SegPill(stringResource(R.string.theme_system), theme == "system", {
                        vm.changeTheme("system")
                        onFont(font)
                    })
                    SegPill(stringResource(R.string.theme_light), theme == "light", {
                        vm.changeTheme("light")
                        onFont(font)
                    })
                    SegPill(stringResource(R.string.theme_dark), theme == "dark", {
                        vm.changeTheme("dark")
                        onFont(font)
                    })
                }
            }

            Spacer(Modifier.height(12.dp))

            // general
            Column(Modifier.fillMaxWidth().panel(cs.surface, 20.dp).padding(16.dp)) {
                Text(stringResource(R.string.set_general), color = cs.onSurfaceVariant, fontSize = 13.sp)
                Stepper(
                    stringResource(R.string.set_goal),
                    stringResource(R.string.unit_min, st.goalMin.toString()),
                    { d -> vm.applySettings(st.copy(goalMin = (st.goalMin + d * 15).coerceIn(15, 720))) },
                )
                Stepper(stringResource(R.string.short_len), stringResource(R.string.unit_min, st.shortMin.toString())) { d -> vm.adjustShort(d) }
                Stepper(stringResource(R.string.long_len), stringResource(R.string.unit_min, st.longMin.toString())) { d -> vm.adjustLong(d) }
                Stepper(stringResource(R.string.long_every), st.longEvery.toString()) { d -> vm.adjustEvery(d) }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.set_keep_screen), Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
                    Switch(checked = st.keepOn, onCheckedChange = { v -> vm.applySettings(st.copy(keepOn = v)) })
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.reset_defaults)) }
            }

            Spacer(Modifier.height(12.dp))

            // backup
            Column(Modifier.fillMaxWidth().panel(cs.surface, 20.dp).padding(16.dp)) {
                Text(stringResource(R.string.backup_title), color = cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.backup_where, Backup.DIR),
                    color = cs.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.backup_include), color = cs.onSurfaceVariant, fontSize = 13.sp)
                CheckRow(stringResource(R.string.backup_basic), basicSize, bkBasic) { bkBasic = !bkBasic }
                CheckRow(stringResource(R.string.backup_ledger), ledgerSize, bkLedger) { bkLedger = !bkLedger }
                CheckRow(stringResource(R.string.backup_notes), notesSize, bkNotes) { bkNotes = !bkNotes }
                CheckRow(stringResource(R.string.backup_media), mediaSize, bkMedia) { bkMedia = !bkMedia }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.runBackup(bkBasic, bkLedger, bkNotes, bkMedia) },
                    enabled = !vm.backupBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) { Text(stringResource(if (vm.backupBusy) R.string.backup_working else R.string.backup_now), fontWeight = FontWeight.Bold) }
                TextButton(
                    onClick = {
                        try {
                            picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        } catch (e: Throwable) {
                            AppLog.log("BACKUP", "Picker failed", e)
                        }
                    },
                ) { Text(stringResource(R.string.restore_button)) }
                if (vm.backupMsg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(vm.backupMsg, color = cs.primary, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_backup), color = cs.onSurface, fontSize = 15.sp)
                        Text(stringResource(R.string.auto_backup_hint), color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Switch(checked = st.autoBackup, onCheckedChange = { v -> vm.changeAutoBackup(v) })
                }
            }

            Spacer(Modifier.height(12.dp))

            // log
            Column(Modifier.fillMaxWidth().panel(cs.surface, 20.dp).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.error_log), Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
                    TextButton(onClick = {
                        showLog = !showLog
                        if (showLog) logText = AppLog.read()
                    }) { Text(stringResource(if (showLog) R.string.hide else R.string.show)) }
                    if (showLog) {
                        TextButton(onClick = {
                            AppLog.clear()
                            logText = ""
                        }) { Text(stringResource(R.string.clear_log)) }
                    }
                }
                if (showLog) {
                    SelectionContainer {
                        Text(
                            if (logText.isEmpty()) stringResource(R.string.log_empty) else logText.takeLast(6000),
                            color = cs.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.developer_label) + ": " + stringResource(R.string.developer_name) + "   ·   v" + version,
                color = cs.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text(stringResource(R.string.reset_defaults_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetTimerDefaults()
                    confirmReset = false
                }) { Text(stringResource(R.string.confirm_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Shown from anywhere once a backup file has been picked. */
@Composable
fun RestoreDialog(vm: MainViewModel) {
    val info = vm.restoreInfo ?: return
    val cs = MaterialTheme.colorScheme
    var useBasic by remember(info) { mutableStateOf(info.basic) }
    var useLedger by remember(info) { mutableStateOf(info.ledger) }
    var useNotes by remember(info) { mutableStateOf(info.notes) }
    var useMedia by remember(info) { mutableStateOf(info.media) }
    AlertDialog(
        onDismissRequest = { vm.cancelRestore() },
        title = { Text(stringResource(R.string.restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.restore_warn), color = cs.onSurface, fontSize = 14.sp, lineHeight = 21.sp)
                if (info.basic) CheckRow(stringResource(R.string.backup_basic), "", useBasic) { useBasic = !useBasic }
                if (info.ledger) CheckRow(stringResource(R.string.backup_ledger), "", useLedger) { useLedger = !useLedger }
                if (info.notes) CheckRow(stringResource(R.string.backup_notes), "", useNotes) { useNotes = !useNotes }
                if (info.media) CheckRow(stringResource(R.string.backup_media), "", useMedia) { useMedia = !useMedia }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.confirmRestore(useBasic && info.basic, useLedger && info.ledger, useNotes && info.notes, useMedia && info.media) },
                enabled = (useBasic && info.basic) || (useLedger && info.ledger) || (useNotes && info.notes) || (useMedia && info.media),
            ) { Text(stringResource(R.string.restore_confirm), color = cs.error) }
        },
        dismissButton = { TextButton(onClick = { vm.cancelRestore() }) { Text(stringResource(R.string.cancel)) } },
    )
}
