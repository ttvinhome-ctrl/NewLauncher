package id.kebasen.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

val WP = intArrayOf(R.drawable.wp_morning, R.drawable.wp_day, R.drawable.wp_night)
val INPUTS = listOf("Tidak Ada", "HDMI 1", "HDMI 2", "HDMI 3", "Antena / TV Digital", "AV")
val QUICK = listOf("Pengaturan" to Settings.ACTION_SETTINGS, "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS, "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
    "Layar" to Settings.ACTION_DISPLAY_SETTINGS, "Suara" to Settings.ACTION_SOUND_SETTINGS, "Pilih Launcher Utama" to Settings.ACTION_HOME_SETTINGS)

class St(val ctx: Context) {
    val p = ctx.getSharedPreferences("p", 0)
    var apps by mutableStateOf(emptyList<AppItem>())
    var weather by mutableStateOf<Weather?>(null)
    var prayer by mutableStateOf(emptyList<Pair<String, String>>())
    var movies by mutableStateOf(emptyList<Movie>())
    var input by mutableStateOf(p.getString("input", null) ?: "Tidak Ada")
    var wifi by mutableStateOf("...")
    var panel by mutableStateOf("")
    var library by mutableStateOf(false)
    var status by mutableStateOf("")
    fun dockApp(d: Dock) = d.pkgs.firstNotNullOfOrNull { k -> apps.find { it.pkg == k } }
    fun open(d: Dock) { p.edit().putInt("u_${d.name}", p.getInt("u_${d.name}", 0) + 1).apply(); openApp(ctx, dockApp(d), d.name) }
    fun like(m: Movie) {
        p.edit().putInt("t_${m.type}", p.getInt("t_${m.type}", 0) + 1).apply()
        DOCK.filter { it.name in listOf("Netflix", "iQIYI", "YouTube") }.maxByOrNull { p.getInt("u_${it.name}", 0) }?.let { open(it) }
    }
    fun pick(v: String) { input = v; p.edit().putString("input", v).apply(); panel = "" }
    fun go(action: String) {
        try { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Toast.makeText(ctx, "Tidak tersedia di TV ini", Toast.LENGTH_SHORT).show() }
    }
    suspend fun checkUpdate() {
        status = "Memeriksa..."
        try {
            val r = withContext(Dispatchers.IO) { latestRelease() }
            if (r == null || !newer(r.tag, BuildConfig.VERSION_NAME)) { status = "Sudah versi terbaru (${BuildConfig.VERSION_NAME})"; return }
            status = "Mengunduh ${r.tag}..."
            installApk(ctx, withContext(Dispatchers.IO) { downloadApk(ctx, r.apk) })
            status = ""
        } catch (e: Exception) { status = "Gagal: ${e.message}" }
    }
}

suspend fun every(ok: Long, fail: Long = 20_000, f: suspend () -> Unit) {
    while (true) { try { f(); delay(ok) } catch (e: CancellationException) { throw e } catch (e: Exception) { delay(fail) } }
}
@Composable fun minute(): State<Date> = produceState(Date()) { while (true) { value = Date(); delay(60_000L - System.currentTimeMillis() % 60_000L + 20L) } }
fun greet(h: Int) = when { h < 5 -> "Good Night"; h < 12 -> "Good Morning"; h < 18 -> "Good Afternoon"; h < 22 -> "Good Evening"; else -> "Good Night" }
fun toMin(s: String): Int { val a = s.split(":"); return a[0].toInt() * 60 + a[1].toInt() }
fun two(n: Int) = n.toString().padStart(2, '0')

fun Modifier.glass(r: Dp = 26.dp, a: Float = 0.16f): Modifier {
    val s = RoundedCornerShape(r)
    return clip(s).background(Color(0x38000000))
        .background(Brush.verticalGradient(listOf(Color.White.copy(a + 0.08f), Color.White.copy(a - 0.06f))))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.5f), Color.White.copy(0.08f))), s)
}

