package com.rfmission.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val status: String,
    val role: String,
    val ip: String? = null,
    val lastSeen: Long = 0L
)

@Entity(tableName = "missions")
data class MissionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val status: String,
    val priority: Int = 0
)

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes ORDER BY lastSeen DESC")
    fun getAllNodes(): Flow<List<NodeEntity>>
    @Upsert
    suspend fun upsertAll(nodes: List<NodeEntity>)
    @Delete
    suspend fun delete(node: NodeEntity)
}

@Dao
interface MissionDao {
    @Query("SELECT * FROM missions ORDER BY priority DESC")
    fun getAllMissions(): Flow<List<MissionEntity>>
    @Upsert
    suspend fun upsertAll(missions: List<MissionEntity>)
}

@Database(entities = [NodeEntity::class, MissionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun missionDao(): MissionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(ctx, AppDatabase::class.java, "rfmission.db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
