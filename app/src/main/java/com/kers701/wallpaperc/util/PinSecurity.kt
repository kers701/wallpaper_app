package com.kers701.wallpaperc.util

import java.security.MessageDigest

object PinSecurity {
    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(pin: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return false
        return hash(pin) == storedHash
    }

    fun isValidPinFormat(pin: String): Boolean =
        pin.length in 4..8 && pin.all { it.isDigit() }
}
