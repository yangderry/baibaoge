package com.baibaoge.home.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baibaoge.home.auth.AuthManager
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.data.entity.ItemEntity
import com.baibaoge.home.data.entity.LocationEntity
import com.baibaoge.home.ui.items.ItemsScreen
import com.baibaoge.home.ui.locations.LocationEditDialog
import com.baibaoge.home.ui.locations.LocationsScreen
import com.baibaoge.home.ui.mine.MineScreen
import com.baibaoge.home.ui.navigation.ItemPrefill
import com.baibaoge.home.ui.pages.HomeScreen
import com.baibaoge.home.util.IdGenerator
import com.baibaoge.home.util.QrCodeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 主界面 Tab（设计方案 5.2：底部 5Tab + 中间凸起添加按钮） */
enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Default.Home),
    ITEMS("物品", Icons.AutoMirrored.Filled.List),
    LOCATION("地点", Icons.Default.Place),
    MINE("我的", Icons.Default.Person)
}

/** 导航条本体高度；凸起按钮总占位 = 64 + 28 */
private val NavBarHeight = 64.dp
private val BottomReservedHeight = 92.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToEdit: (String?, ItemPrefill?) -> Unit,
    onNavigateToArchives: () -> Unit,
    onNavigateScan: () -> Unit,
    onNavigateOcr: () -> Unit,
    onNavigateLocationItems: (String) -> Unit,
    onNavigateBatchPrint: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateAbout: () -> Unit,
    onNavigateSync: () -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    // 首页统计卡片点击后，物品页要定位到的分类
    var itemsInitialType by remember { mutableIntStateOf(ItemEntity.TYPE_FOOD) }
    var showAddSheet by remember { mutableStateOf(false) }
    // 地点 Tab 下点击居中 ⊕ → 直接在本层弹出新增地点对话框
    var showAddLocationDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locationDao = remember { AppDatabase.getInstance(context).locationDao() }

    fun toast(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    // 语音录入：系统 RecognizerIntent 离线识别 → 结果填入物品名称
    val auth = remember { AuthManager.getInstance(context) }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        auth.externalActivityInFlight = false
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.takeIf { it.isNotBlank() }
        if (text != null) {
            onNavigateToEdit(null, ItemPrefill(name = text))
        } else {
            toast("未识别到语音内容")
        }
    }

    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出物品名称")
        }
        try {
            auth.externalActivityInFlight = true
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            auth.externalActivityInFlight = false
            toast("当前设备不支持语音识别")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 页面内容，底部预留导航条+凸起按钮的高度，避免遮挡
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = BottomReservedHeight)
            ) {
                when (currentTab) {
                    MainTab.HOME -> HomeScreen(
                        onItemClick = onNavigateToDetail,
                        onGotoItems = { type ->
                            itemsInitialType = type
                            currentTab = MainTab.ITEMS
                        }
                    )
                    MainTab.ITEMS -> ItemsScreen(
                        initialType = itemsInitialType,
                        onItemClick = onNavigateToDetail
                    )
                    MainTab.LOCATION -> LocationsScreen(
                        onLocationClick = onNavigateLocationItems,
                        onBatchPrint = onNavigateBatchPrint
                    )
                    MainTab.MINE -> MineScreen(
                        onNavigateArchives = onNavigateToArchives,
                        onNavigateSync = onNavigateSync,
                        onNavigateSettings = onNavigateSettings,
                        onNavigateAbout = onNavigateAbout
                    )
                }
            }

            // ===== 自定义底部导航（不用 NavigationBar，保证中央凸起按钮触摸可靠） =====
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // 导航条底色
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(NavBarHeight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavTabItem(MainTab.HOME, currentTab == MainTab.HOME) { currentTab = MainTab.HOME }
                        NavTabItem(MainTab.ITEMS, currentTab == MainTab.ITEMS) { currentTab = MainTab.ITEMS }
                        // 中央占位（宽度与一个 Tab 相同，凸起按钮浮在其上方）
                        Spacer(Modifier.weight(1f))
                        NavTabItem(MainTab.LOCATION, currentTab == MainTab.LOCATION) { currentTab = MainTab.LOCATION }
                        NavTabItem(MainTab.MINE, currentTab == MainTab.MINE) { currentTab = MainTab.MINE }
                    }
                }

                // 中央凸起 ⊕：作为本 Box 的独立子节点，正常布局、z 序最高，56dp 完整可点
                if (currentTab != MainTab.MINE) {
                    Surface(
                        onClick = {
                            if (currentTab == MainTab.LOCATION) showAddLocationDialog = true
                            else showAddSheet = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = NavBarHeight - 28.dp)
                            .size(56.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.surface),
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "添加")
                        }
                    }
                }
            }
        }
    }

    // 地点 Tab：新增地点对话框（本层直接持有，点击即弹）；新增后自动生成地点二维码
    if (showAddLocationDialog) {
        LocationEditDialog(
            title = "新增地点",
            onDismiss = { showAddLocationDialog = false },
            onConfirm = { name, photo ->
                scope.launch {
                    val loc = LocationEntity(
                        locationId = IdGenerator.locationId(),
                        locationName = name,
                        photoPath = photo,
                        createTime = System.currentTimeMillis()
                    )
                    val qrPath = withContext(Dispatchers.IO) {
                        QrCodeUtil.generateToFile(
                            context,
                            QrCodeUtil.locationContent(loc.locationId),
                            "${loc.locationId}.png"
                        )
                    }
                    locationDao.insert(loc.copy(qrPath = qrPath))
                    showAddLocationDialog = false
                }
            }
        )
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text(
                    "选择录入方式",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                // 小图标+文字：四等分横排，与「我的」页 emoji 入口风格一致
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AddEntryItem("🔳", "扫码录入") {
                        showAddSheet = false
                        onNavigateScan()
                    }
                    AddEntryItem("📷", "拍照录入") {
                        showAddSheet = false
                        onNavigateOcr()
                    }
                    AddEntryItem("🎤", "语音录入") {
                        showAddSheet = false
                        startVoiceInput()
                    }
                    AddEntryItem("✏️", "手动录入") {
                        showAddSheet = false
                        onNavigateToEdit(null, null)
                    }
                }
            }
        }
    }
}

/** 底部导航项：选中时图标带胶囊高亮底（pill indicator） */
@Composable
private fun RowScope.NavTabItem(tab: MainTab, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 2.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else androidx.compose.ui.graphics.Color.Transparent
                )
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(tab.icon, contentDescription = tab.label, tint = tint)
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else tint
        )
    }
}

/** 录入方式项：淡色圆角方块 + 语义 emoji + 下方文字 */
@Composable
private fun AddEntryItem(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
