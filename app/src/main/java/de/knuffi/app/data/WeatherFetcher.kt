package de.knuffi.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import de.knuffi.core.Action
import de.knuffi.core.Weather
import de.knuffi.core.WeatherMode
import de.knuffi.core.WeatherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Optional real weather from Open-Meteo (free, no account). Only used when the player switched
 * the weather to "Echtes Wetter" in the settings.
 */
object WeatherFetcher {
    private const val REFRESH_MS = 60 * 60_000L

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Rough position from the last known location of the phone, or null. */
    @SuppressLint("MissingPermission")
    fun lastLocation(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null
        return loc.latitude to loc.longitude
    }

    /** Looks up a place name. Returns (lat, lon, name) or null. */
    suspend fun geocode(name: String): Triple<Double, Double, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(name.trim(), "UTF-8")
            val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=de"))
            val r = json.getJSONArray("results").getJSONObject(0)
            Triple(r.getDouble("latitude"), r.getDouble("longitude"), r.getString("name"))
        }.getOrNull()
    }

    /** Fetches the weather now. Returns true on success. */
    suspend fun refresh(lat: Double, lon: Double, place: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"))
            val cur = json.getJSONObject("current")
            val state = WeatherState(
                kind = Weather.fromWmo(cur.getInt("weather_code")),
                tempC = Math.round(cur.getDouble("temperature_2m")).toInt(),
                fetchedAt = System.currentTimeMillis(),
                place = place,
            )
            GameRepository.perform(Action.SetWeather(state), userPresent = false)
            true
        }.getOrDefault(false)
    }

    /** Refreshes the real weather if it is switched on and older than an hour. */
    suspend fun refreshIfNeeded(context: Context) {
        val s = GameRepository.current.settings
        if (s.weatherMode != WeatherMode.REAL) return
        if (System.currentTimeMillis() - GameRepository.current.weather.fetchedAt < REFRESH_MS) return
        var lat = s.weatherLat
        var lon = s.weatherLon
        if (lat == 0.0 && lon == 0.0) {
            val loc = lastLocation(context) ?: return
            lat = loc.first
            lon = loc.second
        }
        refresh(lat, lon, s.weatherPlace)
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Knuffi-App")
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }
}
