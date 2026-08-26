package com.kers701.wallpaperc.data.local

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
    val source: String = ""
)

@Dao
interface WallpaperDao {
    @Query("SELECT EXISTS(SELECT 1 FROM wallpaper_records WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WallpaperEntity)

    @Query("SELECT * FROM wallpaper_records ORDER BY setAt DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<WallpaperEntity>>

    @Query("SELECT COUNT(*) FROM wallpaper_records")
    suspend fun count(): Int
}

@Database(entities = [WallpaperEntity::class], version = 2, exportSchema = false)
abstract class WallpaperDatabase : RoomDatabase() {
    abstract fun dao(): WallpaperDao

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

        fun get(context: Context): WallpaperDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WallpaperDatabase::class.java,
                    "wallpaperc.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
