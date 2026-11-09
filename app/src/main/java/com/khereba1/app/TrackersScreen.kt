package com.khereba1.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun AddBar(hint: String, requester: FocusRequester, onAdd: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }
    fun submit() {
        if (draft.isNotBlank()) {
            onAdd(draft)
            draft = ""
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .panel(cs.surfaceVariant, 28.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            if (draft.isEmpty()) Text(hint, color = cs.onSurfaceVariant, fontSize = 16.sp)
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requester),
            )
        }
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(cs.primary)
                .clickable { submit() },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = cs.onPrimary, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PaneHeader(title: String, vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = cs.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        GearButton { vm.openOverlay(OVERLAY_SETTINGS) }
    }
}

// ======================================================================
// habits
// ======================================================================

@Composable
fun HabitsPane(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    val requester = remember { FocusRequester() }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var newQuit by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    LaunchedEffect(vm.plusRequest) {
        if (vm.plusRequest == PLUS_TASK) {
            try {
                requester.requestFocus()
            } catch (e: Throwable) {
                // ignore
            }
            vm.consumePlus()
        }
    }
    val today = Trackers.today()
    val active = vm.habits.filter { it.doneAt == 0L }
    val finished = vm.habits.filter { it.doneAt != 0L }
    val builds = active.filter { !it.quit }
    val doneToday = builds.count { it.days.contains(today) }
    Column(Modifier.fillMaxSize()) {
        PaneHeader(
            if (builds.isEmpty()) stringResource(R.string.sub_habits) else stringResource(R.string.habit_summary, doneToday.toString(), builds.size.toString()),
            vm,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SegPill(stringResource(R.string.habit_kind_build), !newQuit, { newQuit = false })
            SegPill(stringResource(R.string.habit_kind_quit), newQuit, { newQuit = true })
        }
        AddBar(
            stringResource(if (newQuit) R.string.habit_add_quit_hint else R.string.habit_add_hint),
            requester,
        ) { vm.addHabit(it, newQuit) }
        if (vm.habits.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.habit_empty), color = cs.onSurfaceVariant, fontSize = 15.sp, lineHeight = 24.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            ) {
                items(active, key = { it.id }) { h ->
                    HabitRow(vm, h, today) { editing = h }
                }
                if (finished.isNotEmpty()) {
                    item(key = "habit_done_header") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showDone = !showDone }
                                .padding(vertical = 14.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.habit_done_header, finished.size.toString()),
                                color = cs.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(if (showDone) "\u25B4" else "\u25BE", color = cs.onSurfaceVariant, fontSize = 16.sp)
                        }
                    }
                    if (showDone) {
                        items(finished, key = { it.id }) { h ->
                            HabitRow(vm, h, today) { editing = h }
                        }
                    }
                }
            }
        }
    }
    val e = editing
    if (e != null) HabitDialog(vm, e) { editing = null }
    val missed = vm.habits.firstOrNull { Trackers.pendingDays(it).isNotEmpty() }
    if (missed != null && e == null) {
        val n = Trackers.pendingDays(missed).size
        AlertDialog(
            onDismissRequest = { },
            title = { Text(missed.name) },
            text = {
                Text(
                    stringResource(if (n == 1) R.string.habit_missed_one else R.string.habit_missed_many, n.toString()),
                    lineHeight = 24.sp,
                )
            },
            confirmButton = { TextButton(onClick = { vm.answerMissed(missed.id, true) }) { Text(stringResource(R.string.habit_missed_kept)) } },
            dismissButton = { TextButton(onClick = { vm.answerMissed(missed.id, false) }) { Text(stringResource(R.string.habit_missed_fell), color = cs.error) } },
        )
    }
}

