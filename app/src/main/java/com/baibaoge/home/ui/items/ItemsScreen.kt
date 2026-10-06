package com.baibaoge.home.ui.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.data.entity.ItemEntity

/**
 * 物品页：顶部分类 Tab（食品/药品/衣物/其他）+ 名称搜索 + 标签筛选。
 * initialType 用于首页统计卡片点击后直接定位到对应分类。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(
    initialType: Int = ItemEntity.TYPE_FOOD,
    onItemClick: (String) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val itemDao = remember { db.itemDao() }
    val locationDao = remember { db.locationDao() }

    val types = listOf(
        ItemEntity.TYPE_FOOD to "食品",
        ItemEntity.TYPE_MEDICINE to "药品",
        ItemEntity.TYPE_CLOTHING to "衣物",
        ItemEntity.TYPE_OTHER to "其他"
    )
    val initialIndex = types.indexOfFirst { it.first == initialType }.coerceAtLeast(0)
    // initialType 变化（从首页点分类卡片）时重置 Tab；用户手动切换不受影响
    var selectedTab by rememberSaveable(initialType) { mutableIntStateOf(initialIndex) }
    var keyword by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedType = types[selectedTab].first
    val rawItems by remember(selectedType, keyword) {
        if (keyword.isBlank()) itemDao.observeInStockByType(selectedType)
        else itemDao.searchInStockByType(selectedType, keyword)
    }.collectAsState(initial = emptyList())

    val locations by locationDao.observeAll().collectAsState(initial = emptyList())
    val locationNames = remember(locations) { locations.associate { it.locationId to it.locationName } }

    // 当前分类下出现过的全部自定义标签
    val allTags = remember(rawItems) {
        rawItems.flatMap { parseTags(it.tags) }.distinct()
    }
    // 切换分类/搜索后，失效的选中标签自动清空
    if (selectedTag != null && selectedTag !in allTags) selectedTag = null
    val items = remember(rawItems, selectedTag) {
        if (selectedTag == null) rawItems
        else rawItems.filter { parseTags(it.tags).contains(selectedTag) }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "物品",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            types.forEachIndexed { index, (_, label) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(label) }
                )
            }
        }
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text("搜索名称") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true
        )
        if (allTags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { selectedTag = if (selectedTag == tag) null else tag },
                        label = { Text(tag) }
                    )
                }
            }
        }
        if (items.isEmpty()) {
            Text(
                when {
                    selectedTag != null -> "标签「$selectedTag」下暂无物品"
                    keyword.isNotBlank() -> "没有匹配「$keyword」的物品"
                    else -> "暂无物品，点击底部 ⊕ 新增"
                },
                modifier = Modifier.padding(32.dp)
            )
        } else {
            LazyColumn {
                items(items, key = { it.itemId }) { item ->
                    ItemCard(
                        item = item,
                        locationName = locationNames[item.locationId],
                        onClick = { onItemClick(item.itemId) }
                    )
                }
            }
        }
    }
}
