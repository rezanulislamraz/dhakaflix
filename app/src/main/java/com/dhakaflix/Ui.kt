package com.dhakaflix

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ======================= ছোট ছোট কম্পোনেন্ট =======================

/** লাইম বর্ডার + স্কেল অ্যানিমেশনসহ বাটন (টাচ ও রিমোট দুটোতেই কাজ করে) */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector? = null,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(stiffness = 400f), label = "pb")
    val bg by animateColorAsState(
        when {
            primary -> Lime
            focused -> CardLift
            else -> CardDark
        },
        tween(150), label = "pbc",
    )
    val fg = if (primary) OnLime else TextHead
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, if (focused) Color.White else if (primary) Color.Transparent else Color(0x33FFFFFF), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun RoundIcon(
    icon: ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, spring(stiffness = 450f), label = "ri")
    val bg by animateColorAsState(
        if (primary || focused) Lime else Color(0x44000000), tween(120), label = "ric",
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (primary || focused) OnLime else Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Tag(text: String, filled: Boolean = false) {
    Text(
        text,
        color = if (filled) OnLime else TextHead,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) Lime else Color(0x22FFFFFF))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(16.dp)).background(CardDark).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Can't reach the server", color = Danger, style = MaterialTheme.typography.titleMedium)
        Text(message, color = TextBody, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Check that you are on a BDIX-enabled ISP and that the server address in Settings is correct.",
            color = TextBody, style = MaterialTheme.typography.bodySmall,
        )
        PillButton("Retry", Icons.Default.Refresh, primary = true, onClick = onRetry)
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Lime, unfocusedBorderColor = Color(0x44FFFFFF),
    focusedTextColor = TextHead, unfocusedTextColor = TextHead,
    cursorColor = Lime, focusedLabelColor = Lime, unfocusedLabelColor = TextBody,
    focusedPlaceholderColor = TextBody, unfocusedPlaceholderColor = TextBody,
)

// ======================= পোস্টার কার্ড =======================
@Composable
fun MediaCard(entry: Entry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val parsed = remember(entry.name) { Names.parse(entry.name) }
    val wantMeta = !entry.isDir || parsed.year != null || parsed.season != null
    val meta by produceState<Meta?>(null, entry.name, Prefs.tmdbKey) {
        if (wantMeta) value = Tmdb.lookup(parsed)
    }
    var focused by remember { mutableStateOf(false) }
    // ফোকাস করলে কার্ড বড় হয় ও লাইম বর্ডার আসে
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, spring(dampingRatio = 0.75f, stiffness = 380f), label = "scale")
    val ring by animateColorAsState(if (focused) Lime else Color.Transparent, tween(160), label = "ring")
    val prog = if (entry.isDir) null else Store.fraction(entry.path)
    val shape = RoundedCornerShape(14.dp)

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(CardDark)
                .border(3.dp, ring, shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick),
        ) {
            val poster = meta?.poster
            if (poster != null) {
                AsyncImage(
                    model = Tmdb.img(poster, "w342"), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        if (entry.isDir) Icons.Default.Folder else Icons.Default.Movie, null,
                        tint = if (entry.isDir) Lime else TextBody, modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        parsed.title.ifBlank { entry.name }, color = TextHead,
                        style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (prog != null) {
                LinearProgressIndicator(
                    progress = { prog },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    color = Lime, trackColor = Color(0x66000000),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            meta?.title ?: parsed.title.ifBlank { entry.name },
            color = TextHead, style = MaterialTheme.typography.titleSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        val sub = listOfNotNull(
            parsed.year?.toString() ?: meta?.year?.takeIf { it.isNotEmpty() },
            if (entry.isDir) "Folder" else null,
            parsed.quality.firstOrNull(),
        ).joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, color = TextBody, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
fun Shelf(title: String, items: List<Entry>, cardW: Dp, onOpen: (Entry) -> Unit) {
    if (items.isEmpty()) return
    Column {
        Text(
            title, style = MaterialTheme.typography.headlineSmall, color = TextHead,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items.distinctBy { it.path }, key = { it.path }) { e ->
                MediaCard(e, Modifier.width(cardW)) { onOpen(e) }
            }
        }
    }
}

// ======================= হিরো ব্যানার =======================
@Composable
fun Hero(
    entry: Entry?, tag: String, wide: Boolean,
    onPlay: () -> Unit, onMore: () -> Unit, onBrowse: () -> Unit,
) {
    val parsed = remember(entry?.name) { entry?.let { Names.parse(it.name) } }
    val meta by produceState<Meta?>(null, entry?.name, Prefs.tmdbKey) {
        value = parsed?.let { Tmdb.lookup(it) }
    }
    val h = if (wide) 380.dp else 320.dp
    Box(Modifier.fillMaxWidth().height(h)) {
        Crossfade(targetState = meta?.backdrop, animationSpec = tween(600), label = "hero") { bd ->
            if (bd != null) {
                AsyncImage(
                    model = Tmdb.img(bd, "w1280"), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1A2608), Bg))))
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x33000000), 1f to Bg)))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xDD0A0B0D), 0.75f to Color.Transparent)))

        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                .fillMaxWidth(if (wide) 0.6f else 1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (entry == null || parsed == null) {
                Text(
                    buildAnnotatedString {
                        append("Dhaka ")
                        withStyle(SpanStyle(color = Lime)) { append("Flix") }
                    },
                    style = if (wide) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                    color = TextHead,
                )
                Text("Everything on your server, beautifully streamed.", color = TextBody, style = MaterialTheme.typography.bodyLarge)
                PillButton("Browse library", Icons.Default.Folder, primary = true, onClick = onBrowse)
            } else {
                Text(tag.uppercase(), color = Lime, style = MaterialTheme.typography.labelLarge)
                Text(
                    meta?.title ?: parsed.title,
                    style = if (wide) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
                    color = TextHead, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val line = listOfNotNull(
                    meta?.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                    parsed.year?.toString() ?: meta?.year?.takeIf { it.isNotEmpty() },
                    parsed.quality.firstOrNull(),
                ).joinToString("  ·  ")
                if (line.isNotEmpty()) Text(line, color = TextHead, style = MaterialTheme.typography.labelLarge)
                meta?.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = TextBody, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Play", Icons.Default.PlayArrow, primary = true, onClick = onPlay)
                    PillButton("Details", onClick = onMore)
                }
            }
        }
    }
}

