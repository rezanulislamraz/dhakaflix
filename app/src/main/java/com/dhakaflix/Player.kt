package com.dhakaflix

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale

// ডায়ালগের অপশন (অডিও / সাবটাইটেল ট্র্যাক)
data class TrackOpt(val label: String, val group: Tracks.Group?, val index: Int, val selected: Boolean)

sealed interface PlayerDialog {
    data class TrackList(val title: String, val text: Boolean, val opts: List<TrackOpt>) : PlayerDialog
    data object SpeedList : PlayerDialog
}

private fun trackLabel(f: Format, i: Int): String {
    val lang = f.language?.takeIf { it != "und" }?.let { Locale(it).displayLanguage }
    val ch = when (f.channelCount) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> null
    }
    val codec = f.sampleMimeType?.substringAfter('/')?.uppercase()
    return listOfNotNull(f.label, lang, codec, ch).distinct().joinToString(" · ").ifBlank { "Track ${i + 1}" }
}

private fun trackOptions(p: Player, type: Int): List<TrackOpt> =
    p.currentTracks.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }
            .map { i -> TrackOpt(trackLabel(g.getTrackFormat(i), i), g, i, g.isTrackSelected(i)) }
    }

private val resizeModes = intArrayOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
)
private val resizeNames = listOf("Fit", "Zoom", "Stretch")

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(file: Entry, siblings: List<Entry>, onBack: () -> Unit, onSwitch: (Entry) -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity

    // একই ফোল্ডারের ভিডিও দিয়ে Next / Previous
    val videos = remember(siblings, file.path) { siblings.filter { !it.isDir && Names.isVideo(it.name) } }
    val idx = videos.indexOfFirst { it.path == file.path }
    val prev = if (idx > 0) videos[idx - 1] else null
    val next = if (idx >= 0) videos.getOrNull(idx + 1) else null

    // ---------- প্লেয়ার তৈরি ----------
    val player = remember {
        // FTP হলে আমাদের কাস্টম DataSource, HTTP হলে স্ট্যান্ডার্ড
        val dsf: DataSource.Factory =
            if (Server.isFtp()) DataSource.Factory { FtpDataSource() }
            else DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)

        // বাফারিং: কম ল্যাগ, নেটওয়ার্ক স্লো হলে বেশি বাফার
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 60_000, 2_500, 5_000)
            .setTargetBufferBytes(48 * 1024 * 1024)
            .build()
        val rf = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)

        ExoPlayer.Builder(ctx, rf)
            .setLoadControl(load)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsf))
            .build().also { p ->
                // একই নামের .srt / .vtt ফাইল থাকলে স্বয়ংক্রিয়ভাবে যোগ হবে
                val base = file.name.substringBeforeLast('.')
                val subs = siblings
                    .filter { !it.isDir && Names.isSub(it.name) && it.name.startsWith(base, ignoreCase = true) }
                    .map {
                        MediaItem.SubtitleConfiguration.Builder(Server.url(it.path))
                            .setMimeType(if (it.name.endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                            .setLabel(it.name.substringAfterLast('.').uppercase() + " · external")
                            .build()
                    }
                val item = MediaItem.Builder().setUri(Server.url(file.path)).setSubtitleConfigurations(subs).build()
                val saved = Store.progress(file.path)
                val start = if (saved != null && saved.pos > 10_000 && saved.pos < saved.dur * 0.95) saved.pos else 0L
                p.setMediaItem(item, start)
                p.prepare()
                p.playWhenReady = true
            }
    }

    // ---------- স্টেট ----------
    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var showUi by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var resize by remember { mutableIntStateOf(0) }
    var speed by remember { mutableStateOf(1f) }
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var flash by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var flashN by remember { mutableIntStateOf(0) }
    var interact by remember { mutableIntStateOf(0) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    fun seekBy(dir: Int) {
        val d = if (player.duration > 0) player.duration else Long.MAX_VALUE
        player.seekTo((player.currentPosition + dir * Prefs.seekSec * 1000L).coerceIn(0L, d))
        flashN++
        flash = (if (dir > 0) "+" else "−") + "${Prefs.seekSec}s" to flashN
        interact++
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
        interact++
    }

    // ---------- প্লেয়ার ইভেন্ট ----------
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    Store.saveProgress(file, 0L, player.duration.coerceAtLeast(1L))
                    if (next != null) onSwitch(next) else onBack()
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                error = "${e.errorCodeName}: ${e.cause?.message ?: e.message ?: ""}"
            }
        }
        player.addListener(l)
        onDispose {
            Store.saveProgress(file, player.currentPosition, player.duration)
            player.removeListener(l)
            player.release()
        }
    }

    // অ্যাপ ব্যাকগ্রাউন্ডে গেলে পজ
    DisposableEffect(player) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_STOP) player.pause() }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }

    // ফুলস্ক্রিন + ল্যান্ডস্কেপ (ফোনে)
    DisposableEffect(Unit) {
        val win = activity.window
        val ctrl = WindowCompat.getInsetsController(win, win.decorView)
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            ctrl.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // পজিশন আপডেট + প্রতি ৫ সেকেন্ডে অগ্রগতি সেভ
    LaunchedEffect(player) {
        var tick = 0
        while (true) {
            pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0L)
            if (++tick % 10 == 0 && player.isPlaying) Store.saveProgress(file, pos, dur)
            delay(500)
        }
    }

    // কন্ট্রোল অটো-হাইড
    LaunchedEffect(showUi, interact, isPlaying, dialog != null, locked) {
        if (showUi && (isPlaying || locked) && dialog == null) {
            delay(if (locked) 2500 else 4500)
            showUi = false
        }
    }
    LaunchedEffect(flash) {
        if (flash != null) { delay(700); flash = null }
    }
    // রিমোটের ফোকাস: কন্ট্রোল দেখালে Play বাটনে, লুকালে রুটে
    LaunchedEffect(showUi, locked, dialog) {
        if (dialog != null) return@LaunchedEffect
        delay(80)
        if (showUi && !locked) runCatching { playFocus.requestFocus() }
        else if (!showUi) runCatching { rootFocus.requestFocus() }
    }

    BackHandler(enabled = true) {
        when {
            dialog != null -> dialog = null
            locked -> { showUi = true; interact++ }
            showUi -> showUi = false
            else -> onBack()
        }
    }

    // ---------- UI ----------
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (locked) { showUi = true; interact++; return@onKeyEvent false }
                when (ev.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { togglePlay(); true }
                    Key.MediaFastForward -> { seekBy(1); true }
                    Key.MediaRewind -> { seekBy(-1); true }
                    Key.MediaNext -> { next?.let(onSwitch); true }
                    Key.MediaPrevious -> { prev?.let(onSwitch); true }
                    else -> if (!showUi) when (ev.key) {
                        Key.DirectionRight -> { seekBy(1); true }
                        Key.DirectionLeft -> { seekBy(-1); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> { showUi = true; interact++; true }
                        else -> false
                    } else false
                }
            }
            .focusRequester(rootFocus)
            .focusable()
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = { showUi = if (locked) true else !showUi; interact++ },
                    onDoubleTap = { o ->
                        if (!locked && Prefs.doubleTap) seekBy(if (o.x < size.width / 2) -1 else 1)
                    },
                )
            },
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    keepScreenOn = true
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK, null,
                        ),
                    )
                    this.player = player
                }
            },
            update = { it.resizeMode = resizeModes[resize] },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Lime)
        }

        // সিক ফ্ল্যাশ (+5s / −5s)
        AnimatedVisibility(flash != null, Modifier.align(Alignment.Center), enter = fadeIn(tween(80)), exit = fadeOut(tween(300))) {
            Text(
                flash?.first ?: "", color = OnLime, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.clip(CircleShape).background(Lime).padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }

        // ---------- কন্ট্রোল ওভারলে ----------
        AnimatedVisibility(showUi && !locked, enter = fadeIn(tween(200)), exit = fadeOut(tween(250))) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(110.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))))
                Box(Modifier.fillMaxWidth().height(230.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))))

                // উপরে: Back + শিরোনাম
                Row(Modifier.align(Alignment.TopStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                    val p = remember(file.name) { Names.parse(file.name) }
                    val ep = if (p.season != null && p.episode != null) "  S%02dE%02d".format(p.season, p.episode) else ""
                    Text(p.title + ep, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // মাঝখানে: Prev, Rewind, Play/Pause, Forward, Next
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIcon(Icons.Default.SkipPrevious, "Previous", size = 48.dp) { prev?.let(onSwitch) }
                    RoundIcon(Icons.Default.FastRewind, "Rewind", size = 52.dp) { seekBy(-1) }
                    RoundIcon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause",
                        Modifier.focusRequester(playFocus), size = 72.dp, primary = true,
                    ) { togglePlay() }
                    RoundIcon(Icons.Default.FastForward, "Forward", size = 52.dp) { seekBy(1) }
                    RoundIcon(Icons.Default.SkipNext, "Next", size = 48.dp) { next?.let(onSwitch) }
                }

                // নিচে: সিক বার + টুলস
                Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtTime(pos), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Text("−" + fmtTime((dur - pos).coerceAtLeast(0)), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    SeekBar(
                        pos, dur,
                        onSeekTo = { player.seekTo(it); interact++ },
                        onStep = { seekBy(it) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToolButton(Icons.Default.Audiotrack, "Audio") {
                            dialog = PlayerDialog.TrackList("Audio", false, trackOptions(player, C.TRACK_TYPE_AUDIO))
                        }
                        ToolButton(Icons.Default.Subtitles, "Subtitles") {
                            dialog = PlayerDialog.TrackList("Subtitles", true, trackOptions(player, C.TRACK_TYPE_TEXT))
                        }
                        ToolButton(Icons.Default.AspectRatio, resizeNames[resize]) {
                            resize = (resize + 1) % resizeModes.size; interact++
                        }
                        ToolButton(Icons.Default.Speed, "${speed}x") { dialog = PlayerDialog.SpeedList }
                        ToolButton(Icons.Default.Lock, "Lock") { locked = true; showUi = true; interact++ }
                    }
                }
            }
        }

        // লক অবস্থায় শুধু আনলক বাটন
        AnimatedVisibility(showUi && locked, Modifier.align(Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
            RoundIcon(Icons.Default.LockOpen, "Unlock", Modifier.padding(24.dp).focusRequester(playFocus), size = 60.dp, primary = true) {
                locked = false; showUi = true; interact++
            }
        }

        // ---------- ডায়ালগ (Audio / Subtitles / Speed) ----------
        dialog?.let { d ->
            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
            val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            val options: List<Triple<String, Boolean, Int>> = when (d) {
                is PlayerDialog.TrackList -> {
                    val list = d.opts.mapIndexed { i, o -> Triple(o.label, o.selected && !(d.text && textOff), i) }
                    if (d.text) listOf(Triple("Off", textOff || d.opts.none { it.selected }, -1)) + list else list
                }
                PlayerDialog.SpeedList -> speeds.mapIndexed { i, s -> Triple("${s}x", s == speed, i) }
            }
            OptionDialog(
                title = when (d) {
                    is PlayerDialog.TrackList -> d.title
                    PlayerDialog.SpeedList -> "Playback speed"
                },
                options = options,
                onPick = { i ->
                    when (d) {
                        is PlayerDialog.TrackList -> {
                            val b = player.trackSelectionParameters.buildUpon()
                            if (d.text && i == -1) {
                                b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)   // সাবটাইটেল বন্ধ
                            } else {
                                val o = d.opts[i]
                                val g = o.group
                                if (g != null) {
                                    if (d.text) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, o.index))
                                }
                            }
                            player.trackSelectionParameters = b.build()
                        }
                        PlayerDialog.SpeedList -> {
                            speed = speeds[i]
                            player.setPlaybackSpeed(speed)
                        }
                    }
                    dialog = null
                    interact++
                },
                onClose = { dialog = null },
            )
        }

        // ---------- এরর ----------
        error?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).padding(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xEE15171B)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Can't play this file", color = Danger, style = MaterialTheme.typography.titleMedium)
                Text(msg, color = TextBody, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Some audio formats (like DTS) are not supported on every device. Try another file or check your connection to the server.",
                    color = TextBody, style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Retry", primary = true) {
                        error = null; buffering = true
                        player.prepare(); player.play()
                    }
                    PillButton("Back") { onBack() }
                }
            }
        }
    }
}

