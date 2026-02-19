package main.components.world

import kotlin.math.*

data class SunPosition(val altitudeRad: Double, val azimuthRad: Double)
data class MoonPosition(val altitudeRad: Double, val azimuthRad: Double, val elongationDeg: Double)

object Astronomy {
    private fun normalize(deg: Double): Double {
        var v = deg % 360.0
        if (v < 0) v += 360.0
        return v
    }

    /**
     * Computes the Sun's position (altitude and azimuth) for a given date and location.
     */
    fun computeSunPosition(date: kotlin.js.Date, latDeg: Double, lonDeg: Double): SunPosition {
        val d = (date.getTime() - 946728000000.0) / 86400000.0
        
        // Mean longitude
        val L = normalize(280.460 + 0.9856474 * d)
        // Mean anomaly
        val g = normalize(357.528 + 0.9856003 * d) * PI / 180.0
        
        // Ecliptic longitude
        val lambda = (L + 1.915 * sin(g) + 0.020 * sin(2 * g)) * PI / 180.0
        // Obliquity of ecliptic
        val epsilon = (23.439 - 0.0000004 * d) * PI / 180.0
        
        // Equatorial coordinates
        val ra = atan2(cos(epsilon) * sin(lambda), cos(lambda))
        val dec = asin(sin(epsilon) * sin(lambda))
        
        // Local Sidereal Time
        val gmst = normalize(280.46061837 + 360.98564736629 * d)
        val lst = (gmst + lonDeg) * PI / 180.0
        val ha = lst - ra
        
        // Horizontal coordinates
        val lat = latDeg * PI / 180.0
        val sinAlt = sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(ha)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
        
        val y = sin(ha)
        val x = cos(ha) * sin(lat) - tan(dec) * cos(lat)
        var az = atan2(y, x) + PI
        az %= (2 * PI)
        if (az < 0) az += 2 * PI
        
        return SunPosition(altitudeRad = alt, azimuthRad = az)
    }

    /**
     * Computes the Moon's position (altitude and azimuth) and elongation for a given date and location.
     */
    fun computeMoonPosition(date: kotlin.js.Date, latDeg: Double, lonDeg: Double): MoonPosition {
        val d = (date.getTime() - 946728000000.0) / 86400000.0
        
        // Moon's orbital elements (approximate)
        val L = normalize(218.316 + 13.176396 * d)
        val M = normalize(134.963 + 13.064993 * d)
        val F = normalize(93.272 + 13.229350 * d)
        
        // Ecliptic longitude/latitude
        val lambda = (L + 6.289 * sin(M * PI / 180.0)) * PI / 180.0
        val beta = (5.128 * sin(F * PI / 180.0)) * PI / 180.0
        val epsilon = (23.439 - 0.0000004 * d) * PI / 180.0
        
        // Equatorial coordinates
        val ra = atan2(sin(lambda) * cos(epsilon) - tan(beta) * sin(epsilon), cos(lambda))
        val dec = asin(sin(beta) * cos(epsilon) + cos(beta) * sin(epsilon) * sin(lambda))
        
        val gmst = normalize(280.46061837 + 360.98564736629 * d)
        val lst = (gmst + lonDeg) * PI / 180.0
        val ha = lst - ra
        
        val lat = latDeg * PI / 180.0
        val sinAlt = sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(ha)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
        
        val y = sin(ha)
        val x = cos(ha) * sin(lat) - tan(dec) * cos(lat)
        val az = (atan2(y, x) + PI).let { if (it < 0) it + 2 * PI else if (it >= 2 * PI) it - 2 * PI else it }
        
        // Elongation from sun (approximate)
        val sunLambda = normalize(280.460 + 0.9856474 * d)
        val elongation = normalize(L - sunLambda)
        
        return MoonPosition(altitudeRad = alt, azimuthRad = az, elongationDeg = elongation)
    }

    /**
     * Computes the Local Sidereal Time (LST) in radians for a given date and longitude.
     */
    fun computeSiderealTime(date: kotlin.js.Date, lonDeg: Double): Double {
        val d = (date.getTime() - 946728000000.0) / 86400000.0
        val gmst = normalize(280.46061837 + 360.98564736629 * d)
        return (gmst + lonDeg) * PI / 180.0
    }
}