@Composable fun Txt(s: String, size: Int, w: FontWeight = FontWeight.Normal, a: Float = 1f, mod: Modifier = Modifier) =
    Text(s, mod, Color.White.copy(a), size.sp, fontWeight = w, maxLines = 2, overflow = TextOverflow.Ellipsis,
        style = TextStyle(shadow = Shadow(Color(0x66000000), Offset(0f, 2f), 10f)))

@Composable fun Tile(mod: Modifier = Modifier, r: Dp = 18.dp, onClick: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.07f else 1f, tween(140))
    Box(mod.graphicsLayer { scaleX = s; scaleY = s }.onFocusChanged { f = it.isFocused }.glass(r, if (f) 0.30f else 0.12f)
        .then(if (f) Modifier.border(2.dp, Color.White, RoundedCornerShape(r)) else Modifier)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.focusable()), content = content)
}

@Composable fun Pic(d: Drawable?, w: Int, h: Int, mod: Modifier, scale: ContentScale = ContentScale.Fit) {
    if (d != null) { val bm = remember(d) { d.toBitmap(w, h).asImageBitmap() }; Image(bm, null, mod, contentScale = scale) }
}

@Composable fun GWifi() = Canvas(Modifier.size(26.dp)) {
    val cx = size.width / 2; val by = size.height * 0.85f; val sk = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
    for (k in 1..3) { val r = size.width * 0.17f * k + size.width * 0.05f; drawArc(Color.White, 225f, 90f, false, Offset(cx - r, by - r), Size(r * 2, r * 2), style = sk) }
    drawCircle(Color.White, 2.6.dp.toPx(), Offset(cx, by))
}
@Composable fun GInput() = Canvas(Modifier.size(26.dp)) {
    drawRoundRect(Color.White, Offset(2.dp.toPx(), 5.dp.toPx()), Size(22.dp.toPx(), 13.dp.toPx()), CornerRadius(3.dp.toPx()), style = Stroke(2.6.dp.toPx()))
    drawLine(Color.White, Offset(8.dp.toPx(), 22.dp.toPx()), Offset(18.dp.toPx(), 22.dp.toPx()), 2.6.dp.toPx(), StrokeCap.Round)
}
@Composable fun GGrid() = Canvas(Modifier.size(26.dp)) {
    for (a in 0..1) for (b in 0..1) drawCircle(Color.White, 4.2.dp.toPx(), Offset((8 + 10 * a).dp.toPx(), (8 + 10 * b).dp.toPx()))
}

@Composable fun Capsule(label: String, glyph: @Composable () -> Unit, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.08f else 1f, tween(150))
    Row(Modifier.graphicsLayer { scaleX = s; scaleY = s }.onFocusChanged { f = it.isFocused }.glass(50.dp, if (f) 0.36f else 0.16f)
        .clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { glyph(); Txt(label, 22, FontWeight.Medium) }
}

