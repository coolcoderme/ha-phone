package io.github.coolcoderme.haphone.data.db

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
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "devices")
data class DeviceRow(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val host: String,
    val port: Int,
    val extrasJson: String,
    val roomId: String?,
    val favorite: Boolean
)

@Entity(tableName = "entities")
data class EntityRow(
    @PrimaryKey val id: String,
    val deviceId: String,
    val kind: String,
    val name: String,
    val available: Boolean,
    val onState: Int?,
    val brightness: Int?,
    val extraJson: String
)

@Entity(tableName = "rooms")
data class RoomRow(
    @PrimaryKey val id: String,
    val name: String
)

@Entity(tableName = "scenes")
data class SceneRow(
    @PrimaryKey val id: String,
    val name: String,
    val actionsJson: String
)

@Entity(tableName = "automations")
data class AutomationRow(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val triggerJson: String,
    val actionsJson: String,
    val lastFiredAt: Long
)

@Dao
interface HubDao {
    @Query("SELECT * FROM devices ORDER BY name")
    fun observeDevices(): Flow<List<DeviceRow>>

    @Query("SELECT * FROM devices ORDER BY name")
    suspend fun devices(): List<DeviceRow>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun device(id: String): DeviceRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevice(row: DeviceRow)

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun deleteDevice(id: String)

    @Query("SELECT * FROM entities ORDER BY name")
    fun observeEntities(): Flow<List<EntityRow>>

    @Query("SELECT * FROM entities ORDER BY name")
    suspend fun entities(): List<EntityRow>

    @Query("SELECT * FROM entities WHERE deviceId = :deviceId")
    suspend fun entitiesForDevice(deviceId: String): List<EntityRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntity(row: EntityRow)

    @Query("DELETE FROM entities WHERE deviceId = :deviceId")
    suspend fun deleteEntitiesForDevice(deviceId: String)

    @Query("SELECT * FROM rooms ORDER BY name")
    fun observeRooms(): Flow<List<RoomRow>>

    @Query("SELECT * FROM rooms ORDER BY name")
    suspend fun rooms(): List<RoomRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoom(row: RoomRow)

    @Query("DELETE FROM rooms WHERE id = :id")
    suspend fun deleteRoom(id: String)

    @Query("SELECT * FROM scenes ORDER BY name")
    fun observeScenes(): Flow<List<SceneRow>>

    @Query("SELECT * FROM scenes ORDER BY name")
    suspend fun scenes(): List<SceneRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScene(row: SceneRow)

    @Query("DELETE FROM scenes WHERE id = :id")
    suspend fun deleteScene(id: String)

    @Query("SELECT * FROM automations ORDER BY name")
    fun observeAutomations(): Flow<List<AutomationRow>>

    @Query("SELECT * FROM automations ORDER BY name")
    suspend fun automations(): List<AutomationRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomation(row: AutomationRow)

    @Update
    suspend fun updateAutomation(row: AutomationRow)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteAutomation(id: String)

    @Query("DELETE FROM devices")
    suspend fun clearDevices()

    @Query("DELETE FROM entities")
    suspend fun clearEntities()

    @Query("DELETE FROM rooms")
    suspend fun clearRooms()

    @Query("DELETE FROM scenes")
    suspend fun clearScenes()

    @Query("DELETE FROM automations")
    suspend fun clearAutomations()
}

@Database(
    entities = [DeviceRow::class, EntityRow::class, RoomRow::class, SceneRow::class, AutomationRow::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): HubDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "haphone.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
