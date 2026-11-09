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
                        ActionTile("📷", stringResource(R.string.cap_photo), onPhoto)
                        ActionTile("🎥", stringResource(R.string.cap_video), onVideo)
                        ActionTile("🎤", stringResource(R.string.cap_voice), onVoice)
                        ActionTile("✍", stringResource(R.string.sheet_text), onText)
                        ActionTile("📎", stringResource(R.string.cap_file), onFile)
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionTile(icon: String, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 24.sp)
        Text(label, color = cs.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
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
            Text(
                "▶ " + NoteMedia.fmtDuration(d.durMs),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color(0x88000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
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
