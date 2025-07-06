package org.uvwstudio.toymanager

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.foundation.Image
import coil.compose.rememberAsyncImagePainter
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
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

import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.ui.graphics.Color

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File

import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import kotlin.system.exitProcess

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

    private var photoFilePath: String? = null
    private var currentTab: TabPage = TabPage.STOCK_IN
    private lateinit var inventoryViewModel: InventoryViewModel


    // 注册拍照 Launcher
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && photoFilePath != null) {
                Log.d("Photo", "Photo saved at $photoFilePath")
                // TODO: 这里可以通知 ViewModel 或 Compose 层，更新对应物品的 photoPath
                onPhotoTaken?.invoke(photoFilePath!!)
            }
            onPhotoTaken = null
        }

    private var onPhotoTaken: ((String) -> Unit)? = null

    // 创建照片文件
    private fun createImageFile(rfid: String): File {
        val imageDir = File(filesDir, "images")
        if (!imageDir.exists()) imageDir.mkdirs()
        return File(imageDir, "$rfid.png")
    }

    // 公开给 Compose 调用的拍照函数
    fun takePhoto(rfid: String, onResult: (String) -> Unit) {
        val photoFile = createImageFile(rfid)
        photoFilePath = photoFile.absolutePath
        val photoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        onPhotoTaken = onResult
        takePictureLauncher.launch(photoUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inventoryViewModel = InventoryViewModel(application)

        var lastBackPressedTime: Long = 0
        val BACK_PRESS_INTERVAL: Long = 2000 // 双击退出的时间间隔

        // 添加自定义的返回键处理
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentBackPressedTime = System.currentTimeMillis()

                // 如果两次点击在间隔时间内
                if (currentBackPressedTime - lastBackPressedTime < BACK_PRESS_INTERVAL) {
                    // 双击退出
                    isEnabled = false  // 禁用该回调，防止重复触发
                    //onBackPressedDispatcher.onBackPressed()  // 执行正常的返回操作
                    finish()
                } else {
                    // 提示用户再按一次退出
                    Toast.makeText(this@MainActivity, "再按一次退出应用", Toast.LENGTH_SHORT).show()
                    lastBackPressedTime = currentBackPressedTime  // 更新上次按下时间
                }
            }
        })

        setContent {
            ToyManagerAppContent(
                takePhotoCallback = { rfid: String ->
                    takePhoto(rfid) {}
                },
                onTabChanged = { tab -> currentTab = tab },
                inventoryViewModel = inventoryViewModel
            )
        }
    }




    override fun onStart() {
        super.onStart()
        RFIDScanner.initDevice()
    }

    override fun onStop() {
        super.onStop()
        RFIDScanner.closeDevice()
    }


    fun performBackup() {
        try {
            val dbFile = File(getDatabasePath("toymanager.db").absolutePath)
            val imagesDir = File(filesDir, "images")
            val outputZip = File("/sdcard/Download/toy_manager_data.zip")

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZip))).use { zipOut ->
                // 添加数据库
                zipOut.putNextEntry(ZipEntry("toymanager.db"))
                dbFile.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()

                // 添加图片
                imagesDir.listFiles()?.forEach { file ->
                    zipOut.putNextEntry(ZipEntry("images/${file.name}"))
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }

            Toast.makeText(this, "备份完成：${outputZip.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "备份失败: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("BACKUP", "备份失败: ${e.message}")
        }
    }

    fun performRestore() {
        try {
            val zipFile = File("/sdcard/Download/toy_manager_data.zip")
            if (!zipFile.exists()) {
                Toast.makeText(this, "未找到备份文件", Toast.LENGTH_SHORT).show()
                return
            }

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zipIn ->
                var entry: ZipEntry?
                while (zipIn.nextEntry.also { entry = it } != null) {
                    entry?.let { e ->
                        val outFile = if (e.name == "toymanager.db") {
                            getDatabasePath("toymanager.db")
                        } else if (e.name.startsWith("images/")) {
                            File(filesDir, e.name)
                        } else return@let

                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { zipIn.copyTo(it) }
                    }
                }
            }

            Toast.makeText(this, "恢复完成，应用即将重启", Toast.LENGTH_LONG).show()
            Handler(Looper.getMainLooper()).postDelayed({
                val pm = packageManager
                val intent = pm.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                exitProcess(0)
            }, 2000)
        } catch (e: Exception) {
            Toast.makeText(this, "恢复失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    /*
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            Log.d("KeyTest", "Key down: code=${event.keyCode}, scanCode=${event.scanCode}")
        }
        return super.dispatchKeyEvent(event)
    }*/
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToyManagerAppContent(
    takePhotoCallback: (String) -> Unit,
    onTabChanged: (TabPage) -> Unit,
    inventoryViewModel: InventoryViewModel
) {
    var scanning by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(TabPage.STOCK_IN) }

    var showBackupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        onTabChanged(selectedTab)
    }

    //inventoryViewModel = viewModel()
    Scaffold(
        bottomBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var rfPower by remember {
                        mutableStateOf(
                            RFIDScanner.queryRFPwr()!!.getValue(1)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("功率：", modifier = Modifier.padding(end = 4.dp))

                        Slider(
                            value = rfPower.toFloat(),
                            onValueChange = { rfPower = it.toInt() },
                            valueRange = 5f..33f,
                            steps = 28,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val current = RFIDScanner.queryRFPwr()!!.getValue(1)
                                rfPower = current
                                Log.d("RFPower", "查询功率：$current")
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            enabled = !scanning

                        ) {
                            Text("查询")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                RFIDScanner.setRFPwr(rfPower)
                                Log.d("RFPower", "设置功率为：$rfPower")
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            enabled = !scanning
                        ) {
                            Text("设置")
                        }
                    }
                }

                BottomAppBar(
                    modifier = Modifier.height(56.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    content = {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            scanning = true
                            onStartScan(selectedTab, inventoryViewModel)
                        }, enabled = !scanning ) {

                            Text("开始扫描")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            scanning = false
                            onStopScan(selectedTab, inventoryViewModel)
                        }, enabled = scanning ) {
                            Text("停止扫描")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            showBackupDialog = true
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                )
            }
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
                    text = { Text("数据库") })
                Tab(
                    selected = selectedTab == TabPage.STOCK_OUT,
                    onClick = { if (!scanning) selectedTab = TabPage.STOCK_OUT },
                    text = { Text("出库") })
            }


            when (selectedTab) {
                TabPage.STOCK_IN -> StockInScreen(
                    scanning = scanning,
                    viewModel = inventoryViewModel,
                    takePhoto = takePhotoCallback,
                )

                TabPage.INVENTORY -> {
                    Log.e("GUI", "Inventory")
                    InventoryScreen(
                        scanning = scanning,
                        viewModel = inventoryViewModel,
                        takePhoto = takePhotoCallback,
                    )
                }

                TabPage.STOCK_OUT -> StockOutScreen(
                    scanning = scanning,
                    viewModel = inventoryViewModel
                )
            }
        }
    }

    if (showBackupDialog) {

        val context = LocalContext.current
        val activity = context.findActivity() as? MainActivity

        BackupRestoreDialog(
            onDismiss = { showBackupDialog = false },
            onBackup = {
                activity?.performBackup()
                showBackupDialog = false
            },
            onRestore = {
                activity?.performRestore()
                showBackupDialog = false
            }
        )
    }
}

