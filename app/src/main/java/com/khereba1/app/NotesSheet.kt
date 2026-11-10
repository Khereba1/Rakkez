package com.khereba1.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DevMedia(val uri: Uri, val video: Boolean, val durMs: Long)

private fun mediaPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun hasMediaAccess(c: Context): Boolean {
    for (p in mediaPermissions()) {
        if (c.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
            // Android 14 "selected photos only" still lets us read the picked ones
            if (Build.VERSION.SDK_INT >= 34 && c.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED) {
                return true
            }
            return false
        }
    }
    return true
}

/** Newest photos and videos on the phone (up to [limit]). */
private fun loadDeviceMedia(c: Context, limit: Int): List<DevMedia> {
    val out = ArrayList<DevMedia>()
    try {
        val proj = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.MediaColumns.DURATION)
        val sel = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + "," + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")"
        val cur = c.contentResolver.query(MediaStore.Files.getContentUri("external"), proj, sel, null, MediaStore.MediaColumns.DATE_ADDED + " DESC")
        if (cur != null) {
            cur.use {
                while (it.moveToNext() && out.size < limit) {
                    val id = it.getLong(0)
                    val vid = it.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val base = if (vid) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    out.add(DevMedia(ContentUris.withAppendedId(base, id), vid, if (vid) it.getLong(2) else 0L))
                }
            }
        }
    } catch (e: Throwable) {
        AppLog.log("NOTES", "Could not list device media", e)
    }
    return out
}

/**
 * Bottom drawer for the center "+" of the notes tab: a grid of the newest photos/videos (tap to select, several at once)
 * and a row of other ways to add: camera, video, voice, text, file.
 */
@Composable
fun AddSheet(
    vm: MainViewModel,
    visible: Boolean,
    onClose: () -> Unit,
    onText: () -> Unit,
    onVoice: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onFile: () -> Unit,
    onSystemPicker: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    BackHandler(enabled = visible) { onClose() }
    var access by remember { mutableStateOf(hasMediaAccess(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        access = hasMediaAccess(ctx)
    }
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val items = produceState<List<DevMedia>>(emptyList(), visible, access) {
        if (visible && access) value = withContext(Dispatchers.IO) { loadDeviceMedia(ctx, 150) } else if (!visible) picked = emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .clickable { onClose() },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.72f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(cs.surface)
                    .clickable(enabled = false) { },
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(42.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(cs.outline),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.sheet_recent),
                        color = cs.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSystemPicker) { Text(stringResource(R.string.sheet_system_picker)) }
                }
                Box(Modifier.weight(1f)) {
                    if (!access) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(stringResource(R.string.sheet_perm), color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 22.sp)
                            Button(
                                onClick = {
                                    try {
                                        ask.launch(mediaPermissions())
                                    } catch (e: Throwable) {
                                        AppLog.log("NOTES", "Permission request failed", e)
                                    }
                                },
                                modifier = Modifier.padding(top = 12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                            ) { Text(stringResource(R.string.sheet_allow), fontWeight = FontWeight.Bold) }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(items.value, key = { it.uri.toString() }) { d ->
                                val idx = picked.indexOf(d.uri)
                                DeviceThumb(d, idx) {
                                    picked = if (idx >= 0) picked - d.uri else picked + d.uri
                                }
                            }
                        }
                    }
                }
                if (picked.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                vm.importItems(picked, false)
                                onClose()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                        ) { Text(stringResource(R.string.sheet_add, picked.size.toString()), fontWeight = FontWeight.Bold) }
                        if (picked.size >= 2) {
                            Button(
                                onClick = {
                                    vm.importItems(picked, true)
                                    onClose()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
                            ) { Text(stringResource(R.string.sheet_as_group), fontWeight = FontWeight.Bold) }
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(cs.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                    ) {
                        ActionTile(RkIc.CAMERA, stringResource(R.string.cap_photo), onPhoto)
                        ActionTile(RkIc.VIDEO, stringResource(R.string.cap_video), onVideo)
                        ActionTile(RkIc.MIC, stringResource(R.string.cap_voice), onVoice)
                        ActionTile(RkIc.PEN, stringResource(R.string.sheet_text), onText)
                        ActionTile(RkIc.FILE, stringResource(R.string.cap_file), onFile)
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionTile(icon: RkIc, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RkIcon(icon, cs.onSurface, 26.dp)
        Text(label, color = cs.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun DeviceThumb(d: DevMedia, order: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val bmp = produceState<ImageBitmap?>(null, d.uri) {
        value = withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.loadThumbnail(d.uri, Size(256, 256), null).asImageBitmap()
            } catch (e: Throwable) {
                null
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(cs.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        val img = bmp.value
        if (img != null) {
            Image(bitmap = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (d.video) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color(0x88000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RkIcon(RkIc.PLAY, Color.White, 10.dp)
                Text(" " + NoteMedia.fmtDuration(d.durMs), color = Color.White, fontSize = 11.sp)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (order >= 0) cs.primary else Color(0x55000000))
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (order >= 0) Text((order + 1).toString(), color = cs.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}


/** Bottom drawer with a big timer for voice notes (pause / resume / save). The result goes to [onSaved]. */
@Composable
fun VoiceSheet(visible: Boolean, onClose: () -> Unit, onSaved: (Media) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val recorder = remember { Recorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }

    fun toast(id: Int) {
        android.widget.Toast.makeText(ctx, LocaleHelper.wrap(ctx).getString(id), android.widget.Toast.LENGTH_SHORT).show()
    }

    fun begin() {
        if (recorder.start()) {
            recording = true
            paused = false
            elapsed = 0L
        } else {
            toast(R.string.note_record_failed)
            onClose()
        }
    }

    fun cancelRec() {
        recorder.cancel()
        recording = false
        paused = false
        onClose()
    }

    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            begin()
        } else {
            toast(R.string.note_mic_denied)
            onClose()
        }
    }
    BackHandler(enabled = visible) { cancelRec() }
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (visible) {
            if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                begin()
            } else {
                try {
                    mic.launch(Manifest.permission.RECORD_AUDIO)
                } catch (e: Throwable) {
                    toast(R.string.note_mic_denied)
                    onClose()
                }
            }
        } else if (recording) {
            recorder.cancel()
            recording = false
            paused = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(recording) {
        while (recording) {
            elapsed = recorder.elapsedMs()
            kotlinx.coroutines.delay(200L)
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val pulseRaw by tr.animateFloat(
        initialValue = 1f,
        targetValue = 1.16f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val pulse = if (paused) 1f else pulseRaw

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .clickable(enabled = false) { },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(cs.surface)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.rec_title), color = cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier
                        .padding(vertical = 22.dp)
                        .size(112.dp)
                        .graphicsLayer(scaleX = pulse, scaleY = pulse)
                        .clip(CircleShape)
                        .background((if (paused) cs.onSurfaceVariant else cs.error).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(if (paused) cs.onSurfaceVariant else cs.error)
                            .clickable(enabled = recording) {
                                if (paused) {
                                    recorder.resume()
                                    paused = false
                                } else {
                                    recorder.pause()
                                    paused = true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { RkIcon(if (paused) RkIc.MIC else RkIc.PAUSE, Color.White, 34.dp) }
                }
                Text(
                    NoteMedia.fmtDuration(elapsed),
                    color = cs.onSurface,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(if (paused) R.string.rec_paused else R.string.rec_hint),
                    color = cs.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { cancelRec() }) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = {
                            val m = recorder.stop()
                            recording = false
                            paused = false
                            if (m != null) onSaved(m)
                            onClose()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = recording,
                        colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                    ) { Text(stringResource(R.string.rec_save), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
