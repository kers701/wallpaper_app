package com.kers.killove.jhsy.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.security.MessageDigest

@Entity(tableName = "search_page_cache")
data class SearchPageCacheEntity(
    @PrimaryKey val cacheKey: String,
    val lastPage: Int,
    val updatedAt: Long
)

@Dao
interface SearchPageCacheDao {
    @Query("SELECT lastPage FROM search_page_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getLastPage(key: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchPageCacheEntity)

    @Query("DELETE FROM search_page_cache")
    suspend fun clearAll()
}

/**
 * 按搜索条件缓存 Wallhaven meta.last_page，供后续随机翻页。
 */
class PageCacheStore(private val dao: SearchPageCacheDao) {

    suspend fun getLastPage(key: String): Int? = dao.getLastPage(key)

    suspend fun putLastPage(key: String, lastPage: Int) {
        dao.upsert(
            SearchPageCacheEntity(
                cacheKey = key,
                lastPage = lastPage.coerceAtLeast(1),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    companion object {
        fun key(
            keyword: String,
            category: String,
            purity: String,
            orientation: String,
            resolution: String
        ): String {
            val raw = listOf(keyword.trim().lowercase(), category, purity, orientation, resolution)
                .joinToString("|")
            val md = MessageDigest.getInstance("SHA-256")
            val dig = md.digest(raw.toByteArray(Charsets.UTF_8))
            return dig.joinToString("") { "%02x".format(it) }.take(32)
        }

        fun from(context: Context): PageCacheStore =
            PageCacheStore(WallpaperDatabase.get(context).pageCacheDao())
    }
}