@Composable
fun StockInScreen(
    scanning: Boolean,
    viewModel: InventoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    takePhoto: (String) -> Unit
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

            val context = LocalContext.current
            val activity = context.findActivity() as? MainActivity

            //val item = InventoryItem(name = name, rfid = rfid, detail = detail)
            var editingItem by remember {
                mutableStateOf(
                    InventoryItem(
                        rfid = editingRFID!!,
                        name = "",
                        detail = "",
                        photoPath = null
                    )
                )
            }
            InventoryItemEditDialog(
                item = editingItem,
                onItemChange = { editingItem = it },
                onDismissRequest = {
                    editingRFID = null
                    editingItem = InventoryItem("", "", "") // 或 null 看你逻辑
                },
                onConfirm = {
                    viewModel.upsert(editingItem)
                    editingRFID = null
                    editingItem = InventoryItem("", "", "")
                },
                onTakePhoto = { rfid ->
                    activity?.takePhoto(rfid) { photoPath ->
                        editingItem = editingItem.copy(photoPath = photoPath)
                        viewModel.upsert(editingItem!!)
                    }
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
                            Text(
                                "RFID: $rfid",
                                modifier = Modifier.weight(1f),
                                color = Color.DarkGray
                            )
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
            RFIDScanner.startScan { _, rfid ->
                Log.d("RFID", rfid)
                viewModel.hitRFID(rfid)
            }
        }

        TabPage.INVENTORY -> {
            viewModel.clearHitItems()
            RFIDScanner.startScan { _, rfid ->
                Log.d("RFID", rfid)
                viewModel.hitRFID(rfid)
            }
        }

        TabPage.STOCK_OUT -> {
            viewModel.clearHitItems()
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
}


@Composable
fun InventoryScreen(
    scanning: Boolean,
    viewModel: InventoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    takePhoto: (String) -> Unit,
) {
    Log.e("GUI", "Inventory1")
    val inDbMap by viewModel.hitItemInDb

    val items by viewModel.allItems.collectAsState(initial = emptyList())
    for (i in items) {
        Log.d("DB", i.rfid)
    }
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }

    editingItem?.let { item ->
        val context = LocalContext.current
        val activity = context.findActivity() as? MainActivity
        InventoryItemEditDialog(
            item = item,
            onItemChange = { editingItem = it },
            onDismissRequest = { editingItem = null },
            onConfirm = {
                viewModel.upsert(item)
                editingItem = null
            },
            onTakePhoto = { rfid ->
                activity?.takePhoto(rfid) { photoPath ->
                    editingItem = editingItem?.copy(photoPath = photoPath)
                    viewModel.upsert(editingItem!!)
                }
            },
            showDeleteButton = true,
            onDelete = {
                viewModel.delete(item)
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
                val isHit = inDbMap.containsKey(item.rfid)
                val bgColor = if (isHit) Color(0xFFD0F0C0) else Color.White // 绿色背景表示命中
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable {
                            editingItem = item
                            //editingCopy = item.copy()
                        },
                    colors = CardDefaults.cardColors(containerColor = bgColor),
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
    onTakePhoto: (String) -> Unit,
    title: String = "编辑物品",
    showDeleteButton: Boolean = false,
    onDelete: (() -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 删除确认弹窗
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除该物品吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete?.invoke()
                    showDeleteConfirm = false
                    onDismissRequest()
                }) {
                    Text("确认", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 主编辑弹窗
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
            Row {
                TextButton(onClick = onDismissRequest) {
                    Text("取消")
                }

                if (showDeleteButton && onDelete != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = item.rfid,
                    onValueChange = {},
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
                    val imagePath = item.photoPath
                    if (imagePath != null && File(imagePath).exists()) {
                        Image(
                            painter = rememberAsyncImagePainter(File(imagePath)),
                            contentDescription = "照片预览",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("照片占位（未来添加）")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { onTakePhoto(item.rfid) }) {
                    Text("拍照")
                }
            }
        }
    )
}


@Composable
fun BackupRestoreDialog(
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("数据管理") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("你可以备份或恢复 ToyManager 的数据库和图片文件。")
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onBackup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("备份")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRestore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("恢复")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

