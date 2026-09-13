package com.kers.killove.jhsy.util

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 湮灭模式关键词缓存（本机文件）。
 *
 * 规则（与产品一致）：
 * - 关键词**一被选中用于搜索**即写入缓存（用哪个记哪个，整句不拆词）
 * - 文件每行：`MM-dd HH:mm | 关键词`
 * - 跃迁提取时，标签与缓存中任一关键词全等（忽略大小写）则不进跃迁
 * - 本轮候选全部命中 → 清空缓存并进入下一纪元（候选全部放行进跃迁）
 * - 条目 ≥ [MAX_ENTRIES] 强制清空
 */
object AnnihilationStore {
    const val MAX_ENTRIES = 777
    private const val FILE_NAME = "annihilation_keywords.txt"
    private const val LAST_ROUND_FILE = "annihilation_last_round.txt"
    private const val LAST_ROUND_META = "annihilation_last_round_meta.txt"
    private val lock = ReentrantLock()
    private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)

    private fun file(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NAME)

    private fun lastRoundFile(context: Context): File =
        File(context.applicationContext.filesDir, LAST_ROUND_FILE)

    private fun lastRoundMetaFile(context: Context): File =
        File(context.applicationContext.filesDir, LAST_ROUND_META)

    /** 仅关键词列表（供 UI 数量 / 匹配） */
    fun list(context: Context): List<String> = lock.withLock {
        loadEntries(context).map { it.keyword }.distinct()
    }

    fun size(context: Context): Int = list(context).size

    fun clear(context: Context) = lock.withLock {
        writeEntries(context, emptyList())
    }

    /** 上一轮因命中缓存而未进入跃迁的关键词 */
    fun lastRoundBlocked(context: Context): List<String> = lock.withLock {
        val meta = lastRoundMetaFile(context)
        if (meta.exists() && meta.readText().trim() == "ascended") {
            val f = lastRoundFile(context)
            if (f.exists() && f.length() > 0L) runCatching { f.writeText("") }
            return emptyList()
        }
        readPlainKeywordLines(lastRoundFile(context))
    }

    fun lastRoundAllAscended(context: Context): Boolean = lock.withLock {
        val f = lastRoundMetaFile(context)
        if (!f.exists()) return false
        f.readText().trim() == "ascended"
    }

    fun hasLastRound(context: Context): Boolean = lock.withLock {
        lastRoundMetaFile(context).exists()
    }

    /**
     * 关键词选中后立刻写入：整句原样，不拆词。
     * 同关键词（忽略大小写）已存在则只更新时间，不重复占行。
     * @return true 若因达到上限而强制清空
     */
    fun recordUsed(context: Context, usedKeyword: String?): Boolean = lock.withLock {
        val raw = usedKeyword?.trim().orEmpty()
        if (raw.isEmpty()) return false
        if (!isCleanKeyword(raw)) return false

        val now = timeFmt.format(Date())
        val entries = loadEntries(context).toMutableList()
        val idx = entries.indexOfFirst { it.keyword.equals(raw, ignoreCase = true) }
        if (idx >= 0) {
            entries[idx] = Entry(now, entries[idx].keyword) // 保留首次写法，刷新时间
        } else {
            entries += Entry(now, raw)
        }
        if (entries.size >= MAX_ENTRIES) {
            writeEntries(context, emptyList())
            return true
        }
        writeEntries(context, entries)
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
            val cacheLower = loadEntries(context).map { it.keyword.lowercase() }.toSet()
            if (cacheLower.isEmpty()) {
                saveLastRound(context, emptyList(), allAscended = true)
                return candidates to false
            }
            val blocked = candidates.filter { it.lowercase() in cacheLower }
            val kept = candidates.filter { it.lowercase() !in cacheLower }
            if (kept.isEmpty()) {
                // 全命中：清空缓存，本轮候选全部放行，调用方升纪元
                writeEntries(context, emptyList())
                saveLastRound(context, blocked, allAscended = false)
                return candidates to true
            }
            saveLastRound(context, blocked, allAscended = blocked.isEmpty())
            kept to false
        }

    // ----- 内部 -----

    private data class Entry(val time: String, val keyword: String) {
        fun line(): String = "$time | $keyword"
    }

    /** 解析一行：`MM-dd HH:mm | keyword`；兼容旧版纯关键词行 */
    private fun parseLine(line: String): Entry? {
        val t = line.trim()
        if (t.isEmpty()) return null
        val sep = " | "
        val i = t.indexOf(sep)
        return if (i >= 0) {
            val time = t.substring(0, i).trim()
            val kw = t.substring(i + sep.length).trim()
            if (kw.isEmpty() || !isCleanKeyword(kw)) null
            else Entry(time.ifEmpty { timeFmt.format(Date()) }, kw)
        } else {
            // 旧格式：整行即关键词（可能是历史拆词残留，仍按整行一条匹配）
            if (!isCleanKeyword(t)) null else Entry("??-?? ??:??", t)
        }
    }

    private fun loadEntries(context: Context): List<Entry> {
        val f = file(context)
        if (!f.exists() || f.length() == 0L) return emptyList()
        if (looksLikeSqlite(f)) {
            runCatching { f.writeText("") }
            return emptyList()
        }
        return runCatching {
            f.readLines(Charsets.UTF_8)
                .mapNotNull { parseLine(it) }
                // 同词保留最后一次
                .fold(linkedMapOf<String, Entry>()) { acc, e ->
                    acc[e.keyword.lowercase()] = e
                    acc
                }
                .values
                .toList()
        }.getOrDefault(emptyList())
    }

    private fun writeEntries(context: Context, entries: List<Entry>) {
        val f = file(context)
        val text = entries.joinToString("\n") { it.line() }
        val tmp = File(f.parentFile, f.name + ".tmp")
        // 进程内锁 + 文件锁，降低 :svc / 主进程互盖
        withFileLock(f) {
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(f)) {
                f.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    private fun withFileLock(target: File, block: () -> Unit) {
        val lockFile = File(target.parentFile, target.name + ".lock")
        runCatching {
            RandomAccessFile(lockFile, "rw").channel.use { ch ->
                var fl: FileLock? = null
                try {
                    fl = ch.lock()
                    block()
                } finally {
                    runCatching { fl?.release() }
                }
            }
        }.onFailure {
            // 锁失败仍尽量写，避免功能全断
            block()
        }
    }

    private fun saveLastRound(context: Context, blocked: List<String>, allAscended: Boolean) {
        lastRoundFile(context).writeText(
            blocked.filter { isCleanKeyword(it) }.distinct().joinToString("\n"),
            Charsets.UTF_8
        )
        lastRoundMetaFile(context).writeText(if (allAscended) "ascended" else "blocked", Charsets.UTF_8)
    }

    private fun isCleanKeyword(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > 120) return false
        val low = t.lowercase()
        if (low.startsWith("sqlite format")) return false
        if (low.contains("create table")) return false
        if (low.contains("sqlite_stat")) return false
        if (t.contains('\u0000')) return false
        if (t.any { ch ->
                val c = ch.code
                c < 0x09 || (c in 0x0B..0x1F) || c == 0x7F
            }
        ) return false
        return true
    }

    private fun looksLikeSqlite(f: File): Boolean {
        val header = runCatching {
            f.inputStream().use { ins ->
                val buf = ByteArray(16)
                val n = ins.read(buf)
                if (n <= 0) return@use ""
                buf.copyOf(n).toString(Charsets.ISO_8859_1)
            }
        }.getOrDefault("")
        return header.startsWith("SQLite format")
    }

    private fun readPlainKeywordLines(f: File): List<String> {
        if (!f.exists() || f.length() == 0L) return emptyList()
        if (looksLikeSqlite(f)) {
            runCatching { f.writeText("") }
            return emptyList()
        }
        return runCatching {
            f.readLines(Charsets.UTF_8)
                .map { it.trim() }
                .filter { isCleanKeyword(it) }
                .distinct()
                .take(500)
                .toList()
        }.getOrDefault(emptyList())
    }
}
