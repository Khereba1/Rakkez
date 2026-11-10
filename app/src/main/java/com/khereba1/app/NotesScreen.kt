@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.khereba1.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
    val v = vm.viewer
    Box(Modifier.fillMaxSize()) {
        if (e != null) NoteEditor(vm, e) else NotesList(vm)
        if (v != null) NoteViewer(vm, v)
    }
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
    var showPlus by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }
    var confirmSel by remember { mutableStateOf(false) }
    var pendingCap by remember { mutableStateOf<File?>(null) }
    val ctx = LocalContext.current

    fun toast(id: Int) {
        Toast.makeText(ctx, LocaleHelperText(ctx, id), Toast.LENGTH_SHORT).show()
    }

    val photoCap = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCap
        pendingCap = null
        if (ok && f != null) vm.addStandalone(f, MK_IMAGE, "image/jpeg") else f?.delete()
    }
    val videoCap = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val f = pendingCap
        pendingCap = null
        if (ok && f != null) vm.addStandalone(f, MK_VIDEO, "video/mp4") else f?.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) vm.importItems(uris)
    }
    val filePick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) vm.importItems(uris)
    }

    LaunchedEffect(vm.plusRequest) {
        if (vm.plusRequest == PLUS_IDEA_NOTES) {
            vm.consumePlus()
            showPlus = true
        }
    }
    LaunchedEffect(vm.voiceRequest) {
        if (vm.voiceRequest > 0) showVoice = true
    }
    val selecting = vm.selected.isNotEmpty()
    BackHandler(enabled = selecting) { vm.clearSelection() }

    val visible = remember(vm.notes, vm.noteScope, vm.noteType, vm.noteQuery) { vm.visibleEntries() }
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
        if (selecting) {
            val selNotes = vm.notes.notes.filter { vm.selected.contains(it.id) }
            val anyGrouped = selNotes.any { it.groupId != 0L }
            // a whole group counts as one unit: "group" only makes sense with at least two units
            val units = selNotes.filter { it.groupId == 0L }.size + selNotes.filter { it.groupId != 0L }.map { it.groupId }.toSet().size
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .panel(cs.primaryContainer, 22.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniBtn("\u2715") { vm.clearSelection() }
                    Text(
                        stringResource(R.string.sel_count, vm.selected.size.toString()),
                        color = cs.onPrimaryContainer,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (units >= 2) {
                        SelAction(stringResource(R.string.grp_group), cs.onPrimaryContainer) { vm.groupSelected() }
                    }
                    if (anyGrouped) {
                        SelAction(stringResource(R.string.grp_ungroup), cs.onPrimaryContainer) { vm.ungroupSelected() }
                    }
                    Box(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.sel_move),
                                color = cs.onPrimaryContainer,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { moveMenu = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                        DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notes_inbox)) },
                                onClick = {
                                    moveMenu = false
                                    vm.moveSelected(0L)
                                },
                            )
                            for (col in vm.notes.collections) {
                                DropdownMenuItem(
                                    text = { Text(col.name) },
                                    onClick = {
                                        moveMenu = false
                                        vm.moveSelected(col.id)
                                    },
                                )
                            }
                        }
                    }
                    SelAction(stringResource(R.string.delete), cs.error) { confirmSel = true }
                }
            }
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
            Pill(stringResource(R.string.nt_file), t == NF_FILE, { vm.filterNotes(NF_FILE) }, hPad = 12.dp, size = 13.sp)
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
                items(visible, key = { it[0].id }) { entry ->
                    NoteCard(vm, entry)
                }
            }
        }
    }

    if (newCollection) {
        NameDialog(stringResource(R.string.notes_new_collection), "", { newCollection = false }, { n -> vm.addCollection(n) })
    }
    if (confirmSel) {
        ConfirmDialog(
            text = stringResource(R.string.sel_delete_confirm, vm.selected.size.toString()),
            onYes = {
                confirmSel = false
                vm.deleteSelected()
            },
            onDismiss = { confirmSel = false },
        )
    }
    AddSheet(
        vm = vm,
        visible = showPlus,
        onClose = { showPlus = false },
        onText = {
            showPlus = false
            vm.newNote(1)
        },
        onVoice = {
            showPlus = false
            showVoice = true
        },
        onPhoto = {
            showPlus = false
            val f = NoteStore.newMediaFile(ctx, "jpg")
            pendingCap = f
            try {
                photoCap.launch(NoteMedia.uriFor(ctx, f))
            } catch (e: Throwable) {
                pendingCap = null
                toast(R.string.note_no_camera)
            }
        },
        onVideo = {
            showPlus = false
            val f = NoteStore.newMediaFile(ctx, "mp4")
            pendingCap = f
            try {
                videoCap.launch(NoteMedia.uriFor(ctx, f))
            } catch (e: Throwable) {
                pendingCap = null
                toast(R.string.note_no_camera)
            }
        },
        onFile = {
            showPlus = false
            try {
                filePick.launch("*/*")
            } catch (e: Throwable) {
                AppLog.log("NOTES", "Picker failed", e)
            }
        },
        onSystemPicker = {
            showPlus = false
            try {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            } catch (e: Throwable) {
                AppLog.log("NOTES", "Gallery picker failed", e)
            }
        },
    )
    VoiceSheet(visible = showVoice, onClose = { showVoice = false }, onSaved = { m -> vm.addStandaloneMedia(m) })
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
private fun RowScope.SelAction(label: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** Large cover of an image or a video frame, filling its box. */
@Composable
fun MediaCover(m: Media, modifier: Modifier, target: Int) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val file = NoteStore.mediaFile(ctx, m)
    val bmp = produceState<ImageBitmap?>(null, m.file) {
        value = withContext(Dispatchers.IO) {
            val b = when (m.kind) {
                MK_IMAGE -> NoteMedia.decodeImage(file, target)
                MK_VIDEO -> NoteMedia.videoFrame(file)
                else -> null
            }
            b?.asImageBitmap()
        }
    }
    Box(modifier.background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
        val img = bmp.value
        if (img != null) {
            Image(bitmap = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (m.kind == MK_VIDEO) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) { RkIcon(RkIc.PLAY, Color.White, 22.dp) }
        }
        if (m.kind == MK_AUDIO) RkIcon(RkIc.MIC, cs.onSurfaceVariant, 28.dp)
        if (m.kind == MK_FILE) RkIcon(RkIc.FILE, cs.onSurfaceVariant, 28.dp)
    }
}