@Composable fun DockBar(st: St, frs: List<FocusRequester>, modifier: Modifier, onFocusIdx: (Int) -> Unit) {
    var fi by remember { mutableIntStateOf(-1) }
    val glow by animateColorAsState(DOCK.getOrNull(fi)?.color ?: Color(0xFF7FA8FF), tween(450))
    Box(modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, glow.copy(if (fi >= 0) 0.6f else 0.25f)))))
        Row(Modifier.padding(bottom = 40.dp).glass(44.dp, 0.14f).padding(horizontal = 34.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Bottom) {
            DOCK.forEachIndexed { i, d ->
                val sz by animateDpAsState(when { fi == i -> 140.dp; fi >= 0 && abs(fi - i) == 1 -> 104.dp; else -> 92.dp }, spring(0.65f, 380f))
                val app = st.dockApp(d)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(30.dp)) { if (fi == i) Txt(d.name, 22, FontWeight.Medium) }
                    Box(Modifier.size(sz).focusRequester(frs[i])
                        .onFocusChanged { if (it.isFocused) { fi = i; onFocusIdx(i) } else if (fi == i) fi = -1 }
                        .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) { st.library = true; true } else false }
                        .clip(RoundedCornerShape(28.dp)).background(Color(0x33FFFFFF))
                        .then(if (fi == i) Modifier.border(3.dp, Color.White, RoundedCornerShape(28.dp)) else Modifier)
                        .clickable { st.open(d) }, contentAlignment = Alignment.Center) {
                        if (app != null) Pic(app.icon ?: app.banner, 256, 256, Modifier.fillMaxSize().padding(6.dp)) else Txt(d.name.take(1), 44, FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable fun PanelBox(st: St, scope: CoroutineScope) {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { on = true }
    val a by animateFloatAsState(if (on) 1f else 0f, tween(200))
    val fr = remember { FocusRequester() }
    LaunchedEffect(st.panel) { delay(120); runCatching { fr.requestFocus() } }
    val first = { i: Int -> if (i == 0) Modifier.focusRequester(fr) else Modifier }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopEnd).padding(top = 118.dp, end = 64.dp).width(440.dp)
            .graphicsLayer { alpha = a; translationY = (1f - a) * -30f }.glass(32.dp, 0.24f).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (st.panel) {
                "input" -> {
                    Txt("Sumber Input", 26, FontWeight.Bold)
                    INPUTS.forEachIndexed { i, n ->
                        Tile(Modifier.fillMaxWidth().then(first(i)), onClick = { st.pick(n) }) {
                            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Txt(n, 22, if (n == st.input) FontWeight.Bold else FontWeight.Normal); Spacer(Modifier.weight(1f)); if (n == st.input) Txt("●", 18)
                            }
                        }
                    }
                }
                "wifi" -> {
                    Txt("Wi-Fi", 26, FontWeight.Bold)
                    remember { wifiLines(st.ctx) }.forEach { Txt(it, 21, a = 0.92f) }
                    Tile(Modifier.fillMaxWidth().focusRequester(fr), onClick = { st.go(Settings.ACTION_WIFI_SETTINGS) }) { Txt("Buka Pengaturan Wi-Fi", 22, mod = Modifier.padding(20.dp, 14.dp)) }
                }
                else -> {
                    Txt("Pintasan", 26, FontWeight.Bold)
                    QUICK.forEachIndexed { i, (n, act) -> Tile(Modifier.fillMaxWidth().then(first(i)), onClick = { st.go(act) }) { Txt(n, 22, mod = Modifier.padding(20.dp, 14.dp)) } }
                    Tile(Modifier.fillMaxWidth(), onClick = { scope.launch { st.checkUpdate() } }) { Txt("Cek Update Launcher (v${BuildConfig.VERSION_NAME})", 22, mod = Modifier.padding(20.dp, 14.dp)) }
                    if (st.status.isNotEmpty()) Txt(st.status, 18, a = 0.85f)
                }
            }
        }
    }
}

