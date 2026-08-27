package com.kers.killove.jhsy.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.kers.killove.jhsy.domain.AvoidanceLocation
import com.kers.killove.jhsy.data.remote.ProxyHttp
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 定位避让：读取当前位置，判断是否在避让点 10 米内。
 * 地点搜索使用高德 Web 服务（需开发者 Key）。
 */
object LocationHelper {
    private const val RADIUS_M = 10.0
    

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun currentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            try {
                if (!lm.isProviderEnabled(p)) continue
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (best == null || loc.time > best.time) best = loc
            } catch (_: Exception) {
            }
        }
        return best
    }

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /** 是否在任一避让点 [RADIUS_M] 米内 */
    fun isInAvoidZone(context: Context, locations: List<AvoidanceLocation>): Pair<Boolean, AvoidanceLocation?> {
        if (locations.isEmpty()) return false to null
        val cur = currentLocation(context) ?: return false to null
        for (loc in locations) {
            val d = distanceMeters(cur.latitude, cur.longitude, loc.lat, loc.lng)
            if (d <= RADIUS_M) return true to loc
        }
        return false to null
    }

    data class PlaceHit(val id: String, val name: String, val address: String, val lat: Double, val lng: Double)

    /** 高德地点关键字搜索 */
    fun searchPlaces(apiKey: String, keyword: String, city: String = ""): List<PlaceHit> {
        if (apiKey.isBlank() || keyword.isBlank()) return emptyList()
        val q = java.net.URLEncoder.encode(keyword.trim(), "UTF-8")
        val c = java.net.URLEncoder.encode(city.trim().ifBlank { "全国" }, "UTF-8")
        val url =
            "https://restapi.amap.com/v3/place/text?key=$apiKey&keywords=$q&city=$c&offset=20&page=1&extensions=base"
        return try {
            val req = Request.Builder().url(url).get().build()
            ProxyHttp.execute(req).use { resp ->
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                if (root.optString("status") != "1") return emptyList()
                val pois = root.optJSONArray("pois") ?: return emptyList()
                buildList {
                    for (i in 0 until pois.length()) {
                        val o = pois.optJSONObject(i) ?: continue
                        val loc = o.optString("location")
                        val parts = loc.split(",")
                        if (parts.size < 2) continue
                        val lng = parts[0].toDoubleOrNull() ?: continue
                        val lat = parts[1].toDoubleOrNull() ?: continue
                        add(
                            PlaceHit(
                                id = o.optString("id").ifBlank { "${lat}_${lng}" },
                                name = o.optString("name"),
                                address = o.optString("address"),
                                lat = lat,
                                lng = lng
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun locationsToJson(list: List<AvoidanceLocation>): String {
        val arr = org.json.JSONArray()
        for (loc in list) {
            arr.put(
                org.json.JSONObject()
                    .put("id", loc.id)
                    .put("name", loc.name)
                    .put("lat", loc.lat)
                    .put("lng", loc.lng)
            )
        }
        return arr.toString()
    }
}
