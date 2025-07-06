// InventoryViewModel.kt
package org.uvwstudio.toymanager

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

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
    private val _hitItemInDb = mutableStateOf<Map<String, Int>>(emptyMap())
    val hitItemInDb: State<Map<String, Int>> get() = _hitItemInDb


    // 使用 LiveData 显示所有物品
    val allItems: LiveData<List<InventoryItem>> = liveData(Dispatchers.IO) {
        val list = dao.getAllItems()
        emit(list.map { it.toModel() })
    }

    fun hitRFID(rfid: String) {
        // 优先判断是否已经在两个 Map 中
        if (_hitItemInDb.value.containsKey(rfid)) {
            _hitItemInDb.value = _hitItemInDb.value.toMutableMap().also {
                it[rfid] = it.getValue(rfid) + 1
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
            val exists = dao.getItemByRfid(rfid) != null
            if (exists) {
                _hitItemInDb.value = _hitItemInDb.value.toMutableMap().also {
                    it[rfid] = 1
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
        val current = _hitItemNotInDb.value.toMutableMap()
        current.remove(rfid)
        _hitItemNotInDb.value = current
    }

    // 插入或更新
    fun upsert(item: InventoryItem) = viewModelScope.launch(Dispatchers.IO) {
        dao.insertItem(item.toEntity())
        refreshItems()
    }

    // 删除
    fun delete(item: InventoryItem) = viewModelScope.launch(Dispatchers.IO) {
        dao.deleteItem(item.toEntity())
        refreshItems()
    }

    // 刷新数据
    private fun refreshItems() = viewModelScope.launch(Dispatchers.IO) {
        val list = dao.getAllItems()
        _itemsLiveData.postValue(list.map { it.toModel() })
    }

    private val _itemsLiveData = MutableLiveData<List<InventoryItem>>()
    val itemsLiveData: LiveData<List<InventoryItem>> get() = _itemsLiveData
}
