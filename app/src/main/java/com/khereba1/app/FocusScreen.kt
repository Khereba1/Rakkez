package com.khereba1.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@Composable
fun FocusScreen(vm: MainViewModel, onBeforeStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val t = vm.timer
    val st = vm.settings
    val active = t.phase == PH_FOCUS || t.phase == PH_SHORT || t.phase == PH_LONG
    val isBreak = t.phase == PH_SHORT || t.phase == PH_LONG
    val remaining = if (active) t.remaining(vm.nowMs) else 0L
    val accent = if (isBreak) cs.tertiary else cs.primary
    val onAccent = if (isBreak) cs.onTertiary else cs.onPrimary
    val progress = if (active && t.totalMs > 0L) (1f - remaining.toFloat() / t.totalMs.toFloat()).coerceIn(0f, 1f) else 0f
    val shownMs = if (active) remaining else st.focusMin * 60_000L
    val totalSec = (shownMs + 999L) / 1000L
    val timeText = String.format(Locale.US, "%02d:%02d", totalSec / 60L, totalSec % 60L)
    val selected = vm.tasks.firstOrNull { it.id == t.taskId }

    var showPicker by remember { mutableStateOf(false) }
    var showIdea by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    LaunchedEffect(vm.plusRequest) {
        if (vm.plusRequest == PLUS_IDEA_FOCUS) {
            showIdea = true
            vm.consumePlus()
        }
    }

    // Keep the screen awake while a period is running (optional in Settings).
    val view = LocalView.current
    val keep = active && t.running && st.keepOn
    DisposableEffect(keep) {
        view.keepScreenOn = keep
        onDispose { view.keepScreenOn = false }
    }

    val phaseName = when (t.phase) {
        PH_FOCUS -> stringResource(R.string.phase_focus)
        PH_SHORT -> stringResource(R.string.phase_short)
        PH_LONG -> stringResource(R.string.phase_long)
        PH_ASK -> stringResource(R.string.phase_ask)
        else -> stringResource(R.string.phase_idle)
    }
    val subText = when {
        t.phase == PH_FOCUS && t.extra -> stringResource(R.string.extra_session)
        isBreak -> phaseName
        !st.cycleMode -> stringResource(R.string.single_session)
        else -> {
            val n = if (t.cycle >= st.longEvery) st.longEvery else t.cycle + 1
            stringResource(R.string.cycle_label, n.toString(), st.longEvery.toString())
        }
    }
    val todayMin = vm.todayFocusMs() / 60000L

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.tab_focus),
                modifier = Modifier.weight(1f),
                color = cs.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            GearButton { vm.openOverlay(OVERLAY_SETTINGS) }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // current task
            Row(
                Modifier
                    .fillMaxWidth()
                    .panel(cs.surface, 18.dp)
                    .clickable { showPicker = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.focus_task_label), color = cs.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        selected?.title ?: stringResource(R.string.focus_task_none),
                        color = cs.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Text("▾", color = cs.onSurfaceVariant, fontSize = 16.sp)
            }

            // single session / cycle
            if (!active) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .panel(cs.surface, 22.dp)
                        .padding(4.dp),
                ) {
                    ModeTab(stringResource(R.string.mode_single), !st.cycleMode, Modifier.weight(1f)) { vm.changeCycleMode(false) }
                    ModeTab(stringResource(R.string.mode_cycle), st.cycleMode, Modifier.weight(1f)) { vm.changeCycleMode(true) }
                }
            }

            // the dial
            DotRing(
                progress = progress,
                lit = accent,
                dim = cs.outlineVariant,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        timeText,
                        style = TextStyle(
                            color = cs.onSurface,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Light,
                            fontFeatureSettings = "tnum",
                            letterSpacing = 0.sp,
                        ),
                    )
                    Text(subText, color = cs.onSurfaceVariant, fontSize = 13.sp)
                }
            }

            if (!active) {
                // duration: - / + (hold to repeat) and three quick presets
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HoldBtn("−", -1, { d -> vm.adjustFocus(d) }, size = 42.dp)
                    Text(
                        stringResource(R.string.unit_minutes_long, st.focusMin.toString()),
                        modifier = Modifier.widthIn(min = 96.dp),
                        textAlign = TextAlign.Center,
                        color = cs.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    HoldBtn("+", 1, { d -> vm.adjustFocus(d) }, size = 42.dp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetPill(stringResource(R.string.preset_sprint), 15, st.focusMin, Modifier.weight(1f)) { vm.setFocusMinutes(15) }
                    PresetPill(stringResource(R.string.preset_classic), 25, st.focusMin, Modifier.weight(1f)) { vm.setFocusMinutes(25) }
                    PresetPill(stringResource(R.string.preset_deep), 45, st.focusMin, Modifier.weight(1f)) { vm.setFocusMinutes(45) }
                }

                if (st.cycleMode) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .panel(cs.surface, 18.dp)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Stepper(
                            stringResource(R.string.short_len),
                            stringResource(R.string.unit_min, st.shortMin.toString()),
                        ) { d -> vm.adjustShort(d) }
                        Stepper(
                            stringResource(R.string.long_len),
                            stringResource(R.string.unit_min, st.longMin.toString()),
                        ) { d -> vm.adjustLong(d) }
                        Stepper(
                            stringResource(R.string.long_every),
                            st.longEvery.toString(),
                        ) { d -> vm.adjustEvery(d) }
                    }
                }
            }

            // controls
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!active) {
                    Button(
                        onClick = {
                            onBeforeStart()
                            vm.startFocus()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .widthIn(min = 230.dp),
                    ) {
                        Text(stringResource(R.string.btn_start), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { if (t.running) vm.pause() else vm.resume() },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .widthIn(min = 150.dp),
                    ) {
                        Text(
                            stringResource(if (t.running) R.string.btn_pause else R.string.btn_resume),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedButton(
                        onClick = { if (isBreak) vm.stop(false) else confirmStop = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onSurfaceVariant),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text(stringResource(if (isBreak) R.string.btn_skip_break else R.string.btn_stop), fontSize = 15.sp)
                    }
                }
            }

            if (t.phase == PH_FOCUS) {
                OutlinedButton(
                    onClick = { showIdea = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.primary),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(stringResource(R.string.idea_button), fontSize = 14.sp)
                }
            }

            if (!active) {
                TextButton(onClick = { vm.resetTimerDefaults() }) {
                    Text(stringResource(R.string.reset_defaults), color = cs.onSurfaceVariant, fontSize = 13.sp)
                }
            }

            Text(
                stringResource(R.string.today_so_far, fmtMinutes(ctx, todayMin)) + "  ›",
                color = cs.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { vm.openOverlay(OVERLAY_TODAY) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showPicker) {
        TaskPickerDialog(
            tasks = vm.tasks.filter { !it.done },
            onPick = { id ->
                vm.selectTask(id)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
    if (showIdea) {
        IdeaDialog(onDismiss = { showIdea = false }, onSave = { text -> vm.addIdea(text) })
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.stop_confirm_title)) },
            text = { Text(stringResource(R.string.stop_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    vm.stop(true)
                }) { Text(stringResource(R.string.stop_keep)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        confirmStop = false
                        vm.stop(false)
                    }) { Text(stringResource(R.string.stop_discard), color = cs.error) }
                    TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.stop_continue)) }
                }
            },
        )
    }
}

