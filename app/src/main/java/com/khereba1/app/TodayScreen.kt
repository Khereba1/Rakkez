package com.khereba1.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun TodayScreen(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    val start = dayStart(now)
    val todaySessions = vm.sessions.filter { it.startAt >= start }.sortedByDescending { it.startAt }
    val todayMs = todaySessions.sumOf { it.durationMs }
    val todayMin = todayMs / 60000L
    val goal = vm.settings.goalMin
    val frac = if (goal > 0) (todayMin.toFloat() / goal.toFloat()).coerceIn(0f, 1f) else 0f
    val doneToday = vm.tasks.count { it.done && it.doneAt >= start }
    val todayIdeas = vm.ideas.filter { it.at >= start }.sortedByDescending { it.at }
    val week = (6 downTo 0).map { i ->
        val s = dayStart(now, -i)
        val e = dayStart(now, -i + 1)
        Pair(s, vm.sessions.filter { it.startAt >= s && it.startAt < e }.sumOf { it.durationMs } / 60000L)
    }
    val maxMin = week.maxOf { it.second }.coerceAtLeast(30L)
    val dayFmt = SimpleDateFormat("EEE", dateLocale())
    val timeFmt = SimpleDateFormat("h:mm a", dateLocale())

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackChevron { vm.closeOverlay() }
            Text(stringResource(R.string.today_title), color = cs.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        // total + goal ring
        Row(
            Modifier
                .fillMaxWidth()
                .panel(cs.surface, 24.dp)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_total), color = cs.onSurfaceVariant, fontSize = 13.sp)
                Text(fmtMinutes(ctx, todayMin), color = cs.onSurface, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.today_goal, fmtMinutes(ctx, goal.toLong())),
                    color = cs.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            val primary = cs.primary
            val track = cs.outlineVariant
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 10.dp.toPx()
                    val d = size.minDimension - w
                    drawArc(
                        color = track,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(d, d),
                        style = Stroke(width = w),
                    )
                    drawArc(
                        color = primary,
                        startAngle = -90f,
                        sweepAngle = 360f * frac,
                        useCenter = false,
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(d, d),
                        style = Stroke(width = w, cap = StrokeCap.Round),
                    )
                }
                Text((frac * 100f).toInt().toString() + "%", color = cs.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), todaySessions.size.toString(), stringResource(R.string.stat_sessions))
            StatCard(Modifier.weight(1f), doneToday.toString(), stringResource(R.string.stat_done))
            StatCard(Modifier.weight(1f), todayIdeas.size.toString(), stringResource(R.string.stat_ideas))
        }

        Spacer(Modifier.height(22.dp))
        Text(stringResource(R.string.week_title), color = cs.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .panel(cs.surface, 20.dp)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEachIndexed { idx, day ->
                val isToday = idx == week.size - 1
                val barH = (day.second.toFloat() / maxMin.toFloat() * 80f).coerceAtLeast(4f)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (day.second > 0L) day.second.toString() else "",
                        color = cs.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(barH.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isToday) cs.primary else cs.outline)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        dayFmt.format(Date(day.first)),
                        color = if (isToday) cs.onSurface else cs.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(stringResource(R.string.ideas_title), color = cs.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        if (todayIdeas.isEmpty()) {
            Text(stringResource(R.string.ideas_empty), color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp)
        } else {
            for (idea in todayIdeas) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .panel(cs.surface, 16.dp)
                        .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(idea.text, Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
                    Pill(
                        text = stringResource(R.string.to_task),
                        selected = false,
                        onClick = { vm.ideaToTask(idea.id) },
                    )
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { vm.deleteIdea(idea.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", color = cs.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
        }

        if (todaySessions.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text(stringResource(R.string.sessions_title), color = cs.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            for (s in todaySessions.take(8)) {
                val title = vm.tasks.firstOrNull { it.id == s.taskId }?.title ?: ""
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(timeFmt.format(Date(s.startAt)), color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.width(76.dp))
                    Text(
                        fmtMinutes(ctx, s.durationMs / 60000L),
                        color = cs.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(72.dp),
                    )
                    Text(
                        title,
                        color = cs.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun StatCard(modifier: Modifier, value: String, label: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .panel(cs.surface, 18.dp)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = cs.primary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = cs.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
    }
}
