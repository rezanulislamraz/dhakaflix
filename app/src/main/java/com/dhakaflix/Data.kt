package com.dhakaflix

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ======================= কনফিগ =======================
// সার্ভারের ডিফল্ট ঠিকানা (Settings থেকেও বদলানো যায়)
const val DEFAULT_SERVER = "ftp://172.16.50.4"

// চাইলে TMDb API key এখানে বসিয়ে দিন, না হলে Settings এ দিন
const val DEFAULT_TMDB_KEY = ""

// ======================= মডেল =======================
data class Entry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long = 0,
    val time: Long = 0,
)

data class Progress(val path: String, val title: String, val pos: Long, val dur: Long, val time: Long)

data class Meta(
    val id: Int,
    val title: String,
    val overview: String,
    val poster: String?,
    val backdrop: String?,
    val rating: Double,
    val year: String,
    val tv: Boolean,
)

data class Parsed(
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val quality: List<String>,
)

// ======================= সেটিংস =======================
object Prefs {
    private lateinit var sp: SharedPreferences

    var server by mutableStateOf(DEFAULT_SERVER)
        private set
    var tmdbKey by mutableStateOf(DEFAULT_TMDB_KEY)
        private set
    var seekSec by mutableIntStateOf(5)
        private set
    var doubleTap by mutableStateOf(true)
        private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("dhakaflix", Context.MODE_PRIVATE)
        server = sp.getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER
        tmdbKey = sp.getString("tmdb", DEFAULT_TMDB_KEY) ?: DEFAULT_TMDB_KEY
        seekSec = sp.getInt("seek", 5)
        doubleTap = sp.getBoolean("dtap", true)
    }

    fun setServer(v: String) {
        var s = v.trim()
        if (s.isEmpty()) s = DEFAULT_SERVER
        if (!s.contains("://")) s = "ftp://$s"
        server = s
        sp.edit().putString("server", s).apply()
        Listings.clear()
    }

    fun setTmdbKey(v: String) {
        tmdbKey = v.trim()
        sp.edit().putString("tmdb", tmdbKey).apply()
    }

    fun setSeek(v: Int) {
        seekSec = v
        sp.edit().putInt("seek", v).apply()
    }

    fun setDoubleTap(v: Boolean) {
        doubleTap = v
        sp.edit().putBoolean("dtap", v).apply()
    }
}

// ======================= ফাইলের নাম বিশ্লেষণ =======================
object Names {
    private val videoExt = setOf("mkv", "mp4", "avi", "mov", "wmv", "flv", "m4v", "ts", "webm", "mpg", "mpeg", "3gp")
    fun isVideo(n: String) = n.substringAfterLast('.', "").lowercase() in videoExt
    fun isSub(n: String) = n.substringAfterLast('.', "").lowercase().let { it == "srt" || it == "vtt" }