@Composable fun Library(st: St, per: Int) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(400); runCatching { fr.requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(WP[per]), null, Modifier.fillMaxSize().blur(30.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0xAA000000)))
        LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 64.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { Txt("Perpustakaan", 40, FontWeight.Bold) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Txt("Cuaca 16 Hari - Kebasen, Banyumas", 26, FontWeight.Medium); Spacer(Modifier.height(12.dp))
                    val w = st.weather
                    if (w == null) Txt("Memuat cuaca...", 20, a = 0.7f)
                    else LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        itemsIndexed(w.days) { i, d ->
                            Tile(Modifier.width(150.dp).then(if (i == 0) Modifier.focusRequester(fr) else Modifier)) {
                                Column(Modifier.padding(16.dp)) {
                                    Txt(dayLabel(d.date), 20, FontWeight.Bold); Txt(wmo(d.code), 18, a = 0.85f)
                                    Txt("${d.max}° / ${d.min}°", 22, FontWeight.Medium); Txt("Hujan ${d.rain}%", 17, a = 0.7f)
                                }
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Txt("Rekomendasi untuk Kamu", 26, FontWeight.Medium); Spacer(Modifier.height(12.dp))
                    if (st.movies.isEmpty()) Txt(if (BuildConfig.TMDB_KEY.isBlank()) "Isi TMDB_KEY (themoviedb.org) untuk mengaktifkan rekomendasi" else "Memuat rekomendasi...", 19, a = 0.7f)
                    else LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(st.movies) { mv -> Tile(Modifier.size(150.dp, 225.dp), onClick = { st.like(mv) }) { AsyncImage(mv.poster, mv.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Txt("Semua Aplikasi", 26, FontWeight.Medium) }
            items(st.apps) { a ->
                Tile(Modifier.fillMaxWidth().aspectRatio(16f / 9f), onClick = { openApp(st.ctx, a, a.label) }) {
                    if (a.banner != null) Pic(a.banner, 320, 180, Modifier.fillMaxSize(), ContentScale.Crop)
                    else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Pic(a.icon, 96, 96, Modifier.size(56.dp)); Txt(a.label, 17, a = 0.9f)
                    }
                }
            }
        }
    }
}

@Composable fun HomeScreen(st: St) {
    val scope = rememberCoroutineScope()
    val now by minute()
    val cal = Calendar.getInstance().apply { time = now }
    val h = cal.get(Calendar.HOUR_OF_DAY); val m = cal.get(Calendar.MINUTE); val x = h * 60 + m
    val per = when { x in 300 until 600 -> 0; x in 600 until 1050 -> 1; else -> 2 }
    val frs = remember { List(DOCK.size) { FocusRequester() } }
    var last by remember { mutableIntStateOf(0) }
    val over = st.library || st.panel.isNotEmpty()
    LaunchedEffect(Unit) {
        launch { every(1_800_000) { st.weather = withContext(Dispatchers.IO) { fetchWeather() } } }
        launch { every(3_600_000) { st.prayer = withContext(Dispatchers.IO) { fetchPrayer() } } }
        launch { every(3_600_000) { st.movies = withContext(Dispatchers.IO) { fetchMovies() }.sortedByDescending { st.p.getInt("t_${it.type}", 0) } } }
        launch { while (true) { st.wifi = wifiName(st.ctx); delay(5000) } }
    }
    BackHandler(over) { st.library = false; st.panel = "" }
    LaunchedEffect(over) { if (!over) { delay(150); runCatching { frs[last].requestFocus() } } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(per, animationSpec = tween(1800)) { Image(painterResource(WP[it]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x55000000), Color.Transparent, Color(0x88000000)))))
        Box(Modifier.fillMaxSize().focusProperties { canFocus = !over }) {
            Column(Modifier.padding(start = 64.dp, top = 44.dp)) {
                Txt(greet(h), 30, FontWeight.Medium, 0.9f)
                Txt(two(h) + ":" + two(m), 132, FontWeight.Light)
                Txt(SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(now), 24, a = 0.9f)
                Spacer(Modifier.height(18.dp))
                st.weather?.let { w ->
                    Row(Modifier.glass(30.dp).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt("${w.temp}°C  ${wmo(w.code)}", 24, FontWeight.Medium); Txt("   Kebasen, Banyumas", 21, a = 0.75f)
                    }
                }
                Spacer(Modifier.height(14.dp))
                val nxt = st.prayer.indexOfFirst { toMin(it.second) > x }.let { if (it < 0) 0 else it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    st.prayer.forEachIndexed { i, pr ->
                        Column(Modifier.glass(20.dp, if (i == nxt) 0.34f else 0.14f).padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Txt(pr.first, 18, a = 0.85f); Txt(pr.second, 26, FontWeight.Medium)
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 64.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Capsule(st.input, { GInput() }) { st.panel = "input" }
                Capsule(st.wifi, { GWifi() }) { st.panel = "wifi" }
                Capsule("Pintasan", { GGrid() }) { st.panel = "quick" }
            }
            DockBar(st, frs, Modifier.align(Alignment.BottomCenter)) { last = it }
        }
        if (st.panel.isNotEmpty()) PanelBox(st, scope)
        AnimatedVisibility(st.library, enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350)), exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(250))) { Library(st, per) }
    }
}
