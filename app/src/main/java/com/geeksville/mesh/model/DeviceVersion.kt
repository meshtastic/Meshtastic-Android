package com.geeksville.mesh.model

import com.geeksville.android.Logging

/**
 * Provide structured access to parse and compare device version strings
 */
data class DeviceVersion(val asString: String) : Comparable<DeviceVersion>, Logging {

    val asInt
        get() = try {
            verStringToInt(asString)
        } catch (e: Exception) {
            warn("Exception while parsing version '$asString', assuming version 0")
            0
        }

    /**
     * Convert a version string of the form 1.23.57 to a comparable integer of
     * the form 12357.
     *
     * Or throw an exception if the string can not be parsed
     */
    private fun verStringToInt(s: String): Int {
        // Allow 1 to two digits per match
        val match =
            Regex("(\\d{1,2}).(\\d{1,2}).(\\d{1,2})").find(s)
                ?: throw Exception("Can't parse version $s")
        val (major, minor, build) = match.destructured
        return major.toInt() * 10000 + minor.toInt() * 100 + build.toInt()
    }

    override fun compareTo(other: DeviceVersion): Int = asInt - other.asInt
}