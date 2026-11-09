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
    val doneToday = vm.habits.count { it.days.contains(today) }
    Column(Modifier.fillMaxSize()) {
        PaneHeader(stringResource(R.string.habit_summary, doneToday.toString(), vm.habits.size.toString()), vm)
        AddBar(stringResource(R.string.habit_add_hint), requester) { vm.addHabit(it) }
        if (vm.habits.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.habit_empty), color = cs.onSurfaceVariant, fontSize = 16.sp, lineHeight = 24.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            ) {
                items(vm.habits, key = { it.id }) { h ->
                    HabitRow(vm, h, today) { editing = h }
                }
            }
        }
    }
    val e = editing
    if (e != null) {
        NameDialog(stringResource(R.string.habit_edit), e.name, { editing = null }, { n ->
            vm.renameHabit(e.id, n)
            editing = null
        })
    }
}

@Composable
private fun HabitRow(vm: MainViewModel, h: Habit, today: Long, onEdit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val streak = Trackers.streak(h)
    var confirm by remember { mutableStateOf(false) }
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
                    if (streak > 0) "🔥 " + stringResource(R.string.habit_streak, streak.toString()) else stringResource(R.string.habit_no_streak),
                    color = cs.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            MiniBtn("✕") { confirm = true }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (back in 6 downTo 0) {
                val day = today - back.toLong()
                val on = h.days.contains(day)
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (on) cs.primary else cs.surfaceVariant)
                        .then(if (back == 0) Modifier.border(2.dp, cs.primary, CircleShape) else Modifier)
                        .clickable { vm.toggleHabitDay(h.id, day) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Text("✓", color = cs.onPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            text = stringResource(R.string.habit_delete_confirm, h.name),
            onYes = {
                confirm = false
                vm.deleteHabit(h.id)
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
                items(vm.courses, key = { it.id }) { c ->
                    CourseRow(vm, c) { editing = c }
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