// ======================= হোম =======================
@Composable
fun HomeScreen(wide: Boolean, onOpen: (Entry) -> Unit, onPlay: (Entry) -> Unit, onLibrary: () -> Unit) {
    val cont = remember(Store.history) {
        Store.history
            .filter { it.dur > 0 && it.pos > 10_000 && it.pos < it.dur * 0.95 }
            .map { Entry(it.title, it.path, false) }
    }
    val recent = remember(Indexer.entries) {
        Indexer.entries.filter { !it.isDir && Names.isVideo(it.name) && it.time > 0 }
            .sortedByDescending { it.time }.take(30)
    }
    var attempt by remember { mutableIntStateOf(0) }
    var folders by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(attempt, Prefs.server) {
        error = null
        try {
            folders = Listings.get(Server.root(), attempt > 0).filter { it.isDir }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }
    val hero = cont.firstOrNull() ?: recent.firstOrNull()
    val cardW = if (wide) 170.dp else 128.dp
    val err = error

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Hero(
                hero, if (cont.isNotEmpty()) "Continue watching" else "Recently added", wide,
                onPlay = { hero?.let(onPlay) }, onMore = { hero?.let(onOpen) }, onBrowse = onLibrary,
            )
        }
        if (err != null) item { ErrorBox(err) { attempt++ } }
        item { Shelf("Continue Watching", cont, cardW, onOpen) }
        item { Shelf("My List", Store.myList, cardW, onOpen) }
        item { Shelf("Recently Added", recent, cardW, onOpen) }
        item { Shelf("Library", folders, cardW, onOpen) }
    }
}

// ======================= ব্রাউজ (ফোল্ডার দেখা) =======================
private sealed interface BrowseState {
    data object Loading : BrowseState
    data class Ok(val list: List<Entry>) : BrowseState
    data class Err(val msg: String) : BrowseState
}

@Composable
fun BrowseScreen(path: String, title: String, wide: Boolean, onOpen: (Entry) -> Unit, onBack: (() -> Unit)?) {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(path, attempt) { mutableStateOf<BrowseState>(BrowseState.Loading) }
    LaunchedEffect(path, attempt) {
        state = try {
            BrowseState.Ok(Listings.get(path, force = attempt > 0))
        } catch (e: Exception) {
            BrowseState.Err(e.message ?: e.javaClass.simpleName)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.width(14.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = TextHead, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(path, style = MaterialTheme.typography.bodySmall, color = TextBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when (val s = state) {
            is BrowseState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Lime)
            }
            is BrowseState.Err -> ErrorBox(s.msg) { attempt++ }
            is BrowseState.Ok -> {
                val shown = s.list.filter { it.isDir || Names.isVideo(it.name) }
                if (shown.isEmpty()) {
                    Text("This folder is empty.", color = TextBody, modifier = Modifier.padding(24.dp))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (wide) 160.dp else 116.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(shown, key = { it.path }) { e -> MediaCard(e) { onOpen(e) } }
                    }
                }
            }
        }
    }
}

