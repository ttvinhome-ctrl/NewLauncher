package id.kebasen.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

const val LAT = -7.50   // Kebasen, Banyumas
const val LON = 109.19

data class Day(val date: String, val code: Int, val max: Int, val min: Int, val rain: Int)
data class Weather(val temp: Int, val code: Int, val days: List<Day>)
data class Movie(val title: String, val poster: String, val type: String)
data class Rel(val tag: String, val apk: String)
data class AppItem(val label: String, val pkg: String, val intent: Intent, val icon: Drawable?, val banner: Drawable?)
data class Dock(val name: String, val color: Color, val pkgs: List<String>)

val DOCK = listOf(
    Dock("Netflix", Color(0xFFE50914), listOf("com.netflix.ninja")),
    Dock("YouTube", Color(0xFFFF3030), listOf("com.google.android.youtube.tv")),
    Dock("Live TV", Color(0xFF2D9CDB), listOf("com.google.android.tv", "com.mediatek.wwtv.tvcenter", "com.xiaomi.mitv.tvplayer")),
    Dock("iQIYI", Color(0xFF00CC36), listOf("com.iqiyi.i18n.tv", "com.iqiyi.i18n")),
    Dock("Spotify", Color(0xFF1DB954), listOf("com.spotify.tv.android")),
)

fun http(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 10000; c.readTimeout = 20000
    c.setRequestProperty("User-Agent", "tv-launcher")
    return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
}

fun wmo(c: Int) = when (c) {
    0 -> "Cerah"; 1, 2 -> "Cerah Berawan"; 3 -> "Berawan"; 45, 48 -> "Berkabut"
    in 51..57 -> "Gerimis"; in 61..67, in 80..82 -> "Hujan"; in 95..99 -> "Petir"; else -> "-"
}

fun dayLabel(s: String): String = try {
    SimpleDateFormat("EEE, d MMM", Locale("id", "ID")).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s)!!)
} catch (e: Exception) { s }

fun fetchWeather(): Weather {
    val j = JSONObject(http("https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=16&timezone=Asia%2FJakarta"))
    val c = j.getJSONObject("current"); val d = j.getJSONObject("daily")
    val t = d.getJSONArray("time"); val w = d.getJSONArray("weather_code")
    val hi = d.getJSONArray("temperature_2m_max"); val lo = d.getJSONArray("temperature_2m_min")
    val r = d.getJSONArray("precipitation_probability_max")
    return Weather(c.getDouble("temperature_2m").roundToInt(), c.getInt("weather_code"),
        (0 until t.length()).map { Day(t.getString(it), w.getInt(it), hi.getDouble(it).roundToInt(), lo.getDouble(it).roundToInt(), r.optInt(it)) })
}

fun fetchPrayer(): List<Pair<String, String>> {
    val t = JSONObject(http("https://api.aladhan.com/v1/timings?latitude=$LAT&longitude=$LON&method=20")).getJSONObject("data").getJSONObject("timings")
    return listOf("Subuh" to "Fajr", "Dzuhur" to "Dhuhr", "Ashar" to "Asr", "Maghrib" to "Maghrib", "Isya" to "Isha").map { it.first to t.getString(it.second).take(5) }
}

fun fetchMovies(): List<Movie> {
    if (BuildConfig.TMDB_KEY.isBlank()) return emptyList()
    val r = JSONObject(http("https://api.themoviedb.org/3/trending/all/week?language=id-ID&api_key=${BuildConfig.TMDB_KEY}")).getJSONArray("results")
    return (0 until r.length()).mapNotNull {
        val o = r.getJSONObject(it); val p = o.optString("poster_path")
        if (p.isBlank() || p == "null") null else Movie(o.optString("title", o.optString("name")), "https://image.tmdb.org/t/p/w342$p", o.optString("media_type"))
    }
}

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager; val seen = HashSet<String>(); val out = ArrayList<AppItem>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        for (r in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
            val p = r.activityInfo.packageName
            if (p == ctx.packageName || !seen.add(p)) continue
            out.add(AppItem(r.loadLabel(pm).toString(), p, Intent(Intent.ACTION_MAIN).addCategory(cat).setClassName(p, r.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), r.loadIcon(pm), r.activityInfo.loadBanner(pm)))
        }
    }
    return out.sortedBy { it.label.lowercase() }
}

fun openApp(ctx: Context, a: AppItem?, name: String) {
    try {
        when {
            a != null -> ctx.startActivity(a.intent)
            name == "Live TV" -> ctx.startActivity(Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> Toast.makeText(ctx, "$name belum terpasang", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) { Toast.makeText(ctx, "Tidak bisa membuka $name", Toast.LENGTH_SHORT).show() }
}

@Suppress("DEPRECATION")
fun wifiName(ctx: Context): String {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Tidak Terhubung"
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet"
    val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val s = wm.connectionInfo?.ssid?.trim('"') ?: ""
    return if (s.isBlank() || s == "<unknown ssid>") "Wi-Fi" else s
}

@Suppress("DEPRECATION")
fun wifiLines(ctx: Context): List<String> = try {
    val i = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo; val ip = i.ipAddress
    listOf("Terhubung ke: ${wifiName(ctx)}", "Sinyal: ${if (i.rssi >= -55) "Kuat" else if (i.rssi >= -70) "Sedang" else "Lemah"} (${i.rssi} dBm)",
        "Kecepatan: ${i.linkSpeed} Mbps", "IP: ${ip and 255}.${ip shr 8 and 255}.${ip shr 16 and 255}.${ip shr 24 and 255}")
} catch (e: Exception) { listOf("Info Wi-Fi tidak tersedia") }

fun latestRelease(): Rel? {
    val j = JSONObject(http("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")); val a = j.getJSONArray("assets")
    for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.getString("name").endsWith(".apk")) return Rel(j.getString("tag_name"), o.getString("browser_download_url")) }
    return null
}

fun newer(tag: String, cur: String): Boolean {
    val a = tag.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }; val b = cur.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) { val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }; if (x != y) return x > y }
    return false
}

fun downloadApk(ctx: Context, url: String): File {
    val f = File(ctx.cacheDir, "update.apk")
    val c = URL(url).openConnection() as HttpURLConnection
    c.setRequestProperty("User-Agent", "tv-launcher")
    c.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
    return f
}

fun installApk(ctx: Context, f: File) {
    val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
}
