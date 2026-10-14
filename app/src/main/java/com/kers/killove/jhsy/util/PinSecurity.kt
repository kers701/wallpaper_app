package com.kers.killove.jhsy.util

import java.security.MessageDigest

object PinSecurity {
    fun hash(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(raw: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return false
        return hash(raw) == storedHash
    }

    fun isValidPinFormat(pin: String): Boolean =
        pin.length in 4..8 && pin.all { it.isDigit() }

    /** 图案：3×3 点序，索引 0–8，至少 4 个不重复点 */
    fun encodePattern(cells: List<Int>): String =
        cells.joinToString("-")

    fun isValidPattern(cells: List<Int>): Boolean {
        if (cells.size !in 4..9) return false
        if (cells.any { it !in 0..8 }) return false
        if (cells.toSet().size != cells.size) return false
        return true
    }

    fun verifyPattern(cells: List<Int>, storedHash: String): Boolean {
        if (!isValidPattern(cells) || storedHash.isBlank()) return false
        return verify(encodePattern(cells), storedHash)
    }
}
