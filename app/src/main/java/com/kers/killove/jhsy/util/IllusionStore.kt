package com.kers.killove.jhsy.util

import android.content.Context
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 虚妄模式：在跃迁+湮灭过滤之后，把「包含本次搜索词」的候选打入虚妄，仅剩余可进跃迁。
 * 「我们从过去走出，应舍弃前尘，从此，身前虚妄，身后亦是虚妄，我们没有过去，也没有未来，但我们却有着千丝万缕的联系」
 */
object IllusionStore {
    private const val LAST_ROUND_FILE = "illusion_last_round.txt"
    private const val LAST_ROUND_META = "illusion_last_round_meta.txt"
    private const val MAX_LINE_LEN = 120
    private const val MAX_LINES = 500
    private val lock = ReentrantLock()

    private fun lastRoundFile(context: Context): File =
        File(context.applicationContext.filesDir, LAST_ROUND_FILE)

    private fun lastRoundMetaFile(context: Context): File =
        File(context.applicationContext.filesDir, LAST_ROUND_META)

    /** 拒绝 SQLite/二进制等脏数据，避免看板展开显示乱码 */
    private fun isCleanKeywordLine(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > MAX_LINE_LEN) return false
        // SQLite 魔数 / SQL 结构片段
        val low = t.lowercase()
        if (low.startsWith("sqlite format")) return false
        if (low.contains("create table")) return false
        if (low.contains("sqlite_stat")) return false
        if (t.contains('\u0000')) return false
        // 必须是可打印字符为主（允许常见标点与非 ASCII 文字）
        val bad = t.count { ch ->
            val c = ch.code
            c < 0x09 || (c in 0x0B..0x1F) || c == 0x7F
        }
        if (bad > 0) return false
        return true
    }

    private fun readKeywordLines(f: File): List<String> {
        if (!f.exists() || f.length() == 0L) return emptyList()
        // 文件头若是 SQLite 库，直接清掉
        val header = runCatching {
            f.inputStream().use { ins ->
                val buf = ByteArray(16)
                val n = ins.read(buf)
                if (n <= 0) return@use ""
                buf.copyOf(n).toString(Charsets.ISO_8859_1)
            }
        }.getOrDefault("")
        if (header.startsWith("SQLite format")) {
            runCatching { f.writeText("") }
            return emptyList()
        }
        return runCatching {
            f.readLines(Charsets.UTF_8)
                .asSequence()
                .map { it.trim() }
                .filter { isCleanKeywordLine(it) }
                .distinct()
                .take(MAX_LINES)
                .toList()
        }.getOrDefault(emptyList())
    }

    /** 上一轮进入虚妄的关键词 */
    fun lastRoundIllusory(context: Context): List<String> = lock.withLock {
        val meta = lastRoundMetaFile(context)
        // meta=none 时强制视为无虚妄词，并清掉可能残留的脏文件
        if (meta.exists() && meta.readText().trim() == "none") {
            val f = lastRoundFile(context)
            if (f.exists() && f.length() > 0L) runCatching { f.writeText("") }
            return emptyList()
        }
        readKeywordLines(lastRoundFile(context))
    }

    fun hasLastRound(context: Context): Boolean = lock.withLock {
        lastRoundMetaFile(context).exists()
    }

    /** 上一轮是否无人进入虚妄 */
    fun lastRoundNone(context: Context): Boolean = lock.withLock {
        val f = lastRoundMetaFile(context)
        if (!f.exists()) return false
        f.readText().trim() == "none"
    }

    /**
     * @return Pair(可进入跃迁的列表, 进入虚妄的列表)
     */
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
        saveLastRound(context, illusory.filter { isCleanKeywordLine(it) }, none = illusory.isEmpty())
        kept to illusory
    }

    /** 整句 + 分词（长度≥2）作为匹配针 */
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
        val listFile = lastRoundFile(context)
        val metaFile = lastRoundMetaFile(context)
        val text = illusory.filter { isCleanKeywordLine(it) }.distinct().joinToString("\n")
        // 原子写：先写临时文件再 rename，避免半截二进制
        val tmp = File(listFile.parentFile, listFile.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(listFile)) {
            listFile.writeText(text, Charsets.UTF_8)
            tmp.delete()
        }
        metaFile.writeText(if (none) "none" else "hit", Charsets.UTF_8)
    }

    /** 手动清空本轮虚妄记录（设置页/排障用） */
    fun clearLastRound(context: Context) = lock.withLock {
        lastRoundFile(context).writeText("")
        lastRoundMetaFile(context).delete()
    }
}
