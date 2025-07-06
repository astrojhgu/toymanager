package org.uvwstudio.toymanager

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.viewinterop.AndroidView

import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.ui.graphics.Color

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.background

import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

enum class TabPage {
    STOCK_IN,
    INVENTORY,
    STOCK_OUT
}

/**
 * ToyManager - RFID仓库管理App
 * 主入口Activity
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToyManagerAppContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToyManagerAppContent() {
    var scanning by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(TabPage.STOCK_IN) }

    val inventoryViewModel: InventoryViewModel = viewModel()
    Scaffold(
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.height(56.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                content = {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        scanning = true
                        onStartScan(selectedTab, inventoryViewModel)
                    }, enabled = !scanning) {

                        Text("开始扫描")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        scanning = false
                        onStopScan(selectedTab, inventoryViewModel)
                    }, enabled = scanning) {
                        Text("停止扫描")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { /* TODO: 设置界面逻辑 */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == TabPage.STOCK_IN,
                    onClick = { if (!scanning) selectedTab = TabPage.STOCK_IN },
                    text = { Text("入库") })
                Tab(
                    selected = selectedTab == TabPage.INVENTORY,
                    onClick = { if (!scanning) selectedTab = TabPage.INVENTORY },
                    text = { Text("盘点/寻找") })
                Tab(
                    selected = selectedTab == TabPage.STOCK_OUT,
                    onClick = { if (!scanning) selectedTab = TabPage.STOCK_OUT },
                    text = { Text("出库") })
            }

            when (selectedTab) {
                TabPage.STOCK_IN -> StockInScreen(
                    scanning = scanning,
                    viewModel = inventoryViewModel
                )

                TabPage.INVENTORY -> {
                    Log.e("GUI", "Inventory")
                    InventoryScreen(
                        scanning = scanning,
                        viewModel = inventoryViewModel
                    )
                }

                TabPage.STOCK_OUT -> StockOutScreen(
                    scanning = scanning,
                    viewModel = inventoryViewModel
                )
            }
        }
    }
}

@Composable
fun StockInScreen(
    scanning: Boolean,
    viewModel: InventoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val notInDbMap by viewModel.hitItemNotInDb
    val inDbMap by viewModel.hitItemInDb

    var editingRFID by remember { mutableStateOf<String?>(null) }

    // 如果开始扫描中，立即清除编辑弹窗
    LaunchedEffect(scanning) {
        if (scanning) {
            editingRFID = null
        }
    }

    // 编辑弹窗
    if (!scanning) {
        editingRFID?.let { rfid ->
            var name by remember { mutableStateOf("") }
            var detail by remember { mutableStateOf("") }

            val item = InventoryItem(name = name, rfid = rfid, detail = detail)

            InventoryItemEditDialog(
                item = item,
                onItemChange = {
                    name = it.name
                    detail = it.detail
                },
                onDismissRequest = { editingRFID = null },
                onConfirm = {
                    viewModel.upsert(InventoryItem(name, rfid, detail))
                    editingRFID = null
                },
                onTakePhoto = {
                    // TODO 拍照逻辑
                },
                title = "录入标签"
            )
        }
    }

    // 列表展示：优先 NotInDb（白底可编辑），后 InDb（灰底不可点）
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Not in DB
        if (notInDbMap.isNotEmpty()) {
            item {
                Text(
                    "新标签",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(notInDbMap.entries.toList()) { (rfid, count) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable(enabled = !scanning) {
                            if (!scanning) editingRFID = rfid
                        },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Text("RFID: $rfid", modifier = Modifier.weight(1f))
                        Text("次数: $count")
                    }
                }
            }
        }

        // In DB
        if (inDbMap.isNotEmpty()) {
            item {
                Text(
                    "已存在标签",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(inDbMap.entries.toList()) { (rfid, pair) ->
                val (name, count) = pair
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.LightGray),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row {
                            Text("RFID: $rfid", modifier = Modifier.weight(1f), color = Color.DarkGray)
                            Text("次数: $count", color = Color.DarkGray)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("名称: $name", color = Color.DarkGray)
                    }
                }
            }
        }
    }
}


