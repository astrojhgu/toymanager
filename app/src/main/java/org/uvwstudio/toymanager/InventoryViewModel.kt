// InventoryViewModel.kt
package org.uvwstudio.toymanager

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.withContext

import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    /*
    init {
        viewModelScope.launch(Dispatchers.IO) {
            // 这里是在协程作用域内部，isActive 可用了
            //while (isActive) {
                Log.d("InventoryViewModel", "后台任务执行中")
            //    delay(3000)
            //}
            RFIDScanner.initDevice()
            RFIDScanner.setPwr(23)
            RFIDScanner.queryPwr()
            RFIDScanner.startScan({_,_->Log.d("RFID","a")})
            delay(10000)
            RFIDScanner.stopScan()
            RFIDScanner.closeDevice()
        }
    }*/

    private val dao = DbManager.getDatabase(application).inventoryItemDao()
    private val _hitItemNotInDb = mutableStateOf<Map<String, Int>>(emptyMap())
    val hitItemNotInDb: State<Map<String, Int>> get() = _hitItemNotInDb
    private val _hitItemInDb = mutableStateOf<Map<String, Pair<String,Int>>>(emptyMap())
    val hitItemInDb: State<Map<String, Pair<String,Int>>> get() = _hitItemInDb


    val allItems: StateFlow<List<InventoryItem>> = dao.getAllItemsFlow()
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun hitRFID(rfid: String) {
        // 优先判断是否已经在两个 Map 中
        if (_hitItemInDb.value.containsKey(rfid)) {
            _hitItemInDb.value = _hitItemInDb.value.toMutableMap().also {
                val v=it.getValue(rfid)
                it[rfid] = Pair(v.first,v.second+1)
            }
            return
        }

        if (_hitItemNotInDb.value.containsKey(rfid)) {
            _hitItemNotInDb.value = _hitItemNotInDb.value.toMutableMap().also {
                it[rfid] = it.getValue(rfid) + 1
            }
            return
        }

        // 启动后台任务查询数据库（首次出现）
        viewModelScope.launch(Dispatchers.IO) {
            val item = dao.getItemByRfid(rfid)
            if (item != null) {
                val name = item.name
                _hitItemInDb.value = _hitItemInDb.value.toMutableMap().also {
                    it[rfid] = name to 1
                }
            } else {
                _hitItemNotInDb.value = _hitItemNotInDb.value.toMutableMap().also {
                    it[rfid] = 1
                }
            }
        }
    }

    fun clearHitItems() {
        _hitItemInDb.value = emptyMap()
        _hitItemNotInDb.value = emptyMap()
    }

    fun hitRFIDStockIn(rfid: String) {
        hitRFID(rfid)
    }

    fun removeRFID(rfid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = dao.getItemByRfid(rfid)
            if (entity != null) {
                dao.deleteItem(entity)

                // 刷新内存状态（切回主线程）
                withContext(Dispatchers.Main) {
                    val current = _hitItemInDb.value.toMutableMap()
                    current.remove(rfid)
                    _hitItemInDb.value = current
                }
            }
        }
    }


    // 插入或更新
    fun upsert(item: InventoryItem) = viewModelScope.launch(Dispatchers.IO) {
        dao.insertItem(item.toEntity())

        // 更新 hitItemInDb / hitItemNotInDb 状态
        val rfid = item.rfid
        val currentInDb = _hitItemInDb.value.toMutableMap()
        val currentNotInDb = _hitItemNotInDb.value.toMutableMap()

        // 取出命中次数（如果之前在 NotInDb 中有）
        val count = currentNotInDb[rfid] ?: 1

        // 从 NotInDb 移除，转移到 InDb
        currentNotInDb.remove(rfid)
        currentInDb[rfid] = Pair(item.name, count)

        _hitItemNotInDb.value = currentNotInDb
        _hitItemInDb.value = currentInDb
    }


    // 删除
    fun delete(item: InventoryItem) = viewModelScope.launch(Dispatchers.IO) {
        dao.deleteItem(item.toEntity())

    }
}
