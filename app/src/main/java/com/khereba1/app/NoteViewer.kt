package com.khereba1.app

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Simple full-screen viewer: swipe between the items, double-tap an image to zoom, ⋮ for "open with another app". */
@Composable
fun NoteViewer(vm: MainViewModel, st: ViewerState) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    BackHandler { vm.closeViewer() }
    val total = st.pages.size
    val pager = rememberPagerState(initialPage = st.start.coerceIn(0, total - 1)) { total }
    var menu by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }
    val cur = st.pages[pager.currentPage.coerceIn(0, total - 1)]

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0B)),
    ) {
        HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { i ->
            val pg = st.pages[i]
            val m = pg.media
            val active = pager.currentPage == i
            if (m == null) {
                TextPage(vm, pg)
            } else {
                when (m.kind) {
                    MK_IMAGE -> ImagePage(m) { z -> if (active) zoomed = z }
                    MK_VIDEO -> VideoPage(m, active, speed)
                    MK_AUDIO -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .panel(cs.surface, 24.dp)
                                .padding(24.dp),
                        ) { AudioPlayer(m, Modifier.width(280.dp), speed = speed) }
                    }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            Modifier
                                .panel(cs.surface, 24.dp)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            RkIcon(RkIc.FILE, cs.onSurface, 34.dp)
                            Text(if (m.label.isEmpty()) m.file else m.label, color = cs.onSurface, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                            TextButton(onClick = { NoteMedia.open(ctx, m) }) { Text(stringResource(R.string.viewer_open_with)) }
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color(0x66000000))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "✕",
                color = Color.White,
                fontSize = 22.sp,
                modifier = Modifier
                    .clickable { vm.closeViewer() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Text(
                (pager.currentPage + 1).toString() + " / " + total.toString(),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Box {
                Text(
                    "⋮",
                    color = Color.White,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .clickable { menu = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val cm = cur.media
                    if (cm != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.viewer_open_with)) },
                            onClick = {
                                menu = false
                                NoteMedia.open(ctx, cm)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.note_share)) },
                            onClick = {
                                menu = false
                                NoteMedia.shareMedia(ctx, cm)
                            },
                        )
                    }
                    if (cm != null && (cm.kind == MK_AUDIO || cm.kind == MK_VIDEO)) {
                        for (sp in listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (sp == speed) "\u2713  " else "     ") + "\u00D7" + (if (sp == 0.75f) "0.75" else if (sp == 1.25f) "1.25" else sp.toString().removeSuffix(".0")),
                                    )
                                },
                                onClick = {
                                    speed = sp
                                    menu = false
                                },
                            )
                        }
                    }
                    if (st.grouped.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.grp_ungroup)) },
                            onClick = {
                                menu = false
                                vm.ungroupIds(st.grouped)
                                vm.closeViewer()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextPage(vm: MainViewModel, pg: VPage) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .panel(cs.surface, 24.dp)
                .padding(20.dp),
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (pg.title.isNotBlank()) {
                    Text(pg.title, color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text(pg.text, color = cs.onSurface, fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 8.dp))
            }
            TextButton(onClick = {
                val n = vm.notes.notes.firstOrNull { it.id == pg.noteId }
                vm.closeViewer()
                if (n != null) vm.openNote(n)
            }) { Text(stringResource(R.string.viewer_edit)) }
        }
    }
}

@Composable
private fun ImagePage(m: Media, onZoom: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val bmp = produceState<ImageBitmap?>(null, m.file) {
        value = withContext(Dispatchers.IO) { NoteMedia.decodeImage(NoteStore.mediaFile(ctx, m), 2048)?.asImageBitmap() }
    }
    var scale by remember { mutableStateOf(1f) }
    var ox by remember { mutableStateOf(0f) }
    var oy by remember { mutableStateOf(0f) }
    val img = bmp.value
    val base = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = {
                if (scale > 1f) {
                    scale = 1f
                    ox = 0f
                    oy = 0f
                    onZoom(false)
                } else {
                    scale = 2.5f
                    onZoom(true)
                }
            })
        }
    val gestures = if (scale > 1f) {
        base.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                ox += pan.x
                oy += pan.y
                if (scale <= 1f) {
                    ox = 0f
                    oy = 0f
                    onZoom(false)
                }
            }
        }
    } else {
        base
    }
    Box(gestures, contentAlignment = Alignment.Center) {
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy),
            )
        }
    }
}

