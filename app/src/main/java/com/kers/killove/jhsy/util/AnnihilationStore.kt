package com.kers.killove.jhsy.util

import android.content.Context
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 湮灭模式关键词缓存（本机文件）。
 * - 使用过的关键词写入缓存
 * - 跃迁提取时命中缓存的不进入跃迁列表
 * - 若本轮候选全部命中 → 清空缓存并进入下一纪元（全部进入跃迁）
 * - 条目 ≥ [MAX_ENTRIES] 强制清空
 * - 首页/概览只展示「上一轮被湮灭」的词；若无一命中则为「全员飞升」
 */
object AnnihilationStore {
    const val MAX_ENTRIES = 777
    private const val FILE_NAME = "annihilation_keywords.txt"
    private const val LAST_ROUND_FILE = "annihilation_last_round.txt"
    private const val LAST_ROUND_META = "annihilation_last_round_meta.txt"
    private val lock = ReentrantLock()

    private fun file(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NAME)

    private fun lastRoundFile(context: Context): File =
        File(context.applicationContext.filesDir, LAST_ROUND_FILE)

    private fun lastRoundMetaFile(context: Context): File =
        File(context.applicationContext.filesDir, LAST_ROUND_META)

    fun list(context: Context): List<String> = lock.withLock { listUnlocked(context) }

    fun size(context: Context): Int = list(context).size

    fun clear(context: Context) = lock.withLock {
        val f = file(context)
        if (f.exists()) f.writeText("")
    }

    /** 上一轮因命中缓存而未进入跃迁的关键词 */
    fun lastRoundBlocked(context: Context): List<String> = lock.withLock {
        val f = lastRoundFile(context)
        if (!f.exists()) return emptyList()
        f.readLines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /** 上一轮是否「全员飞升」（无一命中湮灭缓存） */
    fun lastRoundAllAscended(context: Context): Boolean = lock.withLock {
        val f = lastRoundMetaFile(context)
        if (!f.exists()) return false
        f.readText().trim() == "ascended"
    }

    fun hasLastRound(context: Context): Boolean = lock.withLock {
        lastRoundMetaFile(context).exists()
    }

    /**
     * 记录本次使用的搜索词（整句 + 分词）。
     * @return true 若因达到上限而强制清空
     */
    fun recordUsed(context: Context, usedKeyword: String?): Boolean = lock.withLock {
        val raw = usedKeyword?.trim().orEmpty()
        if (raw.isEmpty()) return false
        val toAdd = linkedSetOf<String>()
        toAdd += raw
        raw.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { toAdd += it }

        val existing = listUnlocked(context).toMutableList()
        val lower = existing.map { it.lowercase() }.toMutableSet()
        for (w in toAdd) {
            if (w.lowercase() !in lower) {
                existing += w
                lower += w.lowercase()
            }
        }
        if (existing.size >= MAX_ENTRIES) {
            file(context).writeText("")
            return true
        }
        file(context).writeText(existing.joinToString("\n"))
        false
    }

    /**
     * 对跃迁候选标签应用湮灭过滤，并记录本轮被湮灭列表。
     * @return Pair(过滤后列表, 是否因「全部命中」而清缓存进入新纪元)
     */
    fun filterForJump(context: Context, candidates: List<String>): Pair<List<String>, Boolean> =
        lock.withLock {
            if (candidates.isEmpty()) {
                saveLastRound(context, emptyList(), allAscended = true)
                return candidates to false
            }
            val cacheLower = listUnlocked(context).map { it.lowercase() }.toSet()
            if (cacheLower.isEmpty()) {
                saveLastRound(context, emptyList(), allAscended = true)
                return candidates to false
            }
            val blocked = candidates.filter { it.lowercase() in cacheLower }
            val kept = candidates.filter { it.lowercase() !in cacheLower }
            if (kept.isEmpty()) {
                file(context).writeText("")
                saveLastRound(context, blocked, allAscended = false)
                return candidates to true
            }
            saveLastRound(context, blocked, allAscended = blocked.isEmpty())
            kept to false
        }

    private fun saveLastRound(context: Context, blocked: List<String>, allAscended: Boolean) {
        lastRoundFile(context).writeText(blocked.joinToString("\n"))
        lastRoundMetaFile(context).writeText(if (allAscended) "ascended" else "blocked")
    }

    private fun listUnlocked(context: Context): List<String> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return f.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