@Composable
private fun ProgressBar(frac: Float, color: androidx.compose.ui.graphics.Color) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(cs.surfaceVariant),
    ) {
        if (frac > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(frac.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun HabitRow(vm: MainViewModel, h: Habit, today: Long, onEdit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cur = Trackers.current(h)
    val best = Trackers.bestOf(h)
    var confirm by remember { mutableStateOf(false) }
    val achieved = h.doneAt != 0L
    val accent = if (h.quit) cs.tertiary else cs.primary
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .panel(cs.surface, 20.dp)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onEdit() },
            ) {
                Text(h.name, color = cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(if (h.quit) R.string.habit_tag_quit else R.string.habit_tag_build, h.target.toString()),
                    color = accent,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (achieved) Text("\u2713 " + stringResource(if (h.quit) R.string.habit_quit_done else R.string.habit_build_done), color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        ProgressBar(if (h.target <= 0) 0f else cur.toFloat() / h.target.toFloat(), accent)
        Text(
            stringResource(if (h.quit) R.string.habit_quit_progress else R.string.habit_build_progress, cur.toString(), h.target.toString(), best.toString()),
            color = cs.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (h.quit) {
            TextButton(onClick = { confirm = true }, modifier = Modifier.padding(top = 2.dp)) {
                Text(stringResource(R.string.habit_relapse), color = cs.error, fontWeight = FontWeight.Bold)
            }
        } else {
            val on = h.days.contains(today)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (on) cs.primary else cs.surfaceVariant)
                    .clickable { vm.toggleHabitToday(h.id) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (on) "\u2713 " + stringResource(R.string.habit_done_today) else stringResource(R.string.habit_mark_today),
                    color = if (on) cs.onPrimary else cs.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            text = stringResource(R.string.habit_relapse_confirm, h.name),
            onYes = {
                confirm = false
                vm.relapseHabit(h.id)
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun HabitDialog(vm: MainViewModel, h: Habit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(h.name) }
    var target by remember { mutableStateOf(h.target.toString()) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habit_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.course_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { v -> target = v.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.habit_target)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.habit_target_hint), color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.delete), color = cs.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.editHabit(h.id, name, target.toIntOrNull() ?: h.target)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (confirm) {
        ConfirmDialog(
            text = stringResource(R.string.habit_delete_confirm, h.name),
            onYes = {
                confirm = false
                vm.deleteHabit(h.id)
                onDismiss()
            },
            onDismiss = { confirm = false },
        )
    }
}

// ======================================================================
// courses
// ======================================================================

@Composable
fun CoursesPane(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    val requester = remember { FocusRequester() }
    var editing by remember { mutableStateOf<Course?>(null) }
    var showDone by remember { mutableStateOf(false) }
    LaunchedEffect(vm.plusRequest) {
        if (vm.plusRequest == PLUS_TASK) {
            try {
                requester.requestFocus()
            } catch (e: Throwable) {
                // ignore
            }
            vm.consumePlus()
        }
    }
    Column(Modifier.fillMaxSize()) {
        PaneHeader(stringResource(R.string.sub_courses), vm)
        AddBar(stringResource(R.string.course_add_hint), requester) { vm.addCourse(it) }
        if (vm.courses.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.course_empty), color = cs.onSurfaceVariant, fontSize = 16.sp, lineHeight = 24.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            ) {
                val open = vm.courses.filter { it.done < it.total }
                val fin = vm.courses.filter { it.done >= it.total }
                items(open, key = { it.id }) { c ->
                    CourseRow(vm, c) { editing = c }
                }
                if (fin.isNotEmpty()) {
                    item(key = "course_done_header") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showDone = !showDone }
                                .padding(vertical = 14.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.course_done_header, fin.size.toString()),
                                color = cs.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(if (showDone) "\u25B4" else "\u25BE", color = cs.onSurfaceVariant, fontSize = 16.sp)
                        }
                    }
                    if (showDone) {
                        items(fin, key = { it.id }) { c ->
                            CourseRow(vm, c) { editing = c }
                        }
                    }
                }
            }
        }
    }
    val e = editing
    if (e != null) CourseDialog(vm, e) { editing = null }
}

@Composable
private fun CourseRow(vm: MainViewModel, c: Course, onEdit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val frac = if (c.total <= 0) 0f else c.done.toFloat() / c.total.toFloat()
    val finished = c.done >= c.total
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .panel(cs.surface, 20.dp)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.name,
                color = cs.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEdit() },
            )
            Text(
                if (finished) "✓ " + stringResource(R.string.course_finished) else c.done.toString() + " / " + c.total.toString(),
                color = if (finished) cs.primary else cs.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(cs.surfaceVariant),
        ) {
            if (frac > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(frac.coerceIn(0f, 1f))
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(cs.primary),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "−",
                color = cs.onSurface,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(cs.surfaceVariant)
                    .clickable { vm.stepCourse(c.id, -1) }
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            )
            Text(
                "+",
                color = cs.onPrimary,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(cs.primary)
                    .clickable { vm.stepCourse(c.id, 1) }
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            )
            Text(
                stringResource(R.string.course_hint),
                color = cs.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CourseDialog(vm: MainViewModel, c: Course, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(c.name) }
    var total by remember { mutableStateOf(c.total.toString()) }
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.course_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.course_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = total,
                    onValueChange = { v -> total = v.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.course_total)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.delete), color = cs.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.editCourse(c.id, name, total.toIntOrNull() ?: c.total)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (confirm) {
        ConfirmDialog(
            text = stringResource(R.string.course_delete_confirm, c.name),
            onYes = {
                confirm = false
                vm.deleteCourse(c.id)
                onDismiss()
            },
            onDismiss = { confirm = false },
        )
    }
}