@Composable
private fun VideoPage(m: Media, active: Boolean, speed: Float) {
    val ctx = LocalContext.current
    val path = NoteStore.mediaFile(ctx, m).absolutePath
    val vv = remember { arrayOfNulls<VideoView>(1) }
    val mpHolder = remember { arrayOfNulls<android.media.MediaPlayer>(1) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0) }
    var dur by remember { mutableStateOf(m.durMs.toInt()) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableStateOf(0f) }
    var ctl by remember { mutableStateOf(true) }

    fun toggle() {
        val v = vv[0] ?: return
        try {
            if (v.isPlaying) {
                v.pause()
                playing = false
                ctl = true
            } else {
                v.start()
                playing = true
                val p = mpHolder[0]
                if (p != null) setSpeed(p, speed)
            }
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Video toggle failed", e)
        }
    }

    DisposableEffect(m.file) {
        onDispose {
            try {
                vv[0]?.stopPlayback()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(active) {
        val v = vv[0]
        if (v != null) {
            try {
                if (active) {
                    v.start()
                    playing = true
                } else if (v.isPlaying) {
                    v.pause()
                    playing = false
                }
            } catch (e: Throwable) {
                // ignore
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(speed, playing) {
        val p = mpHolder[0]
        if (p != null && playing) setSpeed(p, speed)
    }
    androidx.compose.runtime.LaunchedEffect(playing) {
        while (playing) {
            val v = vv[0]
            if (v != null && !dragging) {
                try {
                    val c = v.currentPosition
                    if (c != pos) pos = c
                } catch (e: Throwable) {
                    // ignore
                }
            }
            androidx.compose.runtime.withFrameNanos { }
        }
    }
    androidx.compose.runtime.LaunchedEffect(playing, ctl) {
        if (playing && ctl) {
            kotlinx.coroutines.delay(2500L)
            ctl = false
        }
    }

    val shownPos = if (dragging) dragPos.toInt() else pos
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { c ->
                val v = VideoView(c)
                v.setVideoURI(Uri.fromFile(java.io.File(path)))
                v.setOnPreparedListener { mp ->
                    mpHolder[0] = mp
                    if (v.duration > 0) dur = v.duration
                    if (active) {
                        v.start()
                        playing = true
                        setSpeed(mp, speed)
                    } else {
                        v.seekTo(1)
                    }
                }
                v.setOnCompletionListener {
                    playing = false
                    pos = 0
                    ctl = true
                    try {
                        v.seekTo(1)
                    } catch (e: Throwable) {
                        // ignore
                    }
                }
                vv[0] = v
                v
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { ctl = !ctl },
        )
        AnimatedVisibility(
            visible = ctl || !playing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0x99000000))
                    .clickable { toggle() },
                contentAlignment = Alignment.Center,
            ) { RkIcon(if (playing) RkIc.PAUSE else RkIc.PLAY, Color.White, 34.dp) }
        }
        AnimatedVisibility(
            visible = ctl || !playing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                SeekBar(
                    frac = shownPos.toFloat() / maxOf(dur, 1).toFloat(),
                    onChange = { f ->
                        dragging = true
                        dragPos = f * maxOf(dur, 1).toFloat()
                    },
                    onDone = {
                        try {
                            vv[0]?.seekTo(dragPos.toInt())
                            pos = dragPos.toInt()
                        } catch (e: Throwable) {
                            // ignore
                        }
                        dragging = false
                    },
                    track = Color(0x55FFFFFF),
                    fill = Color.White,
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(NoteMedia.fmtDuration(shownPos.toLong()), color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(NoteMedia.fmtDuration(dur.toLong()), color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