// ======================= সার্চ =======================
@Composable
fun SearchScreen(wide: Boolean, onOpen: (Entry) -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val results = remember(q, Indexer.entries) {
        val t = q.lowercase().split(' ').filter { it.isNotBlank() }
        if (t.isEmpty()) emptyList() else Indexer.entries.asSequence().filter { e ->
            val n = e.name.lowercase().replace('.', ' ').replace('_', ' ')
            t.all { n.contains(it) }
        }.take(150).toList()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Search title, year, language…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = CircleShape,
        )
        Spacer(Modifier.height(10.dp))
        when {
            Indexer.entries.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (Indexer.running) "Scanning server… ${Indexer.count} items so far"
                    else "Search needs a one-time scan of your server.",
                    color = TextBody,
                )
                if (!Indexer.running) PillButton("Scan server now", Icons.Default.Refresh, primary = true) { Indexer.rebuild(ctx) }
            }
            q.isBlank() -> Text("${Indexer.entries.size} items indexed. Type to search.", color = TextBody)
            results.isEmpty() -> Text("No matches.", color = TextBody)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(if (wide) 160.dp else 116.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(results, key = { it.path }) { e -> MediaCard(e) { onOpen(e) } }
            }
        }
    }
}

// ======================= বিস্তারিত পেজ =======================
@Composable
fun DetailsScreen(file: Entry, siblings: List<Entry>, wide: Boolean, onPlay: (Entry) -> Unit, onBack: () -> Unit) {
    val parsed = remember(file.name) { Names.parse(file.name) }
    val meta by produceState<Meta?>(null, file.name, Prefs.tmdbKey) { value = Tmdb.lookup(parsed) }
    val saved = Store.progress(file.path)
    val resume = saved != null && saved.pos > 10_000 && saved.pos < saved.dur * 0.95
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(350)
        runCatching { playFocus.requestFocus() }
    }
    val videos = remember(siblings) { siblings.filter { !it.isDir && Names.isVideo(it.name) } }
    val seasons = remember(videos) { videos.mapNotNull { Names.parse(it.name).season }.distinct().sorted() }
    var season by remember { mutableStateOf(parsed.season) }
    val listShown = if (seasons.size > 1 && season != null) videos.filter { Names.parse(it.name).season == season } else videos
    val inList = Store.inList(file.path)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(if (wide) 360.dp else 300.dp)) {
                val bd = meta?.backdrop
                if (bd != null) {
                    AsyncImage(
                        model = Tmdb.img(bd, "w1280"), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1A2608), Bg))))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x44000000), 1f to Bg)))
                RoundIcon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    Modifier.align(Alignment.TopStart).padding(16.dp), onClick = onBack,
                )
                Row(
                    Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    meta?.poster?.let {
                        AsyncImage(
                            model = Tmdb.img(it, "w342"), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(if (wide) 130.dp else 96.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            meta?.title ?: parsed.title,
                            style = if (wide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                            color = TextHead, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            meta?.rating?.takeIf { it > 0 }?.let { Tag("★ %.1f TMDb".format(it), filled = true) }
                            (parsed.year?.toString() ?: meta?.year)?.takeIf { it.isNotEmpty() }?.let { Tag(it) }
                            parsed.quality.forEach { Tag(it) }
                            fmtSize(file.size).takeIf { it.isNotEmpty() }?.let { Tag(it) }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    if (resume) "Resume ${fmtTime(saved!!.pos)}" else "Play", Icons.Default.PlayArrow,
                    primary = true, modifier = Modifier.focusRequester(playFocus),
                ) { onPlay(file) }
                PillButton(
                    if (inList) "In My List" else "My List",
                    if (inList) Icons.Default.Check else Icons.Default.Add,
                ) { Store.toggleList(file) }
            }
        }
        item {
            val overview = meta?.overview?.takeIf { it.isNotBlank() } ?: when {
                Prefs.tmdbKey.isBlank() -> "Add a free TMDb API key in Settings to see posters, ratings and the plot summary."
                else -> "No details found for this title."
            }
            Text(overview, color = TextBody, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
        }
        if (videos.size > 1) {
            item {
                Text("In this folder", color = TextHead, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp))
                if (seasons.size > 1) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        seasons.forEach { s ->
                            FilterChip(
                                selected = season == s, onClick = { season = s }, label = { Text("Season $s") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lime, selectedLabelColor = OnLime),
                            )
                        }
                    }
                }
            }
            items(listShown, key = { it.path }) { v -> EpisodeRow(v, v.path == file.path) { onPlay(v) } }
        }
    }
}

