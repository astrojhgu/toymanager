package org.uvwstudio.toymanager

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 数据表结构：库存物品
 */
@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey val rfid: String,
    val name: String,
    val detail: String,
    val photoPath: String? = null
)

/**
 * 数据访问接口（DAO）
 */
@Dao
interface InventoryItemDao {
    @Query("SELECT * FROM inventory_items")
    suspend fun getAllItems(): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items WHERE rfid = :rfid")
    suspend fun getItemByRfid(rfid: String): InventoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItemEntity)

    @Delete
    suspend fun deleteItem(item: InventoryItemEntity)

    @Update
    suspend fun updateItem(item: InventoryItemEntity)

    @Query("DELETE FROM inventory_items")
    suspend fun clearAll()

    @Query("SELECT * FROM inventory_items")
    fun getAllItemsFlow(): Flow<List<InventoryItemEntity>>
}

/**
 * Room数据库定义
 */
@Database(entities = [InventoryItemEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventoryItemDao(): InventoryItemDao
}

/**
 * 数据库单例管理器
 */
object DbManager {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "toymanager.db"
            ).build()
            INSTANCE = instance
            instance
        }
    }
}

fun InventoryItemEntity.toModel(): InventoryItem =
    InventoryItem(name = name, rfid = rfid, detail = detail, photoPath = photoPath)

fun InventoryItem.toEntity(): InventoryItemEntity =
    InventoryItemEntity(rfid = rfid, name = name, detail = detail, photoPath = photoPath)