package com.gv.app.ui.rutas

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.tan

/** A concello outline: one or more outer rings (polygon parts), each a list of [lon, lat]. */
data class Concello(
    val name: String,
    val province: String,
    val rings: List<List<DoubleArray>>,
)

/** Web-Mercator fit-to-size projection (mirrors d3 `geoMercator().fitSize`). */
class GeoProjection(
    private val scale: Double,
    private val minMx: Double,
    private val maxMy: Double,
    private val offX: Double,
    private val offY: Double,
) {
    fun x(lon: Double): Float = ((mercatorX(lon) - minMx) * scale + offX).toFloat()
    fun y(lat: Double): Float = ((maxMy - mercatorY(lat)) * scale + offY).toFloat()

    companion object {
        fun fit(concellos: List<Concello>, width: Float, height: Float, pad: Float = 12f): GeoProjection {
            var minMx = Double.MAX_VALUE
            var maxMx = -Double.MAX_VALUE
            var minMy = Double.MAX_VALUE
            var maxMy = -Double.MAX_VALUE
            for (c in concellos) for (ring in c.rings) for (p in ring) {
                val mx = mercatorX(p[0])
                val my = mercatorY(p[1])
                if (mx < minMx) minMx = mx
                if (mx > maxMx) maxMx = mx
                if (my < minMy) minMy = my
                if (my > maxMy) maxMy = my
            }
            val spanX = (maxMx - minMx).coerceAtLeast(1e-9)
            val spanY = (maxMy - minMy).coerceAtLeast(1e-9)
            val scale = min((width - 2 * pad) / spanX, (height - 2 * pad) / spanY)
            val offX = pad + ((width - 2 * pad) - spanX * scale) / 2
            val offY = pad + ((height - 2 * pad) - spanY * scale) / 2
            return GeoProjection(scale, minMx, maxMy, offX, offY)
        }
    }
}

object GaliciaGeo {
    /** Parse the bundled GeoJSON (call off the main thread — ~1.2 MB). */
    fun load(context: Context): List<Concello> {
        val text = context.assets.open("galicia-concellos.json").bufferedReader().use { it.readText() }
        val features = JSONObject(text).getJSONArray("features")
        val out = ArrayList<Concello>(features.length())
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val props = feature.getJSONObject("properties")
            val rings = parseRings(feature.getJSONObject("geometry"))
            if (rings.isNotEmpty()) {
                out.add(Concello(props.getString("name"), props.optString("province", ""), rings))
            }
        }
        return out
    }

    private fun parseRings(geom: JSONObject): List<List<DoubleArray>> {
        val coords = geom.getJSONArray("coordinates")
        val rings = ArrayList<List<DoubleArray>>()
        when (geom.getString("type")) {
            "Polygon" -> if (coords.length() > 0) rings.add(parseRing(coords.getJSONArray(0)))
            "MultiPolygon" -> for (p in 0 until coords.length()) {
                val poly = coords.getJSONArray(p)
                if (poly.length() > 0) rings.add(parseRing(poly.getJSONArray(0)))
            }
        }
        return rings
    }

    private fun parseRing(ring: JSONArray): List<DoubleArray> {
        val pts = ArrayList<DoubleArray>(ring.length())
        for (i in 0 until ring.length()) {
            val c = ring.getJSONArray(i)
            pts.add(doubleArrayOf(c.getDouble(0), c.getDouble(1)))
        }
        return pts
    }
}

internal fun mercatorX(lonDeg: Double): Double = Math.toRadians(lonDeg)
internal fun mercatorY(latDeg: Double): Double = ln(tan(PI / 4 + Math.toRadians(latDeg) / 2))

/** Ray-casting hit test in screen space for a tapped point against a concello's rings. */
fun Concello.containsScreenPoint(px: Float, py: Float, projection: GeoProjection): Boolean {
    for (ring in rings) {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = projection.x(ring[i][0]); val yi = projection.y(ring[i][1])
            val xj = projection.x(ring[j][0]); val yj = projection.y(ring[j][1])
            if (((yi > py) != (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi) + xi)) {
                inside = !inside
            }
            j = i
        }
        if (inside) return true
    }
    return false
}
