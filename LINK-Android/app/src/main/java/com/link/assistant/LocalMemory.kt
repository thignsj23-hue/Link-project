package com.link.assistant

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "chat_memory")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM chat_memory ORDER BY id DESC LIMIT 100")
    suspend fun latest(): List<MemoryEntity>

    @Insert
    suspend fun insert(message: MemoryEntity)

    @Query("DELETE FROM chat_memory")
    suspend fun clear()
}

@Database(entities = [MemoryEntity::class], version = 1, exportSchema = false)
abstract class LinkDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile private var instance: LinkDatabase? = null

        fun get(context: Context): LinkDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LinkDatabase::class.java,
                "link-memory.db"
            ).build().also { instance = it }
        }
    }
}
