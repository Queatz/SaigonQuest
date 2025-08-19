package main.components.world

import kotlin.math.*

/**
 * Lightweight solar math helpers for the 3D lat/lng picker.
 * Pure Kotlin (no JS/dynamic), safe for commonMain.
 */
object SunMath {
    /**
     * Computes the unit sun direction vector in Earth-fixed frame for the given UTC time.
     * The vector points from Earth center toward the Sun. Use the negative as light.direction.
     * Returns Triple(x, y, z).
     */
    fun computeSunDirectionUtc(nowMillis: Double): Triple<Double, Double, Double> {
        // Julian Day
        val jd = nowMillis / 86400000.0 + 2440587.5
        val n = jd - 2451545.0 // days since J2000.0

        // Mean longitude of the Sun (deg)
        var L = 280.460 + 0.9856474 * n
        L = normalizeDegrees(L)

        // Mean anomaly (deg)
        val g = (357.528 + 0.9856003 * n) * PI / 180.0

        // Ecliptic longitude (deg)
        var lambda = L + 1.915 * sin(g) + 0.020 * sin(2 * g)
        lambda = normalizeDegrees(lambda)
        val lambdaRad = lambda * PI / 180.0

        // Obliquity of the ecliptic (deg)
        val epsilon = (23.439 - 0.0000004 * n) * PI / 180.0

        // Right ascension (rad) and declination (rad)
        val alpha = atan2(cos(epsilon) * sin(lambdaRad), cos(lambdaRad))
        val delta = asin(sin(epsilon) * sin(lambdaRad))

        // Greenwich Mean Sidereal Time (deg)
        var gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0)
        gmst = normalizeDegrees(gmst)

        // Subsolar longitude (deg) = alpha(deg) - GMST, normalized to [-180, 180]
        var subSolarLon = (alpha * 180.0 / PI) - gmst
        subSolarLon = normalizeDegrees180(subSolarLon)

        // Subsolar latitude (deg) = declination
        val subSolarLat = (delta * 180.0 / PI)

        // Convert to unit vector
        val v = toUnitVectorFromLatLng(subSolarLat, subSolarLon)
        return Triple(v.first, v.second, v.third)
    }

    /** Convert lat/lng in degrees to a unit vector on a sphere centered at origin. */
    fun toUnitVectorFromLatLng(latDeg: Double, lonDeg: Double): Triple<Double, Double, Double> {
        val lat = latDeg * PI / 180.0
        val lon = lonDeg * PI / 180.0
        val cosLat = cos(lat)
        val x = cosLat * cos(lon)
        val y = sin(lat)
        val z = cosLat * sin(lon)
        return Triple(x, y, z)
    }

    /** Convert a unit vector to lat/lng in degrees. Longitude normalized to [-180, 180]. */
    fun toLatLngFromUnitVector(x: Double, y: Double, z: Double): Pair<Double, Double> {
        val lat = asin(y.coerceIn(-1.0, 1.0)) * 180.0 / PI
        var lon = atan2(z, x) * 180.0 / PI
        lon = normalizeDegrees180(lon)
        return Pair(lat, lon)
    }

    private fun normalizeDegrees(a: Double): Double {
        var v = a % 360.0
        if (v < 0) v += 360.0
        return v
    }

    private fun normalizeDegrees180(a: Double): Double {
        var v = normalizeDegrees(a)
        if (v > 180.0) v -= 360.0
        return v
    }
}
