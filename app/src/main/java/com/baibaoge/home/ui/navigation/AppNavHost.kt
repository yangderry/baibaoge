package com.baibaoge.home.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.ui.items.ItemDetailScreen
import com.baibaoge.home.ui.items.ItemEditScreen
import com.baibaoge.home.ui.locations.BatchPrintQrScreen
import com.baibaoge.home.ui.locations.LocationItemsScreen
import com.baibaoge.home.ui.main.MainScreen
import com.baibaoge.home.ui.mine.ArchivesScreen
import com.baibaoge.home.ui.mine.SettingsScreen
import com.baibaoge.home.ui.mine.SyncLogScreen
import com.baibaoge.home.ui.mine.SyncScreen
import com.baibaoge.home.ui.ocr.OcrCaptureScreen
import com.baibaoge.home.ui.scan.ScanScreen
import kotlinx.coroutines.launch

/** 新增物品预填数据（扫码/拍照 OCR/语音录入传入，用户在表单中确认后才落库） */
data class ItemPrefill(
    val name: String? = null,
    val barcode: String? = null,
    val produceDate: Long = 0L,
    val expiryDate: Long = 0L,
    val photoPath: String? = null
)

/** 应用导航路由 */
object Routes {
    const val MAIN = "main"
    const val ITEM_DETAIL = "item_detail/{itemId}"
    const val ITEM_EDIT =
        "item_edit?itemId={itemId}&name={name}&barcode={barcode}&produceDate={produceDate}&expiryDate={expiryDate}&photoPath={photoPath}"
    const val ARCHIVES = "archives"
    const val SCAN = "scan"
    const val OCR = "ocr_capture"
    const val LOCATION_ITEMS = "location_items/{locationId}"
    const val BATCH_PRINT_QR = "batch_print_qr"
    const val SETTINGS = "settings"
    const val SYNC = "sync"
    const val SYNC_LOG = "sync_log"

    fun itemDetail(itemId: String) = "item_detail/$itemId"
    fun locationItems(locationId: String) = "location_items/$locationId"

    fun itemEdit(itemId: String? = null, prefill: ItemPrefill? = null): String {
        val params = mutableListOf<String>()
        if (!itemId.isNullOrBlank()) params += "itemId=${Uri.encode(itemId)}"
        prefill?.let { p ->
            p.name?.takeIf { it.isNotBlank() }?.let { params += "name=${Uri.encode(it)}" }
            p.barcode?.takeIf { it.isNotBlank() }?.let { params += "barcode=${Uri.encode(it)}" }
            if (p.produceDate > 0) params += "produceDate=${p.produceDate}"
            if (p.expiryDate > 0) params += "expiryDate=${p.expiryDate}"
            p.photoPath?.takeIf { it.isNotBlank() }?.let { params += "photoPath=${Uri.encode(it)}" }
        }
        return if (params.isEmpty()) "item_edit" else "item_edit?${params.joinToString("&")}"
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                onNavigateToDetail = { id -> navController.navigate(Routes.itemDetail(id)) },
                onNavigateToEdit = { id, prefill ->
                    navController.navigate(Routes.itemEdit(id, prefill))
                },
                onNavigateToArchives = { navController.navigate(Routes.ARCHIVES) },
                onNavigateScan = { navController.navigate(Routes.SCAN) },
                onNavigateOcr = { navController.navigate(Routes.OCR) },
                onNavigateLocationItems = { id -> navController.navigate(Routes.locationItems(id)) },
                onNavigateBatchPrint = { navController.navigate(Routes.BATCH_PRINT_QR) },
                onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateSync = { navController.navigate(Routes.SYNC) }
            )
        }
        composable(
            Routes.ITEM_DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            // 通知点击深链：baibaoge://item/{itemId}
            deepLinks = listOf(navDeepLink { uriPattern = "baibaoge://item/{itemId}" })
        ) { entry ->
            val itemId = entry.arguments?.getString("itemId") ?: return@composable
            ItemDetailScreen(
                itemId = itemId,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Routes.itemEdit(id)) }
            )
        }
        composable(
            Routes.ITEM_EDIT,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType; defaultValue = "" },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                navArgument("produceDate") { type = NavType.LongType; defaultValue = 0L },
                navArgument("expiryDate") { type = NavType.LongType; defaultValue = 0L },
                navArgument("photoPath") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val args = entry.arguments
            val name = args?.getString("name")?.takeIf { it.isNotBlank() }
            val barcode = args?.getString("barcode")?.takeIf { it.isNotBlank() }
            val produceDate = args?.getLong("produceDate") ?: 0L
            val expiryDate = args?.getLong("expiryDate") ?: 0L
            val photoPath = args?.getString("photoPath")?.takeIf { it.isNotBlank() }
            val prefill = if (name != null || barcode != null || produceDate > 0 ||
                expiryDate > 0 || photoPath != null
            ) {
                ItemPrefill(name, barcode, produceDate, expiryDate, photoPath)
            } else null

            ItemEditScreen(
                itemId = args?.getString("itemId")?.takeIf { it.isNotBlank() },
                prefill = prefill,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ARCHIVES) {
            ArchivesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SCAN) {
            val context = LocalContext.current
            val itemDao = remember { AppDatabase.getInstance(context).itemDao() }
            val scope = rememberCoroutineScope()
            ScanScreen(
                onOpenItem = { id -> navController.navigate(Routes.itemDetail(id)) },
                onOpenLocation = { id -> navController.navigate(Routes.locationItems(id)) },
                onBarcode = { code ->
                    // 商品条码：已在库 → 直接定位详情；未录入 → 新增并预填 barcode
                    scope.launch {
                        val found = itemDao.getInStockByBarcode(code)
                        if (found != null) {
                            navController.navigate(Routes.itemDetail(found.itemId))
                        } else {
                            navController.navigate(Routes.itemEdit(null, ItemPrefill(barcode = code)))
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.OCR) {
            OcrCaptureScreen(
                onConfirm = { prefill ->
                    navController.navigate(Routes.itemEdit(null, prefill)) {
                        popUpTo(Routes.MAIN)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.LOCATION_ITEMS,
            arguments = listOf(navArgument("locationId") { type = NavType.StringType })
        ) { entry ->
            val locationId = entry.arguments?.getString("locationId") ?: return@composable
            LocationItemsScreen(
                locationId = locationId,
                onItemClick = { id -> navController.navigate(Routes.itemDetail(id)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.BATCH_PRINT_QR) {
            BatchPrintQrScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SYNC) {
            SyncScreen(
                onBack = { navController.popBackStack() },
                onNavigateLogs = { navController.navigate(Routes.SYNC_LOG) }
            )
        }
        composable(Routes.SYNC_LOG) {
            SyncLogScreen(onBack = { navController.popBackStack() })
        }
    }
}
