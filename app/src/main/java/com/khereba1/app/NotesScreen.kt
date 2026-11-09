package com.khereba1.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun NotesRoot(vm: MainViewModel) {
    val e = vm.editing
    if (e != null) NoteEditor(vm, e) else NotesList(vm)
}

// ======================================================================
// list
// ======================================================================

@Composable
fun NotesList(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    var newCollection by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var renameCol by remember { mutableStateOf(false) }
    var deleteCol by remember { mutableStateOf(false) }

    LaunchedEffect(vm.plusRequest) {
        if (vm.plusRequest == PLUS_IDEA_NOTES) {
            vm.consumePlus()
            vm.newNote(0)
        }
    }

    val visible = vm.visibleNotes()
    val scope = vm.noteScope
    val currentCol = vm.notes.collections.firstOrNull { it.id == scope }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.tab_notes),
                modifier = Modifier.weight(1f),
                color = cs.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            GearButton { vm.openOverlay(OVERLAY_SETTINGS) }
        }

        // search
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .panel(cs.surfaceVariant, 24.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            if (vm.noteQuery.isEmpty()) {
                Text(stringResource(R.string.notes_search), color = cs.onSurfaceVariant, fontSize = 15.sp)
            }
            BasicTextField(
                value = vm.noteQuery,
                onValueChange = { v -> vm.searchNotes(v) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(cs.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // collections
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(stringResource(R.string.notes_all), scope == -1L, { vm.scopeNotes(-1L) })
            Pill(stringResource(R.string.notes_inbox), scope == 0L, { vm.scopeNotes(0L) })
            for (col in vm.notes.collections) {
                Pill(col.name, scope == col.id, { vm.scopeNotes(col.id) })
            }
            Pill(stringResource(R.string.notes_archive), scope == -2L, { vm.scopeNotes(-2L) })
            Pill("＋ " + stringResource(R.string.notes_new_collection), false, { newCollection = true })
        }

        // type filters (automatic classification)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val t = vm.noteType
            Pill(stringResource(R.string.nt_all), t == NF_ALL, { vm.filterNotes(NF_ALL) }, hPad = 12.dp, size = 13.sp)
            Pill(stringResource(R.string.nt_text), t == NF_TEXT, { vm.filterNotes(NF_TEXT) }, hPad = 12.dp, size = 13.sp)
            Pill(stringResource(R.string.nt_link), t == NF_LINK, { vm.filterNotes(NF_LINK) }, hPad = 12.dp, size = 13.sp)
            Pill(stringResource(R.string.nt_image), t == NF_IMAGE, { vm.filterNotes(NF_IMAGE) }, hPad = 12.dp, size = 13.sp)
            Pill(stringResource(R.string.nt_audio), t == NF_AUDIO, { vm.filterNotes(NF_AUDIO) }, hPad = 12.dp, size = 13.sp)
            Pill(stringResource(R.string.nt_video), t == NF_VIDEO, { vm.filterNotes(NF_VIDEO) }, hPad = 12.dp, size = 13.sp)
        }

        if (currentCol != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(currentCol.name, color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { manage = true }) { Text(stringResource(R.string.notes_manage)) }
            }
        }

        if (visible.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.notes_empty),
                    color = cs.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
            ) {
                items(visible, key = { it.id }) { n ->
                    NoteCard(vm, n)
                }
            }
        }
    }

    if (newCollection) {
        NameDialog(stringResource(R.string.notes_new_collection), "", { newCollection = false }, { n -> vm.addCollection(n) })
    }
    if (manage && currentCol != null) {
        AlertDialog(
            onDismissRequest = { manage = false },
            title = { Text(currentCol.name) },
            text = {
                Column {
                    TextButton(onClick = {
                        manage = false
                        renameCol = true
                    }) { Text(stringResource(R.string.notes_rename_collection)) }
                    TextButton(onClick = {
                        manage = false
                        deleteCol = true
                    }) { Text(stringResource(R.string.notes_delete_collection), color = cs.error) }
                }
            },
            confirmButton = { TextButton(onClick = { manage = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (renameCol && currentCol != null) {
        NameDialog(stringResource(R.string.notes_rename_collection), currentCol.name, { renameCol = false }, { n -> vm.renameCollection(currentCol.id, n) })
    }
    if (deleteCol && currentCol != null) {
        ConfirmDialog(
            text = stringResource(R.string.notes_delete_collection_confirm, currentCol.name),
            onYes = {
                vm.deleteCollection(currentCol.id)
                deleteCol = false
            },
            onDismiss = { deleteCol = false },
        )
    }
}

@Composable
fun NoteCard(vm: MainViewModel, n: Note) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val head = NoteTools.headline(n)
    val prev = NoteTools.preview(n)
    val url = NoteTools.firstUrl(n.body)
    val dateText = remember(n.updatedAt) { SimpleDateFormat("d MMM", dateLocale()).format(Date(n.updatedAt)) }
    val colName = vm.notes.collections.firstOrNull { it.id == n.collectionId }?.name ?: ""
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .panel(cs.surface, 20.dp)
            .clickable { vm.openNote(n) }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (head.isEmpty()) stringResource(R.string.note_untitled) else head,
                color = cs.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (n.pinned) Text("★", color = cs.primary, fontSize = 16.sp)
        }
        if (prev.isNotEmpty()) {
            Text(prev, color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        if (url != null) {
            Text(
                "🔗 " + NoteTools.domain(url),
                color = cs.primary,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { NoteMedia.openUrl(ctx, url) },
            )
        }
        if (n.media.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shown = n.media.take(3)
                for (m in shown) MediaThumb(m, 64)
                if (n.media.size > 3) Text("+" + (n.media.size - 3).toString(), color = cs.onSurfaceVariant, fontSize = 14.sp)
            }
        }
        Text(
            dateText + (if (colName.isNotEmpty()) " · " + colName else ""),
            color = cs.onSurfaceVariant,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** A small preview of an attachment: image, video frame, audio length or a file icon. */
@Composable
fun MediaThumb(m: Media, size: Int) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val file = NoteStore.mediaFile(ctx, m)
    val bmp = produceState<ImageBitmap?>(null, m.file) {
        value = withContext(Dispatchers.IO) {
            val b = when (m.kind) {
                MK_IMAGE -> NoteMedia.decodeImage(file, size * 3)
                MK_VIDEO -> NoteMedia.videoFrame(file)
                else -> null
            }
            b?.asImageBitmap()
        }
    }
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val img = bmp.value
        if (img != null) {
            Image(bitmap = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        when (m.kind) {
            MK_VIDEO -> Text("▶", color = cs.onSurface, fontSize = 20.sp)
            MK_AUDIO -> Text("🎤\n" + NoteMedia.fmtDuration(m.durMs), color = cs.onSurface, fontSize = 12.sp)
            MK_FILE -> Text("📎", color = cs.onSurface, fontSize = 22.sp)
            else -> {}
        }
    }
}

// ======================================================================
// editor
// ======================================================================

@Composable
fun NoteEditor(vm: MainViewModel, n: Note) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var colMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newCollection by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<File?>(null) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    val recorder = remember { Recorder(ctx) }
    val url = NoteTools.firstUrl(n.body)

    fun toast(id: Int) {
        Toast.makeText(ctx, LocaleHelperText(ctx, id), Toast.LENGTH_SHORT).show()
    }

    fun startRecording() {
        if (recorder.start()) {
            recording = true
            elapsed = 0L
        } else {
            toast(R.string.note_record_failed)
        }
    }

    fun stopRecording() {
        recording = false
        val m = recorder.stop()
        if (m != null) vm.addDraftMedia(m)
    }

    val attach = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.attachUris(uris)
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending
        pending = null
        if (ok && f != null) vm.addCapture(f, MK_IMAGE, "image/jpeg") else f?.delete()
    }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val f = pending
        pending = null
        if (ok && f != null) vm.addCapture(f, MK_VIDEO, "video/mp4") else f?.delete()
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else toast(R.string.note_mic_denied)
    }

    fun takePhoto() {
        val f = NoteStore.newMediaFile(ctx, "jpg")
        pending = f
        try {
            photo.launch(NoteMedia.uriFor(ctx, f))
        } catch (e: Throwable) {
            pending = null
            toast(R.string.note_no_camera)
        }
    }

    fun takeVideo() {
        val f = NoteStore.newMediaFile(ctx, "mp4")
        pending = f
        try {
            video.launch(NoteMedia.uriFor(ctx, f))
        } catch (e: Throwable) {
            pending = null
            toast(R.string.note_no_camera)
        }
    }

    fun startVoice() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            try {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            } catch (e: Throwable) {
                toast(R.string.note_mic_denied)
            }
        }
    }

    LaunchedEffect(Unit) {
        val a = vm.noteAction
        vm.consumeNoteAction()
        when (a) {
            2 -> startVoice()
            3 -> takePhoto()
            4 -> takeVideo()
            else -> {}
        }
    }
    LaunchedEffect(recording) {
        val start = System.currentTimeMillis()
        while (recording) {
            elapsed = System.currentTimeMillis() - start
            delay(250L)
        }
    }
    DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }

    val colName = vm.notes.collections.firstOrNull { it.id == n.collectionId }?.name ?: stringResource(R.string.notes_inbox)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackChevron { vm.closeNote() }
            Box(Modifier.weight(1f)) {
                Pill(colName + " ▾", false, { colMenu = true })
                DropdownMenu(expanded = colMenu, onDismissRequest = { colMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_inbox)) },
                        onClick = {
                            vm.editDraftMeta(n.pinned, n.archived, 0L)
                            colMenu = false
                        },
                    )
                    for (col in vm.notes.collections) {
                        DropdownMenuItem(
                            text = { Text(col.name) },
                            onClick = {
                                vm.editDraftMeta(n.pinned, n.archived, col.id)
                                colMenu = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("＋ " + stringResource(R.string.notes_new_collection)) },
                        onClick = {
                            colMenu = false
                            newCollection = true
                        },
                    )
                }
            }
            MiniBtn(if (n.pinned) "★" else "☆") { vm.editDraftMeta(!n.pinned, n.archived, n.collectionId) }
            Box {
                MiniBtn("⋮") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.note_share)) },
                        onClick = {
                            menu = false
                            NoteMedia.share(ctx, n)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (n.archived) R.string.note_unarchive else R.string.note_archive)) },
                        onClick = {
                            menu = false
                            vm.editDraftMeta(n.pinned, !n.archived, n.collectionId)
                            vm.closeNote()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = cs.error) },
                        onClick = {
                            menu = false
                            confirmDelete = true
                        },
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                if (n.title.isEmpty()) {
                    Text(stringResource(R.string.note_title_hint), color = cs.onSurfaceVariant, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                BasicTextField(
                    value = n.title,
                    onValueChange = { v -> vm.editDraftText(v, n.body) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().heightIn(min = 140.dp)) {
                if (n.body.isEmpty()) {
                    Text(stringResource(R.string.note_body_hint), color = cs.onSurfaceVariant, fontSize = 16.sp)
                }
                BasicTextField(
                    value = n.body,
                    onValueChange = { v -> vm.editDraftText(n.title, v) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface, fontSize = 16.sp, lineHeight = 25.sp),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (url != null) {
                Text(
                    "🔗 " + NoteTools.domain(url),
                    color = cs.primary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .clickable { NoteMedia.openUrl(ctx, url) },
                )
            }
            for (m in n.media) {
                MediaRow(vm, m)
            }
            Spacer(Modifier.height(16.dp))
        }

        if (recording) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .panel(cs.surface, 22.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("●", color = cs.error, fontSize = 16.sp)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.note_recording) + "  " + NoteMedia.fmtDuration(elapsed),
                    color = cs.onSurface,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { stopRecording() },
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                ) { Text(stringResource(R.string.note_stop_save), fontWeight = FontWeight.Bold) }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SegPill("🎤 " + stringResource(R.string.cap_voice), false, { startVoice() })
                SegPill("📷 " + stringResource(R.string.cap_photo), false, { takePhoto() })
                SegPill("🎥 " + stringResource(R.string.cap_video), false, { takeVideo() })
                SegPill("📎 " + stringResource(R.string.cap_file), false, {
                    try {
                        attach.launch("*/*")
                    } catch (e: Throwable) {
                        AppLog.log("NOTES", "Picker failed", e)
                    }
                })
            }
        }
    }

    if (newCollection) {
        NameDialog(stringResource(R.string.notes_new_collection), "", { newCollection = false }, { name ->
            vm.addCollection(name)
            val created = vm.notes.collections.lastOrNull()
            if (created != null) vm.editDraftMeta(n.pinned, n.archived, created.id)
        })
    }
    if (confirmDelete) {
        ConfirmDialog(
            text = stringResource(R.string.note_delete_confirm),
            onYes = {
                confirmDelete = false
                vm.deleteNote(n.id)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun LocaleHelperText(c: android.content.Context, id: Int): String = LocaleHelper.wrap(c).getString(id)

@Composable
fun MediaRow(vm: MainViewModel, m: Media) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .panel(cs.surface, 16.dp)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (m.kind) {
            MK_AUDIO -> AudioPlayer(m, Modifier.weight(1f))
            MK_FILE -> {
                Text(
                    "📎 " + (if (m.label.isEmpty()) m.file else m.label),
                    color = cs.onSurface,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { NoteMedia.open(ctx, m) }
                        .padding(8.dp),
                )
            }
            else -> {
                Box(
                    Modifier
                        .weight(1f)
                        .clickable { NoteMedia.open(ctx, m) },
                ) {
                    MediaThumb(m, 120)
                }
            }
        }
        MiniBtn("✕") { vm.removeDraftMedia(m.id) }
    }
}

@Composable
fun AudioPlayer(m: Media, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }

    fun stopPlay() {
        val p = player[0]
        player[0] = null
        playing = false
        if (p != null) {
            try {
                p.stop()
            } catch (e: Throwable) {
                // ignore
            }
            try {
                p.release()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    fun startPlay() {
        try {
            val p = MediaPlayer()
            p.setDataSource(NoteStore.mediaFile(ctx, m).absolutePath)
            p.prepare()
            p.setOnCompletionListener { stopPlay() }
            p.start()
            player[0] = p
            playing = true
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not play audio", e)
            stopPlay()
        }
    }

    DisposableEffect(m.file) {
        onDispose { stopPlay() }
    }

    Row(modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(cs.primaryContainer)
                .clickable { if (playing) stopPlay() else startPlay() },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (playing) "■" else "▶", color = cs.onPrimaryContainer, fontSize = 16.sp)
        }
        Spacer(Modifier.size(10.dp))
        Text("🎤 " + NoteMedia.fmtDuration(m.durMs), color = cs.onSurface, fontSize = 15.sp)
    }
}