    private val yearRe = Regex("""(?<![0-9])((?:19|20)\d{2})(?![0-9])""")
    private val epRe = Regex("""[Ss](\d{1,2})[ ._-]?[Ee](\d{1,3})""")
    private val junkRe = Regex(
        """\b(2160p|1080p|720p|480p|4k|uhd|bluray|blu-ray|brrip|bdrip|webrip|web-dl|webdl|hdrip|hdtv|dvdrip|dvdscr|hdcam|x264|x265|h264|h265|hevc|10bit|hdr|dual|esub|dubbed|remux)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val tagRe = Regex("""\b(2160p|4k|1080p|720p|480p|hdr|hevc|x265|bluray|web-?dl|webrip)\b""", RegexOption.IGNORE_CASE)

    // "Dune.Part.Two.2024.1080p.BluRay.mkv" -> title=Dune Part Two, year=2024, quality=[1080P, BLURAY]
    fun parse(raw: String): Parsed {
        val base = if (isVideo(raw)) raw.substringBeforeLast('.') else raw
        val clean = base.replace('.', ' ').replace('_', ' ')
            .replace('[', ' ').replace(']', ' ').replace('(', ' ').replace(')', ' ')
        val ep = epRe.find(clean)
        val yr = yearRe.findAll(clean).firstOrNull { it.range.first > 0 }
        val junk = junkRe.find(clean)
        val cut = listOfNotNull(ep?.range?.first, yr?.range?.first, junk?.range?.first).minOrNull() ?: clean.length
        var title = clean.substring(0, cut).replace(Regex("\\s+"), " ").trim(' ', '-')
        if (title.isBlank()) title = clean.trim()
        val q = tagRe.findAll(clean).map { it.value.uppercase() }.distinct().toList()
        return Parsed(
            title,
            yr?.groupValues?.get(1)?.toInt(),
            ep?.groupValues?.get(1)?.toInt(),
            ep?.groupValues?.get(2)?.toInt(),
            q,
        )
    }
}

fun fmtSize(b: Long): String = when {
    b <= 0 -> ""
    b >= 1L shl 30 -> "%.1f GB".format(b / (1024.0 * 1024 * 1024))
    else -> "%d MB".format(b / (1024 * 1024))
}

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

// ======================= সার্ভার (FTP বা HTTP) =======================
data class Conn(val scheme: String, val host: String, val port: Int, val base: String)

object Server {
    fun conn(): Conn {
        val u = Uri.parse(Prefs.server)
        val scheme = (u.scheme ?: "ftp").lowercase()
        val port = if (u.port > 0) u.port else when (scheme) {
            "ftp" -> 21
            "https" -> 443
            else -> 80
        }
        return Conn(scheme, u.host ?: "172.16.50.4", port, (u.path ?: "").trimEnd('/'))
    }

    fun isFtp() = conn().scheme == "ftp"
    fun root(): String = conn().base.ifEmpty { "/" }
    fun join(dir: String, name: String) = (if (dir.endsWith("/")) dir else "$dir/") + name
    fun parentOf(path: String): String = path.trimEnd('/').substringBeforeLast('/').ifEmpty { "/" }

    // প্লেয়ারের জন্য পূর্ণ URL (ftp://host/path বা http://host/path)
    fun url(path: String): Uri {
        val c = conn()
        return Uri.Builder().scheme(c.scheme).encodedAuthority("${c.host}:${c.port}").path(path).build()
    }

    fun newFtp(): FTPClient {
        val c = FTPClient()
        val cn = conn()
        c.setConnectTimeout(15_000)
        c.setControlEncoding("UTF-8")
        c.connect(cn.host, cn.port)
        c.setSoTimeout(30_000)
        if (!c.login("anonymous", "anonymous@dhakaflix.app")) {
            runCatching { c.disconnect() }
            throw IOException("FTP login refused (anonymous)")
        }
        c.enterLocalPassiveMode()
        c.setFileType(FTP.BINARY_FILE_TYPE)
        return c
    }

    fun ftpList(c: FTPClient, path: String): List<Entry> {
        if (!c.changeWorkingDirectory(path)) throw IOException("Cannot open $path (${c.replyString?.trim()})")
        val files = c.listFiles() ?: return emptyList()
        return files.filterNotNull()
            .filter { it.name != "." && it.name != ".." && !it.name.startsWith(".") }
            .map { f -> Entry(f.name, join(path, f.name), f.isDirectory, f.size, f.timestamp?.timeInMillis ?: 0L) }
            .sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun httpGet(url: String, bearer: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer $bearer")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // Apache/nginx স্টাইলের ডিরেক্টরি লিস্টিং (HTTP সার্ভারের জন্য)
    fun httpList(path: String): List<Entry> {
        val p = if (path.endsWith("/")) path else "$path/"
        val html = httpGet(url(p).toString())
        val out = ArrayList<Entry>()
        Regex("""href\s*=\s*["']([^"'#?]+)["']""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            var h = Uri.decode(m.groupValues[1])
            if (h.startsWith("http") || h.startsWith("..") || h.startsWith("./") || h == "/") return@forEach
            if (h.startsWith("/")) {
                if (!h.startsWith(p) || h == p) return@forEach
                h = h.removePrefix(p)
            }
            val dir = h.endsWith("/")
            val name = h.trimEnd('/')
            if (name.isEmpty() || name.contains('/') || name.startsWith(".")) return@forEach
            out.add(Entry(name, join(p, name), dir))
        }
        return out.distinctBy { it.path }
            .sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    suspend fun list(path: String): List<Entry> = withContext(Dispatchers.IO) {
        if (isFtp()) {
            val c = newFtp()
            try {
                ftpList(c, path)
            } finally {
                runCatching { c.disconnect() }
            }
        } else {
            httpList(path)
        }
    }
}

// ফোল্ডার লিস্টের ক্যাশ (বারবার সার্ভারে না গিয়ে)
object Listings {
    private val cache = HashMap<String, List<Entry>>()