// ======================= সিক বার =======================
@Composable
private fun SeekBar(pos: Long, dur: Long, onSeekTo: (Long) -> Unit, onStep: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = drag ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val h by animateDpAsState(if (focused || drag != null) 10.dp else 5.dp, tween(120), label = "sb")

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) when (ev.key) {
                    Key.DirectionLeft -> { onStep(-1); true }
                    Key.DirectionRight -> { onStep(1); true }
                    else -> false
                } else false
            }
            .focusable()
            .pointerInput(dur) {
                detectTapGestures { o -> if (dur > 0) onSeekTo((o.x / size.width * dur).toLong()) }
            }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { if (dur > 0) onSeekTo((it * dur).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(h).clip(CircleShape).background(Color(0x44FFFFFF)))
        Box(Modifier.fillMaxWidth(frac).height(h).clip(CircleShape).background(Lime))
        Box(Modifier.offset(x = maxWidth * frac - 7.dp).size(14.dp).clip(CircleShape).background(if (focused) Color.White else Lime))
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Lime else Color(0x33FFFFFF), tween(120), label = "tb")
    val fg = if (focused) OnLime else Color.White
    Row(
        Modifier.clip(CircleShape).background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

// অপশন বাছাইয়ের পপআপ: Triple(লেবেল, সিলেক্টেড কি না, ইনডেক্স)
@Composable
private fun OptionDialog(
    title: String,
    options: List<Triple<String, Boolean, Int>>,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(360.dp).fillMaxHeight(0.8f).clip(RoundedCornerShape(18.dp)).background(Color(0xFF15171B))
                .clickable(enabled = false) {}.padding(18.dp),
        ) {
            Text(title, color = TextHead, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
            if (options.isEmpty()) {
                Text("Nothing available for this video.", color = TextBody, style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn {
                items(options.size) { n ->
                    val (label, selected, idx) = options[n]
                    var focused by remember { mutableStateOf(false) }
                    val bg by animateColorAsState(if (focused) Color(0xFF1F2329) else Color.Transparent, tween(100), label = "od")
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg)
                            .then(if (n == 0) Modifier.focusRequester(first) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .clickable { onPick(idx) }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = if (selected) Lime else TextHead, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2)
                        if (selected) Text("●", color = Lime)
                    }
                }
            }
        }
    }
}