@Composable
fun ModeTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .panel(if (selected) cs.primaryContainer else cs.surface, 18.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
fun PresetPill(name: String, minutes: Int, current: Int, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sel = current == minutes
    Box(
        modifier = modifier
            .panel(if (sel) cs.primary else cs.surfaceVariant, 20.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name + " " + minutes.toString(),
            color = if (sel) cs.onPrimary else cs.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun TaskPickerDialog(tasks: List<Task>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_task_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.no_task_option),
                    color = cs.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(0L) }
                        .padding(vertical = 12.dp),
                )
                for (task in tasks) {
                    Text(
                        task.title,
                        color = cs.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(task.id) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun IdeaDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.idea_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.idea_hint)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) onSave(text)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Shown after every finished focus session: keep going for a few more minutes, take a break, or stop. */
@Composable
fun AskDialog(vm: MainViewModel) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val st = vm.settings
    var custom by remember { mutableStateOf("") }
    val isLong = st.cycleMode && vm.timer.cycle >= st.longEvery
    val breakMin = if (isLong) st.longMin else st.shortMin
    val customMin = custom.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.ask_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.ask_text, fmtMinutes(ctx, vm.todayFocusMs() / 60000L)),
                    color = cs.onSurfaceVariant,
                )
                Text(stringResource(R.string.ask_extra_label), color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in listOf(5, 10, 15, 20)) {
                        Pill(
                            text = "+" + m.toString(),
                            selected = false,
                            onClick = { vm.startExtra(m) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { v -> custom = v.filter { ch -> ch.isDigit() }.take(3) },
                        placeholder = { Text(stringResource(R.string.ask_minutes_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { vm.startExtra(customMin) },
                        enabled = customMin in 1..180,
                    ) { Text(stringResource(R.string.ask_go)) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.startBreak() },
                colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary),
            ) { Text(stringResource(R.string.ask_break, breakMin.toString()), textAlign = TextAlign.Center) }
        },
        dismissButton = { TextButton(onClick = { vm.finishAsk() }) { Text(stringResource(R.string.ask_finish)) } },
    )
}