    suspend fun get(path: String, force: Boolean = false): List<Entry> {
        if (!force) synchronized(cache) { cache[path] }?.let { return it }
        val l = Server.list(path)
        synchronized(cache) { cache[path] = l }
        return l
    }

    fun clear() = synchronized(cache) { cache.clear() }
}

// ======================= TMDb (পোস্টার, বিবরণ) =======================
object Tmdb {
    private val cache = HashMap<String, Meta?>()
    private var file: File? = null
    private val sem = Semaphore(3)

    fun img(path: String, size: String) = "https://image.tmdb.org/t/p/$size$path"

    fun init(ctx: Context) {
        val f = File(ctx.filesDir, "tmdb_cache.json")
        file = f
        try {
            val o = JSONObject(f.readText())
            for (k in o.keys()) {
                val m = o.getJSONObject(k)
                cache[k] = if (m.length() == 0) null else Meta(
                    m.optInt("id"), m.optString("title"), m.optString("overview"),
                    m.optString("poster").ifEmpty { null }, m.optString("backdrop").ifEmpty { null },
                    m.optDouble("rating", 0.0), m.optString("year"), m.optBoolean("tv"),
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        val f = file ?: return
        try {
            val o = JSONObject()
            synchronized(cache) {
                for ((k, m) in cache) {
                    val j = JSONObject()
                    if (m != null) {
                        j.put("id", m.id).put("title", m.title).put("overview", m.overview)
                            .put("poster", m.poster ?: "").put("backdrop", m.backdrop ?: "")
                            .put("rating", m.rating).put("year", m.year).put("tv", m.tv)
                    }
                    o.put(k, j)
                }
            }
            f.writeText(o.toString())
        } catch (_: Exception) {
        }
    }

    suspend fun lookup(p: Parsed): Meta? {
        val key = "${p.title.lowercase()}|${p.year ?: 0}|${p.season != null}"
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val k = Prefs.tmdbKey.trim()
        if (k.isEmpty() || p.title.isBlank()) return null
        return sem.withPermit { withContext(Dispatchers.IO) { fetch(p, key, k) } }
    }

    private fun fetch(p: Parsed, key: String, k: String): Meta? {
        try {
            val q = URLEncoder.encode(p.title, "UTF-8")
            val bearer = if (k.length > 40) k else null   // লম্বা টোকেন হলে v4 Bearer
            val auth = if (bearer == null) "&api_key=$k" else ""
            val url = "https://api.themoviedb.org/3/search/multi?query=$q&include_adult=false$auth"
            val arr = JSONObject(Server.httpGet(url, bearer)).getJSONArray("results")
            var best: JSONObject? = null
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val t = o.optString("media_type")
                if (t != "movie" && t != "tv") continue
                if (p.season != null && t != "tv") continue
                val date = o.optString("release_date").ifEmpty { o.optString("first_air_date") }
                if (best == null) best = o
                if (p.year != null && date.startsWith(p.year.toString())) {
                    best = o
                    break
                }
            }
            val b = best
            val meta = if (b == null) null else {
                val date = b.optString("release_date").ifEmpty { b.optString("first_air_date") }
                Meta(
                    b.optInt("id"),
                    b.optString("title").ifEmpty { b.optString("name") },
                    b.optString("overview"),
                    b.optString("poster_path").takeIf { it.isNotEmpty() && it != "null" },
                    b.optString("backdrop_path").takeIf { it.isNotEmpty() && it != "null" },
                    b.optDouble("vote_average", 0.0),
                    date.take(4),
                    b.optString("media_type") == "tv",
                )
            }
            synchronized(cache) { cache[key] = meta }
            save()
            return meta
        } catch (e: Exception) {
            return null   // নেটওয়ার্ক সমস্যা হলে ক্যাশ করি না, পরে আবার চেষ্টা হবে
        }
    }
}

// ======================= Continue Watching ও My List =======================
object Store {
    private lateinit var sp: SharedPreferences

    var history by mutableStateOf(listOf<Progress>())
        private set
    var myList by mutableStateOf(listOf<Entry>())
        private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("dhakaflix_store", Context.MODE_PRIVATE)
        try {
            val a = JSONArray(sp.getString("history", "[]"))
            history = (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Progress(o.getString("path"), o.getString("title"), o.getLong("pos"), o.getLong("dur"), o.getLong("time"))
            }
        } catch (_: Exception) {
        }
        try {
            val a = JSONArray(sp.getString("mylist", "[]"))
            myList = (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Entry(o.getString("name"), o.getString("path"), o.getBoolean("dir"), o.optLong("size"))
            }
        } catch (_: Exception) {
        }
    }

    private fun persistHistory() {
        val a = JSONArray()
        history.forEach {
            a.put(JSONObject().put("path", it.path).put("title", it.title).put("pos", it.pos).put("dur", it.dur).put("time", it.time))
        }
        sp.edit().putString("history", a.toString()).apply()
    }

    private fun persistList() {
        val a = JSONArray()
        myList.forEach {
            a.put(JSONObject().put("name", it.name).put("path", it.path).put("dir", it.isDir).put("size", it.size))
        }
        sp.edit().putString("mylist", a.toString()).apply()
    }

    fun saveProgress(e: Entry, pos: Long, dur: Long) {
        if (dur <= 0) return
        val p = Progress(e.path, e.name, pos, dur, System.currentTimeMillis())
        history = (listOf(p) + history.filter { it.path != e.path }).take(100)
        persistHistory()
    }

    fun progress(path: String): Progress? = history.firstOrNull { it.path == path }

    fun fraction(path: String): Float? {
        val p = progress(path) ?: return null
        if (p.dur <= 0) return null
        return (p.pos.toFloat() / p.dur).takeIf { it in 0.02f..0.95f }
    }

    fun inList(path: String) = myList.any { it.path == path }

    fun toggleList(e: Entry) {
        myList = if (inList(e.path)) myList.filter { it.path != e.path } else listOf(e) + myList
        persistList()
    }

    fun clearHistory() {
        history = emptyList()
        persistHistory()
    }

    fun clearList() {
        myList = emptyList()
        persistList()
    }
}

// ======================= ইনডেক্স (সার্চ ও Recently Added এর জন্য) =======================
object Indexer {
    var entries by mutableStateOf(listOf<Entry>())
        private set
    var running by mutableStateOf(false)
        private set
    var count by mutableIntStateOf(0)
        private set
    var message by mutableStateOf("")
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun indexFile(ctx: Context) = File(ctx.filesDir, "index.tsv")