/** One list entry: a single note, or a group of notes shown as one card. */
@Composable
fun NoteCard(vm: MainViewModel, entry: List<Note>) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val grouped = entry.size > 1
    val n = entry[0]
    val ids = entry.map { it.id }
    val isSel = ids.all { vm.selected.contains(it) }
    val selecting = vm.selected.isNotEmpty()
    val hasText = NoteTools.hasText(n)
    val mediaOnly = !grouped && !hasText && n.media.isNotEmpty()
    val first = n.media.firstOrNull()
    val shape = RoundedCornerShape(20.dp)
    val dateText = remember(n.updatedAt) { SimpleDateFormat("d MMM", dateLocale()).format(Date(n.updatedAt)) }
    val colName = vm.notes.collections.firstOrNull { it.id == n.collectionId }?.name ?: ""
    val dateLine = dateText + (if (colName.isNotEmpty()) " \u00B7 " + colName else "")

    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .then(if (isSel) Modifier.border(3.dp, cs.primary, shape) else Modifier)
            .pointerInput(ids, selecting) {
                detectTapGestures(
                    onLongPress = { vm.toggleSelect(ids) },
                    onTap = {
                        if (selecting) {
                            vm.toggleSelect(ids)
                        } else if (grouped || !hasText) {
                            vm.openViewerFor(entry)
                        } else {
                            vm.openNote(n)
                        }
                    },
                )
            },
    ) {
        if (grouped) {
            // visual members as a row of tiles; voice notes / files / texts as compact rows under them
            val visual = entry.filter { x -> x.media.firstOrNull()?.let { it.kind == MK_IMAGE || it.kind == MK_VIDEO } == true }
            val others = entry.filter { x -> !visual.contains(x) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(if (isSel) cs.primaryContainer else cs.surface)
                    .padding(12.dp),
            ) {
                if (visual.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        for (x in visual.take(4)) {
                            val m = x.media[0]
                            MediaCover(m, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)), 300)
                        }
                    }
                }
                for (x in others.take(3)) {
                    val m = x.media.firstOrNull()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = if (visual.isNotEmpty() || x !== others[0]) 6.dp else 0.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RkIcon(
                            when (m?.kind) {
                                MK_AUDIO -> RkIc.MIC
                                MK_FILE -> RkIc.FILE
                                else -> RkIc.PEN
                            },
                            cs.onSurfaceVariant,
                            18.dp,
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            when (m?.kind) {
                                MK_AUDIO -> NoteMedia.fmtDuration(m.durMs)
                                MK_FILE -> if (m.label.isEmpty()) m.file else m.label
                                else -> NoteTools.headline(x)
                            },
                            color = cs.onSurface,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                        )
                    }
                }
                if (others.size > 3) {
                    Text("+" + (others.size - 3).toString(), color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.grp_title, entry.size.toString()),
                        color = cs.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(dateLine, color = cs.onSurfaceVariant, fontSize = 11.5.sp)
                }
            }
        } else if (mediaOnly && first != null && first.kind == MK_VIDEO) {
            // video: same framed card as a voice note (rounded preview inside a panel + a info row)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(if (isSel) cs.primaryContainer else cs.surface)
                    .padding(10.dp),
            ) {
                MediaCover(first, Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp)), 1000)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RkIcon(RkIc.VIDEO, cs.onSurfaceVariant, 18.dp)
                    Text(
                        if (first.durMs > 0L) NoteMedia.fmtDuration(first.durMs) else "",
                        color = cs.onSurface,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(dateLine, color = cs.onSurfaceVariant, fontSize = 11.5.sp)
                }
            }
        } else if (mediaOnly && first != null && first.kind == MK_IMAGE) {
            // photo / video: a big picture card
            Box(Modifier.fillMaxWidth()) {
                MediaCover(first, Modifier.fillMaxWidth().height(190.dp), 1000)
                if (first.kind == MK_VIDEO && first.durMs > 0L) {
                    Text(
                        NoteMedia.fmtDuration(first.durMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .background(Color(0x99000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                Text(
                    dateLine + (if (n.media.size > 1) " \u00B7 +" + (n.media.size - 1).toString() else ""),
                    color = Color.White,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        } else if (mediaOnly && first != null && first.kind == MK_AUDIO) {
            // voice note: a compact player row
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (isSel) cs.primaryContainer else cs.surface)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AudioPlayer(first, Modifier.weight(1f), seekable = false)
                Text(dateLine, color = cs.onSurfaceVariant, fontSize = 11.5.sp, modifier = Modifier.padding(start = 10.dp))
            }
        } else if (mediaOnly && first != null) {
            // a file
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (isSel) cs.primaryContainer else cs.surface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.padding(end = 12.dp)) { RkIcon(RkIc.FILE, cs.onSurfaceVariant, 28.dp) }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (first.label.isEmpty()) first.file else first.label,
                        color = cs.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(dateLine, color = cs.onSurfaceVariant, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        } else {
            // a text note (with or without media inside it)
            val head = NoteTools.headline(n)
            val prev = NoteTools.preview(n)
            val url = NoteTools.firstUrl(n.body)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(if (isSel) cs.primaryContainer else cs.surface)
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
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                        modifier = Modifier.weight(1f),
                    )
                    if (n.pinned) Text("\u2605", color = cs.primary, fontSize = 16.sp)
                }
                if (prev.isNotEmpty()) {
                    Text(prev, color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(textDirection = TextDirection.Content), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
                if (url != null) {
                    Text(
                        "\u2197 " + NoteTools.domain(url),
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
                        for (m in n.media.take(3)) MediaThumb(m, 56)
                        if (n.media.size > 3) Text("+" + (n.media.size - 3).toString(), color = cs.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
                Text(dateLine, color = cs.onSurfaceVariant, fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (isSel) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(cs.primary),
                contentAlignment = Alignment.Center,
            ) { Text("\u2713", color = cs.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
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
            MK_VIDEO -> RkIcon(RkIc.PLAY, cs.onSurface, 22.dp)
            MK_AUDIO -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RkIcon(RkIc.MIC, cs.onSurface, 20.dp)
                Text(NoteMedia.fmtDuration(m.durMs), color = cs.onSurface, fontSize = 11.sp)
            }
            MK_FILE -> RkIcon(RkIc.FILE, cs.onSurface, 22.dp)
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
    var showVoice by remember { mutableStateOf(false) }
    val url = NoteTools.firstUrl(n.body)

    fun toast(id: Int) {
        Toast.makeText(ctx, LocaleHelperText(ctx, id), Toast.LENGTH_SHORT).show()
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
        showVoice = true
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
                    Text(stringResource(R.string.note_title_hint), color = cs.onSurfaceVariant, fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                }
                BasicTextField(
                    value = n.title,
                    onValueChange = { v -> vm.editDraftText(v, n.body) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = cs.onBackground,
                        fontSize = 22.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textDirection = TextDirection.Content,
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                    ),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().heightIn(min = 140.dp)) {
                if (n.body.isEmpty()) {
                    Text(stringResource(R.string.note_body_hint), color = cs.onSurfaceVariant, fontSize = 16.sp, lineHeight = 26.sp)
                }
                BasicTextField(
                    value = n.body,
                    onValueChange = { v -> vm.editDraftText(n.title, v) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = cs.onSurface,
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        textDirection = TextDirection.Content,
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                    ),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (url != null) {
                Text(
                    "\u2197 " + NoteTools.domain(url),
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

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconPill(RkIc.MIC, stringResource(R.string.cap_voice)) { startVoice() }
            IconPill(RkIc.CAMERA, stringResource(R.string.cap_photo)) { takePhoto() }
            IconPill(RkIc.VIDEO, stringResource(R.string.cap_video)) { takeVideo() }
            IconPill(RkIc.FILE, stringResource(R.string.cap_file)) {
                try {
                    attach.launch("*/*")
                } catch (e: Throwable) {
                    AppLog.log("NOTES", "Picker failed", e)
                }
            }
        }
    }
    VoiceSheet(visible = showVoice, onClose = { showVoice = false }, onSaved = { m -> vm.addDraftMedia(m) })

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
                Box(Modifier.padding(start = 6.dp)) { RkIcon(RkIc.FILE, cs.onSurfaceVariant, 22.dp) }
                Text(
                    (if (m.label.isEmpty()) m.file else m.label),
                    color = cs.onSurface,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { val e = vm.editing; if (e != null) vm.openViewerMedia(e, m.id) }
                        .padding(8.dp),
                )
            }
            else -> {
                Box(
                    Modifier
                        .weight(1f)
                        .clickable { val e = vm.editing; if (e != null) vm.openViewerMedia(e, m.id) },
                ) {
                    MediaThumb(m, 120)
                }
            }
        }
        MiniBtn("✕") { vm.removeDraftMedia(m.id) }
    }
}

@Composable
fun AudioPlayer(m: Media, modifier: Modifier, seekable: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(m.durMs.toInt()) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableStateOf(0f) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }

    fun release() {
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

    fun ensure(): MediaPlayer? {
        val old = player[0]
        if (old != null) return old
        return try {
            val p = MediaPlayer()
            p.setDataSource(NoteStore.mediaFile(ctx, m).absolutePath)
            p.prepare()
            p.setOnCompletionListener { mp ->
                playing = false
                try {
                    mp.seekTo(0)
                } catch (e: Throwable) {
                    // ignore
                }
                pos = 0
            }
            if (p.duration > 0) total = p.duration
            player[0] = p
            p
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not open audio", e)
            release()
            null
        }
    }

    fun toggle() {
        val p = ensure() ?: return
        try {
            if (p.isPlaying) {
                p.pause()
                playing = false
            } else {
                p.start()
                playing = true
            }
        } catch (e: Throwable) {
            AppLog.log("NOTES", "Could not play audio", e)
            release()
        }
    }

    DisposableEffect(m.file) {
        onDispose { release() }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player[0]
            if (p != null && !dragging) {
                try {
                    val c = p.currentPosition
                    if (c != pos) pos = c
                } catch (e: Throwable) {
                    // ignore
                }
            }
            androidx.compose.runtime.withFrameNanos { }
        }
    }

    val shownPos = if (dragging) dragPos.toInt() else pos
    Row(modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(cs.primaryContainer)
                .clickable { toggle() },
            contentAlignment = Alignment.Center,
        ) {
            RkIcon(if (playing) RkIc.PAUSE else RkIc.PLAY, cs.onPrimaryContainer, 18.dp)
        }
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            if (!seekable && playing) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(cs.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((shownPos.toFloat() / maxOf(total, 1).toFloat()).coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(cs.primary),
                    )
                }
            }
            if (seekable) androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                androidx.compose.material3.Slider(
                    value = shownPos.toFloat().coerceIn(0f, maxOf(total, 1).toFloat()),
                    onValueChange = { v ->
                        dragging = true
                        dragPos = v
                    },
                    onValueChangeFinished = {
                        val p = ensure()
                        if (p != null) {
                            try {
                                p.seekTo(dragPos.toInt())
                                pos = dragPos.toInt()
                            } catch (e: Throwable) {
                                // ignore
                            }
                        }
                        dragging = false
                    },
                    valueRange = 0f..maxOf(total, 1).toFloat(),
                    modifier = Modifier.height(28.dp),
                )
            }
            Text(
                if (!seekable && !playing) NoteMedia.fmtDuration(total.toLong()) else NoteMedia.fmtDuration(shownPos.toLong()) + " / " + NoteMedia.fmtDuration(total.toLong()),
                color = cs.onSurfaceVariant,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