fun onStartScan(tab: TabPage, viewModel: InventoryViewModel) {

    when (tab) {
        TabPage.STOCK_IN -> {
            viewModel.clearHitItems()
            RFIDScanner.initDevice()
            RFIDScanner.setRFPwr(23)
            RFIDScanner.queryRFPwr()
            RFIDScanner.startScan { _, rfid ->
                Log.d("RFID", rfid)
                viewModel.hitRFID(rfid)
            }
        }

        TabPage.INVENTORY -> {}
        TabPage.STOCK_OUT -> {
            viewModel.clearHitItems()
            RFIDScanner.initDevice()
            RFIDScanner.setRFPwr(23)
            RFIDScanner.queryRFPwr()
            RFIDScanner.startScan { _, rfid ->
                Log.d("RFID", rfid)
                viewModel.hitRFID(rfid)
            }
        }
    }
}

fun onStopScan(tab: TabPage, viewModel: InventoryViewModel) {
    Log.d("RFID", "Stop Scan clicked")
    RFIDScanner.stopScan()
    RFIDScanner.closeDevice()
}


@Composable
fun InventoryScreen(
    scanning: Boolean,
    viewModel: InventoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    Log.e("GUI", "Inventory1")

    val items by viewModel.allItems.collectAsState(initial = emptyList())
    for (i in items){
        Log.e("DB", i.rfid)
    }
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var editingCopy by remember { mutableStateOf<InventoryItem?>(null) }

    if (editingItem != null && editingCopy != null) {
        InventoryItemEditDialog(
            item = editingCopy!!,
            onItemChange = { editingCopy = it },
            onDismissRequest = {
                editingItem = null
                editingCopy = null
            },
            onConfirm = {
                viewModel.upsert(editingCopy!!)
                editingItem = null
                editingCopy = null
            },
            onTakePhoto = {
                // TODO 拍照逻辑
            },
            title = "编辑物品"
        )
    }

    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无标签数据")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable {
                            editingItem = item
                            editingCopy = item.copy()
                        },
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("RFID: ${item.rfid}", style = MaterialTheme.typography.bodyMedium)
                        Text("名称: ${item.name}", style = MaterialTheme.typography.bodySmall)
                        Text("详情: ${item.detail}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun StockOutScreen(
    scanning: Boolean,
    viewModel: InventoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val notInDbMap by viewModel.hitItemNotInDb
    val inDbMap by viewModel.hitItemInDb

    var editingRFID by remember { mutableStateOf<String?>(null) }

    // 如果正在扫描，清除弹窗
    LaunchedEffect(scanning) {
        if (scanning) editingRFID = null
    }

    // 弹窗（只对已存在标签生效）
    if (!scanning) {
        editingRFID?.let { rfid ->
            val (name, _) = inDbMap[rfid] ?: return@let
            AlertDialog(
                onDismissRequest = { editingRFID = null },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeRFID(rfid)
                        editingRFID = null
                    }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { editingRFID = null }) {
                        Text("取消")
                    }
                },
                title = { Text("出库标签") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("RFID：$rfid")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("名称：$name")
                    }
                }
            )
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 已存在标签（可点击出库）
        if (inDbMap.isNotEmpty()) {
            item {
                Text(
                    "可出库标签",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(inDbMap.entries.toList()) { (rfid, pair) ->
                val (name, count) = pair
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable(enabled = !scanning) {
                            if (!scanning) editingRFID = rfid
                        },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row {
                            Text("RFID: $rfid", modifier = Modifier.weight(1f))
                            Text("次数: $count")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("名称: $name")
                    }
                }
            }
        }

        // 不在库标签（不可点击）
        if (notInDbMap.isNotEmpty()) {
            item {
                Text(
                    "未知标签",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(notInDbMap.entries.toList()) { (rfid, count) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.LightGray),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Text("RFID: $rfid", modifier = Modifier.weight(1f), color = Color.DarkGray)
                        Text("次数: $count", color = Color.DarkGray)
                    }
                }
            }
        }
    }
}


@Composable
fun InventoryItemEditDialog(
    item: InventoryItem,
    onItemChange: (InventoryItem) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onTakePhoto: () -> Unit,
    title: String = "编辑物品"
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = item.name.isNotBlank()
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("取消") }
        },
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = item.rfid,
                    onValueChange = { /* 不可编辑，空实现 */ },
                    label = { Text("RFID") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true
                )
                OutlinedTextField(
                    value = item.name,
                    onValueChange = { onItemChange(item.copy(name = it)) },
                    label = { Text("物品名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = item.detail,
                    onValueChange = { onItemChange(item.copy(detail = it)) },
                    label = { Text("详细信息") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("照片占位（未来添加）")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onTakePhoto) {
                    Text("拍照")
                }
            }
        }
    )
}