    fun load(ctx: Context) {
        scope.launch {
            val f = indexFile(ctx)
            if (!f.exists()) return@launch
            val list = ArrayList<Entry>()
            f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size >= 5) list.add(Entry(p[0], p[1], p[2] == "1", p[3].toLongOrNull() ?: 0L, p[4].toLongOrNull() ?: 0L))
            }
            entries = list
            count = list.size
            message = "${list.size} items indexed"
        }
    }

    fun clear(ctx: Context) {
        entries = emptyList()
        count = 0
        message = ""
        indexFile(ctx).delete()
    }

    // সার্ভারের সব ফোল্ডার ঘুরে (সর্বোচ্চ ৫ লেভেল) নাম সেভ করে — সার্চ ও Recently Added এর জন্য
    fun rebuild(ctx: Context) {
        if (running) return
        running = true
        count = 0
        message = "Scanning server…"
        val app = ctx.applicationContext
        scope.launch {
            var ftp: FTPClient? = null
            try {
                val out = ArrayList<Entry>()
                val queue = ArrayDeque<Pair<String, Int>>()
                queue.add(Server.root() to 0)
                if (Server.isFtp()) ftp = Server.newFtp()
                while (queue.isNotEmpty() && out.size < 30_000) {
                    val (p, d) = queue.removeFirst()
                    val client = ftp
                    val items: List<Entry> = try {
                        if (client != null) Server.ftpList(client, p) else Server.httpList(p)
                    } catch (e: Exception) {
                        if (client != null) {
                            runCatching { client.disconnect() }
                            ftp = runCatching { Server.newFtp() }.getOrNull()
                        }
                        emptyList()
                    }
                    for (e in items) {
                        if (e.isDir) {
                            out.add(e)
                            if (d < 5) queue.add(e.path to d + 1)
                        } else if (Names.isVideo(e.name)) {
                            out.add(e)
                        }
                    }
                    count = out.size
                }
                indexFile(app).bufferedWriter().use { w ->
                    out.forEach {
                        w.write("${it.name.replace('\t', ' ')}\t${it.path.replace('\t', ' ')}\t${if (it.isDir) 1 else 0}\t${it.size}\t${it.time}\n")
                    }
                }
                entries = out
                message = "Done — ${out.size} items indexed"
            } catch (e: Exception) {
                message = "Scan failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                runCatching { ftp?.disconnect() }
                running = false
            }
        }
    }
}
