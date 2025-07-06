// InventoryViewModel.kt
package org.uvwstudio.toymanager

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
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
    private val _stockInItems = mutableStateOf<Map<String, Int>>(emptyMap())
    val stockInItems: State<Map<String, Int>> get() = _stockInItems


    // 使用 LiveData 显示所有物品
    val allItems: LiveData<List<InventoryItem>> = liveData(Dispatchers.IO) {
        val list = dao.getAllItems()
        emit(list.map { it.toModel() })
    }

    fun clearStockInItems() {
        _stockInItems.value = emptyMap()
    }

    fun hitRFID(rfid: String) {
        val currentMap = _stockInItems.value.toMutableMap()
        val count = currentMap.getOrPut(rfid) { 0 }
        currentMap[rfid] = count + 1
        _stockInItems.value = currentMap // 触发 UI 更新
        Log.d("RFID", "RFID:$rfid, cnt: ${count + 1}")
    }

    fun removeRFID(rfid: String) {
        val current = _stockInItems.value.toMutableMap()
        current.remove(rfid)
        _stockInItems.value = current
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
