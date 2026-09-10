package com.kers.killove.jhsy.util

import android.content.Context
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 虚妄模式：在跃迁+溺灭过滤之后，把「包含本次搜索词」的候选打入虚妄，仅剩余可进跃迁。
 */
object IllusionStore {
    private const val LAST_ROUND_FILE = "illusion_last_round.txt"
    private const val LAST_ROUND_META = "illusion_last_round_meta.txt"
    private val lock = ReentrantLock()

    private fun lastRoundFile(context: Context): File =
        File(context.applicationContext.filesDir, LAST_ROUND_FILE)

    private fun lastRoundMetaFile(context: Context): File =
        File(context.applicationContext.filesDir, LAST_ROUND_META)

    fun lastRoundIllusory(context: Context): List<String> = lock.withLock {
        val f = lastRoundFile(context)
        if (!f.exists()) return emptyList()
        f.readLines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    fun hasLastRound(context: Context): Boolean = lock.withLock {
        lastRoundMetaFile(context).exists()
    }

    fun lastRoundNone(context: Context): Boolean = lock.withLock {
        val f = lastRoundMetaFile(context)
        if (!f.exists()) return false
        f.readText().trim() == "none"
    }

    fun filterForJump(
        context: Context,
        candidates: List<String>,
        usedKeyword: String?
    ): Pair<List<String>, List<String>> = lock.withLock {
        if (candidates.isEmpty()) {
            saveLastRound(context, emptyList(), none = true)
            return emptyList<String>() to emptyList()
        }
        val needles = needlesFrom(usedKeyword)
        if (needles.isEmpty()) {
            saveLastRound(context, emptyList(), none = true)
            return candidates to emptyList()
        }
        val illusory = candidates.filter { c ->
            val low = c.lowercase()
            needles.any { n -> low.contains(n) }
        }
        val kept = candidates.filter { c ->
            val low = c.lowercase()
            needles.none { n -> low.contains(n) }
        }
        saveLastRound(context, illusory, none = illusory.isEmpty())
        kept to illusory
    }

    fun needlesFrom(usedKeyword: String?): List<String> {
        val raw = usedKeyword?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        val set = linkedSetOf<String>()
        set += raw.lowercase()
        raw.split(Regex("\\s+")).map { it.trim() }.filter { it.length >= 2 }.forEach {
            set += it.lowercase()
        }
        return set.toList()
    }

    private fun saveLastRound(context: Context, illusory: List<String>, none: Boolean) {
        lastRoundFile(context).writeText(illusory.joinToString("\n"))
        lastRoundMetaFile(context).writeText(if (none) "none" else "hit")
    }
}
