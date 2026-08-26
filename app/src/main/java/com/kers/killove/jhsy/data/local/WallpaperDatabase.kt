package com.kers.killove.jhsy.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wallpaper_records")
data class WallpaperEntity(
    @PrimaryKey val id: String,
    val path: String,
    val category: String,
    val purity: String,
    val sourceUrl: String,
    val setAt: Long,
    val width: Int = 0,
    val height: Int = 0,
    val fileSize: Long = 0L,
    val source: String = "",
    /** 本次搜索使用的关键词（Wallhaven 时有意义） */
    val keyword: String = ""
)

@Dao
interface WallpaperDao {
    @Query("SELECT EXISTS(SELECT 1 FROM wallpaper_records WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    /** 同一 Wallhaven 图可能以 id / id_Home / id_Lock 入库 */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM wallpaper_records
            WHERE id = :baseId
               OR id LIKE :baseId || '_%'
               OR (sourceUrl != '' AND sourceUrl = :url)
            LIMIT 1
        )
    """)
    suspend fun existsBaseOrUrl(baseId: String, url: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WallpaperEntity)

    @Query("SELECT * FROM wallpaper_records ORDER BY setAt DESC LIMIT :limit")
    fun recent(limit: Int = 77): Flow<List<WallpaperEntity>>

    @Query("SELECT COUNT(*) FROM wallpaper_records")
    suspend fun count(): Int

    @Query("DELETE FROM wallpaper_records WHERE setAt NOT IN (SELECT setAt FROM wallpaper_records ORDER BY setAt DESC LIMIT :keep)")
    suspend fun trimToKeep(keep: Int = 77)

    @Query("DELETE FROM wallpaper_records")
    suspend fun deleteAll()
}

@Database(
    entities = [WallpaperEntity::class, SearchPageCacheEntity::class],
    version = 4,
    exportSchema = false
)
abstract class WallpaperDatabase : RoomDatabase() {
    abstract fun dao(): WallpaperDao
    abstract fun pageCacheDao(): SearchPageCacheDao

    companion object {
        @Volatile private var instance: WallpaperDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wallpaper_records ADD COLUMN width INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wallpaper_records ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wallpaper_records ADD COLUMN fileSize INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE wallpaper_records ADD COLUMN source TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wallpaper_records ADD COLUMN keyword TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS search_page_cache (
                        cacheKey TEXT NOT NULL PRIMARY KEY,
                        lastPage INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): WallpaperDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WallpaperDatabase::class.java,
                    "wallpaperc.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
