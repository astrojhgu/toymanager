package org.uvwstudio.toymanager

import android.os.Bundle
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

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.background

import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.foundation.clickable

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


    Scaffold(
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.height(56.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                content = {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { scanning = true }, enabled = !scanning) {

                        Text("开始扫描")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { scanning = false }, enabled = scanning) {
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
                Tab(selected = selectedTab == TabPage.STOCK_IN, onClick = { selectedTab = TabPage.STOCK_IN }, text = { Text("入库") })
                Tab(selected = selectedTab == TabPage.INVENTORY, onClick = { selectedTab = TabPage.INVENTORY }, text = { Text("盘点/寻找") })
                Tab(selected = selectedTab == TabPage.STOCK_OUT, onClick = { selectedTab = TabPage.STOCK_OUT }, text = { Text("出库") })
            }

            val inventoryViewModel: InventoryViewModel = viewModel()


            when (selectedTab) {
                TabPage.STOCK_IN -> StockInScreen(scanning = scanning, viewModel = inventoryViewModel)
                TabPage.INVENTORY -> InventoryScreen(viewModel = inventoryViewModel)
                TabPage.STOCK_OUT -> StockOutScreen()
            }
        }
    }
}

@Composable
fun StockInScreen(
    scanning: Boolean,
    viewModel: InventoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    // 改为使用 stockInItems，而不是 allItems
    val stockMap by viewModel.stockInItems

    // 编辑功能保留，但这里只能编辑 RFID（没有绑定的详细信息）
    var editingRFID by remember { mutableStateOf<String?>(null) }

    // 启动或停止扫描逻辑
    LaunchedEffect(scanning) {
        if (scanning) {
            viewModel.clearStockInItems()
            RFIDScanner.initDevice()
            RFIDScanner.setRFPwr(23)
            RFIDScanner.queryRFPwr()
            RFIDScanner.startScan { _, rfid ->
                viewModel.hitRFID(rfid)
            }
        } else {
            RFIDScanner.stopScan()
            RFIDScanner.closeDevice()
        }
    }

    // 编辑弹窗，仅允许修改 RFID（示例，真实业务可能不这么做）
    editingRFID?.let { rfid ->
        var name by remember { mutableStateOf("") }
        var detail by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { editingRFID = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.upsert(
                            InventoryItem(
                                name = name,
                                rfid = rfid,
                                detail = detail
                            )
                        )
                        editingRFID = null
                    },
                    enabled = name.isNotBlank() // 名称不能为空
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        editingRFID = null
                    }) { Text("取消") }
                }
            },
            title = { Text("录入标签") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = rfid,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("RFID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("物品名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = detail,
                        onValueChange = { detail = it },
                        label = { Text("详细信息") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("照片预览（占位）")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        // TODO: 添加拍照逻辑
                    }) {
                        Text("拍照")
                    }
                }
            }
        )
    }


    // 主界面：显示扫描到的 RFID 列表
    if (stockMap.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("尚未扫描到任何 RFID 标签")
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            stockMap.entries.forEach { (rfid, count) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { editingRFID = rfid },
                    elevation = CardDefaults.cardElevation()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = rfid)
                        Text(text = "扫描次数: $count")
                    }
                }
            }
        }
    }
}


@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val items by viewModel.allItems.observeAsState(initial = emptyList<InventoryItem>())
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var editingCopy by remember { mutableStateOf<InventoryItem?>(null) }

    if (editingItem != null && editingCopy != null) {
        AlertDialog(
            onDismissRequest = { editingItem = null; editingCopy = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.upsert(editingCopy!!)
                    editingItem = null
                    editingCopy = null
                }) { Text("确认") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { editingCopy = editingItem }) { Text("还原") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        editingItem = null
                        editingCopy = null
                    }) { Text("取消") }
                }
            },
            title = { Text("编辑物品") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = editingCopy!!.rfid,
                        onValueChange = { editingCopy = editingCopy!!.copy(rfid = it) },
                        label = { Text("RFID编号") }
                    )
                    OutlinedTextField(
                        value = editingCopy!!.name,
                        onValueChange = { editingCopy = editingCopy!!.copy(name = it) },
                        label = { Text("物品名称") }
                    )
                    OutlinedTextField(
                        value = editingCopy!!.detail,
                        onValueChange = { editingCopy = editingCopy!!.copy(detail = it) },
                        label = { Text("详细信息") },
                        modifier = Modifier.height(100.dp)
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
                }
            }
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
        AndroidView(
            factory = { ctx ->
                RecyclerView(ctx).apply {
                    layoutManager = LinearLayoutManager(ctx)
                    adapter = InventoryAdapter(items) { clicked ->
                        editingItem = clicked
                        editingCopy = clicked.copy()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun StockOutScreen() {
    // TODO: 出库页面：扫码确认出库，更新数据库状态
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("出库页面内容（待实现）")
    }
}
