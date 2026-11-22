package com.dhakaflix

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Store.init(this)
        Tmdb.init(this)
        Indexer.load(this)
        setContent { DhakaFlixTheme { App() } }
    }
}

// ======================= নেভিগেশন =======================
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Settings : Screen
    data class Browse(val path: String, val title: String) : Screen
    data class Details(val file: Entry, val siblings: List<Entry>) : Screen
    data class Player(val file: Entry, val siblings: List<Entry>) : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var busy by mutableStateOf(false)
    val current: Screen get() = stack.last()

    fun tab(s: Screen) { stack.clear(); stack.add(s) }
    fun push(s: Screen) { stack.add(s) }
    fun replaceTop(s: Screen) { stack[stack.lastIndex] = s }
    fun back(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        if (stack[0] != Screen.Home) { tab(Screen.Home); return true }
        return false
    }
}

private data class TabDef(val label: String, val icon: ImageVector, val screen: Screen)

private val tabs = listOf(
    TabDef("Home", Icons.Default.Home, Screen.Home),
    TabDef("Library", Icons.Default.VideoLibrary, Screen.Browse("/", "Library")),
    TabDef("Search", Icons.Default.Search, Screen.Search),
    TabDef("Settings", Icons.Default.Settings, Screen.Settings),
)

private fun tabIndex(s: Screen) = when (s) {
    is Screen.Home -> 0
    is Screen.Browse -> 1
    is Screen.Search -> 2
    is Screen.Settings -> 3
    else -> 0
}

// ======================= মূল অ্যাপ =======================
@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = remember { Nav() }
    val isTv = remember {
        (ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
    // টিভি বা বড় স্ক্রিনে সাইড রেল, ফোনে নিচে বার
    val wide = isTv || LocalConfiguration.current.screenWidthDp >= 700
    val cur = nav.current

    BackHandler(enabled = cur !is Screen.Player) {
        if (!nav.back()) (ctx as Activity).finish()
    }

    // ফোল্ডার খুললে: শুধু একটা ভিডিও থাকলে সরাসরি Details, নাহলে Browse
    fun open(e: Entry) {
        scope.launch {
            nav.busy = true
            try {
                if (e.isDir) {
                    val list = try { Listings.get(e.path) } catch (ex: Exception) { null }
                    val vids = list?.filter { !it.isDir && Names.isVideo(it.name) }.orEmpty()
                    if (list != null && vids.size == 1 && list.none { it.isDir }) nav.push(Screen.Details(vids[0], list))
                    else nav.push(Screen.Browse(e.path, e.name))
                } else {
                    val sib = try { Listings.get(Server.parentOf(e.path)) } catch (ex: Exception) { emptyList() }
                    nav.push(Screen.Details(e, sib))
                }
            } finally {
                nav.busy = false
            }
        }
    }

    fun play(e: Entry) {
        scope.launch {
            nav.busy = true
            try {
                val sib = try { Listings.get(Server.parentOf(e.path)) } catch (ex: Exception) { emptyList() }
                nav.push(Screen.Player(e, sib))
            } finally {
                nav.busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (cur is Screen.Player) {
            key(cur.file.path) {
                PlayerScreen(
                    cur.file, cur.siblings,
                    onBack = { nav.back() },
                    onSwitch = { e -> nav.replaceTop(Screen.Player(e, cur.siblings)) },
                )
            }
        } else {
            val selected = tabIndex(nav.stack.first())
            val content: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = cur,
                        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(140)) },
                        label = "nav",
                    ) { s ->
                        when (s) {
                            is Screen.Home -> HomeScreen(wide, ::open, ::play) { nav.tab(tabs[1].screen) }
                            is Screen.Browse -> BrowseScreen(
                                s.path, s.title, wide, ::open,
                                onBack = if (nav.stack.size > 1) ({ nav.back() }) else null,
                            )
                            is Screen.Search -> SearchScreen(wide, ::open)
                            is Screen.Settings -> SettingsScreen()
                            is Screen.Details -> DetailsScreen(s.file, s.siblings, wide, ::play) { nav.back() }
                            is Screen.Player -> {}
                        }
                    }
                    if (nav.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Lime, trackColor = Color.Transparent)
                    }
                }
            }

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = Rail,
                        header = {
                            Box(
                                Modifier.padding(top = 18.dp, bottom = 10.dp).size(40.dp)
                                    .clip(RoundedCornerShape(11.dp)).background(Lime),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.PlayArrow, null, tint = OnLime) }
                        },
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        tabs.forEachIndexed { i, t ->
                            NavigationRailItem(
                                selected = selected == i,
                                onClick = { nav.tab(t.screen) },
                                icon = { Icon(t.icon, t.label) },
                                label = { Text(t.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = OnLime, indicatorColor = Lime,
                                    unselectedIconColor = TextBody, selectedTextColor = TextHead, unselectedTextColor = TextBody,
                                ),
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                    NavigationBar(containerColor = Rail) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = selected == i,
                                onClick = { nav.tab(t.screen) },
                                icon = { Icon(t.icon, t.label) },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = OnLime, indicatorColor = Lime,
                                    unselectedIconColor = TextBody, selectedTextColor = TextHead, unselectedTextColor = TextBody,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