@Composable
fun EpisodeRow(v: Entry, current: Boolean, onClick: () -> Unit) {
    val p = remember(v.name) { Names.parse(v.name) }
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) CardLift else CardDark, tween(120), label = "ep")
    val label = if (p.season != null && p.episode != null) "S%02dE%02d".format(p.season, p.episode) else null
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp)).background(bg)
            .border(2.dp, if (focused) Lime else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.PlayArrow, null, tint = if (current) Lime else TextBody)
        Column(Modifier.weight(1f)) {
            Text(label ?: p.title, color = TextHead, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(v.name, color = TextBody, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Store.fraction(v.path)?.let {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Lime, trackColor = Color(0x33FFFFFF))
            }
        }
        Text(fmtSize(v.size), color = TextBody, style = MaterialTheme.typography.labelMedium)
    }
}

// ======================= সেটিংস =======================
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var serverText by remember { mutableStateOf(Prefs.server) }
    var keyText by remember { mutableStateOf(Prefs.tmdbKey) }
    var status by remember { mutableStateOf<String?>(null) }

    @Composable
    fun Section(t: String) = Text(
        t.uppercase(), color = Lime, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, color = TextHead)

            Section("Server")
            OutlinedTextField(
                value = serverText, onValueChange = { serverText = it }, singleLine = true,
                label = { Text("Server address (ftp:// or http://)") },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Save", Icons.Default.Check, primary = true) {
                    val changed = Prefs.server != serverText.trim()
                    Prefs.setServer(serverText)
                    serverText = Prefs.server
                    if (changed) Indexer.clear(ctx)
                    status = "Saved ${Prefs.server}"
                }
                PillButton("Test connection", Icons.Default.Refresh) {
                    status = "Testing…"
                    scope.launch {
                        status = try {
                            val l = Server.list(Server.root())
                            "Connected ✓ — ${l.size} items in root"
                        } catch (e: Exception) {
                            "Failed: ${e.message ?: e.javaClass.simpleName}"
                        }
                    }
                }
                PillButton("Reset") { serverText = DEFAULT_SERVER; Prefs.setServer(DEFAULT_SERVER); status = "Reset to $DEFAULT_SERVER" }
            }
            status?.let { Text(it, color = TextBody, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }

            Section("Posters & details (TMDb)")
            OutlinedTextField(
                value = keyText, onValueChange = { keyText = it; Prefs.setTmdbKey(it) }, singleLine = true,
                label = { Text("TMDb API key") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            )
            Text(
                "Free key: themoviedb.org → Settings → API. Posters need internet (not just BDIX).",
                color = TextBody, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
            )

            Section("Playback")
            Text("Seek interval", color = TextHead, style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(5, 10, 15, 20, 30, 60).forEach { s ->
                    FilterChip(
                        selected = Prefs.seekSec == s, onClick = { Prefs.setSeek(s) }, label = { Text("${s}s") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lime, selectedLabelColor = OnLime),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Double-tap to seek", color = TextHead, style = MaterialTheme.typography.titleSmall)
                    Text("Phones and tablets: double-tap left/right side of the video", color = TextBody, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = Prefs.doubleTap, onCheckedChange = { Prefs.setDoubleTap(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Lime, checkedThumbColor = OnLime),
                )
            }

            Section("Library index")
            Text(
                if (Indexer.running) "Scanning… ${Indexer.count} items" else Indexer.message.ifEmpty { "Not scanned yet" },
                color = TextBody, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            PillButton(if (Indexer.running) "Scanning…" else "Rebuild index", Icons.Default.Refresh) { Indexer.rebuild(ctx) }
            Text(
                "The index powers Search and the Recently Added row.",
                color = TextBody, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
            )

            Section("Your data")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Clear Continue Watching") { Store.clearHistory(); status = "History cleared" }
                PillButton("Clear My List") { Store.clearList(); status = "My List cleared" }
            }

            Section("About")
            Text("Dhaka Flix 1.0 · Media3 player · FTP & HTTP browsing", color = TextBody, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
