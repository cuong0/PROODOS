package com.example.ui
import androidx.compose.animation.AnimatedVisibility

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.example.data.*
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

// Formatter to match 1.000đ requirement
fun formatCurrency(value: Double): String {
    return try {
        val symbols = DecimalFormatSymbols(Locale("vi", "VN")).apply {
            groupingSeparator = '.'
        }
        val formatter = DecimalFormat("#,###", symbols)
        "${formatter.format(value)}đ"
    } catch (e: Exception) {
        "${value.toInt()}đ"
    }
}

fun removeVietnameseAccents(str: String): String {
    val temp = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
    val pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
    var normalized = pattern.matcher(temp).replaceAll("")
    normalized = normalized.replace('đ', 'd')
    normalized = normalized.replace('Đ', 'D')
    return normalized.lowercase()
}

fun getRelevanceScore(target: String, query: String): Double? {
    val t = removeVietnameseAccents(target)
    val q = removeVietnameseAccents(query).trim()
    if (q.isEmpty()) return 0.0

    val qWords = q.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    val tWords = t.split("\\s+".toRegex()).filter { it.isNotEmpty() }

    if (qWords.isEmpty()) return 0.0

    var matchCount = 0
    var score = 0.0

    for (qw in qWords) {
        var found = false
        for ((index, tw) in tWords.withIndex()) {
            if (tw.startsWith(qw)) {
                score += 10.0 / (index + 1)
                if (tw == qw) {
                    score += 5.0
                }
                found = true
            } else if (tw.contains(qw)) {
                score += 5.0 / (index + 1)
                found = true
            }
        }
        if (!found) {
            if (t.contains(qw)) {
                score += 2.0
                found = true
            }
        }
        if (found) {
            matchCount++
        }
    }

    if (matchCount < qWords.size) {
        return null
    }

    if (t.contains(q)) {
        score += 20.0
        if (t.startsWith(q)) {
            score += 30.0
        }
    }

    return score
}

fun formatDotsInput(input: String): String {
    val clean = input.replace(".", "").replace(",", "").filter { it.isDigit() }
    if (clean.isEmpty()) return ""
    return try {
        val parsed = clean.toDouble()
        val symbols = DecimalFormatSymbols(Locale("vi", "VN")).apply {
            groupingSeparator = '.'
        }
        val formatter = DecimalFormat("#,###", symbols)
        formatter.format(parsed)
    } catch (e: Exception) {
        clean
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))
    return sdf.format(Date(timestamp))
}

class ThousandsSeparatorVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        
        val formatted = java.lang.StringBuilder()
        val len = originalText.length
        for (i in 0 until len) {
            formatted.append(originalText[i])
            if ((len - 1 - i) % 3 == 0 && i != len - 1) {
                formatted.append('.')
            }
        }
        
        val transformedString = formatted.toString()
        
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                if (offset > len) return transformedString.length
                
                var dotsCount = 0
                for (i in 0 until offset) {
                    if ((len - 1 - i) % 3 == 0 && i != len - 1) {
                        dotsCount++
                    }
                }
                return offset + dotsCount
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                if (offset > transformedString.length) return len
                
                var originalOffset = 0
                var transformedIndex = 0
                while (transformedIndex < offset && originalOffset < len) {
                    if (transformedString[transformedIndex] == '.') {
                        transformedIndex++
                    } else {
                        transformedIndex++
                        originalOffset++
                    }
                }
                return originalOffset
            }
        }
        
        return TransformedText(
            AnnotatedString(transformedString),
            offsetMapping
        )
    }
}

fun formatDateHeader(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MM, yyyy", Locale("vi", "VN"))
    val parts = sdf.format(Date(timestamp)).split(" ")
    return "${parts[0]} ${"Tháng".t()} ${parts[1]}"
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val subScreen by viewModel.currentSubScreen.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()

    LaunchedEffect(currentLang) {
        Translator.currentLanguageState.value = currentLang
    }

    // Request permissions instantly on startup
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        val galleryGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
        } else {
            (permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false) &&
            (permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false)
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    MyApplicationTheme(darkTheme = isDark) {
        val backupProgress by viewModel.backupProgress.collectAsStateWithLifecycle()
        var showBackupExitWarningDialog by remember { mutableStateOf(false) }

        if (backupProgress != null && subScreen == null) {
            BackHandler {
                showBackupExitWarningDialog = true
            }
        }

        if (showBackupExitWarningDialog) {
            val progressPct = ((backupProgress ?: 0f) * 100).toInt()
            AlertDialog(
                onDismissRequest = { showBackupExitWarningDialog = false },
                title = {
                    Text(
                        text = "Cảnh báo sao lưu".t(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        text = "Ứng dụng đang thực hiện sao lưu dữ liệu ($progressPct%). Quá trình này sẽ tiếp tục chạy ngầm an toàn ngay cả khi bạn ra ngoài màn hình chính, khóa điện thoại hoặc thoát ứng dụng.\n\nBạn có muốn ẩn ứng dụng để sao lưu chạy nền không?".t(),
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBackupExitWarningDialog = false
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                    addCategory(android.content.Intent.CATEGORY_HOME)
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Chạy dưới nền".t())
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                showBackupExitWarningDialog = false
                                (context as? android.app.Activity)?.finish()
                            }
                        ) {
                            Text(
                                text = "Thoát hoàn toàn".t(),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { showBackupExitWarningDialog = false }
                        ) {
                            Text(text = "Ở lại".t())
                        }
                    }
                }
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { focusManager.clearFocus() }
                    )
                },
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                bottomBar = {
                    if (subScreen == null) {
                        BottomBar(
                            currentTab = currentTab,
                            onTabSelected = { viewModel.selectTab(it) }
                        )
                    }
                }
            ) { innerPadding ->
                val layoutDirection = LocalLayoutDirection.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = innerPadding.calculateLeftPadding(layoutDirection),
                            end = innerPadding.calculateRightPadding(layoutDirection),
                            bottom = innerPadding.calculateBottomPadding(),
                            top = if (subScreen != null) 0.dp else innerPadding.calculateTopPadding()
                        )
                ) {
                    if (subScreen != null) {
                        BackHandler {
                            when (subScreen) {
                                SubScreen.CREATE_PRODUCT, SubScreen.EDIT_PRODUCT -> {
                                    viewModel.selectSubScreen(SubScreen.PRODUCTS_LIST)
                                }
                                SubScreen.CREATE_CATEGORY -> {
                                    viewModel.selectSubScreen(SubScreen.CATEGORIES_LIST)
                                }
                                else -> {
                                    viewModel.selectSubScreen(null)
                                }
                            }
                        }
                                                // SubScreens routing overlay
                        when (subScreen) {
                            SubScreen.CART -> CartScreen(viewModel)
                            SubScreen.PRODUCTS_LIST -> ProductsListScreen(viewModel)
                            SubScreen.CATEGORIES_LIST -> CategoriesListScreen(viewModel)
                            SubScreen.CREATE_PRODUCT -> CreateProductScreen(viewModel)
                            SubScreen.CREATE_CATEGORY -> CreateCategoryScreen(viewModel)
                            SubScreen.INVOICE_DETAIL -> InvoiceDetailScreen(viewModel)
                            SubScreen.EDIT_PRODUCT -> EditProductScreen(viewModel)
                            SubScreen.CUSTOMERS_LIST -> CustomersListScreen(viewModel)
                            SubScreen.INVENTORY_MANAGEMENT -> InventoryManagementScreen(viewModel)
                            SubScreen.DEBT_MANAGEMENT -> DebtManagementScreen(viewModel)
                            SubScreen.IMPORT_MANAGEMENT -> ImportManagementScreen(viewModel)
                            else -> {}
                        }
                    } else {
                        // Main Tabs routing
                        when (currentTab) {
                            MainTab.BAN_HANG -> PosTab(viewModel)
                            MainTab.HOA_DON -> InvoicesTab(viewModel)
                            MainTab.QUAN_LY -> ManagementTab(viewModel)
                            MainTab.CAI_DAT -> SettingsTab(viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomBar(currentTab: MainTab, onTabSelected: (MainTab) -> Unit) {
    NavigationBar(
        modifier = Modifier.testTag("bottom_nav_bar"),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = currentTab == MainTab.BAN_HANG,
            onClick = { onTabSelected(MainTab.BAN_HANG) },
            icon = { Icon(Icons.Default.Storefront, contentDescription = "Bán hàng".t()) },
            label = { Text("Bán hàng".t(), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onBackground,
                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                unselectedIconColor = MaterialTheme.colorScheme.secondary,
                unselectedTextColor = MaterialTheme.colorScheme.secondary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        NavigationBarItem(
            selected = currentTab == MainTab.HOA_DON,
            onClick = { onTabSelected(MainTab.HOA_DON) },
            icon = { Icon(Icons.Outlined.ReceiptLong, contentDescription = "Hóa đơn".t()) },
            label = { Text("Hóa đơn".t(), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onBackground,
                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                unselectedIconColor = MaterialTheme.colorScheme.secondary,
                unselectedTextColor = MaterialTheme.colorScheme.secondary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        NavigationBarItem(
            selected = currentTab == MainTab.QUAN_LY,
            onClick = { onTabSelected(MainTab.QUAN_LY) },
            icon = { Icon(Icons.Default.Category, contentDescription = "Quản lý".t()) },
            label = { Text("Quản lý".t(), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onBackground,
                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                unselectedIconColor = MaterialTheme.colorScheme.secondary,
                unselectedTextColor = MaterialTheme.colorScheme.secondary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        NavigationBarItem(
            selected = currentTab == MainTab.CAI_DAT,
            onClick = { onTabSelected(MainTab.CAI_DAT) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Cài đặt".t()) },
            label = { Text("Cài đặt".t(), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onBackground,
                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                unselectedIconColor = MaterialTheme.colorScheme.secondary,
                unselectedTextColor = MaterialTheme.colorScheme.secondary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

// ======================== TABS IMPLEMENTATION ========================

// 1. Tab "Bán Hàng" (POS)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val products by viewModel.products.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isGrid by viewModel.isGridView.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()

    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeZoomImageUri by remember { mutableStateOf<String?>(null) }

    val gridScrollPosition = viewModel.getScrollPosition("pos_tab_grid")
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
        initialFirstVisibleItemIndex = gridScrollPosition.first,
        initialFirstVisibleItemScrollOffset = gridScrollPosition.second
    )
    LaunchedEffect(gridState) {
        snapshotFlow { Pair(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                viewModel.saveScrollPosition("pos_tab_grid", index, offset)
            }
    }

    val listScrollPosition = viewModel.getScrollPosition("pos_tab_list")
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = listScrollPosition.first,
        initialFirstVisibleItemScrollOffset = listScrollPosition.second
    )
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                viewModel.saveScrollPosition("pos_tab_list", index, offset)
            }
    }

    activeZoomImageUri?.let { uri ->
        ZoomableImageDialog(imageUri = uri, onDismiss = { activeZoomImageUri = null })
    }

    val filteredProducts = remember(products, categories, selectedCategoryId, searchQuery) {
        val categoryMap = categories.associateBy { it.id }
        if (searchQuery.isBlank()) {
            products.filter {
                val matchesCategory = selectedCategoryId == null || it.categoryId == selectedCategoryId
                val isAvailable = !it.trackInventory || it.stockQuantity > 0
                matchesCategory && isAvailable
            }.sortedWith(
                compareBy<com.example.data.Product> { prod ->
                    val cat = prod.categoryId?.let { categoryMap[it] }
                    cat?.name?.lowercase() ?: "zzz_khong_co_danh_muc"
                }.thenBy { it.name.lowercase() }
            )
        } else {
            products.mapNotNull { prod ->
                val matchesCategory = selectedCategoryId == null || prod.categoryId == selectedCategoryId
                val isAvailable = !prod.trackInventory || prod.stockQuantity > 0
                if (matchesCategory && isAvailable) {
                    val score = getRelevanceScore(prod.name, searchQuery)
                    if (score != null) prod to score else null
                } else null
            }.sortedWith(
                compareByDescending<Pair<com.example.data.Product, Double>> { it.second }
                    .thenBy { prodPair ->
                        val cat = prodPair.first.categoryId?.let { categoryMap[it] }
                        cat?.name?.lowercase() ?: "zzz_khong_co_danh_muc"
                    }.thenBy { it.first.name.lowercase() }
            ).map { it.first }
        }
    }

    val ScrollableHeader = @Composable { paddingHorizontal: androidx.compose.ui.unit.Dp ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(8.dp))
            // Search product text field styled
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Tìm kiếm sản phẩm...".t(), color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = paddingHorizontal)
                    .height(52.dp)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .testTag("pos_search_input"),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
            )

            // Custom Capsule Category Selector Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentPadding = PaddingValues(horizontal = paddingHorizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val isSelected = selectedCategoryId == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                            .clickable { selectedCategoryId = null }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tất cả".t(),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items(categories) { cat ->
                    val isSelected = selectedCategoryId == cat.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                            .clickable { selectedCategoryId = cat.id }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat.name,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    val EmptyProductsView = @Composable {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 60.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Không tìm thấy sản phẩm nào".t(),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        // App Top Header (Always Visible)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Proodos",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Phiên bản 1.3.9".t(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }

            // Tactile Shopping Cart icon customized
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { viewModel.selectSubScreen(SubScreen.CART) }
                    .testTag("view_cart_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = "Giỏ hàng",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
                if (cart.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cart.sumOf { it.quantity }.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (isGrid) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ScrollableHeader(10.dp)
                    }
                    if (filteredProducts.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyProductsView()
                        }
                    } else {
                        items(filteredProducts) { item ->
                            ProductGridCard(
                                product = item,
                                cartQuantity = cart.find { it.product.id == item.id }?.quantity ?: 0,
                                onClick = {
                                    val added = viewModel.addToCart(item)
                                    if (added) {
                                        ToastUtils.show(context, "Đã thêm ${item.name}".t())
                                    } else {
                                        ToastUtils.show(context, "Không thể thêm! Vượt quá số lượng hàng tồn: ${item.stockQuantity}".t())
                                    }
                                },
                                onThumbnailClick = { uri ->
                                    activeZoomImageUri = uri
                                }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        ScrollableHeader(8.dp)
                    }
                    if (filteredProducts.isEmpty()) {
                        item {
                            EmptyProductsView()
                        }
                    } else {
                        items(filteredProducts) { item ->
                            ProductListCard(
                                product = item,
                                cartQuantity = cart.find { it.product.id == item.id }?.quantity ?: 0,
                                onClick = {
                                    val added = viewModel.addToCart(item)
                                    if (added) {
                                        ToastUtils.show(context, "Đã thêm ${item.name}".t())
                                    } else {
                                        ToastUtils.show(context, "Không thể thêm! Vượt quá số lượng hàng tồn: ${item.stockQuantity}".t())
                                    }
                                },
                                onThumbnailClick = { uri ->
                                    activeZoomImageUri = uri
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProductGridCard(
    product: Product,
    cartQuantity: Int = 0,
    onClick: () -> Unit,
    onThumbnailClick: ((String) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("product_grid_${product.id}"),
        shape = RoundedCornerShape(24.dp), // rounded-3xl translation
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant), // Herbal/neutral border
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat styling per modern HTML spec
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Image Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clip(RoundedCornerShape(16.dp)) // rounded-2xl
                    .background(MaterialTheme.colorScheme.surfaceVariant) // Light warm-grey background
                    .then(
                        if (!product.imageUri.isNullOrEmpty() && onThumbnailClick != null) {
                            Modifier.clickable { onThumbnailClick(product.imageUri) }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!product.imageUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = Uri.parse(product.imageUri),
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.ShoppingBag,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary // Natural olive green
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = product.name,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary, // Muted herbal-slate
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Sell price: large font top
            Text(
                text = formatCurrency(product.sellPrice),
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground, // Dark obsidian olive
                fontWeight = FontWeight.Bold
            )

            // Cost price: smaller below
            Text(
                text = "Vốn: ".t() + formatCurrency(product.importPrice),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Inventory badge
            val remainingStock = product.stockQuantity - cartQuantity
            val stockText = if (product.trackInventory) "Tồn: ".t() + remainingStock else "Tồn: ".t() + "∞"
            val isOutOfStock = product.trackInventory && remainingStock <= 0
            val isLowStock = product.trackInventory && remainingStock <= 5

            val (bgStockColor, textStockColor) = when {
                isOutOfStock -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer // Soft red
                isLowStock -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer  // Soft yellow
                else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer        // Soft green
            }

            Box(
                modifier = Modifier
                    .background(bgStockColor, RoundedCornerShape(99.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = stockText,
                    fontSize = 10.sp,
                    color = textStockColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ProductListCard(
    product: Product,
    cartQuantity: Int = 0,
    onClick: () -> Unit,
    onThumbnailClick: ((String) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("product_list_${product.id}"),
        shape = RoundedCornerShape(20.dp), // Higher rounding for list cards
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant), // Natural border line
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (!product.imageUri.isNullOrEmpty() && onThumbnailClick != null) {
                            Modifier.clickable { onThumbnailClick(product.imageUri) }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!product.imageUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = Uri.parse(product.imageUri),
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.ShoppingBag,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = formatCurrency(product.sellPrice),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Vốn: ".t() + formatCurrency(product.importPrice),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                        )
                    }

                    val remainingStock = product.stockQuantity - cartQuantity
                    val stockText = if (product.trackInventory) "Tồn: ".t() + remainingStock else "Tồn: ".t() + "∞"
                    val isOutOfStock = product.trackInventory && remainingStock <= 0
                    val isLowStock = product.trackInventory && remainingStock <= 5

                    val (bgStockColor, textStockColor) = when {
                        isOutOfStock -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                        isLowStock -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                    }

                    Box(
                        modifier = Modifier
                            .background(bgStockColor, RoundedCornerShape(99.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stockText,
                            fontSize = 10.sp,
                            color = textStockColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// 2. Tab "Hóa Đơn" (History)
@Composable
fun InvoicesTab(viewModel: MainViewModel) {
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()

    val scrollPosition = viewModel.getScrollPosition("invoices_tab")
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollPosition.first,
        initialFirstVisibleItemScrollOffset = scrollPosition.second
    )
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                viewModel.saveScrollPosition("invoices_tab", index, offset)
            }
    }

    var showReportPanel by remember { mutableStateOf(false) }

    var fromDateMillis by remember {
        mutableStateOf(java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis)
    }
    
    var toDateMillis by remember {
        mutableStateOf(java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.timeInMillis)
    }

    var confirmedDateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lịch sử hóa đơn".t(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    TextButton(
                        onClick = { showReportPanel = !showReportPanel },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("btn_lap_bao_cao")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Lập báo cáo".t(), fontSize = 14.sp)
                    }
                }
                
                if (showReportPanel) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val context = LocalContext.current
                        val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("vi", "VN")) }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // "Từ ngày" Button
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Từ ngày".t(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                OutlinedButton(
                                    onClick = {
                                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = fromDateMillis }
                                        android.app.DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                val newCal = java.util.Calendar.getInstance().apply {
                                                    set(java.util.Calendar.YEAR, year)
                                                    set(java.util.Calendar.MONTH, month)
                                                    set(java.util.Calendar.DAY_OF_MONTH, day)
                                                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                                                    set(java.util.Calendar.MINUTE, 0)
                                                    set(java.util.Calendar.SECOND, 0)
                                                    set(java.util.Calendar.MILLISECOND, 0)
                                                }
                                                fromDateMillis = newCal.timeInMillis
                                            },
                                            cal.get(java.util.Calendar.YEAR),
                                            cal.get(java.util.Calendar.MONTH),
                                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                                        ).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = dateFormat.format(java.util.Date(fromDateMillis)),
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            
                            // "Đến ngày" Button
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Đến ngày".t(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                OutlinedButton(
                                    onClick = {
                                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = toDateMillis }
                                        android.app.DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                val newCal = java.util.Calendar.getInstance().apply {
                                                    set(java.util.Calendar.YEAR, year)
                                                    set(java.util.Calendar.MONTH, month)
                                                    set(java.util.Calendar.DAY_OF_MONTH, day)
                                                    set(java.util.Calendar.HOUR_OF_DAY, 23)
                                                    set(java.util.Calendar.MINUTE, 59)
                                                    set(java.util.Calendar.SECOND, 59)
                                                    set(java.util.Calendar.MILLISECOND, 999)
                                                }
                                                toDateMillis = newCal.timeInMillis
                                            },
                                            cal.get(java.util.Calendar.YEAR),
                                            cal.get(java.util.Calendar.MONTH),
                                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                                        ).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = dateFormat.format(java.util.Date(toDateMillis)),
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        
                        // Search Button
                        Button(
                            onClick = {
                                confirmedDateRange = Pair(fromDateMillis, toDateMillis)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_report_search"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Tìm kiếm".t(), fontSize = 14.sp)
                        }
                        
                        // Report Results
                        val contextPrefs = LocalContext.current
                        val prefs = remember(contextPrefs) { contextPrefs.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE) }
                        
                        val reportStats = remember(confirmedDateRange, invoices) {
                            val range = confirmedDateRange
                            if (range == null) null
                            else {
                                val (start, end) = range
                                val filtered = invoices.filter { it.invoice.timestamp in start..end }
                                val totalOrders = filtered.size
                                val totalAmount = filtered.sumOf { it.invoice.totalAmount }
                                val totalProfit = filtered.sumOf { invoiceWithItems ->
                                    val invId = invoiceWithItems.invoice.id
                                    val isCostActive = prefs.getBoolean("invoice_cost_active_$invId", false)
                                    val costAmountStr = prefs.getString("invoice_cost_amount_$invId", "") ?: ""
                                    val costAmount = if (isCostActive) (costAmountStr.toDoubleOrNull() ?: 0.0) else 0.0
                                    invoiceWithItems.invoice.profit - costAmount
                                }
                                Triple(totalOrders, totalAmount, totalProfit)
                            }
                        }
                        
                        reportStats?.let { (orders, amount, profit) ->
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            
                            Text(
                                text = "Kết quả báo cáo".t(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Card 1: Total Orders
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Tổng đơn hàng".t(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "$orders",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                
                                // Card 2: Total Amount
                                Card(
                                    modifier = Modifier.weight(1.2f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Tổng doanh thu".t(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = formatCurrency(amount),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                
                                // Card 3: Total Profit
                                val profitBgColor = if (profit >= 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                val profitTextColor = if (profit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                val profitLabel = if (profit >= 0) "Tổng lãi".t() else "Tổng lỗ".t()
                                
                                Card(
                                    modifier = Modifier.weight(1.2f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = profitBgColor
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = profitLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = profitTextColor.copy(alpha = 0.8f),
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = formatCurrency(profit),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = profitTextColor,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (invoices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Chưa có hóa đơn nào được tạo".t(),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            // Group invoices by Day
            val grouped = invoices.groupBy { formatDateHeader(it.invoice.timestamp) }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                grouped.forEach { (dateHeader, list) ->
                    item {
                        val context = LocalContext.current
                        val prefs = remember(context) { context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE) }
                        val totalDayProfit = list.sumOf { invoiceWithItems ->
                            val invId = invoiceWithItems.invoice.id
                            val isCostActive = prefs.getBoolean("invoice_cost_active_$invId", false)
                            val costAmountStr = prefs.getString("invoice_cost_amount_$invId", "") ?: ""
                            val costAmount = if (isCostActive) (costAmountStr.toDoubleOrNull() ?: 0.0) else 0.0
                            invoiceWithItems.invoice.profit - costAmount
                        }
                        val dayProfitColor = if (totalDayProfit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        val dayProfitText = if (totalDayProfit >= 0) {
                            "Tổng lãi: ".t() + formatCurrency(totalDayProfit)
                        } else {
                            "Tổng lỗ: ".t() + formatCurrency(totalDayProfit)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateHeader,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = dayProfitColor
                            )
                            Text(
                                text = dayProfitText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = dayProfitColor
                            )
                        }
                    }
                    items(list) { invoiceWithItems ->
                        InvoiceHistoryCard(
                            invoiceWithItems = invoiceWithItems,
                            onClick = { viewModel.viewInvoiceDetail(invoiceWithItems) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceHistoryCard(invoiceWithItems: InvoiceWithItems, onClick: () -> Unit) {
    val inv = invoiceWithItems.invoice
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE) }
    val isDebt = prefs.getBoolean("invoice_debt_${inv.id}", false)
    val cardBg = if (isDebt) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface

    val isCostActive = prefs.getBoolean("invoice_cost_active_${inv.id}", false)
    val costAmountStr = prefs.getString("invoice_cost_amount_${inv.id}", "") ?: ""
    val costAmount = if (isCostActive) (costAmountStr.toDoubleOrNull() ?: 0.0) else 0.0
    val displayedProfit = inv.profit - costAmount

    val profitText = if (displayedProfit >= 0) {
        "Lãi: ".t() + formatCurrency(displayedProfit)
    } else {
        "Lỗ: ".t() + formatCurrency(displayedProfit)
    }
    val profitColor = if (displayedProfit >= 0) Color(0xFF4CAF50) else Color.Red

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("invoice_card_${inv.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = inv.storeName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Giờ: ".t() + formatDate(inv.timestamp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(inv.totalAmount),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = profitText,
                    fontSize = 11.sp,
                    color = profitColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// 3. Tab "Quản Lý" (Management Hub)
@Composable
fun ManagementTab(viewModel: MainViewModel) {
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE) }

    val tongTienMat = invoices.sumOf { invoiceWithItems ->
        val id = invoiceWithItems.invoice.id
        val paymentMethodKey = "invoice_payment_method_$id"
        val paymentMethod = prefs.getString(paymentMethodKey, "TM") ?: "TM"
        
        val isCurrentlyDebt = prefs.getBoolean("invoice_debt_$id", false)
        val paidDebtAmount = prefs.getSafeFloat("invoice_debt_paid_$id", 0f).toDouble()
        val isDebtInvoice = isCurrentlyDebt || paidDebtAmount > 0.0
        
        if (isDebtInvoice) {
            prefs.getSafeFloat("invoice_debt_paid_tm_$id", 0f).toDouble()
        } else {
            if (paymentMethod == "TM") invoiceWithItems.invoice.totalAmount else 0.0
        }
    }

    val tongTaiKhoan = invoices.sumOf { invoiceWithItems ->
        val id = invoiceWithItems.invoice.id
        val paymentMethodKey = "invoice_payment_method_$id"
        val paymentMethod = prefs.getString(paymentMethodKey, "TM") ?: "TM"
        
        val isCurrentlyDebt = prefs.getBoolean("invoice_debt_$id", false)
        val paidDebtAmount = prefs.getSafeFloat("invoice_debt_paid_$id", 0f).toDouble()
        val isDebtInvoice = isCurrentlyDebt || paidDebtAmount > 0.0
        
        if (isDebtInvoice) {
            prefs.getSafeFloat("invoice_debt_paid_ck_$id", 0f).toDouble()
        } else {
            if (paymentMethod == "CK") invoiceWithItems.invoice.totalAmount else 0.0
        }
    }

    val tongCongNo = invoices.filter { combined ->
        prefs.getSafeBoolean("invoice_debt_${combined.invoice.id}", false)
    }.sumOf { combined ->
        val paidAmt = prefs.getSafeFloat("invoice_debt_paid_${combined.invoice.id}", 0f).toDouble()
        maxOf(0.0, combined.invoice.totalAmount - paidAmt)
    }

    var manualCashAdjustment by remember { mutableStateOf(prefs.getSafeFloat("manual_cash_adjustment", 0f).toDouble()) }
    var manualAccountAdjustment by remember { mutableStateOf(prefs.getSafeFloat("manual_account_adjustment", 0f).toDouble()) }

    val finalTienMat = tongTienMat + manualCashAdjustment
    val finalTaiKhoan = tongTaiKhoan + manualAccountAdjustment

    var showEditMoneySource by remember { mutableStateOf(false) }

    val scrollPos = viewModel.getScrollPosition("management_tab")
    val scrollState = rememberScrollState(initial = scrollPos.second)
    LaunchedEffect(scrollState.value) {
        viewModel.saveScrollPosition("management_tab", 0, scrollState.value)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                text = "Bảng quản lý".t(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 20.dp, top = 8.dp)
            )

            // Menu 1: Quản lý Mặt hàng
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clickable { viewModel.selectSubScreen(SubScreen.PRODUCTS_LIST) }
                    .testTag("manage_products_menu"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Inventory,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Quản lý Mặt hàng".t(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Xem, thêm mặt hàng mới, thiết lập giá bán, và cấu hình lượng hàng tồn kho.".t(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Menu 2: Quản lý hàng tồn
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clickable { viewModel.selectSubScreen(SubScreen.INVENTORY_MANAGEMENT) }
                    .testTag("manage_inventory_menu"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Assessment,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Quản lý hàng tồn".t(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Theo dõi lượng tồn kho từng sản phẩm, đơn giá nhập và tổng giá trị tồn kho toàn hệ thống.".t(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Menu 3: Quản lý nhập hàng
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clickable { viewModel.selectSubScreen(SubScreen.IMPORT_MANAGEMENT) }
                    .testTag("manage_import_menu"),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1B3B22) else Color(0xFFE8F5E9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val greenColor = if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32)
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = greenColor
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Quản lý nhập hàng".t(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = greenColor
                        )
                        Text(
                            text = "Quản lý đơn nhập hàng, thêm nhà cung cấp, cập nhật giá vốn và số lượng tồn kho.".t(),
                            fontSize = 12.sp,
                            color = greenColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Menu 4: Quản lý công nợ
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clickable { viewModel.selectSubScreen(SubScreen.DEBT_MANAGEMENT) }
                    .testTag("manage_debt_menu"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CreditCard,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Quản lý công nợ".t(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Theo dõi và quản lý các đơn hàng bán nợ, danh sách khách hàng nợ và lịch hẹn thanh toán.".t(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Menu 5: Quản lý khách hàng
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clickable { viewModel.selectSubScreen(SubScreen.CUSTOMERS_LIST) }
                    .testTag("manage_customers_menu"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Quản lý Khách hàng".t(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Xem lịch sử mua hàng, chi tiết các hóa đơn và sản phẩm mà khách hàng đã lấy.".t(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Menu 6: Quản lý Danh mục
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clickable { viewModel.selectSubScreen(SubScreen.CATEGORIES_LIST) }
                    .testTag("manage_categories_menu"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Category,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Quản lý Danh mục".t(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Phân vùng nhóm danh mục hàng hóa để tối ưu hóa việc phân nhóm hiển thị trên POS.".t(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Bottom Taskbar: Nguồn tiền
        val textColor = if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32)
        var offsetX by remember { mutableStateOf(0f) }
        val maxReveal = -80f
        val density = LocalDensity.current
        val maxRevealPx = remember { with(density) { maxReveal.dp.toPx() } }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // Edit Button Underlay (visible when swiped)
            Card(
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    IconButton(
                        onClick = {
                            showEditMoneySource = true
                            offsetX = 0f
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(80.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .testTag("edit_money_source_reveal_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Sửa nguồn tiền".t(),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Foreground Swipeable Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.toInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (offsetX < maxRevealPx / 2) {
                                    offsetX = maxRevealPx
                                } else {
                                    offsetX = 0f
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount).coerceIn(maxRevealPx, 0f)
                            }
                        )
                    }
                    .testTag("money_source_taskbar"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nguồn tiền".t(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = "Tiền mặt: ".t() + formatCurrency(finalTienMat),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )
                            Text(
                                text = "Tài khoản: ".t() + formatCurrency(finalTaiKhoan),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatCurrency(finalTienMat + finalTaiKhoan),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = formatCurrency(tongCongNo),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { viewModel.selectSubScreen(SubScreen.DEBT_MANAGEMENT) }
                        )
                    }
                }
            }
        }

        if (showEditMoneySource) {
            var cashInput by remember { mutableStateOf(formatDotsInput(finalTienMat.toLong().toString())) }
            var accountInput by remember { mutableStateOf(formatDotsInput(finalTaiKhoan.toLong().toString())) }

            AlertDialog(
                onDismissRequest = { showEditMoneySource = false },
                title = {
                    Text(
                        text = "Điều chỉnh nguồn tiền".t(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = TextFieldValue(text = cashInput, selection = TextRange(cashInput.length)),
                            onValueChange = { textFieldValue ->
                                cashInput = formatDotsInput(textFieldValue.text)
                            },
                            label = { Text("Tiền mặt (VND)".t()) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("edit_cash_input")
                        )

                        OutlinedTextField(
                            value = TextFieldValue(text = accountInput, selection = TextRange(accountInput.length)),
                            onValueChange = { textFieldValue ->
                                accountInput = formatDotsInput(textFieldValue.text)
                            },
                            label = { Text("Tài khoản (VND)".t()) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("edit_account_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val parsedCash = cashInput.replace(".", "").toDoubleOrNull() ?: 0.0
                            val parsedAccount = accountInput.replace(".", "").toDoubleOrNull() ?: 0.0

                            val newManualCash = parsedCash - tongTienMat
                            val newManualAccount = parsedAccount - tongTaiKhoan

                            prefs.edit()
                                .putFloat("manual_cash_adjustment", newManualCash.toFloat())
                                .putFloat("manual_account_adjustment", newManualAccount.toFloat())
                                .apply()

                            manualCashAdjustment = newManualCash
                            manualAccountAdjustment = newManualAccount

                            showEditMoneySource = false
                            ToastUtils.show(context, "Đã cập nhật nguồn tiền thành công!".t())
                        },
                        modifier = Modifier.testTag("save_money_source_btn")
                    ) {
                        Text("Lưu".t())
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showEditMoneySource = false },
                        modifier = Modifier.testTag("cancel_money_source_btn")
                    ) {
                        Text("Hủy".t())
                    }
                }
            )
        }
    }
}

// 4. Tab "Cài Đặt" (Settings / Config)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(viewModel: MainViewModel) {
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isGrid by viewModel.isGridView.collectAsStateWithLifecycle()
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val backupProgress by viewModel.backupProgress.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showLangDropdown by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showBackupBeforeDeletePrompt by remember { mutableStateOf(false) }
    var showCancelBackupConfirm by remember { mutableStateOf(false) }

    // Multi-permissions trigger launcher
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Toast.makeText(context, "Đã cập quyền hoàn tất".t(), Toast.LENGTH_SHORT).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(context, uri) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(context, uri) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val scrollPosition = viewModel.getScrollPosition("settings_tab")
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollPosition.first,
        initialFirstVisibleItemScrollOffset = scrollPosition.second
    )
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                viewModel.saveScrollPosition("settings_tab", index, offset)
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Cài đặt hệ thống".t(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )
        }

        // Section 1: Đăng nhập hoặc đăng ký
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Đăng nhập tài khoản".t(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Sao lưu cơ sở dữ liệu bán hàng đám mây".t(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            Toast.makeText(context, "Chức năng trực tuyến đang được triển khai".t(), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("login_button")
                    ) {
                        Text("Đăng nhập hoặc Đăng ký".t(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section 2: Tùy chọn Bố cục hiển thị
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cấu hình POS".t(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val layoutLabel = "Bố cục hiển thị: ".t() + (if (isGrid) "Dạng lưới G-View".t() else "Dạng danh sách L-View".t())
                        Text(layoutLabel, fontSize = 13.sp)
                        Switch(
                            checked = isGrid,
                            onCheckedChange = { viewModel.setGridView(it) },
                            thumbContent = {
                                Icon(
                                    imageVector = if (isGrid) Icons.Default.GridView else Icons.Default.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            },
                            modifier = Modifier.testTag("layout_toggle")
                        )
                    }
                }
            }
        }

        // Section 3: Chế độ Sáng/Tối (Dark/Light mode)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chế độ tối (Dark mode)".t(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Tiết kiệm pin và dịu mắt khi sử dụng ban đêm".t(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    Switch(
                        checked = isDark,
                        onCheckedChange = { viewModel.setDarkMode(it) },
                        modifier = Modifier.testTag("dark_mode_toggle")
                    )
                }
            }
        }

        // Section 4: Ngôn ngữ
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ngôn ngữ (Language)".t(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showLangDropdown = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("language_selector_btn")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(currentLang)
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                             }
                        }

                        DropdownMenu(
                            expanded = showLangDropdown,
                            onDismissRequest = { showLangDropdown = false }
                        ) {
                            listOf("Tiếng Việt", "Tiếng Anh".t(), "Tiếng Trung Quốc".t(), "Khác".t()).forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang) },
                                    onClick = {
                                        viewModel.setLanguage(lang)
                                        showLangDropdown = false
                                        val toastMsg = "Đã đổi thành ".t() + lang
                                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 5: "Cấp quyền cho tất cả"
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cấp quyền hệ thống".t(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Cho phép gửi thông báo hóa đơn, truy cập lịch và tải hình ảnh sản phẩm".t(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val perms = mutableListOf<String>()
                            // Calendar permissions
                            perms.add(Manifest.permission.READ_CALENDAR)
                            perms.add(Manifest.permission.WRITE_CALENDAR)
                            // Storage & Notification permissions
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                perms.add(Manifest.permission.READ_MEDIA_IMAGES)
                            } else {
                                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }

                            val ungranted = perms.filter { perm ->
                                androidx.core.content.ContextCompat.checkSelfPermission(context, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            }

                            if (ungranted.isNotEmpty()) {
                                permLauncher.launch(ungranted.toTypedArray())
                            } else {
                                Toast.makeText(context, "Tất cả các quyền cần thiết đã được cấp".t(), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("grant_permissions_button")
                    ) {
                        Text("Cấp quyền cho tất cả".t(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }



        // Section 5.3: Sao lưu & Khôi phục dữ liệu
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sao lưu".t(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Sao lưu toàn bộ dữ liệu có trong ứng dụng không bỏ sót thông tin nào cả (sản phẩm, danh mục, hóa đơn, đơn nhập, cấu hình hệ thống, hình ảnh). Hỗ trợ kích thước tệp tối đa lên tới 800MB.".t(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val dateStr = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).format(java.util.Date())
                                    exportLauncher.launch("Proodos_Backup_$dateStr.json")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Không thể mở trình ghi tệp: ".t() + e.localizedMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("export_data_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sao lưu".t(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                try {
                                    importLauncher.launch(arrayOf("*/*"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Không thể mở trình chọn tệp: ".t() + e.localizedMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("import_data_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Khôi phục".t(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (backupProgress != null) {
                        val progressPct = (backupProgress!! * 100).toInt()
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Đang sao lưu...".t(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "$progressPct%",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = backupProgress!!,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                            if (!backupMessage.isNullOrEmpty()) {
                                Text(
                                    text = backupMessage!!,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showCancelBackupConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("cancel_backup_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hủy sao lưu".t(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }


        if (showCancelBackupConfirm) {
            item {
                AlertDialog(
                    onDismissRequest = { showCancelBackupConfirm = false },
                    title = { Text("Xác nhận hủy".t(), fontWeight = FontWeight.Bold) },
                    text = { Text("Bạn có chắc chắn muốn hủy quá trình sao lưu hiện tại và xóa bản sao lưu dở dang này không?".t()) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showCancelBackupConfirm = false
                                viewModel.cancelBackup(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.testTag("confirm_cancel_backup_btn")
                        ) {
                            Text("Đồng ý hủy".t())
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showCancelBackupConfirm = false },
                            modifier = Modifier.testTag("dismiss_cancel_backup_btn")
                        ) {
                            Text("Quay lại".t())
                        }
                    }
                )
            }
        }


        // Section 5.4: Đồng bộ nhà cung cấp
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Đồng bộ nhà cung cấp".t(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Đồng bộ hóa nhà cung cấp từ các đơn nhập hàng cũ".t(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.syncAllSuppliersToProducts {
                                Toast.makeText(context, "Đồng bộ thành công".t(), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("sync_suppliers_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Đồng bộ nhà cung cấp".t(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }


        // Section 5.5: Dọn dẹp dữ liệu (Xóa tất cả dữ liệu)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Dọn dẹp dữ liệu".t(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("Xóa tất cả dữ liệu hiện có khỏi hệ thống (sản phẩm, danh mục, hóa đơn...)".t(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showBackupBeforeDeletePrompt = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("clear_all_data_btn")
                    ) {
                        Text("Xóa tất cả dữ liệu".t(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        // Section 6: Giới thiệu
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Giới thiệu".t(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Phiên bản: 1.3.9".t(), fontSize = 13.sp)
                    Text("Người sáng lập: Cường lâm".t(), fontSize = 13.sp)
                    Text("Liên hệ (Zalo): ".t() + "0964935879", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("Xác nhận xóa tất cả dữ liệu".t()) },
            text = { Text("Bạn có chắc chắn muốn xóa TẤT CẢ dữ liệu (mặt hàng, danh mục, hóa đơn...) không? Hành động này không thể hoàn tác.".t()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData {
                            Toast.makeText(context, "Đã xóa toàn bộ dữ liệu".t(), Toast.LENGTH_SHORT).show()
                        }
                        showDeleteAllConfirm = false
                    },
                    modifier = Modifier.testTag("confirm_clear_all_btn")
                ) {
                    Text("Xóa hết".t(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAllConfirm = false },
                    modifier = Modifier.testTag("cancel_clear_all_btn")
                ) {
                    Text("Hủy".t())
                }
            }
        )
    }

    if (showBackupBeforeDeletePrompt) {
        AlertDialog(
            onDismissRequest = { showBackupBeforeDeletePrompt = false },
            title = { Text("Xác thực sao lưu".t()) },
            text = { Text("Để đảm bảo an toàn cho dữ liệu bán hàng của bạn, vui lòng thực hiện sao lưu trước khi tiến hành xóa toàn bộ dữ liệu khỏi hệ thống.".t()) },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val dateStr = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).format(java.util.Date())
                            exportLauncher.launch("Proodos_Backup_$dateStr.json")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Không thể mở trình ghi tệp: ".t() + e.localizedMessage, Toast.LENGTH_SHORT).show()
                        }
                        showBackupBeforeDeletePrompt = false
                        showDeleteAllConfirm = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sao lưu dữ liệu".t(), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBackupBeforeDeletePrompt = false }
                ) {
                    Text("Hủy".t())
                }
            }
        )
    }
}

// ======================== SUBSCREENS IMPLEMENTATION ========================

// 1. SubScreen: Giỏ Hàng (Cart Screen)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(viewModel: MainViewModel) {
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val savedStoreName by viewModel.storeName.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var storeNameField by remember { mutableStateOf(savedStoreName) }
    var itemToDelete by remember { mutableStateOf<CartItem?>(null) }
    var showCheckoutSuccess by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("TM") }

    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val previousCustomers = remember(invoices) {
        invoices.map { it.invoice.storeName.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }
    var isFocused by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(true) }

    val filteredSuggestions = remember(storeNameField, previousCustomers) {
        if (storeNameField.isBlank()) {
            emptyList()
        } else {
            previousCustomers.filter { customer ->
                customer.contains(storeNameField, ignoreCase = true) &&
                        !customer.equals(storeNameField, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(savedStoreName) {
        if (storeNameField != savedStoreName) {
            storeNameField = savedStoreName
        }
    }

    // Save storeName to shared prefs dynamically as typed
    fun onStoreNameChanged(name: String) {
        storeNameField = name
        viewModel.setStoreName(name)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết Hóa đơn".t()) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.selectSubScreen(null) },
                        modifier = Modifier.testTag("cart_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về".t())
                    }
                }
            )
        }
    ) { paddingVal ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVal)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tên khách hàng / cửa hàng input card with auto-save
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Tối ưu hóa quản lý và tra soát khách hàng".t(),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nhập Tên Khách Hàng (Tự động lưu)".t(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = storeNameField,
                                onValueChange = {
                                    onStoreNameChanged(it)
                                    showSuggestions = true
                                },
                                singleLine = true,
                                placeholder = { Text("Tên khách hàng...".t()) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("store_name_input")
                                    .onFocusChanged { isFocused = it.isFocused }
                            )

                            val showDropdown = showSuggestions && isFocused && filteredSuggestions.isNotEmpty()

                            DropdownMenu(
                                expanded = showDropdown,
                                onDismissRequest = { showSuggestions = false },
                                properties = PopupProperties(focusable = false),
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                filteredSuggestions.take(5).forEach { customer ->
                                    DropdownMenuItem(
                                        text = { Text(customer) },
                                        onClick = {
                                            onStoreNameChanged(customer)
                                            showSuggestions = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (cart.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Giỏ hàng của bạn đang trống".t(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(cart) { cartItem ->
                            CartItemRow(
                                cartItem = cartItem,
                                onQuantityChange = { q ->
                                    val success = viewModel.updateCartQuantity(cartItem.product.id, q)
                                    if (!success) {
                                        ToastUtils.show(context, "Không thể bán quá số lượng hàng tồn! Tối đa: ${cartItem.product.stockQuantity}".t())
                                    }
                                },
                                onPriceChange = { p -> viewModel.updateCartPrice(cartItem.product.id, p) },
                                onDelete = { itemToDelete = cartItem }
                            )
                        }
                    }

                    // Bottom Checkout Summary Panel
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tổng cộng tiền hàng:".t(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = formatCurrency(cart.sumOf { it.sellPrice * it.quantity }),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (storeNameField.isBlank()) {
                                        Toast.makeText(context, "Vui lòng nhập tên khách hàng".t(), Toast.LENGTH_SHORT).show()
                                    } else {
                                        showPaymentDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("checkout_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("THANH TOÁN Giao Dịch".t(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // UX Xóa PopUp: Confirm Dialog for delete item
            if (itemToDelete != null) {
                AlertDialog(
                    onDismissRequest = { itemToDelete = null },
                    title = { Text("Xác nhận xóa".t()) },
                    text = { Text("Bạn có chắc chắn muốn xóa mặt hàng '".t() + (itemToDelete?.product?.name ?: "") + "' khỏi giỏ hàng?".t()) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                itemToDelete?.let { viewModel.removeFromCart(it.product.id) }
                                itemToDelete = null
                            },
                            modifier = Modifier.testTag("confirm_delete_btn")
                        ) {
                            Text("Xóa".t(), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { itemToDelete = null },
                            modifier = Modifier.testTag("cancel_delete_btn")
                        ) {
                            Text("Hủy".t())
                        }
                    }
                )
            }

            if (showPaymentDialog) {
                AlertDialog(
                    onDismissRequest = { showPaymentDialog = false },
                    title = { Text("Chọn phương thức thanh toán".t(), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val tmSelected = selectedPaymentMethod == "TM"
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                                    .clickable { selectedPaymentMethod = "TM" },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (tmSelected) Color(0xFF81C784) else Color(0xFFC8E6C9)
                                ),
                                border = if (tmSelected) BorderStroke(2.dp, Color(0xFF2E7D32)) else null
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                    Text("Tiền mặt".t(), fontWeight = if (tmSelected) FontWeight.Bold else FontWeight.Normal, color = Color(0xFF1B5E20), fontSize = 14.sp)
                                }
                            }
                            
                            val ckSelected = selectedPaymentMethod == "CK"
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                                    .clickable { selectedPaymentMethod = "CK" },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (ckSelected) Color(0xFF81C784) else Color(0xFFC8E6C9)
                                ),
                                border = if (ckSelected) BorderStroke(2.dp, Color(0xFF2E7D32)) else null
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                    Text("Chuyển khoản".t(), fontWeight = if (ckSelected) FontWeight.Bold else FontWeight.Normal, color = Color(0xFF1B5E20), fontSize = 14.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showPaymentDialog = false
                                viewModel.checkout(
                                    customerName = storeNameField.trim(),
                                    customerPhone = null,
                                    paymentMethod = selectedPaymentMethod
                                ) {
                                    showCheckoutSuccess = true
                                    onStoreNameChanged("")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Xác nhận".t(), color = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPaymentDialog = false }) {
                            Text("Hủy".t())
                        }
                    }
                )
            }

            // Pay-out Success Modal overlay
            if (showCheckoutSuccess) {
                Dialog(onDismissRequest = {
                    showCheckoutSuccess = false
                    viewModel.selectSubScreen(null)
                }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Thanh Toán Thành Công!".t(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Hóa đơn đã được lưu trữ trong danh mục lịch sử. Hàng tồn kho đã được tự động khấu trừ.".t(),
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    showCheckoutSuccess = false
                                    viewModel.selectSubScreen(null)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("success_confirm")
                            ) {
                                Text("Đóng".t())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    cartItem: CartItem,
    onQuantityChange: (Int) -> Unit,
    onPriceChange: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var priceEditor by remember(cartItem.sellPrice) { mutableStateOf(cartItem.sellPrice.toInt().toString()) }
    var qtyEditor by remember(cartItem.quantity) { mutableStateOf(cartItem.quantity.toString()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cart_item_${cartItem.product.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cartItem.product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Vốn: ".t() + "${formatCurrency(cartItem.importPrice)}/" + "sp".t(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (cartItem.product.trackInventory) {
                        Text(
                            text = "Tồn còn lại: ".t() + (cartItem.product.stockQuantity - cartItem.quantity),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Delete Button
                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("delete_item_btn_${cartItem.product.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Hủy",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sửa giá bán bằng bàn phím
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Đơn giá:".t(), fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                    OutlinedTextField(
                        value = TextFieldValue(text = priceEditor, selection = TextRange(priceEditor.length)),
                        onValueChange = { textFieldValue ->
                            val input = textFieldValue.text
                            val filtered = input.filter { it.isDigit() }
                            priceEditor = filtered
                            val newPrice = filtered.toDoubleOrNull()
                            if (newPrice != null) {
                                onPriceChange(newPrice)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = ThousandsSeparatorVisualTransformation(),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .width(100.dp)
                            .height(44.dp)
                            .testTag("price_input_${cartItem.product.id}")
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    priceEditor = ""
                                } else {
                                    if (priceEditor.isEmpty()) {
                                        priceEditor = cartItem.sellPrice.toInt().toString()
                                    }
                                }
                            },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Điểu chỉnh số lượng (+ / -) và bàn phím
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .clip(CircleShape)
                            .clickable { if (cartItem.quantity > 1) onQuantityChange(cartItem.quantity - 1) }
                            .testTag("qty_minus_${cartItem.product.id}")
                    ) {
                        Text(
                            text = "-",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedTextField(
                        value = TextFieldValue(text = qtyEditor, selection = TextRange(qtyEditor.length)),
                        onValueChange = { textFieldValue ->
                            val input = textFieldValue.text
                            val filtered = input.filter { it.isDigit() }
                            qtyEditor = filtered
                            val num = filtered.toIntOrNull()
                            if (num != null && num > 0) {
                                onQuantityChange(num)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .width(75.dp)
                            .height(44.dp)
                            .padding(horizontal = 4.dp)
                            .testTag("qty_input_${cartItem.product.id}")
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    qtyEditor = ""
                                } else {
                                    if (qtyEditor.isEmpty()) {
                                        qtyEditor = cartItem.quantity.toString()
                                    }
                                }
                            },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .clip(CircleShape)
                            .clickable { onQuantityChange(cartItem.quantity + 1) }
                            .testTag("qty_plus_${cartItem.product.id}")
                    ) {
                        Text(
                            text = "+",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// 2. SubScreen: Invoice Details View (Chi Tiết Hóa Đơn Lịch Sử)
fun saveDebtCalendarEvent(
    context: Context,
    customerName: String,
    invoiceId: Int,
    timeMillis: Long,
    onSuccess: (Long) -> Unit,
    onFailure: (String) -> Unit
) {
    try {
        val cr = context.contentResolver
        
        val projection = arrayOf(
            android.provider.CalendarContract.Calendars._ID,
            android.provider.CalendarContract.Calendars.IS_PRIMARY
        )
        val uri = android.provider.CalendarContract.Calendars.CONTENT_URI
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        var calId: Long = -1
        cursor?.use {
            val idCol = it.getColumnIndex(android.provider.CalendarContract.Calendars._ID)
            val primaryCol = it.getColumnIndex(android.provider.CalendarContract.Calendars.IS_PRIMARY)
            if (it.moveToFirst()) {
                while (!it.isAfterLast) {
                    val id = it.getLong(idCol)
                    val isPrimary = if (primaryCol >= 0) it.getInt(primaryCol) == 1 else false
                    if (isPrimary) {
                        calId = id
                        break
                    }
                    it.moveToNext()
                }
            }
        }
        
        if (calId == -1L) {
            val fallbackCursor = context.contentResolver.query(uri, arrayOf(android.provider.CalendarContract.Calendars._ID), null, null, null)
            fallbackCursor?.use {
                if (it.moveToFirst()) {
                    calId = it.getLong(0)
                }
            }
        }
        
        if (calId == -1L) {
            calId = 1
        }
        
        val title = "Đã đến lịch thanh toán của ".t() + customerName
        val description = "Lịch hẹn thanh toán cho hóa đơn #$invoiceId".t()
        
        val values = android.content.ContentValues().apply {
            put(android.provider.CalendarContract.Events.DTSTART, timeMillis)
            put(android.provider.CalendarContract.Events.DTEND, timeMillis + 30 * 60 * 1000)
            put(android.provider.CalendarContract.Events.TITLE, title)
            put(android.provider.CalendarContract.Events.DESCRIPTION, description)
            put(android.provider.CalendarContract.Events.CALENDAR_ID, calId)
            put(android.provider.CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        
        val eventUri = cr.insert(android.provider.CalendarContract.Events.CONTENT_URI, values)
        if (eventUri == null) {
            onFailure("Không thể tạo sự kiện lịch".t())
            return
        }
        
        val eventId = eventUri.lastPathSegment?.toLongOrNull()
        if (eventId == null || eventId == -1L) {
            onFailure("Lỗi lấy ID sự kiện lịch".t())
            return
        }
        
        val reminder1 = android.content.ContentValues().apply {
            put(android.provider.CalendarContract.Reminders.MINUTES, 15)
            put(android.provider.CalendarContract.Reminders.EVENT_ID, eventId)
            put(android.provider.CalendarContract.Reminders.METHOD, android.provider.CalendarContract.Reminders.METHOD_ALERT)
        }
        cr.insert(android.provider.CalendarContract.Reminders.CONTENT_URI, reminder1)
        
        val reminder2 = android.content.ContentValues().apply {
            put(android.provider.CalendarContract.Reminders.MINUTES, 0)
            put(android.provider.CalendarContract.Reminders.EVENT_ID, eventId)
            put(android.provider.CalendarContract.Reminders.METHOD, android.provider.CalendarContract.Reminders.METHOD_ALERT)
        }
        cr.insert(android.provider.CalendarContract.Reminders.CONTENT_URI, reminder2)
        
        // Auto delete event from device calendar 5 minutes after start time
        val deleteTimeMillis = timeMillis + 5 * 60 * 1000
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
        if (alarmManager != null) {
            val deleteIntent = android.content.Intent(context, CalendarDeleteReceiver::class.java).apply {
                putExtra("EVENT_ID", eventId)
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                eventId.toInt(),
                deleteIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        deleteTimeMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        android.app.AlarmManager.RTC_WAKEUP,
                        deleteTimeMillis,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                alarmManager.set(
                    android.app.AlarmManager.RTC_WAKEUP,
                    deleteTimeMillis,
                    pendingIntent
                )
            }
        }
        
        onSuccess(eventId)
    } catch (e: Exception) {
        onFailure(e.localizedMessage ?: "Lỗi không xác định")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(viewModel: MainViewModel) {
    val activeInvoice by viewModel.activeInvoice.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showRefundConfirm by remember { mutableStateOf(false) }
    var showTurnOffDebtConfirm by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<InvoiceItem?>(null) }
    var itemToDelete by remember { mutableStateOf<InvoiceItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tra soát hóa đơn".t()) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.selectSubScreen(null) },
                        modifier = Modifier.testTag("invoice_detail_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về".t())
                    }
                }
            )
        }
    ) { paddingVal ->
        activeInvoice?.let { combined ->
            val invoice = combined.invoice
            val items = combined.items

            val prefs = remember { context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE) }
            val debtKey = "invoice_debt_${invoice.id}"
            val timeKey = "invoice_debt_time_${invoice.id}"
            val eventIdKey = "invoice_calendar_event_${invoice.id}"

            val costActiveKey = "invoice_cost_active_${invoice.id}"
            val costAmountKey = "invoice_cost_amount_${invoice.id}"

            var isCostActive by remember { mutableStateOf(prefs.getBoolean(costActiveKey, false)) }
            var costAmountStr by remember { mutableStateOf(prefs.getString(costAmountKey, "") ?: "") }

            val costAmount = remember(isCostActive, costAmountStr) {
                if (isCostActive) {
                    costAmountStr.toDoubleOrNull() ?: 0.0
                } else {
                    0.0
                }
            }

            val displayedProfit = remember(invoice.profit, costAmount) {
                invoice.profit - costAmount
            }

            var isDebtSale by remember { mutableStateOf(prefs.getBoolean(debtKey, false)) }
            val paymentMethodKey = "invoice_payment_method_${invoice.id}"
            var paymentMethod by remember { mutableStateOf(prefs.getString(paymentMethodKey, "TM") ?: "TM") }
            val savedTime = prefs.getLong(timeKey, 0L)

            val calendar = remember { 
                java.util.Calendar.getInstance().apply {
                    if (savedTime > 0L) {
                        timeInMillis = savedTime
                    }
                }
            }

            var selectedHour by remember { mutableStateOf(calendar.get(java.util.Calendar.HOUR_OF_DAY)) }
            var selectedMinute by remember { mutableStateOf(calendar.get(java.util.Calendar.MINUTE)) }
            var selectedYear by remember { mutableStateOf(calendar.get(java.util.Calendar.YEAR)) }
            var selectedMonth by remember { mutableStateOf(calendar.get(java.util.Calendar.MONTH)) }
            var selectedDay by remember { mutableStateOf(calendar.get(java.util.Calendar.DAY_OF_MONTH)) }

            val calendarPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val readGranted = permissions[android.Manifest.permission.READ_CALENDAR] ?: false
                val writeGranted = permissions[android.Manifest.permission.WRITE_CALENDAR] ?: false
                if (readGranted && writeGranted) {
                    Toast.makeText(context, "Quyền lịch đã được cấp. Hãy nhấn 'Lưu lịch hẹn' một lần nữa để lưu.".t(), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Không thể lưu lịch hẹn vì thiếu quyền truy cập lịch.".t(), Toast.LENGTH_SHORT).show()
                }
            }

            val hasReadPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CALENDAR
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            val hasWritePermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_CALENDAR
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingVal)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Receipt Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mã hóa đơn: ".t() + "#${invoice.id}", fontWeight = FontWeight.Bold)
                            Text(formatDate(invoice.timestamp), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Khách hàng: ".t() + (invoice.customerName ?: invoice.storeName),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Danh sách mặt hàng:".t(), style = textStyle)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "TM",
                            style = textStyle.copy(
                                fontWeight = if (paymentMethod == "TM") FontWeight.Bold else FontWeight.Normal,
                                color = if (paymentMethod == "TM") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.clickable {
                                paymentMethod = "TM"
                                prefs.edit().putString(paymentMethodKey, "TM").apply()
                            }
                        )
                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (paymentMethod == "CK") MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    val next = if (paymentMethod == "TM") "CK" else "TM"
                                    paymentMethod = next
                                    prefs.edit().putString(paymentMethodKey, next).apply()
                                }
                                .padding(2.dp),
                            contentAlignment = if (paymentMethod == "CK") Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary)
                            )
                        }
                        Text(
                            text = "CK",
                            style = textStyle.copy(
                                fontWeight = if (paymentMethod == "CK") FontWeight.Bold else FontWeight.Normal,
                                color = if (paymentMethod == "CK") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.clickable {
                                paymentMethod = "CK"
                                prefs.edit().putString(paymentMethodKey, "CK").apply()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items.forEach { item ->
                        SwipeableInvoiceItemCard(
                            item = item,
                            onEdit = { itemToEdit = item },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Thêm mặt hàng từ giỏ hàng section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_from_cart_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Thêm mặt hàng từ giỏ hàng".t(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val cartItemCount = cart.size
                            Text(
                                text = if (cartItemCount > 0) {
                                    "Giỏ hàng hiện có %d mặt hàng".t().format(cartItemCount)
                                } else {
                                    "Giỏ hàng đang trống".t()
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.addCartItemsToActiveInvoice(combined) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Đã thêm các mặt hàng từ giỏ hàng vào hóa đơn!".t(), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Giỏ hàng hiện tại đang trống. Vui lòng thêm sản phẩm vào giỏ hàng trước!".t(), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.testTag("btn_add_from_cart_to_invoice")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Thêm từ giỏ hàng".t(),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // TIỀN LÃI Logic (Lãi = Giá Bán - Giá Nhập)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tổng tiền hàng:".t())
                            Text(
                                formatCurrency(invoice.totalAmount),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color.Red
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 10.dp))

                        val profitColor = if (displayedProfit >= 0) Color(0xFF4CAF50) else Color.Red
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "LỢI NHUẬN (TIỀN LÃI):".t(),
                                fontWeight = FontWeight.Bold,
                                color = profitColor
                            )
                            // Profit Highlight color and tag decoration
                            Box(
                                modifier = Modifier
                                    .background(profitColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = formatCurrency(displayedProfit),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = profitColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Phân vùng Bán nợ
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Bán nợ".t(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bán công nợ".t(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = isDebtSale,
                                onCheckedChange = { checked ->
                                    if (!checked) {
                                        showTurnOffDebtConfirm = true
                                    } else {
                                        isDebtSale = true
                                        prefs.edit().putBoolean(debtKey, true).apply()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                        
                        if (isDebtSale) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "Lịch hẹn thanh toán".t(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Time Picker Button
                                OutlinedButton(
                                    onClick = {
                                        android.app.TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                selectedHour = hour
                                                selectedMinute = minute
                                                val cal = java.util.Calendar.getInstance().apply {
                                                    set(java.util.Calendar.YEAR, selectedYear)
                                                    set(java.util.Calendar.MONTH, selectedMonth)
                                                    set(java.util.Calendar.DAY_OF_MONTH, selectedDay)
                                                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                                                    set(java.util.Calendar.MINUTE, minute)
                                                    set(java.util.Calendar.SECOND, 0)
                                                    set(java.util.Calendar.MILLISECOND, 0)
                                                }
                                                prefs.edit().putLong(timeKey, cal.timeInMillis).apply()
                                            },
                                            selectedHour,
                                            selectedMinute,
                                            true
                                        ).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute),
                                        fontSize = 13.sp
                                    )
                                }
                                
                                // Date Picker Button
                                OutlinedButton(
                                    onClick = {
                                        android.app.DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                selectedYear = year
                                                selectedMonth = month
                                                selectedDay = day
                                                val cal = java.util.Calendar.getInstance().apply {
                                                    set(java.util.Calendar.YEAR, year)
                                                    set(java.util.Calendar.MONTH, month)
                                                    set(java.util.Calendar.DAY_OF_MONTH, day)
                                                    set(java.util.Calendar.HOUR_OF_DAY, selectedHour)
                                                    set(java.util.Calendar.MINUTE, selectedMinute)
                                                    set(java.util.Calendar.SECOND, 0)
                                                    set(java.util.Calendar.MILLISECOND, 0)
                                                }
                                                prefs.edit().putLong(timeKey, cal.timeInMillis).apply()
                                            },
                                            selectedYear,
                                            selectedMonth,
                                            selectedDay
                                        ).show()
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = String.format(Locale.US, "%02d/%02d/%d", selectedDay, selectedMonth + 1, selectedYear),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Button(
                                onClick = {
                                    if (hasReadPermission && hasWritePermission) {
                                        val targetCal = java.util.Calendar.getInstance().apply {
                                            set(java.util.Calendar.YEAR, selectedYear)
                                            set(java.util.Calendar.MONTH, selectedMonth)
                                            set(java.util.Calendar.DAY_OF_MONTH, selectedDay)
                                            set(java.util.Calendar.HOUR_OF_DAY, selectedHour)
                                            set(java.util.Calendar.MINUTE, selectedMinute)
                                            set(java.util.Calendar.SECOND, 0)
                                            set(java.util.Calendar.MILLISECOND, 0)
                                        }
                                        
                                        saveDebtCalendarEvent(
                                            context = context,
                                            customerName = invoice.customerName ?: invoice.storeName,
                                            invoiceId = invoice.id,
                                            timeMillis = targetCal.timeInMillis,
                                            onSuccess = { newId ->
                                                prefs.edit().putLong(eventIdKey, newId).apply()
                                                Toast.makeText(context, "Đã lưu lịch hẹn thanh toán vào lịch máy!".t(), Toast.LENGTH_LONG).show()
                                            },
                                            onFailure = { err ->
                                                Toast.makeText(context, "Lưu thất bại: ".t() + err, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } else {
                                        calendarPermissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.READ_CALENDAR,
                                                android.Manifest.permission.WRITE_CALENDAR
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Lưu lịch hẹn".t(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Phân vùng Chi phí phát sinh
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("additional_cost_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Chi phí phát sinh".t(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bật chi phí phát sinh".t(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = isCostActive,
                                onCheckedChange = { checked ->
                                    isCostActive = checked
                                    prefs.edit().putBoolean(costActiveKey, checked).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("additional_cost_switch")
                            )
                        }
                        
                        if (isCostActive) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = costAmountStr,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.all { it.isDigit() }) {
                                        costAmountStr = input
                                        prefs.edit().putString(costAmountKey, input).apply()
                                    }
                                },
                                label = { Text("Số tiền chi phí phát sinh".t()) },
                                placeholder = { Text("Ví dụ: 50000".t()) },
                                suffix = { Text("đ") },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                visualTransformation = ThousandsSeparatorVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("additional_cost_input"),
                                singleLine = true
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showRefundConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("refund_invoice_btn")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Hoàn tiền".t())
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("HOÀN TIỀN & HỦY HÓA ĐƠN".t(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            if (showRefundConfirm) {
                AlertDialog(
                    onDismissRequest = { showRefundConfirm = false },
                    title = { Text("Xác nhận hoàn tiền".t()) },
                    text = { 
                        Text("Bạn có chắc chắn muốn hoàn tiền cho hóa đơn này không?\nHành động này sẽ xóa hóa đơn và hoàn lại toàn bộ sản phẩm vào kho hàng.".t()) 
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.refundInvoice(combined) {
                                    Toast.makeText(context, "Đã hoàn trả sản phẩm vào kho và xóa hóa đơn!".t(), Toast.LENGTH_LONG).show()
                                }
                                showRefundConfirm = false
                            },
                            modifier = Modifier.testTag("confirm_refund_btn")
                        ) {
                            Text("Hoàn tiền".t(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showRefundConfirm = false },
                            modifier = Modifier.testTag("cancel_refund_btn")
                        ) {
                            Text("Không".t())
                        }
                    }
                )
            }

            if (showTurnOffDebtConfirm) {
                AlertDialog(
                    onDismissRequest = { showTurnOffDebtConfirm = false },
                    title = { Text("Xác nhận tắt bán nợ".t()) },
                    text = { 
                        Text("Bạn có chắc chắn muốn tắt bán công nợ cho hóa đơn này không?".t()) 
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                isDebtSale = false
                                prefs.edit().putBoolean(debtKey, false).apply()
                                showTurnOffDebtConfirm = false
                            },
                            modifier = Modifier.testTag("confirm_turn_off_debt_btn")
                        ) {
                            Text("Tắt bán nợ".t(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showTurnOffDebtConfirm = false },
                            modifier = Modifier.testTag("cancel_turn_off_debt_btn")
                        ) {
                            Text("Không".t())
                        }
                    }
                )
            }

            if (itemToEdit != null) {
                val currentItem = itemToEdit!!
                var priceInput by remember {
                    val initialPrice = currentItem.sellPrice.toLong()
                    val symbols = DecimalFormatSymbols(Locale("vi", "VN")).apply {
                        groupingSeparator = '.'
                    }
                    val formatter = DecimalFormat("#,###", symbols)
                    mutableStateOf(formatter.format(initialPrice))
                }
                var quantityInput by remember { mutableStateOf(currentItem.quantity.toString()) }
                var isQuantityFirstFocus by remember { mutableStateOf(true) }

                AlertDialog(
                    onDismissRequest = { itemToEdit = null },
                    title = { Text("Chỉnh sửa sản phẩm".t()) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(currentItem.productName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            
                            OutlinedTextField(
                                value = TextFieldValue(text = priceInput, selection = TextRange(priceInput.length)),
                                onValueChange = { textFieldValue ->
                                    val input = textFieldValue.text
                                    val clean = input.replace(".", "").filter { it.isDigit() }
                                    val formatted = if (clean.isEmpty()) "" else {
                                        val value = clean.toLongOrNull() ?: 0L
                                        val symbols = DecimalFormatSymbols(Locale("vi", "VN")).apply {
                                            groupingSeparator = '.'
                                        }
                                        val formatter = DecimalFormat("#,###", symbols)
                                        formatter.format(value)
                                    }
                                    priceInput = formatted
                                },
                                label = { Text("Giá bán".t()) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("edit_item_price_input")
                            )

                            OutlinedTextField(
                                value = quantityInput,
                                onValueChange = { quantityInput = it },
                                label = { Text("Số lượng".t()) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_item_quantity_input")
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused && isQuantityFirstFocus) {
                                            quantityInput = ""
                                            isQuantityFirstFocus = false
                                        } else if (!focusState.isFocused) {
                                            isQuantityFirstFocus = true
                                        }
                                    }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val cleanPrice = priceInput.replace(".", "")
                                val newPrice = cleanPrice.toDoubleOrNull() ?: currentItem.sellPrice
                                var newQty = quantityInput.toIntOrNull() ?: 0
                                if (newQty <= 0) {
                                    ToastUtils.show(context, "Số lượng phải lớn hơn 0!".t())
                                } else {
                                    val targetProd = products.find { it.id == currentItem.productId }
                                    if (targetProd != null && targetProd.trackInventory) {
                                        val maxAllowed = targetProd.stockQuantity + currentItem.quantity
                                        if (newQty > maxAllowed) {
                                            newQty = maxAllowed
                                            ToastUtils.show(context, "Số lượng vượt quá hàng tồn kho! Tự động điều chỉnh về %d".t().format(maxAllowed))
                                        }
                                    }
                                    viewModel.updateInvoiceItemInActiveInvoice(combined, currentItem, newPrice, newQty)
                                    itemToEdit = null
                                    ToastUtils.show(context, "Đã cập nhật sản phẩm!".t())
                                }
                            },
                            modifier = Modifier.testTag("confirm_edit_item_btn")
                        ) {
                            Text("Lưu".t())
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { itemToEdit = null },
                            modifier = Modifier.testTag("cancel_edit_item_btn")
                        ) {
                            Text("Hủy".t())
                        }
                    }
                )
            }

            if (itemToDelete != null) {
                val currentItem = itemToDelete!!
                AlertDialog(
                    onDismissRequest = { itemToDelete = null },
                    title = { Text("Xác nhận xóa".t()) },
                    text = {
                        Text("Bạn có chắc chắn muốn xóa sản phẩm '%s' khỏi hóa đơn này?".t().format(currentItem.productName))
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteInvoiceItemFromActiveInvoice(combined, currentItem)
                                itemToDelete = null
                                Toast.makeText(context, "Đã xóa sản phẩm khỏi hóa đơn!".t(), Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("confirm_delete_item_btn")
                        ) {
                            Text("Xóa".t())
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { itemToDelete = null },
                            modifier = Modifier.testTag("cancel_delete_item_btn")
                        ) {
                            Text("Hủy".t())
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SwipeableInvoiceItemCard(
    item: InvoiceItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minSwipeOffset = with(density) { -130.dp.toPx() }
    val maxSwipeOffset = 0f

    val swipeOffset = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    coroutineScope.launch { swipeOffset.animateTo(0f) }
                    onEdit()
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .fillMaxHeight()
                    .width(65.dp)
                    .testTag("swipe_btn_edit_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Sửa".t(),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            IconButton(
                onClick = {
                    coroutineScope.launch { swipeOffset.animateTo(0f) }
                    onDelete()
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .fillMaxHeight()
                    .width(65.dp)
                    .testTag("swipe_btn_delete_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Xóa".t(),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val targetValue = (swipeOffset.value + dragAmount).coerceIn(minSwipeOffset, maxSwipeOffset)
                                swipeOffset.snapTo(targetValue)
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                val halfOffset = minSwipeOffset / 2
                                if (swipeOffset.value < halfOffset) {
                                    swipeOffset.animateTo(minSwipeOffset)
                                } else {
                                    swipeOffset.animateTo(0f)
                                }
                            }
                        }
                    )
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Số lượng: ".t() + "${item.quantity} x ${formatCurrency(item.sellPrice)}",
                        fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatCurrency(item.sellPrice * item.quantity),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val itemProfit = (item.sellPrice - item.importPrice) * item.quantity
                    Text(
                        "Lãi: ".t() + formatCurrency(itemProfit),
                        fontSize = 11.sp,
                        color = if (itemProfit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// 3. SubScreen: Products Management UI (Quản Lý Mặt Hàng LIST)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsListScreen(viewModel: MainViewModel) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    var productToDelete by remember { mutableStateOf<com.example.data.Product?>(null) }
    var activeZoomImageUri by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val scrollPosition = viewModel.getScrollPosition("products_list")
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollPosition.first,
        initialFirstVisibleItemScrollOffset = scrollPosition.second
    )
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                viewModel.saveScrollPosition("products_list", index, offset)
            }
    }

    activeZoomImageUri?.let { uri ->
        ZoomableImageDialog(imageUri = uri, onDismiss = { activeZoomImageUri = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mặt Hàng".t()) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.selectSubScreen(null) },
                        modifier = Modifier.testTag("products_list_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về".t())
                    }
                }
            )
        },
        floatingActionButton = {
            // Nút FAB hình dâu + ở giữa bottom góc phải
            FloatingActionButton(
                onClick = { viewModel.selectSubScreen(SubScreen.CREATE_PRODUCT) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_product")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm hàng mới")
            }
        }
    ) { paddingVal ->
        if (products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingVal),
                contentAlignment = Alignment.Center
            ) {
                Text("Chưa có mặt hàng nào. Nhấn nút + bên dưới để tạo mới.".t())
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingVal)
            ) {
                // Search Input
                AnimatedVisibility(visible = listState.firstVisibleItemIndex == 0) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("product_search_input"),
                        placeholder = { Text("Tìm kiếm sản phẩm...".t()) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa tìm kiếm".t())
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                val filteredProducts = remember(products, searchQuery) {
                    if (searchQuery.isBlank()) {
                        products
                    } else {
                        products.mapNotNull { prod ->
                            val score = getRelevanceScore(prod.name, searchQuery)
                            if (score != null) prod to score else null
                        }.sortedByDescending { it.second }.map { it.first }
                    }
                }

                if (filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Không tìm thấy mặt hàng phù hợp".t())
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredProducts) { prod ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .then(
                                                if (!prod.imageUri.isNullOrEmpty()) {
                                                    Modifier.clickable { activeZoomImageUri = prod.imageUri }
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!prod.imageUri.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = Uri.parse(prod.imageUri),
                                                contentDescription = prod.name,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text("Nhập: ".t() + formatCurrency(prod.importPrice), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                            Text(
                                                text = "Tồn: ".t() + prod.stockQuantity,
                                                fontSize = 12.sp,
                                                color = if (prod.stockQuantity <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Text("Bán: ".t() + formatCurrency(prod.sellPrice), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }

                                    IconButton(
                                        onClick = { viewModel.startEditingProduct(prod) },
                                        modifier = Modifier.testTag("edit_product_${prod.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Chỉnh sửa mặt hàng", tint = MaterialTheme.colorScheme.primary)
                                    }

                                    IconButton(
                                        onClick = { productToDelete = prod },
                                        modifier = Modifier.testTag("delete_product_${prod.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Xóa mặt hàng", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Xác nhận xóa".t()) },
            text = { Text("Bạn có chắc chắn muốn xóa mặt hàng '".t() + (productToDelete?.name ?: "") + "' không?".t()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        productToDelete?.let { viewModel.deleteProductFromDb(it) }
                        productToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_product_btn")
                ) {
                    Text("Xóa".t(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { productToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_product_btn")
                ) {
                    Text("Hủy".t())
                }
            }
        )
    }
}

// SubScreen: Tạo Mặt Hàng Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProductScreen(viewModel: MainViewModel) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var nameVal by remember { mutableStateOf("") }
    var categoryInputText by remember { mutableStateOf("") }
    var importPriceVal by remember { mutableStateOf("") }
    var sellPriceVal by remember { mutableStateOf("") }
    var trackInventoryVal by remember { mutableStateOf(true) }
    var stockQuantityVal by remember { mutableStateOf("0") }
    var imageUriVal by remember { mutableStateOf<String?>(null) }
    var rawSelectedUri by remember { mutableStateOf<Uri?>(null) }

    var expandedCatDropdown by remember { mutableStateOf(false) }

    // Media chooser launcher image picker
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            rawSelectedUri = it
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // AppBar: Nút Back (trái), Tiêu đề 'Tạo mặt hàng' (ở giữa), Nút 'LƯU' (text góc phải).
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.selectSubScreen(SubScreen.PRODUCTS_LIST) },
                        modifier = Modifier.testTag("create_product_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Hủy bỏ".t())
                    }
                },
                title = { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Tạo mặt hàng".t(), fontWeight = FontWeight.Bold) } },
                actions = {
                    TextButton(
                        onClick = {
                            if (nameVal.isBlank()) {
                                Toast.makeText(context, "Vui lòng nhập tên mặt hàng".t(), Toast.LENGTH_SHORT).show()
                            } else {
                                val cost = importPriceVal.toDoubleOrNull() ?: 0.0
                                val sell = sellPriceVal.toDoubleOrNull() ?: 0.0
                                val stock = stockQuantityVal.toIntOrNull() ?: 0
                                val catName = if (categoryInputText.isBlank()) "Không danh mục" else categoryInputText

                                viewModel.saveProduct(
                                    name = nameVal,
                                    categoryName = catName,
                                    importPrice = cost,
                                    sellPrice = sell,
                                    trackInventory = trackInventoryVal,
                                    stockQuantity = stock,
                                    imageUri = imageUriVal,
                                    onComplete = {
                                        Toast.makeText(context, "Đã lưu mặt hàng mới".t(), Toast.LENGTH_SHORT).show()
                                        viewModel.selectSubScreen(SubScreen.PRODUCTS_LIST)
                                    }
                                )
                            }
                        },
                        modifier = Modifier.testTag("save_product_btn")
                    ) {
                        Text("LƯU".t(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { paddingVal ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVal)
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // UI Input: Dùng TextFormField với kiểu underline (represented via filled textfields with transparent containers on Compose)
            item {
                TextField(
                    value = nameVal,
                    onValueChange = { nameVal = it },
                    label = { Text("Tên mặt hàng".t()) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_product_name")
                )
            }

            // 'Danh mục' (Dropdown)
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = categoryInputText,
                        onValueChange = { categoryInputText = it },
                        label = { Text("Danh mục (mặc định 'Không danh mục')".t()) },
                        placeholder = { Text("Chọn hoặc nhập danh mục mới...".t()) },
                        trailingIcon = {
                            IconButton(onClick = { expandedCatDropdown = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_product_category")
                    )

                    DropdownMenu(
                        expanded = expandedCatDropdown,
                        onDismissRequest = { expandedCatDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Không danh mục".t()) },
                            onClick = {
                                categoryInputText = "Không danh mục"
                                expandedCatDropdown = false
                            }
                        )
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    categoryInputText = cat.name
                                    expandedCatDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // 'Giá nhập' (Number)
            item {
                TextField(
                    value = TextFieldValue(text = importPriceVal, selection = TextRange(importPriceVal.length)),
                    onValueChange = { textFieldValue -> importPriceVal = textFieldValue.text.filter { ch -> ch.isDigit() } },
                    label = { Text("Giá nhập".t()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandsSeparatorVisualTransformation(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_product_import_price")
                )
            }

            // 'Giá bán' (Number, có dòng chữ chú thích nhỏ bên dưới)
            item {
                Column {
                    TextField(
                        value = TextFieldValue(text = sellPriceVal, selection = TextRange(sellPriceVal.length)),
                        onValueChange = { textFieldValue -> sellPriceVal = textFieldValue.text.filter { ch -> ch.isDigit() } },
                        label = { Text("Giá bán".t()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = ThousandsSeparatorVisualTransformation(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_product_sell_price")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Giá bán sỉ & lẻ thực tế sẽ được hiển thị công khai trên giao diện POS.".t(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }

            // Phân vùng 'Hàng tồn kho' (Tiêu đề chữ màu xanh lá)
            item {
                Column {
                    Text(
                        text = "Hàng tồn kho".t(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Theo dõi hàng trong kho".t(), fontSize = 14.sp)
                        // Switch Toggle : Default ON (Green track colored)
                        Switch(
                            checked = trackInventoryVal,
                            onCheckedChange = { trackInventoryVal = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.surface,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("toggle_track_inventory")
                        )
                    }

                    if (trackInventoryVal) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TextField(
                            value = stockQuantityVal,
                            onValueChange = { stockQuantityVal = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Trong kho".t()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_product_stock")
                        )
                    }
                }
            }

            // Phân vùng 'Đại diện trên POS' (Tiêu đề chữ màu xanh lá)
            item {
                Column {
                    Text(
                        text = "Ảnh sản phẩm".t(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { imageLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("select_image_btn")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chọn hình ảnh".t(), fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUriVal != null) {
                                AsyncImage(
                                    model = Uri.parse(imageUriVal),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    rawSelectedUri?.let { uri ->
        ImageCropperDialog(
            imageUri = uri,
            onDismiss = { rawSelectedUri = null },
            onConfirm = { croppedUri ->
                imageUriVal = croppedUri.toString()
                rawSelectedUri = null
            }
        )
    }
}

// SubScreen: Chỉnh sửa Mặt Hàng Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductScreen(viewModel: MainViewModel) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val productToEdit by viewModel.editingProduct.collectAsStateWithLifecycle()
    val importOrders by viewModel.importOrders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Initialize with editing product details
    var nameVal by remember(productToEdit) { mutableStateOf(productToEdit?.name ?: "") }
    
    // Find category name by categoryId
    val initialCatName = remember(productToEdit, categories) {
        val catId = productToEdit?.categoryId
        if (catId != null) {
            categories.find { it.id == catId }?.name ?: "Không danh mục"
        } else {
            "Không danh mục"
        }
    }
    var categoryInputText by remember(initialCatName) { mutableStateOf(initialCatName) }
    
    val previousSuppliers = remember(importOrders) {
        importOrders.map { it.importOrder.supplierName }.filter { it.isNotBlank() }.distinct()
    }
    var supplierInputText by remember(productToEdit) { mutableStateOf(productToEdit?.supplierName ?: "") }
    var expandedSupDropdown by remember { mutableStateOf(false) }
    
    val filteredSuppliers = remember(previousSuppliers, supplierInputText) {
        if (supplierInputText.isEmpty()) {
            previousSuppliers
        } else {
            previousSuppliers.filter { it.contains(supplierInputText, ignoreCase = true) && it.lowercase() != supplierInputText.lowercase() }
        }
    }
    
    var importPriceVal by remember(productToEdit) { mutableStateOf(productToEdit?.importPrice?.toInt()?.toString() ?: "0") }
    var sellPriceVal by remember(productToEdit) { mutableStateOf(productToEdit?.sellPrice?.toInt()?.toString() ?: "0") }
    var trackInventoryVal by remember(productToEdit) { mutableStateOf(productToEdit?.trackInventory ?: true) }
    var stockQuantityVal by remember(productToEdit) { mutableStateOf(productToEdit?.stockQuantity?.toString() ?: "0") }
    var imageUriVal by remember(productToEdit) { mutableStateOf(productToEdit?.imageUri) }
    var rawSelectedUri by remember { mutableStateOf<Uri?>(null) }

    var expandedCatDropdown by remember { mutableStateOf(false) }

    // Media chooser launcher image picker
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            rawSelectedUri = it
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.selectSubScreen(SubScreen.PRODUCTS_LIST) },
                        modifier = Modifier.testTag("edit_product_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Hủy bỏ".t())
                    }
                },
                title = { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Chỉnh sửa mặt hàng".t(), fontWeight = FontWeight.Bold) } },
                actions = {
                    TextButton(
                        onClick = {
                            val prodId = productToEdit?.id
                            if (prodId == null) {
                                Toast.makeText(context, "Không tìm thấy mặt hàng để chỉnh sửa".t(), Toast.LENGTH_SHORT).show()
                            } else if (nameVal.isBlank()) {
                                Toast.makeText(context, "Vui lòng nhập tên mặt hàng".t(), Toast.LENGTH_SHORT).show()
                            } else {
                                val cost = importPriceVal.toDoubleOrNull() ?: 0.0
                                val sell = sellPriceVal.toDoubleOrNull() ?: 0.0
                                val stock = stockQuantityVal.toIntOrNull() ?: 0
                                val catName = if (categoryInputText.isBlank()) "Không danh mục" else categoryInputText

                                viewModel.updateProductInDb(
                                    productId = prodId,
                                    name = nameVal,
                                    categoryName = catName,
                                    importPrice = cost,
                                    sellPrice = sell,
                                    trackInventory = trackInventoryVal,
                                    stockQuantity = stock,
                                    imageUri = imageUriVal,
                                    supplierName = supplierInputText,
                                    onComplete = {
                                        Toast.makeText(context, "Đã cập nhật thông tin mặt hàng".t(), Toast.LENGTH_SHORT).show()
                                        viewModel.selectSubScreen(SubScreen.PRODUCTS_LIST)
                                    }
                                )
                            }
                        },
                        modifier = Modifier.testTag("update_product_btn")
                    ) {
                        Text("CẬP NHẬT".t(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { paddingVal ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVal)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // UI Input: Product Name
            item {
                TextField(
                    value = nameVal,
                    onValueChange = { nameVal = it },
                    label = { Text("Tên mặt hàng".t()) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_product_name")
                )
            }

            // 'Danh mục' (Dropdown) và 'Nhà cung cấp' (Dropdown) trên cùng 1 dòng
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        TextField(
                            value = categoryInputText,
                            onValueChange = { categoryInputText = it },
                            label = { Text("Danh mục".t(), fontSize = 14.sp) },
                            placeholder = { Text("Chọn hoặc nhập danh mục mới...".t(), fontSize = 14.sp) },
                            trailingIcon = {
                                IconButton(onClick = { expandedCatDropdown = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .testTag("input_edit_product_category")
                        )

                        DropdownMenu(
                            expanded = expandedCatDropdown,
                            onDismissRequest = { expandedCatDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Không danh mục".t()) },
                                onClick = {
                                    categoryInputText = "Không danh mục"
                                    expandedCatDropdown = false
                                }
                            )
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        categoryInputText = cat.name
                                        expandedCatDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        TextField(
                            value = supplierInputText,
                            onValueChange = { 
                                supplierInputText = it 
                            },
                            label = { Text("Nhà cung cấp".t(), fontSize = 14.sp) },
                            trailingIcon = {
                                IconButton(onClick = { expandedSupDropdown = !expandedSupDropdown }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .testTag("input_edit_product_supplier")
                        )

                        DropdownMenu(
                            expanded = expandedSupDropdown && filteredSuppliers.isNotEmpty(),
                            onDismissRequest = { expandedSupDropdown = false }
                        ) {
                            filteredSuppliers.forEach { supplier ->
                                DropdownMenuItem(
                                    text = { Text(if (supplier == "Tất cả") "Tất cả".t() else supplier) },
                                    onClick = {
                                        supplierInputText = supplier
                                        expandedSupDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 'Giá nhập' (Number)
            item {
                TextField(
                    value = TextFieldValue(text = importPriceVal, selection = TextRange(importPriceVal.length)),
                    onValueChange = { textFieldValue -> importPriceVal = textFieldValue.text.filter { ch -> ch.isDigit() } },
                    label = { Text("Giá nhập".t()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ThousandsSeparatorVisualTransformation(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_product_import_price")
                )
            }

            // 'Giá bán' (Number)
            item {
                Column {
                    TextField(
                        value = TextFieldValue(text = sellPriceVal, selection = TextRange(sellPriceVal.length)),
                        onValueChange = { textFieldValue -> sellPriceVal = textFieldValue.text.filter { ch -> ch.isDigit() } },
                        label = { Text("Giá bán".t()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = ThousandsSeparatorVisualTransformation(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_product_sell_price")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Giá bán sỉ & lẻ thực tế sẽ được hiển thị công khai trên giao diện POS.".t(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }

            // Phân vùng 'Hàng tồn kho'
            item {
                Column {
                    Text(
                        text = "Hàng tồn kho".t(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Theo dõi hàng trong kho".t(), fontSize = 14.sp)
                        Switch(
                            checked = trackInventoryVal,
                            onCheckedChange = { trackInventoryVal = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.surface,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("toggle_edit_track_inventory")
                        )
                    }

                    if (trackInventoryVal) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TextField(
                            value = stockQuantityVal,
                            onValueChange = { stockQuantityVal = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Trong kho".t()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_edit_product_stock")
                        )
                    }
                }
            }

            // Phân vùng 'Đại diện trên POS'
            item {
                Column {
                    Text(
                        text = "Ảnh sản phẩm".t(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { imageLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("select_edit_image_btn")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chọn hình ảnh".t(), fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUriVal != null) {
                                AsyncImage(
                                    model = Uri.parse(imageUriVal),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    rawSelectedUri?.let { uri ->
        ImageCropperDialog(
            imageUri = uri,
            onDismiss = { rawSelectedUri = null },
            onConfirm = { croppedUri ->
                imageUriVal = croppedUri.toString()
                rawSelectedUri = null
            }
        )
    }
}

// 4. SubScreen: Categories Management (QUẢN LÝ DANH MỤC LIST SCREEN)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesListScreen(viewModel: MainViewModel) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    var categoryToDelete by remember { mutableStateOf<com.example.data.Category?>(null) }
    var categoryToEdit by remember { mutableStateOf<com.example.data.Category?>(null) }
    var editCategoryNameField by remember { mutableStateOf("") }
    var productToRemoveFromCategory by remember { mutableStateOf<com.example.data.Product?>(null) }
    var expandedCategoryIds by remember { mutableStateOf(setOf<Int>()) }

    val scrollPosition = viewModel.getScrollPosition("categories_list")
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollPosition.first,
        initialFirstVisibleItemScrollOffset = scrollPosition.second
    )
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                viewModel.saveScrollPosition("categories_list", index, offset)
            }
    }

    val uncategorizedProducts = remember(products, categories) {
        products.filter { it.categoryId == null || categories.none { cat -> cat.id == it.categoryId } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phân Loại Danh Mục".t()) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.selectSubScreen(null) },
                        modifier = Modifier.testTag("categories_list_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về")
                    }
                }
            )
        }
    ) { paddingVal ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVal)
                .padding(16.dp)
        ) {
            Button(
                onClick = { viewModel.selectSubScreen(SubScreen.CREATE_CATEGORY) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_to_create_category"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Thêm danh mục mới".t(), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(categories) { cat ->
                    val count = products.filter { it.categoryId == cat.id }.size
                    val isExpanded = expandedCategoryIds.contains(cat.id)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedCategoryIds = if (isExpanded) {
                                    expandedCategoryIds - cat.id
                                } else {
                                    expandedCategoryIds + cat.id
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Thu gọn" else "Mở rộng",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Column {
                                        Text(cat.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(
                                            text = "Có $count mặt hàng liên quan".t(),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        categoryToEdit = cat
                                        editCategoryNameField = cat.name
                                    },
                                    modifier = Modifier.testTag("edit_category_${cat.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Sửa".t(), tint = MaterialTheme.colorScheme.primary)
                                }

                                IconButton(
                                    onClick = { categoryToDelete = cat },
                                    modifier = Modifier.testTag("delete_category_${cat.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error)
                                }
                            }

                            if (isExpanded) {
                                val catProds = products.filter { it.categoryId == cat.id }
                                if (catProds.isEmpty()) {
                                    Text(
                                        text = "Không có sản phẩm nào trong danh mục này".t(),
                                        fontSize = 13.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(start = 36.dp, end = 16.dp, bottom = 14.dp)
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.padding(start = 36.dp, end = 16.dp, bottom = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        catProds.forEach { prod ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!prod.imageUri.isNullOrEmpty()) {
                                                        AsyncImage(
                                                            model = Uri.parse(prod.imageUri),
                                                            contentDescription = prod.name,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.ShoppingBag,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(prod.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Nhập: ".t() + formatCurrency(prod.importPrice), fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                                        Text(
                                                            text = "Tồn: ".t() + prod.stockQuantity,
                                                            fontSize = 11.sp,
                                                            color = if (prod.stockQuantity <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                    Text("Bán: ".t() + formatCurrency(prod.sellPrice), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IconButton(
                                                    onClick = { productToRemoveFromCategory = prod },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Xóa khỏi danh mục".t(),
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Special "Khác" item
                item {
                    val isKhácExpanded = expandedCategoryIds.contains(-1)
                    val khácCount = uncategorizedProducts.size

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedCategoryIds = if (isKhácExpanded) {
                                    expandedCategoryIds - -1
                                } else {
                                    expandedCategoryIds + -1
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isKhácExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isKhácExpanded) "Thu gọn" else "Mở rộng",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Column {
                                        Text("Khác".t(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(
                                            text = "Có $khácCount mặt hàng liên quan".t(),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            }

                            if (isKhácExpanded) {
                                if (uncategorizedProducts.isEmpty()) {
                                    Text(
                                        text = "Không có sản phẩm nào trong danh mục này".t(),
                                        fontSize = 13.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(start = 36.dp, end = 16.dp, bottom = 14.dp)
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.padding(start = 36.dp, end = 16.dp, bottom = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        uncategorizedProducts.forEach { prod ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!prod.imageUri.isNullOrEmpty()) {
                                                        AsyncImage(
                                                            model = Uri.parse(prod.imageUri),
                                                            contentDescription = prod.name,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.ShoppingBag,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(prod.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Nhập: ".t() + formatCurrency(prod.importPrice), fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                                                        Text(
                                                            text = "Tồn: ".t() + prod.stockQuantity,
                                                            fontSize = 11.sp,
                                                            color = if (prod.stockQuantity <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                    Text("Bán: ".t() + formatCurrency(prod.sellPrice), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (categoryToDelete != null) {
        val linkedProductsCount = products.filter { it.categoryId == categoryToDelete?.id }.size
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Xác nhận xóa danh mục".t()) },
            text = { 
                Text("Bạn có chắc chắn muốn xóa danh mục '".t() + (categoryToDelete?.name ?: "") + "' không?\n(Có ".t() + linkedProductsCount.toString() + " mặt hàng thuộc danh mục này)".t()) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        categoryToDelete?.let { viewModel.deleteCategoryFromDb(it) }
                        categoryToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_category_btn")
                ) {
                    Text("Xóa".t(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { categoryToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_category_btn")
                ) {
                    Text("Hủy".t())
                }
            }
        )
    }

    if (productToRemoveFromCategory != null) {
        AlertDialog(
            onDismissRequest = { productToRemoveFromCategory = null },
            title = { Text("Xác nhận xóa sản phẩm khỏi danh mục".t()) },
            text = {
                Text("Bạn có chắc chắn muốn xóa sản phẩm '${productToRemoveFromCategory?.name}' khỏi danh mục này không?".t())
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        productToRemoveFromCategory?.let { viewModel.removeProductFromCategory(it) }
                        productToRemoveFromCategory = null
                    }
                ) {
                    Text("Xóa".t(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { productToRemoveFromCategory = null }
                ) {
                    Text("Hủy".t())
                }
            }
        )
    }

    if (categoryToEdit != null) {
        AlertDialog(
            onDismissRequest = { categoryToEdit = null },
            title = { Text("Chỉnh sửa tên danh mục".t()) },
            text = {
                OutlinedTextField(
                    value = editCategoryNameField,
                    onValueChange = { editCategoryNameField = it },
                    label = { Text("Tên danh mục".t()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_category_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nameClean = editCategoryNameField.trim()
                        if (nameClean.isNotEmpty()) {
                            categoryToEdit?.let {
                                viewModel.updateCategoryInDb(it.copy(name = nameClean))
                            }
                            categoryToEdit = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_edit_category_btn")
                ) {
                    Text("Lưu".t())
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { categoryToEdit = null },
                    modifier = Modifier.testTag("cancel_edit_category_btn")
                ) {
                    Text("Hủy".t())
                }
            }
        )
    }
}

// SubScreen: Tạo Danh Mục và assign sản phẩm
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCategoryScreen(viewModel: MainViewModel) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var categoryNameVal by remember { mutableStateOf("") }
    val assignedProductIds = remember { mutableStateListOf<Int>() }

    // Filter products that do not have a category yet, or list everything
    val selectableProducts = products

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.selectSubScreen(SubScreen.CATEGORIES_LIST) },
                        modifier = Modifier.testTag("create_category_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Huỷ bỏ")
                    }
                },
                title = { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Tạo danh mục".t(), fontWeight = FontWeight.Bold) } },
                actions = {
                    TextButton(
                        onClick = {
                            if (categoryNameVal.isBlank()) {
                                Toast.makeText(context, "Vui lòng nhập tên danh mục".t(), Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.saveCategory(
                                    name = categoryNameVal,
                                    assignedProductIds = assignedProductIds,
                                    onComplete = {
                                        Toast.makeText(context, "Đã lưu danh mục mới".t(), Toast.LENGTH_SHORT).show()
                                        viewModel.selectSubScreen(SubScreen.CATEGORIES_LIST)
                                    }
                                )
                            }
                        },
                        modifier = Modifier.testTag("save_category_btn")
                    ) {
                        Text("LƯU".t(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { paddingVal ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVal)
                .padding(16.dp)
        ) {
            // UI Input: Kiểu underline (transparent background filled text field on Compose)
            TextField(
                value = categoryNameVal,
                onValueChange = { categoryNameVal = it },
                label = { Text("Tên danh mục".t()) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_category_name")
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Gán các sản phẩm đã có vào danh mục này:".t(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (selectableProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Không tìm thấy mặt hàng nào để gán.".t(), fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectableProducts) { prod ->
                        val isChecked = assignedProductIds.contains(prod.id)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) assignedProductIds.remove(prod.id)
                                    else assignedProductIds.add(prod.id)
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    val catLabel = if (prod.categoryId != null) "Đang thuộc danh mục khác" else "Chưa phân nhóm"
                                    Text(catLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { check ->
                                        if (check == true) assignedProductIds.add(prod.id)
                                        else assignedProductIds.remove(prod.id)
                                    },
                                    modifier = Modifier.testTag("checkbox_assign_${prod.id}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersListScreen(viewModel: MainViewModel) {
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var expandedCustomerName by remember { mutableStateOf<String?>(null) }
    var showRenameDialogForCustomer by remember { mutableStateOf<String?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    val scrollPosition = viewModel.getScrollPosition("customers_list")
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollPosition.first,
        initialFirstVisibleItemScrollOffset = scrollPosition.second
    )
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                viewModel.saveScrollPosition("customers_list", index, offset)
            }
    }

    val customerGroups = remember(invoices) {
        invoices.groupBy { it.invoice.storeName.trim() }
    }

    val filteredCustomers = remember(customerGroups, searchQuery) {
        if (searchQuery.isBlank()) {
            customerGroups.toList()
        } else {
            customerGroups.toList().mapNotNull { (name, invoicesList) ->
                val nameScore = getRelevanceScore(name, searchQuery)
                var maxProductScore: Double? = null
                for (inv in invoicesList) {
                    for (item in inv.items) {
                        val prodScore = getRelevanceScore(item.productName, searchQuery)
                        if (prodScore != null) {
                            if (maxProductScore == null || prodScore > maxProductScore) {
                                maxProductScore = prodScore
                            }
                        }
                    }
                }
                if (nameScore != null || maxProductScore != null) {
                    val finalScore = (nameScore ?: 0.0) * 1.5 + (maxProductScore ?: 0.0)
                    (name to invoicesList) to finalScore
                } else {
                    null
                }
            }.sortedByDescending { it.second }.map { it.first }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.selectSubScreen(null) },
                        modifier = Modifier.testTag("customers_list_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về".t())
                    }
                },
                title = { Box(modifier = Modifier.fillMaxWidth()) { Text("Quản lý Khách hàng".t(), fontWeight = FontWeight.Bold) } }
            )
        }
    ) { paddingVal ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVal)
                .padding(16.dp)
        ) {
            AnimatedVisibility(visible = listState.firstVisibleItemIndex == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Tìm kiếm khách hàng...".t()) },
                    placeholder = { Text("Nhập tên khách hàng...".t()) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("customer_search_input")
                )
            }

            if (filteredCustomers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "Chưa có khách hàng nào được lưu lịch sử mua hàng.".t() else "Không tìm thấy khách hàng phù hợp.".t(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredCustomers) { (customerName, listInvoices) ->
                        val isExpanded = expandedCustomerName == customerName
                        val totalInvoicesCount = listInvoices.size
                        val totalSpent = listInvoices.sumOf { it.invoice.totalAmount }
                        val totalProfit = listInvoices.sumOf { it.invoice.profit }
                        var showCustomerProfit by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                                .testTag("customer_card_$customerName"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isExpanded) 
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                else 
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp, 
                                if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            onClick = {
                                expandedCustomerName = if (isExpanded) null else customerName
                            }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = customerName,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isExpanded) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IconButton(
                                                    onClick = {
                                                        showRenameDialogForCustomer = customerName
                                                        renameNewName = customerName
                                                    },
                                                    modifier = Modifier.size(24.dp).testTag("edit_customer_name_btn")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Sửa tên khách hàng".t(),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (searchQuery.isNotBlank()) {
                                            val matchedProducts = listInvoices.flatMap { it.items }
                                                .map { it.productName }
                                                .distinct()
                                                .filter { getRelevanceScore(it, searchQuery) != null }
                                            if (matchedProducts.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = matchedProducts.joinToString(", "),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "$totalInvoicesCount " + " đơn hàng".t(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = formatCurrency(totalSpent),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (isExpanded) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (showCustomerProfit) {
                                                    Text(
                                                        text = "Tổng lãi: ".t() + formatCurrency(totalProfit),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                IconButton(
                                                    onClick = { showCustomerProfit = !showCustomerProfit },
                                                    modifier = Modifier.size(24.dp).padding(2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (showCustomerProfit) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = "Toggle Customer Profit",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (isExpanded) {
                                    Divider(
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )

                                    Text(
                                        text = "Danh sách đơn hàng đã lấy:".t(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    listInvoices.forEach { invoiceWithItems ->
                                        CustomerInvoiceCard(invoiceWithItems, searchQuery)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showRenameDialogForCustomer != null) {
        val oldName = showRenameDialogForCustomer!!
        AlertDialog(
            onDismissRequest = { showRenameDialogForCustomer = null },
            title = { Text("Sửa tên khách hàng".t()) },
            text = {
                Column {
                    Text(text = "Nhập tên mới cho khách hàng %s:".t().format(oldName), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameNewName,
                        onValueChange = { renameNewName = it },
                        label = { Text("Tên khách hàng mới".t()) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rename_customer_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nameClean = renameNewName.trim()
                        if (nameClean.isNotEmpty()) {
                            viewModel.updateCustomerName(oldName, nameClean)
                            if (expandedCustomerName == oldName) {
                                expandedCustomerName = nameClean
                            }
                            showRenameDialogForCustomer = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_rename_customer_btn")
                ) {
                    Text("Lưu".t(), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRenameDialogForCustomer = null },
                    modifier = Modifier.testTag("cancel_rename_customer_btn")
                ) {
                    Text("Hủy".t())
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryManagementScreen(viewModel: MainViewModel) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    val scrollPosition = viewModel.getScrollPosition("inventory_list")
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollPosition.first,
        initialFirstVisibleItemScrollOffset = scrollPosition.second
    )
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                viewModel.saveScrollPosition("inventory_list", index, offset)
            }
    }

    var sortOption by remember { mutableStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Filter and sort products
    val filteredProducts = remember(products, searchQuery, sortOption) {
        val baseList = if (searchQuery.isBlank()) {
            products
        } else {
            products.mapNotNull { prod ->
                val score = getRelevanceScore(prod.name, searchQuery)
                if (score != null) prod to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }
        
        when (sortOption) {
            1 -> {
                val inStock = baseList.filter { it.stockQuantity > 0 }.sortedBy { it.stockQuantity * it.importPrice }
                val outOfStock = baseList.filter { it.stockQuantity <= 0 }
                inStock + outOfStock
            }
            2 -> {
                val inStock = baseList.filter { it.stockQuantity > 0 }.sortedByDescending { it.stockQuantity * it.importPrice }
                val outOfStock = baseList.filter { it.stockQuantity <= 0 }
                inStock + outOfStock
            }
            else -> {
                if (searchQuery.isBlank()) {
                    baseList.sortedByDescending { it.stockQuantity }
                } else {
                    baseList
                }
            }
        }
    }

    val grandTotal = remember(filteredProducts) {
        filteredProducts.sumOf { it.stockQuantity * it.importPrice }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản lý hàng tồn".t()) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.selectSubScreen(null) },
                        modifier = Modifier.testTag("inventory_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về".t())
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Lọc".t(),
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Tồn kho từ nhiều đến ít".t(), fontWeight = if (sortOption == 0) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { sortOption = 0; showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Tổng tồn từ thấp đến cao".t(), fontWeight = if (sortOption == 1) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { sortOption = 1; showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Tổng tồn từ cao đến thấp".t(), fontWeight = if (sortOption == 2) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { sortOption = 2; showSortMenu = false }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Under screen total sum inside a card or elevated surface
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Tổng tiền tồn kho".t(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "(" + filteredProducts.size + " " + " mặt hàng".t() + ")",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = formatCurrency(grandTotal),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("inventory_grand_total")
                    )
                }
            }
        }
    ) { paddingVal ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVal)
        ) {
            // Search input field
            AnimatedVisibility(visible = listState.firstVisibleItemIndex == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("inventory_search_input"),
                    placeholder = { Text("Tìm kiếm sản phẩm...".t()) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa tìm kiếm".t())
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (products.isEmpty()) "Không có sản phẩm nào tồn kho.".t() else "Không tìm thấy sản phẩm phù hợp.".t(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredProducts) { prod ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Product Image or Icon
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!prod.imageUri.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = Uri.parse(prod.imageUri),
                                            contentDescription = prod.name,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.ShoppingBag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                // Product Details (Name, qty, import price)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prod.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Tồn kho: ".t() + prod.stockQuantity,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (prod.stockQuantity <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "|",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Text(
                                            text = "Giá nhập: ".t() + formatCurrency(prod.importPrice),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                // Product Total value
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Tổng tồn".t(),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatCurrency(prod.stockQuantity * prod.importPrice),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class CustomerDebtGroup(
    val customerName: String,
    val invoices: List<InvoiceWithItems>,
    val nearestScheduledTime: Long,
    val totalAmount: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtManagementScreen(viewModel: MainViewModel) {
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val prefs = remember { context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val scrollPosition = viewModel.getScrollPosition("debt_management")
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollPosition.first,
        initialFirstVisibleItemScrollOffset = scrollPosition.second
    )
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                viewModel.saveScrollPosition("debt_management", index, offset)
            }
    }

    // Group active debt invoices by customer name, sorted by scheduled payment date (nearest first)
    val customerGroups = remember(invoices, refreshTrigger) {
        val activeDebts = invoices.filter { combined ->
            prefs.getSafeBoolean("invoice_debt_${combined.invoice.id}", false) &&
            (combined.invoice.totalAmount - prefs.getSafeFloat("invoice_debt_paid_${combined.invoice.id}", 0f).toDouble() > 0.0)
        }
        
        // Group by customerName (fallback to storeName or default name if blank)
        val groupedMap = activeDebts.groupBy { combined ->
            val name = combined.invoice.customerName ?: combined.invoice.storeName
            if (name.isNullOrBlank()) "Khách lẻ".t() else name
        }
        
        groupedMap.map { (customerName, list) ->
            val times = list.map { combined ->
                prefs.getSafeLong("invoice_debt_time_${combined.invoice.id}", 0L)
            }.filter { it > 0L }
            
            // Sort by earliest scheduled payment time
            val minTime = if (times.isNotEmpty()) times.minOrNull() ?: 0L else 0L
            
            val totalAmt = list.sumOf { combined ->
                val paidAmt = prefs.getSafeFloat("invoice_debt_paid_${combined.invoice.id}", 0f).toDouble()
                maxOf(0.0, combined.invoice.totalAmount - paidAmt)
            }
            
            CustomerDebtGroup(
                customerName = customerName,
                invoices = list,
                nearestScheduledTime = minTime,
                totalAmount = totalAmt
            )
        }.filter { it.totalAmount > 0.0 }
        .sortedBy { group ->
            if (group.nearestScheduledTime == 0L) Long.MAX_VALUE else group.nearestScheduledTime
        }
    }

    // Grand total of all active debt invoices
    val grandTotalDebtCount = remember(customerGroups) {
        customerGroups.sumOf { it.invoices.size }
    }
    val grandTotalDebtAmount = remember(customerGroups) {
        customerGroups.sumOf { it.totalAmount }
    }

    var selectedCustomerGroup by remember { mutableStateOf<CustomerDebtGroup?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Quản lý công nợ".t()) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.selectSubScreen(null) },
                        modifier = Modifier.testTag("debt_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về".t())
                    }
                }
            )
        },
        bottomBar = {
            // "tổng công nợ" section at the bottom of the screen
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Tổng công nợ".t(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Có $grandTotalDebtCount đơn hàng nợ".t(),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = formatCurrency(grandTotalDebtAmount),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    ) { paddingVal ->
        if (customerGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingVal),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Không có khách hàng nào đang nợ.".t(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingVal)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(customerGroups) { group ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCustomerGroup = group }
                            .testTag("debt_customer_${group.customerName.hashCode()}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.customerName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Đơn hàng nợ: ".t() + "${group.invoices.size}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Hẹn trả gần nhất: ".t() + if (group.nearestScheduledTime > 0L) formatDate(group.nearestScheduledTime) else "Chưa hẹn".t(),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = formatCurrency(group.totalAmount),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // Customer details dialog for debt management (displays all invoices under the customer)
    selectedCustomerGroup?.let { group ->
        Dialog(
            onDismissRequest = { selectedCustomerGroup = null }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { focusManager.clearFocus() }
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header of detail dialog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chi Tiết Công Nợ".t(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { selectedCustomerGroup = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng".t())
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Customer and general info
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Khách hàng: ".t() + group.customerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tổng số hóa đơn nợ: ".t() + "${group.invoices.size}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tổng tiền nợ: ".t() + formatCurrency(group.totalAmount),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Small sub-section "Thanh toán trước"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Thanh toán trước".t(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                var payAmountStr by remember { mutableStateOf("") }
                                
                                OutlinedTextField(
                                    value = payAmountStr,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() }) {
                                            payAmountStr = input
                                        }
                                    },
                                    label = { Text("Nhập số tiền thanh toán".t()) },
                                    placeholder = { Text("Ví dụ: 500000".t()) },
                                    suffix = { Text("đ") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                    ),
                                    visualTransformation = ThousandsSeparatorVisualTransformation(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("debt_pay_input"),
                                    singleLine = true
                                )
                                
                                Spacer(modifier = Modifier.height(10.dp))

                                val onPayClick = { method: String ->
                                    focusManager.clearFocus()
                                    val amt = payAmountStr.toDoubleOrNull() ?: 0.0
                                    if (amt <= 0.0) {
                                        ToastUtils.show(context, "Vui lòng nhập số tiền hợp lệ".t())
                                    } else if (amt > group.totalAmount) {
                                        ToastUtils.show(context, "Số tiền thanh toán vượt quá tổng nợ hiện tại!".t())
                                    } else {
                                        // Process prepayment distribution (waterfall)
                                        val editor = prefs.edit()
                                        var remainingPayment = amt
                                        val sortedInvoices = group.invoices.sortedBy { it.invoice.timestamp }
                                        
                                        for (combined in sortedInvoices) {
                                            if (remainingPayment <= 0.0) break
                                            
                                            val invoice = combined.invoice
                                            val currentPaid = prefs.getSafeFloat("invoice_debt_paid_${invoice.id}", 0f).toDouble()
                                            val netDebt = maxOf(0.0, invoice.totalAmount - currentPaid)
                                            
                                            if (netDebt <= 0.0) continue
                                            
                                            // Set the payment method of this invoice to match the payment option selected ("TM" or "CK")
                                            editor.putString("invoice_payment_method_${invoice.id}", method)
                                            
                                            val paymentAmountAdded = minOf(remainingPayment, netDebt)
                                            if (method == "TM") {
                                                val prevPaidTm = prefs.getSafeFloat("invoice_debt_paid_tm_${invoice.id}", 0f)
                                                editor.putFloat("invoice_debt_paid_tm_${invoice.id}", (prevPaidTm + paymentAmountAdded).toFloat())
                                            } else if (method == "CK") {
                                                val prevPaidCk = prefs.getSafeFloat("invoice_debt_paid_ck_${invoice.id}", 0f)
                                                editor.putFloat("invoice_debt_paid_ck_${invoice.id}", (prevPaidCk + paymentAmountAdded).toFloat())
                                            }
                                            
                                            if (remainingPayment >= netDebt) {
                                                editor.putFloat("invoice_debt_paid_${invoice.id}", invoice.totalAmount.toFloat())
                                                editor.putBoolean("invoice_debt_${invoice.id}", false)
                                                remainingPayment -= netDebt
                                            } else {
                                                val newPaid = currentPaid + remainingPayment
                                                editor.putFloat("invoice_debt_paid_${invoice.id}", newPaid.toFloat())
                                                remainingPayment = 0.0
                                            }
                                        }
                                        editor.apply()
                                        refreshTrigger++
                                        payAmountStr = ""
                                        ToastUtils.show(context, "Đã thanh toán trước thành công số tiền: ".t() + formatCurrency(amt))
                                        
                                        // Real-time update local group state to refresh Dialog
                                        val updatedInvoices = group.invoices.mapNotNull { combined ->
                                            val invPaid = prefs.getSafeFloat("invoice_debt_paid_${combined.invoice.id}", 0f).toDouble()
                                            val invNetDebt = maxOf(0.0, combined.invoice.totalAmount - invPaid)
                                            if (invNetDebt <= 0.0) null else combined
                                        }
                                        val updatedGroupTotal = updatedInvoices.sumOf { combined ->
                                            val invPaid = prefs.getSafeFloat("invoice_debt_paid_${combined.invoice.id}", 0f).toDouble()
                                            maxOf(0.0, combined.invoice.totalAmount - invPaid)
                                        }
                                        if (updatedGroupTotal <= 0.0 || updatedInvoices.isEmpty()) {
                                            selectedCustomerGroup = null
                                        } else {
                                            selectedCustomerGroup = group.copy(
                                                invoices = updatedInvoices,
                                                totalAmount = updatedGroupTotal
                                            )
                                        }
                                    }
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onPayClick("TM") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("debt_pay_tm_btn"),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Thanh toán TM".t(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = { onPayClick("CK") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("debt_pay_ck_btn"),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Thanh toán CK".t(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Danh sách đơn hàng nợ:".t(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        group.invoices.forEach { combined ->
                            val invoice = combined.invoice
                            val items = combined.items
                            val scheduledTime = prefs.getSafeLong("invoice_debt_time_${invoice.id}", 0L)
                            val paidAmt = prefs.getSafeFloat("invoice_debt_paid_${invoice.id}", 0f).toDouble()
                            val netDebt = maxOf(0.0, invoice.totalAmount - paidAmt)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Mã HD: #".t() + invoice.id,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "Còn nợ: ".t() + formatCurrency(netDebt),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            if (paidAmt > 0.0) {
                                                Text(
                                                    text = "Đã trả: ".t() + formatCurrency(paidAmt),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Thời gian mua: ".t() + formatDate(invoice.timestamp),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Hẹn trả: ".t() + if (scheduledTime > 0L) formatDate(scheduledTime) else "Chưa hẹn".t(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error
                                    )

                                    if (!invoice.customerPhone.isNullOrBlank()) {
                                        Text(
                                            text = "SĐT: ".t() + invoice.customerPhone,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    Spacer(modifier = Modifier.height(4.dp))

                                    items.forEach { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${item.productName} (x${item.quantity})",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = formatCurrency(item.sellPrice * item.quantity),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons "Xóa nợ" and "Đóng"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedCustomerGroup = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Đóng".t())
                        }

                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Xóa nợ".t(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for clear debt
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Xác nhận xóa nợ".t()) },
            text = { Text("Bạn có chắc chắn muốn xóa toàn bộ công nợ của khách hàng %s? Toàn bộ các hóa đơn nợ của khách hàng này sẽ được xóa.".t().format(selectedCustomerGroup?.customerName ?: "")) },
            confirmButton = {
                Button(
                    onClick = {
                        selectedCustomerGroup?.let { group ->
                            group.invoices.forEach { combined ->
                                val invoice = combined.invoice
                                
                                // Delete/disable debt status in SharedPreferences
                                prefs.edit()
                                    .putBoolean("invoice_debt_${invoice.id}", false)
                                    .putFloat("invoice_debt_paid_${invoice.id}", 0f)
                                    .putFloat("invoice_debt_paid_tm_${invoice.id}", 0f)
                                    .putFloat("invoice_debt_paid_ck_${invoice.id}", 0f)
                                    .apply()
                                
                                // Try to delete calendar event if one was stored
                                val eventId = prefs.getSafeLong("invoice_calendar_event_${invoice.id}", -1L)
                                if (eventId != -1L) {
                                    try {
                                        val cr = context.contentResolver
                                        val uri = android.content.ContentUris.withAppendedId(
                                            android.provider.CalendarContract.Events.CONTENT_URI,
                                            eventId
                                        )
                                        cr.delete(uri, null, null)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            
                            Toast.makeText(context, "Đã xóa toàn bộ công nợ thành công!".t(), Toast.LENGTH_SHORT).show()
                            refreshTrigger++
                        }
                        showDeleteConfirmDialog = false
                        selectedCustomerGroup = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xác nhận".t(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Hủy".t())
                }
            }
        )
    }
}

fun android.content.SharedPreferences.getSafeFloat(key: String, defaultValue: Float): Float {
    return try {
        this.getFloat(key, defaultValue)
    } catch (e: Exception) {
        val raw = this.all[key]
        if (raw is Number) raw.toFloat()
        else raw?.toString()?.toFloatOrNull() ?: defaultValue
    }
}

fun android.content.SharedPreferences.getSafeLong(key: String, defaultValue: Long): Long {
    return try {
        this.getLong(key, defaultValue)
    } catch (e: Exception) {
        val raw = this.all[key]
        if (raw is Number) raw.toLong()
        else raw?.toString()?.toLongOrNull() ?: defaultValue
    }
}

fun android.content.SharedPreferences.getSafeBoolean(key: String, defaultValue: Boolean): Boolean {
    return try {
        this.getBoolean(key, defaultValue)
    } catch (e: Exception) {
        val raw = this.all[key]
        if (raw is Boolean) raw
        else if (raw is Number) raw.toInt() != 0
        else raw?.toString()?.lowercase()?.trim() == "true" || raw?.toString() == "1"
    }
}

@Composable
fun ZoomableImageDialog(
    imageUri: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Prevents clicking the background when tapping on the image container
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = Uri.parse(imageUri),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Đóng".t(),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

object ToastUtils {
    private var lastToast: Toast? = null
    fun show(context: Context, text: String, duration: Int = Toast.LENGTH_SHORT) {
        lastToast?.cancel()
        val toast = Toast.makeText(context.applicationContext, text, duration)
        lastToast = toast
        toast.show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportManagementScreen(viewModel: MainViewModel) {
    val importOrders by viewModel.importOrders.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showCreateOrderDialog by remember { mutableStateOf(false) }
    var expandedOrderId by remember { mutableStateOf<Int?>(null) }
    var editingOrder by remember { mutableStateOf<ImportOrderWithItems?>(null) }
    var deletingOrder by remember { mutableStateOf<ImportOrderWithItems?>(null) }
    
    var showReportPanel by remember { mutableStateOf(false) }
    var fromDateMillis by remember { 
        mutableStateOf(
            java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    var toDateMillis by remember {
        mutableStateOf(
            java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }.timeInMillis
        )
    }
    var confirmedDateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    
    var selectedSuppliers by remember { mutableStateOf(setOf("Tất cả")) }
    var confirmedSuppliers by remember { mutableStateOf(setOf("Tất cả")) }
    var expandedSupplierDropdown by remember { mutableStateOf(false) }

    val allSuppliers = remember(importOrders) {
        listOf("Tất cả") + importOrders.map { it.importOrder.supplierName }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val scrollPosition = viewModel.getScrollPosition("import_management")
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollPosition.first,
        initialFirstVisibleItemScrollOffset = scrollPosition.second
    )
    LaunchedEffect(listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (index, offset) ->
                viewModel.saveScrollPosition("import_management", index, offset)
            }
    }

    val displayOrders = remember(importOrders) {
        importOrders.sortedWith(
            compareByDescending<ImportOrderWithItems> { it.importOrder.isDraft }
                .thenByDescending { it.importOrder.timestamp }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản lý nhập hàng".t(), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.selectSubScreen(null) },
                        modifier = Modifier.testTag("import_management_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về".t())
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showReportPanel = !showReportPanel },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("lập báo cáo nhập hàng".t(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateOrderDialog = true },
                modifier = Modifier.testTag("add_import_order_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Lập đơn nhập hàng".t())
            }
        }
    ) { paddingVal ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVal)
        ) {
            if (showReportPanel) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("vi", "VN")) }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // "Từ ngày" Button
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Từ ngày".t(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            OutlinedButton(
                                onClick = {
                                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = fromDateMillis }
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val newCal = java.util.Calendar.getInstance().apply {
                                                set(java.util.Calendar.YEAR, year)
                                                set(java.util.Calendar.MONTH, month)
                                                set(java.util.Calendar.DAY_OF_MONTH, day)
                                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                                set(java.util.Calendar.MINUTE, 0)
                                                set(java.util.Calendar.SECOND, 0)
                                                set(java.util.Calendar.MILLISECOND, 0)
                                            }
                                            fromDateMillis = newCal.timeInMillis
                                        },
                                        cal.get(java.util.Calendar.YEAR),
                                        cal.get(java.util.Calendar.MONTH),
                                        cal.get(java.util.Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = dateFormat.format(java.util.Date(fromDateMillis)),
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        
                        // "Đến ngày" Button
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Đến ngày".t(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            OutlinedButton(
                                onClick = {
                                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = toDateMillis }
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val newCal = java.util.Calendar.getInstance().apply {
                                                set(java.util.Calendar.YEAR, year)
                                                set(java.util.Calendar.MONTH, month)
                                                set(java.util.Calendar.DAY_OF_MONTH, day)
                                                set(java.util.Calendar.HOUR_OF_DAY, 23)
                                                set(java.util.Calendar.MINUTE, 59)
                                                set(java.util.Calendar.SECOND, 59)
                                                set(java.util.Calendar.MILLISECOND, 999)
                                            }
                                            toDateMillis = newCal.timeInMillis
                                        },
                                        cal.get(java.util.Calendar.YEAR),
                                        cal.get(java.util.Calendar.MONTH),
                                        cal.get(java.util.Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = dateFormat.format(java.util.Date(toDateMillis)),
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    
                    
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = expandedSupplierDropdown,
                        onExpandedChange = { expandedSupplierDropdown = !expandedSupplierDropdown },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val displayText = if (selectedSuppliers.contains("Tất cả")) "Tất cả".t() else selectedSuppliers.joinToString(", ")
                        OutlinedTextField(
                            value = displayText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Chọn nhà cung cấp".t()) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSupplierDropdown) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSupplierDropdown,
                            onDismissRequest = { expandedSupplierDropdown = false }
                        ) {
                            allSuppliers.forEach { supplier ->
                                val isSelected = selectedSuppliers.contains(supplier)
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = null,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(if (supplier == "Tất cả") "Tất cả".t() else supplier)
                                        }
                                    },
                                    onClick = {
                                        if (supplier == "Tất cả") {
                                            selectedSuppliers = setOf("Tất cả")
                                        } else {
                                            val newSet = selectedSuppliers.toMutableSet()
                                            newSet.remove("Tất cả")
                                            if (isSelected) {
                                                newSet.remove(supplier)
                                                if (newSet.isEmpty()) newSet.add("Tất cả")
                                            } else {
                                                newSet.add(supplier)
                                            }
                                            selectedSuppliers = newSet
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Search Button
                    Button(
                        onClick = {
                            confirmedDateRange = Pair(fromDateMillis, toDateMillis)
                            confirmedSuppliers = selectedSuppliers
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Tìm kiếm".t(), fontSize = 14.sp)
                    }
                    
                    val reportStats = remember(confirmedDateRange, confirmedSuppliers, importOrders) {
                        val range = confirmedDateRange
                        if (range == null) null
                        else {
                            val (start, end) = range
                            val filtered = importOrders.filter { 
                                it.importOrder.timestamp in start..end && 
                                !it.importOrder.isDraft &&
                                (confirmedSuppliers.contains("Tất cả") || confirmedSuppliers.contains(it.importOrder.supplierName))
                            }
                            val totalOrders = filtered.size
                            val totalAmount = filtered.sumOf { it.importOrder.totalAmount }
                            Pair(totalOrders, totalAmount)
                        }
                    }
                    
                    reportStats?.let { (orders, amount) ->
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        
                        Text(
                            text = "Kết quả báo cáo".t(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Tổng đơn nhập".t(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$orders",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            
                            Card(
                                modifier = Modifier.weight(1.2f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Tổng tiền nhập".t(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatCurrency(amount),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (importOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Chưa có đơn nhập hàng nào".t(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Vui lòng ấn nút + ở góc phải bên dưới để lập đơn hàng nhập mới.".t(),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayOrders, key = { it.importOrder.id }) { orderWithItems ->
                    val order = orderWithItems.importOrder
                    val items = orderWithItems.items
                    val isExpanded = expandedOrderId == order.id

                    val density = LocalDensity.current
                    val maxRevealPx = with(density) { -140.dp.toPx() }
                    var offsetX by remember(order.id) { mutableStateOf(0f) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                    ) {
                        // Background actions visible on swiping left
                        Row(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Edit Pen (Bút)
                            IconButton(
                                onClick = {
                                    offsetX = 0f
                                    editingOrder = orderWithItems
                                },
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(70.dp)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    .testTag("edit_import_order_btn_${order.id}")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Sửa".t(),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text("Sửa".t(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Delete Trash (Thùng rác)
                            IconButton(
                                onClick = {
                                    offsetX = 0f
                                    deletingOrder = orderWithItems
                                },
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(70.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .testTag("delete_import_order_btn_${order.id}")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Xóa".t(),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text("Xóa".t(), fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Foreground Content Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(offsetX.toInt(), 0) }
                                .pointerInput(order.id) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            if (offsetX < maxRevealPx / 2) {
                                                offsetX = maxRevealPx
                                            } else {
                                                offsetX = 0f
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            offsetX = (offsetX + dragAmount).coerceIn(maxRevealPx, 0f)
                                        }
                                    )
                                }
                                .clickable {
                                    if (offsetX != 0f) {
                                        offsetX = 0f
                                    } else {
                                        expandedOrderId = if (isExpanded) null else order.id
                                    }
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (order.isDraft) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = order.supplierName.ifBlank { "Nhà cung cấp ẩn danh".t() },
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (order.isDraft) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "NHÁP".t(),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("vi", "VN"))
                                        Text(
                                            text = sdf.format(java.util.Date(order.timestamp)),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatCurrency(order.totalAmount),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${items.size} " + "mặt hàng".t(),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                if (isExpanded) {
                                    Divider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )

                                    Text(
                                        text = "Chi tiết đơn hàng:".t(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items.forEach { item ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${item.productName} (x${item.quantity})",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = formatCurrency(item.importPrice * item.quantity),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (order.isDraft) {
                                            Button(
                                                onClick = {
                                                    viewModel.completeImportOrder(orderWithItems) {
                                                        Toast.makeText(context, "Đã xác nhận hoàn thành đơn nhập hàng!".t(), Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                modifier = Modifier.height(34.dp).testTag("complete_draft_order_btn_${order.id}")
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("HOÀN THÀNH".t(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        TextButton(
                                            onClick = {
                                                editingOrder = orderWithItems
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier.height(34.dp).testTag("expand_edit_import_order_btn_${order.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("SỬA ĐƠN".t(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = {
                                                deletingOrder = orderWithItems
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Xóa đơn", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        } // Closing the Column that wraps Scaffold content
    }

    if (showCreateOrderDialog) {
        CreateImportOrderDialog(
            viewModel = viewModel,
            products = products,
            categories = categories,
            onDismiss = { showCreateOrderDialog = false },
            onSuccess = {
                showCreateOrderDialog = false
                Toast.makeText(context, "Lập đơn nhập hàng thành công!".t(), Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    kotlinx.coroutines.delay(200)
                    listState.animateScrollToItem(0)
                }
            }
        )
    }

    if (editingOrder != null) {
        EditImportOrderDialog(
            viewModel = viewModel,
            orderWithItems = editingOrder!!,
            products = products,
            categories = categories,
            onDismiss = { editingOrder = null },
            onSuccess = {
                editingOrder = null
                Toast.makeText(context, "Cập nhật đơn nhập hàng thành công!".t(), Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    kotlinx.coroutines.delay(200)
                    listState.animateScrollToItem(0)
                }
            }
        )
    }

    if (deletingOrder != null) {
        val orderWithItems = deletingOrder!!
        AlertDialog(
            onDismissRequest = { deletingOrder = null },
            title = { Text("Xác nhận xóa".t(), fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc chắn muốn xóa đơn nhập hàng này? Hành động này sẽ hoàn tác và khôi phục (giảm) số lượng tồn kho tương ứng của các sản phẩm trong đơn.".t()) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteImportOrder(orderWithItems)
                        Toast.makeText(context, "Đã xóa đơn nhập hàng và cập nhật lại tồn kho".t(), Toast.LENGTH_SHORT).show()
                        deletingOrder = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("XÓA".t())
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingOrder = null }) {
                    Text("HỦY".t())
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateImportOrderDialog(
    viewModel: MainViewModel,
    products: List<Product>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val prefs = remember(context) { context.getSharedPreferences("proodos_prefs", Context.MODE_PRIVATE) }

    val invoices by viewModel.invoices.collectAsStateWithLifecycle()

    val tongTienMat = invoices.sumOf { invoiceWithItems ->
        val id = invoiceWithItems.invoice.id
        val paymentMethodKey = "invoice_payment_method_$id"
        val paymentMethod = prefs.getString(paymentMethodKey, "TM") ?: "TM"
        
        val isCurrentlyDebt = prefs.getBoolean("invoice_debt_$id", false)
        val paidDebtAmount = prefs.getSafeFloat("invoice_debt_paid_$id", 0f).toDouble()
        val isDebtInvoice = isCurrentlyDebt || paidDebtAmount > 0.0
        
        if (isDebtInvoice) {
            prefs.getSafeFloat("invoice_debt_paid_tm_$id", 0f).toDouble()
        } else {
            if (paymentMethod == "TM") invoiceWithItems.invoice.totalAmount else 0.0
        }
    }

    val tongTaiKhoan = invoices.sumOf { invoiceWithItems ->
        val id = invoiceWithItems.invoice.id
        val paymentMethodKey = "invoice_payment_method_$id"
        val paymentMethod = prefs.getString(paymentMethodKey, "TM") ?: "TM"
        
        val isCurrentlyDebt = prefs.getBoolean("invoice_debt_$id", false)
        val paidDebtAmount = prefs.getSafeFloat("invoice_debt_paid_$id", 0f).toDouble()
        val isDebtInvoice = isCurrentlyDebt || paidDebtAmount > 0.0
        
        if (isDebtInvoice) {
            prefs.getSafeFloat("invoice_debt_paid_ck_$id", 0f).toDouble()
        } else {
            if (paymentMethod == "CK") invoiceWithItems.invoice.totalAmount else 0.0
        }
    }

    val manualCashAdjustment = prefs.getSafeFloat("manual_cash_adjustment", 0f).toDouble()
    val manualAccountAdjustment = prefs.getSafeFloat("manual_account_adjustment", 0f).toDouble()

    val finalTienMat = tongTienMat + manualCashAdjustment
    val finalTaiKhoan = tongTaiKhoan + manualAccountAdjustment

    var supplierName by remember { mutableStateOf("") }
    var selectedItems by remember { mutableStateOf(emptyList<ImportOrderItemTemp>()) }

    var showAddItemDialog by remember { mutableStateOf(false) }

    var supplierDropdownExpanded by remember { mutableStateOf(false) }
    val importOrders by viewModel.importOrders.collectAsStateWithLifecycle(emptyList())
    val previousSuppliers = remember(importOrders) {
        importOrders.map { it.importOrder.supplierName }
            .filter { it.isNotBlank() && it != "Nhà cung cấp".t() && it != "Nhà cung cấp" }
            .distinct()
    }
    val filteredSuppliers = remember(previousSuppliers, supplierName) {
        if (supplierName.isEmpty()) {
            previousSuppliers
        } else {
            previousSuppliers.filter { it.contains(supplierName, ignoreCase = true) && it.lowercase() != supplierName.lowercase() }
        }
    }
    var itemToEdit by remember { mutableStateOf<ImportOrderItemTemp?>(null) }

    var cashInput by remember { mutableStateOf("0") }
    var bankTransferInput by remember { mutableStateOf("0") }
    var showLimitConfirmation by remember { mutableStateOf(false) }
    var showExitWarningDialog by remember { mutableStateOf(false) }

    val orderTotal = selectedItems.sumOf { it.quantity * it.importPrice }

    val performCompleteOrder = { cashPaid: Double, transferPaid: Double ->
        val currentManualCash = prefs.getSafeFloat("manual_cash_adjustment", 0f).toDouble()
        val currentManualAccount = prefs.getSafeFloat("manual_account_adjustment", 0f).toDouble()

        prefs.edit()
            .putFloat("manual_cash_adjustment", (currentManualCash - cashPaid).toFloat())
            .putFloat("manual_account_adjustment", (currentManualAccount - transferPaid).toFloat())
            .apply()

        val supplier = if (supplierName.isBlank()) "Nhà cung cấp".t() else supplierName
        viewModel.createImportOrder(
            supplierName = supplier,
            items = selectedItems,
            cashPaid = cashPaid,
            transferPaid = transferPaid,
            onComplete = onSuccess
        )
    }

    LaunchedEffect(orderTotal, finalTienMat) {
        val maxCash = maxOf(0.0, finalTienMat)
        if (orderTotal <= maxCash) {
            cashInput = formatDotsInput(orderTotal.toLong().toString())
            bankTransferInput = "0"
        } else {
            cashInput = formatDotsInput(maxCash.toLong().toString())
            val remaining = maxOf(0.0, orderTotal - maxCash)
            bankTransferInput = formatDotsInput(remaining.toLong().toString())
        }
    }

    Dialog(
        onDismissRequest = {
            if (selectedItems.isNotEmpty()) {
                showExitWarningDialog = true
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Lập đơn nhập hàng".t(), fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (selectedItems.isNotEmpty()) {
                                    showExitWarningDialog = true
                                } else {
                                    onDismiss()
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng".t())
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .padding(16.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        }
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = supplierName,
                            onValueChange = { 
                                supplierName = it
                                supplierDropdownExpanded = true
                            },
                            label = { Text("Tên nhà cung cấp".t()) },
                            placeholder = { Text("Nhập tên nhà cung cấp...".t()) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_supplier_input")
                                .onFocusChanged { focusState ->
                                    supplierDropdownExpanded = focusState.isFocused && filteredSuppliers.isNotEmpty()
                                },
                            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                            singleLine = true
                        )

                        if (filteredSuppliers.isNotEmpty()) {
                            DropdownMenu(
                                expanded = supplierDropdownExpanded,
                                onDismissRequest = { supplierDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f),
                                properties = PopupProperties(focusable = false)
                            ) {
                                filteredSuppliers.forEach { supplier ->
                                    DropdownMenuItem(
                                        text = { Text(if (supplier == "Tất cả") "Tất cả".t() else supplier) },
                                        onClick = {
                                            supplierName = supplier
                                            supplierDropdownExpanded = false
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Danh sách hàng nhập".t(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (selectedItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(24.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { focusManager.clearFocus() })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Chưa có mặt hàng nào trong đơn".t(),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { focusManager.clearFocus() })
                                },
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedItems) { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            itemToEdit = item
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.productName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row {
                                                Text(
                                                    text = "${item.quantity} x " + formatCurrency(item.importPrice),
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "(Bấm để sửa)".t(),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End,
                                            modifier = Modifier.wrapContentWidth()
                                        ) {
                                            Text(
                                                text = formatCurrency(item.quantity * item.importPrice),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 4.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Clip
                                            )
                                            IconButton(
                                                onClick = {
                                                    selectedItems = selectedItems.filterNot { it.productId == item.productId }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Xóa",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Button "+ Thêm mặt hàng" above "Tổng giá trị đơn nhập"
                    Button(
                        onClick = { showAddItemDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_item_to_order_inline_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Thêm mặt hàng".t(), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tổng giá trị đơn nhập:".t(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatCurrency(orderTotal),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Payment inputs: "Tiền mặt" and "Chuyển khoản" side-by-side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = TextFieldValue(text = cashInput, selection = TextRange(cashInput.length)),
                            onValueChange = { textFieldValue ->
                                val newValue = textFieldValue.text
                                val cleanValue = newValue.replace(".", "")
                                val cashVal = cleanValue.toDoubleOrNull() ?: 0.0
                                cashInput = formatDotsInput(cleanValue)
                                val transferVal = maxOf(0.0, orderTotal - cashVal)
                                bankTransferInput = formatDotsInput(transferVal.toLong().toString())
                            },
                            label = { Text("Tiền mặt".t()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_payment_cash_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = TextFieldValue(text = bankTransferInput, selection = TextRange(bankTransferInput.length)),
                            onValueChange = { textFieldValue ->
                                val newValue = textFieldValue.text
                                val cleanValue = newValue.replace(".", "")
                                val transferVal = cleanValue.toDoubleOrNull() ?: 0.0
                                bankTransferInput = formatDotsInput(cleanValue)
                                val cashVal = maxOf(0.0, orderTotal - transferVal)
                                cashInput = formatDotsInput(cashVal.toLong().toString())
                            },
                            label = { Text("Chuyển khoản".t()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_payment_transfer_input"),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Button "Hoàn thành đơn" at the bottom
                    Button(
                        onClick = {
                            val cashPaid = cashInput.replace(".", "").toDoubleOrNull() ?: 0.0
                            val transferPaid = bankTransferInput.replace(".", "").toDoubleOrNull() ?: 0.0

                            if (cashPaid > finalTienMat || transferPaid > finalTaiKhoan) {
                                showLimitConfirmation = true
                            } else {
                                performCompleteOrder(cashPaid, transferPaid)
                            }
                        },
                        enabled = selectedItems.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("complete_import_order_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("HOÀN THÀNH ĐƠN".t(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    if (showAddItemDialog) {
        AddProductToImportDialog(
            products = products,
            categories = categories,
            viewModel = viewModel,
            supplierName = supplierName,
            onDismiss = { showAddItemDialog = false },
            onAdd = { newItem ->
                val existingIndex = selectedItems.indexOfFirst { it.productId == newItem.productId }
                if (existingIndex >= 0) {
                    val existing = selectedItems[existingIndex]
                    val updated = existing.copy(
                        quantity = existing.quantity + newItem.quantity,
                        importPrice = newItem.importPrice
                    )
                    selectedItems = selectedItems.toMutableList().apply {
                        set(existingIndex, updated)
                    }
                } else {
                    selectedItems = selectedItems + newItem
                }
                showAddItemDialog = false
            }
        )
    }

    if (itemToEdit != null) {
        val editingItem = itemToEdit!!
        val matchingProduct = products.find { it.id == editingItem.productId }
        val currentStock = matchingProduct?.stockQuantity ?: 0
        val oldPrice = matchingProduct?.importPrice ?: 0.0
        EditImportItemDetailsDialog(
            item = editingItem,
            currentStock = currentStock,
            oldPrice = oldPrice,
            onDismiss = { itemToEdit = null },
            onSave = { updatedItem ->
                val index = selectedItems.indexOfFirst { it.productId == updatedItem.productId }
                if (index >= 0) {
                    selectedItems = selectedItems.toMutableList().apply {
                        set(index, updatedItem)
                    }
                }
                itemToEdit = null
            }
        )
    }

    if (showLimitConfirmation) {
        val cashPaid = cashInput.replace(".", "").toDoubleOrNull() ?: 0.0
        val transferPaid = bankTransferInput.replace(".", "").toDoubleOrNull() ?: 0.0

        AlertDialog(
            onDismissRequest = { showLimitConfirmation = false },
            title = {
                Text(
                    text = "Xác nhận vượt hạn mức chi trả".t(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column {
                    Text(
                        text = "Số tiền thanh toán vượt quá hạn mức khả dụng trong nguồn tiền. Bạn có chắc chắn muốn tiếp tục?".t(),
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (cashPaid > finalTienMat) {
                        Text(
                            text = "- Tiền mặt chi trả: %s (Khả dụng: %s)".t()
                                .format(formatCurrency(cashPaid), formatCurrency(finalTienMat)),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (transferPaid > finalTaiKhoan) {
                        Text(
                            text = "- Chuyển khoản chi trả: %s (Khả dụng: %s)".t()
                                .format(formatCurrency(transferPaid), formatCurrency(finalTaiKhoan)),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLimitConfirmation = false
                        performCompleteOrder(cashPaid, transferPaid)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xác nhận tiếp tục".t(), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLimitConfirmation = false }
                ) {
                    Text("Hủy bỏ".t())
                }
            }
        )
    }

    if (showExitWarningDialog) {
        AlertDialog(
            onDismissRequest = { showExitWarningDialog = false },
            title = { Text("Cảnh báo".t(), fontWeight = FontWeight.Bold) },
            text = { Text("Bạn đang có mặt hàng trong đơn nhập. Bạn có muốn lưu nháp đơn hàng này không?".t()) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitWarningDialog = false
                        val cashPaid = cashInput.replace(".", "").toDoubleOrNull() ?: 0.0
                        val transferPaid = bankTransferInput.replace(".", "").toDoubleOrNull() ?: 0.0
                        val supplier = if (supplierName.isBlank()) "Nhà cung cấp".t() else supplierName
                        
                        viewModel.createImportOrder(
                            supplierName = supplier,
                            items = selectedItems,
                            cashPaid = cashPaid,
                            transferPaid = transferPaid,
                            isDraft = true,
                            onComplete = {
                                onDismiss()
                                Toast.makeText(context, "Đã lưu nháp đơn nhập hàng!".t(), Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.testTag("confirm_save_draft_btn")
                ) {
                    Text("Lưu nháp".t(), color = MaterialTheme.colorScheme.onTertiary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitWarningDialog = false
                        onDismiss()
                    },
                    modifier = Modifier.testTag("confirm_discard_btn")
                ) {
                    Text("Thoát".t())
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductToImportDialog(
    products: List<Product>,
    categories: List<Category>,
    viewModel: MainViewModel,
    supplierName: String = "",
    onDismiss: () -> Unit,
    onAdd: (ImportOrderItemTemp) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var quantityText by remember { mutableStateOf("1") }
    var importPriceText by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var showCreateNewProductDialog by remember { mutableStateOf(false) }

    val quantityFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedProduct) {
        if (selectedProduct != null) {
            kotlinx.coroutines.delay(100)
            try {
                quantityFocusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val importOrders by viewModel.importOrders.collectAsStateWithLifecycle(emptyList())

    val previousSupplierItems = remember(importOrders, supplierName, products) {
        if (supplierName.isBlank()) emptyList<Pair<Product, Double>>() else {
            val matchingOrders = importOrders.filter {
                it.importOrder.supplierName.equals(supplierName, ignoreCase = true)
            }.sortedByDescending { it.importOrder.timestamp }

            val productLatestPrices = mutableMapOf<Int, Double>()
            for (order in matchingOrders) {
                for (item in order.items) {
                    if (!productLatestPrices.containsKey(item.productId)) {
                        productLatestPrices[item.productId] = item.importPrice
                    }
                }
            }

            productLatestPrices.mapNotNull { (productId, price) ->
                val product = products.find { it.id == productId }
                if (product != null) {
                    if (product.supplierName.isNotBlank() && !product.supplierName.equals(supplierName, ignoreCase = true)) {
                        null
                    } else {
                        product to price
                    }
                } else null
            }
        }
    }

    val availableProducts = remember(products, previousSupplierItems, supplierName) {
        if (supplierName.isNotBlank()) {
            val currentSupplierProds = products.filter { it.supplierName.equals(supplierName, ignoreCase = true) }
            val prevSupplierProds = previousSupplierItems.map { it.first }
            (currentSupplierProds + prevSupplierProds).distinctBy { it.id }
        } else {
            products
        }
    }

    val filteredProducts = remember(availableProducts, searchQuery) {
        if (searchQuery.isBlank()) {
            availableProducts
        } else {
            availableProducts.mapNotNull { prod ->
                val score = getRelevanceScore(prod.name, searchQuery)
                if (score != null) prod to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm sản phẩm nhập".t(), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text(
                        text = "Chọn sản phẩm có sẵn".t(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedProduct?.name ?: searchQuery,
                            onValueChange = {
                                if (selectedProduct != null) {
                                    selectedProduct = null
                                    importPriceText = ""
                                }
                                searchQuery = it
                            },
                            placeholder = { Text("Tìm tên sản phẩm...".t()) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_product_search"),
                            trailingIcon = {
                                if (selectedProduct != null || searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        selectedProduct = null
                                        searchQuery = ""
                                        importPriceText = ""
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Xóa".t())
                                    }
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                }
                            },
                            singleLine = true
                        )
                    }

                    if (selectedProduct == null && searchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kết quả tìm kiếm:".t(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))

                        val displayProducts = filteredProducts.take(6)
                        if (displayProducts.isEmpty()) {
                            Text(
                                text = "Không tìm thấy sản phẩm nào".t(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                displayProducts.forEach { prod ->
                                    val prevPrice = previousSupplierItems.find { it.first.id == prod.id }?.second
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedProduct = prod
                                                val priceToFill = prevPrice ?: prod.importPrice
                                                importPriceText = formatDotsInput(priceToFill.toLong().toString())
                                                searchQuery = prod.name
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                if (prevPrice != null) {
                                                    Text(
                                                        text = "Nhập gần đây: ".t() + formatCurrency(prevPrice),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = formatCurrency(prod.importPrice),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                                if (filteredProducts.size > 6) {
                                    Text(
                                        text = "... và ${filteredProducts.size - 6} sản phẩm khác. Vui lòng nhập thêm chữ để tìm chính xác hơn.".t(),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedProduct != null) {
                    val prod = selectedProduct!!
                    
                    var wasQuantityFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it },
                            label = { Text("Số lượng".t()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(quantityFocusRequester)
                                .testTag("import_quantity_input")
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && !wasQuantityFocused) {
                                        quantityText = ""
                                    }
                                    wasQuantityFocused = focusState.isFocused
                                },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = TextFieldValue(text = importPriceText, selection = TextRange(importPriceText.length)),
                            onValueChange = { textFieldValue -> importPriceText = formatDotsInput(textFieldValue.text) },
                            label = { Text("Giá nhập mới".t()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("import_price_input"),
                            singleLine = true
                        )
                    }
                    
                    val oldPrice = prod.importPrice
                    val newPrice = importPriceText.replace(".", "").toDoubleOrNull() ?: 0.0
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Cập nhật trực tiếp giá nhập hệ thống:".t(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${formatCurrency(oldPrice)} (cũ) -> ${formatCurrency(newPrice)} (mới)".t(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                Button(
                    onClick = { showCreateNewProductDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_create_new_product_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tạo mặt hàng mới".t(), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prod = selectedProduct
                    val qty = quantityText.toIntOrNull() ?: 0
                    val prc = importPriceText.replace(".", "").toDoubleOrNull() ?: 0.0
                    if (prod != null && qty > 0 && prc >= 0.0) {
                        onAdd(
                            ImportOrderItemTemp(
                                productId = prod.id,
                                productName = prod.name,
                                quantity = qty,
                                importPrice = prc
                            )
                        )
                    }
                },
                enabled = selectedProduct != null && (quantityText.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.testTag("confirm_add_product_btn")
            ) {
                Text("THÊM".t())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("HỦY".t())
            }
        }
    )

    if (showCreateNewProductDialog) {
        CreateNewProductInImportDialog(
            viewModel = viewModel,
            categories = categories,
            onDismiss = { showCreateNewProductDialog = false },
            onProductCreated = { newProd, qty ->
                selectedProduct = newProd
                quantityText = qty.toString()
                importPriceText = formatDotsInput(newProd.importPrice.toLong().toString())
                searchQuery = newProd.name
                showCreateNewProductDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNewProductInImportDialog(
    viewModel: MainViewModel,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onProductCreated: (Product, Int) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var nameVal by remember { mutableStateOf("") }
    var categoryInputText by remember { mutableStateOf("") }
    var importPriceVal by remember { mutableStateOf("") }
    var sellPriceVal by remember { mutableStateOf("") }
    var trackInventoryVal by remember { mutableStateOf(true) }
    var stockQuantityVal by remember { mutableStateOf("1") }
    var imageUriVal by remember { mutableStateOf<String?>(null) }
    var rawSelectedUri by remember { mutableStateOf<Uri?>(null) }

    var expandedCatDropdown by remember { mutableStateOf(false) }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            rawSelectedUri = it
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo mặt hàng mới".t(), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = nameVal,
                    onValueChange = { nameVal = it },
                    label = { Text("Tên mặt hàng *".t()) },
                    placeholder = { Text("Nhập tên mặt hàng...".t()) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = categoryInputText,
                        onValueChange = {
                            categoryInputText = it
                            expandedCatDropdown = true
                        },
                        label = { Text("Danh mục".t()) },
                        placeholder = { Text("Chọn hoặc nhập danh mục mới...".t()) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { expandedCatDropdown = !expandedCatDropdown }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        singleLine = true
                    )

                    DropdownMenu(
                        expanded = expandedCatDropdown,
                        onDismissRequest = { expandedCatDropdown = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    categoryInputText = cat.name
                                    expandedCatDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = TextFieldValue(text = importPriceVal, selection = TextRange(importPriceVal.length)),
                        onValueChange = { textFieldValue -> importPriceVal = formatDotsInput(textFieldValue.text) },
                        label = { Text("Giá nhập".t()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = TextFieldValue(text = sellPriceVal, selection = TextRange(sellPriceVal.length)),
                        onValueChange = { textFieldValue -> sellPriceVal = formatDotsInput(textFieldValue.text) },
                        label = { Text("Giá bán".t()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = trackInventoryVal,
                        onCheckedChange = { trackInventoryVal = it }
                    )
                    Text(text = "Theo dõi tồn kho".t(), fontSize = 14.sp)
                }

                if (trackInventoryVal) {
                    OutlinedTextField(
                        value = stockQuantityVal,
                        onValueChange = { stockQuantityVal = it },
                        label = { Text("Số lượng nhập".t()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Ảnh sản phẩm".t(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { imageLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("import_select_image_btn")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chọn hình ảnh".t(), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageUriVal != null) {
                            AsyncImage(
                                model = Uri.parse(imageUriVal),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameVal.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập tên mặt hàng".t(), Toast.LENGTH_SHORT).show()
                    } else {
                        val cost = importPriceVal.replace(".", "").toDoubleOrNull() ?: 0.0
                        val sell = sellPriceVal.replace(".", "").toDoubleOrNull() ?: 0.0
                        val stock = stockQuantityVal.toIntOrNull() ?: 0
                        val catName = if (categoryInputText.isBlank()) "Không danh mục" else categoryInputText

                        viewModel.saveProductForImport(
                            name = nameVal,
                            categoryName = catName,
                            importPrice = cost,
                            sellPrice = sell,
                            trackInventory = trackInventoryVal,
                            stockQuantity = 0, // Set initial stock in database to 0 so the import order updates it correctly
                            imageUri = imageUriVal,
                            onComplete = { createdProd ->
                                onProductCreated(createdProd, stock)
                            }
                        )
                    }
                }
            ) {
                Text("LƯU".t())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("HỦY".t())
            }
        }
    )

    rawSelectedUri?.let { uri ->
        ImageCropperDialog(
            imageUri = uri,
            onDismiss = { rawSelectedUri = null },
            onConfirm = { croppedUri ->
                imageUriVal = croppedUri.toString()
                rawSelectedUri = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditImportOrderDialog(
    viewModel: MainViewModel,
    orderWithItems: ImportOrderWithItems,
    products: List<Product>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var supplierName by remember { mutableStateOf(orderWithItems.importOrder.supplierName) }
    var selectedItems by remember { mutableStateOf(orderWithItems.items.map {
        ImportOrderItemTemp(
            productId = it.productId,
            productName = it.productName,
            quantity = it.quantity,
            importPrice = it.importPrice
        )
    }) }

    var showAddItemDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ImportOrderItemTemp?>(null) }

    var supplierDropdownExpanded by remember { mutableStateOf(false) }
    val importOrders by viewModel.importOrders.collectAsStateWithLifecycle(emptyList())
    val previousSuppliers = remember(importOrders) {
        importOrders.map { it.importOrder.supplierName }
            .filter { it.isNotBlank() && it != "Nhà cung cấp".t() && it != "Nhà cung cấp" }
            .distinct()
    }
    val filteredSuppliers = remember(previousSuppliers, supplierName) {
        if (supplierName.isEmpty()) {
            previousSuppliers
        } else {
            previousSuppliers.filter { it.contains(supplierName, ignoreCase = true) && it.lowercase() != supplierName.lowercase() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Chỉnh sửa đơn nhập".t(), fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng".t())
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    val supplier = if (supplierName.isBlank()) "Nhà cung cấp".t() else supplierName
                                    viewModel.updateImportOrder(
                                        orderId = orderWithItems.importOrder.id,
                                        timestamp = orderWithItems.importOrder.timestamp,
                                        supplierName = supplier,
                                        items = selectedItems,
                                        isDraft = orderWithItems.importOrder.isDraft,
                                        onComplete = onSuccess
                                    )
                                },
                                enabled = selectedItems.isNotEmpty(),
                                modifier = Modifier.testTag("submit_edit_import_order_btn")
                            ) {
                                Text(
                                    text = "HOÀN TẤT".t(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (selectedItems.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showAddItemDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.testTag("edit_add_item_to_order_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Thêm mặt hàng".t())
                    }
                },
                bottomBar = {
                    val orderTotal = selectedItems.sumOf { item -> item.quantity * item.importPrice }
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tổng giá trị đơn nhập:".t(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatCurrency(orderTotal),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .padding(16.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        }
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = supplierName,
                            onValueChange = { 
                                supplierName = it
                                supplierDropdownExpanded = true
                            },
                            label = { Text("Tên nhà cung cấp".t()) },
                            placeholder = { Text("Nhập tên nhà cung cấp...".t()) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_import_supplier_input")
                                .onFocusChanged { focusState ->
                                    supplierDropdownExpanded = focusState.isFocused && filteredSuppliers.isNotEmpty()
                                },
                            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                            singleLine = true
                        )

                        if (filteredSuppliers.isNotEmpty()) {
                            DropdownMenu(
                                expanded = supplierDropdownExpanded,
                                onDismissRequest = { supplierDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f),
                                properties = PopupProperties(focusable = false)
                            ) {
                                filteredSuppliers.forEach { supplier ->
                                    DropdownMenuItem(
                                        text = { Text(if (supplier == "Tất cả") "Tất cả".t() else supplier) },
                                        onClick = {
                                            supplierName = supplier
                                            supplierDropdownExpanded = false
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Danh sách hàng nhập".t(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "(Ấn vào mặt hàng để chỉnh sửa)".t(),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (selectedItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(24.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { focusManager.clearFocus() })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Chưa có mặt hàng nào trong đơn".t(),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { focusManager.clearFocus() })
                                },
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedItems) { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            itemToEdit = item
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.productName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row {
                                                Text(
                                                    text = "${item.quantity} x " + formatCurrency(item.importPrice),
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.End,
                                            modifier = Modifier.wrapContentWidth()
                                        ) {
                                            Text(
                                                text = formatCurrency(item.quantity * item.importPrice),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 4.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Clip
                                            )
                                            IconButton(
                                                onClick = {
                                                    selectedItems = selectedItems.filterNot { it.productId == item.productId }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Xóa",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
    }

    if (showAddItemDialog) {
        AddProductToImportDialog(
            products = products,
            categories = categories,
            viewModel = viewModel,
            supplierName = supplierName,
            onDismiss = { showAddItemDialog = false },
            onAdd = { newItem ->
                val existingIndex = selectedItems.indexOfFirst { it.productId == newItem.productId }
                if (existingIndex >= 0) {
                    val existing = selectedItems[existingIndex]
                    val updated = existing.copy(
                        quantity = existing.quantity + newItem.quantity,
                        importPrice = newItem.importPrice
                    )
                    selectedItems = selectedItems.toMutableList().apply {
                        set(existingIndex, updated)
                    }
                } else {
                    selectedItems = selectedItems + newItem
                }
                showAddItemDialog = false
            }
        )
    }

    if (itemToEdit != null) {
        val editingItem = itemToEdit!!
        val matchingProduct = products.find { it.id == editingItem.productId }
        val currentStock = matchingProduct?.stockQuantity ?: 0
        val oldPrice = matchingProduct?.importPrice ?: 0.0
        EditImportItemDetailsDialog(
            item = editingItem,
            currentStock = currentStock,
            oldPrice = oldPrice,
            onDismiss = { itemToEdit = null },
            onSave = { updatedItem ->
                val index = selectedItems.indexOfFirst { it.productId == updatedItem.productId }
                if (index >= 0) {
                    selectedItems = selectedItems.toMutableList().apply {
                        set(index, updatedItem)
                    }
                }
                itemToEdit = null
            },
            onReturnProduct = { pId, qty, prc ->
                viewModel.returnImportedProduct(
                    orderId = orderWithItems.importOrder.id,
                    productId = pId,
                    returnQuantity = qty,
                    returnPrice = prc
                ) {
                    val index = selectedItems.indexOfFirst { it.productId == pId }
                    if (index >= 0) {
                        val existing = selectedItems[index]
                        val newQty = (existing.quantity - qty).coerceAtLeast(0)
                        selectedItems = if (newQty == 0) {
                            selectedItems.filterNot { it.productId == pId }
                        } else {
                            selectedItems.toMutableList().apply {
                                set(index, existing.copy(quantity = newQty))
                            }
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditImportItemDetailsDialog(
    item: ImportOrderItemTemp,
    currentStock: Int,
    oldPrice: Double,
    onDismiss: () -> Unit,
    onSave: (ImportOrderItemTemp) -> Unit,
    onReturnProduct: ((Int, Int, Double) -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    var quantityText by remember { mutableStateOf(item.quantity.toString()) }
    var importPriceText by remember { mutableStateOf(formatDotsInput(item.importPrice.toLong().toString())) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var showConfirmReturnDialog by remember { mutableStateOf(false) }
    var pendingReturnQty by remember { mutableStateOf(0) }
    var pendingReturnPrice by remember { mutableStateOf(0.0) }

    if (showConfirmReturnDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmReturnDialog = false },
            title = {
                Text(
                    text = "Xác nhận hoàn hàng".t(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "${"Bạn có chắc chắn muốn hoàn".t()} $pendingReturnQty ${"sản phẩm với tổng số tiền là".t()} ${formatCurrency(pendingReturnQty * pendingReturnPrice)} ${"không?\n\nHệ thống sẽ tự động trừ kho và cộng lại tiền mặt.".t()}",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReturnProduct?.invoke(item.productId, pendingReturnQty, pendingReturnPrice)
                        showConfirmReturnDialog = false
                        showReturnDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_return_ok_button")
                ) {
                    Text("Xác nhận".t().uppercase(java.util.Locale.getDefault()))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmReturnDialog = false },
                    modifier = Modifier.testTag("confirm_return_cancel_button")
                ) {
                    Text("HỦY".t())
                }
            }
        )
    }

    if (showReturnDialog) {
        var returnQtyText by remember { mutableStateOf(currentStock.toString()) }
        var returnPriceText by remember { mutableStateOf(formatDotsInput(item.importPrice.toLong().toString())) }
        val focusManagerReturn = LocalFocusManager.current

        AlertDialog(
            onDismissRequest = { showReturnDialog = false },
            title = { Text("Hoàn hàng".t(), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManagerReturn.clearFocus() })
                        },
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${"Sản phẩm:".t()} ${item.productName}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    
                    OutlinedTextField(
                        value = returnQtyText,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }
                            if (clean.isEmpty()) {
                                returnQtyText = ""
                            } else {
                                val qtyVal = clean.toIntOrNull() ?: 0
                                if (qtyVal <= currentStock) {
                                    returnQtyText = clean
                                } else {
                                    returnQtyText = currentStock.toString()
                                }
                            }
                        },
                        label = { Text("Số lượng hoàn".t() + " (${"Hàng tồn:".t()} $currentStock)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("return_qty_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = TextFieldValue(text = returnPriceText, selection = TextRange(returnPriceText.length)),
                        onValueChange = { textFieldValue -> returnPriceText = formatDotsInput(textFieldValue.text) },
                        label = { Text("Giá hoàn hàng".t()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("return_price_input"),
                        singleLine = true
                    )

                    val qty = returnQtyText.toIntOrNull() ?: 0
                    val prc = returnPriceText.replace(".", "").toDoubleOrNull() ?: 0.0
                    val refundVal = qty * prc
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Tính toán tiền hoàn lại:".t(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "$qty x ${formatCurrency(prc)} = ${formatCurrency(refundVal)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = returnQtyText.toIntOrNull() ?: 0
                        val prc = returnPriceText.replace(".", "").toDoubleOrNull() ?: 0.0
                        if (qty > 0 && prc >= 0.0) {
                            pendingReturnQty = qty
                            pendingReturnPrice = prc
                            showConfirmReturnDialog = true
                        }
                    },
                    enabled = (returnQtyText.toIntOrNull() ?: 0) > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("return_confirm_button")
                ) {
                    Text("HOÀN THÀNH".t())
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReturnDialog = false },
                    modifier = Modifier.testTag("return_cancel_button")
                ) {
                    Text("HỦY".t())
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sửa thông tin mặt hàng nhập".t(), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.productName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (onReturnProduct != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .clickable { showReturnDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("btn_return_goods")
                        ) {
                            Text(
                                text = "Hoàn hàng".t(),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                var wasQuantityFocused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Số lượng".t()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && !wasQuantityFocused) {
                                quantityText = ""
                            }
                            wasQuantityFocused = focusState.isFocused
                        },
                    singleLine = true
                )

                OutlinedTextField(
                    value = TextFieldValue(text = importPriceText, selection = TextRange(importPriceText.length)),
                    onValueChange = { textFieldValue -> importPriceText = formatDotsInput(textFieldValue.text) },
                    label = { Text("Giá nhập mới".t()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                val newPrice = importPriceText.replace(".", "").toDoubleOrNull() ?: 0.0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Cập nhật trực tiếp giá nhập hệ thống:".t(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${formatCurrency(oldPrice)} (cũ) -> ${formatCurrency(newPrice)} (mới)".t(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityText.toIntOrNull() ?: 0
                    val prc = importPriceText.replace(".", "").toDoubleOrNull() ?: 0.0
                    if (qty > 0 && prc >= 0.0) {
                        onSave(item.copy(quantity = qty, importPrice = prc))
                    }
                },
                enabled = (quantityText.toIntOrNull() ?: 0) > 0
            ) {
                Text("CẬP NHẬT".t())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("HỦY".t())
            }
        }
    )
}

fun loadScaledBitmap(context: Context, uri: Uri, maxDimension: Int = 1200): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }

        var sampleSize = 1
        if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while (halfHeight / sampleSize >= maxDimension && halfWidth / sampleSize >= maxDimension) {
                sampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        
        var decoded: Bitmap? = null
        context.contentResolver.openInputStream(uri)?.use { nextInputStream ->
            decoded = BitmapFactory.decodeStream(nextInputStream, null, decodeOptions)
        }
        
        decoded?.let { rotateImageIfRequired(context, it, uri) }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun rotateImageIfRequired(context: Context, img: Bitmap, selectedImage: Uri): Bitmap {
    return try {
        val orientation = context.contentResolver.openInputStream(selectedImage)?.use { input ->
            val ei = ExifInterface(input)
            ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
            else -> img
        }
    } catch (e: Exception) {
        img
    }
}

fun rotateImage(img: Bitmap, degree: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degree)
    val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
    if (rotatedImg != img) {
        img.recycle()
    }
    return rotatedImg
}

fun rotateBitmapNoRecycle(img: Bitmap, degree: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degree)
    return Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
}

fun saveCroppedImage(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val filename = "cropped_product_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
        }
        Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Uri) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(imageUri) { mutableStateOf(true) }

    LaunchedEffect(imageUri) {
        isLoading = true
        withContext(Dispatchers.IO) {
            bitmap = loadScaledBitmap(context, imageUri)
        }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Căn chỉnh ảnh sản phẩm".t(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Di chuyển hoặc phóng to/thu nhỏ ảnh để vừa khung hình 1:1".t(),
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val currentBitmap = bitmap
                    if (currentBitmap != null) {
                        val bW = currentBitmap.width.toFloat()
                        val bH = currentBitmap.height.toFloat()

                        val boxSizeDp = 280.dp
                        val density = LocalDensity.current
                        val boxSizePx = with(density) { boxSizeDp.toPx() }

                        val initialScale = maxOf(boxSizePx / bW, boxSizePx / bH)
                        val dispW = bW * initialScale
                        val dispH = bH * initialScale

                        var scale by remember { mutableStateOf(1f) }
                        var offset by remember { mutableStateOf(Offset.Zero) }

                        Box(
                            modifier = Modifier
                                .size(boxSizeDp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        
                                        val maxOffsetX = (dispW * scale - boxSizePx) / 2f
                                        val maxOffsetY = (dispH * scale - boxSizePx) / 2f
                                        
                                        offset = Offset(
                                            x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                            y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                        )
                                    }
                                }
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            val imageBitmap = remember(currentBitmap) { currentBitmap.asImageBitmap() }
                            androidx.compose.foundation.Image(
                                bitmap = imageBitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(width = with(density) { dispW.toDp() }, height = with(density) { dispH.toDp() })
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    }
                            )

                            // Net/grid lines 3x3 overlay for aesthetic framing
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val gridColor = Color.White.copy(alpha = 0.4f)
                                val strokeWidth = 1.dp.toPx()
                                
                                // Vertical lines
                                drawLine(
                                    color = gridColor,
                                    start = Offset(size.width / 3f, 0f),
                                    end = Offset(size.width / 3f, size.height),
                                    strokeWidth = strokeWidth
                                )
                                drawLine(
                                    color = gridColor,
                                    start = Offset(size.width * 2f / 3f, 0f),
                                    end = Offset(size.width * 2f / 3f, size.height),
                                    strokeWidth = strokeWidth
                                )
                                
                                // Horizontal lines
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, size.height / 3f),
                                    end = Offset(size.width, size.height / 3f),
                                    strokeWidth = strokeWidth
                                )
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, size.height * 2f / 3f),
                                    end = Offset(size.width, size.height * 2f / 3f),
                                    strokeWidth = strokeWidth
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Rotate & zoom scale indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Thu phóng".t() + ": ${String.format("%.1f", scale)}x",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 14.sp
                            )

                            TextButton(
                                onClick = {
                                    val current = bitmap
                                    if (current != null) {
                                        bitmap = rotateBitmapNoRecycle(current, 90f)
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Rotate 90",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Xoay 90°".t(), fontSize = 14.sp)
                            }
                        }

                        // Slider row for easy single finger zooming
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Zoom out",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        scale = (scale - 0.2f).coerceAtLeast(1f)
                                        val maxOffsetX = (dispW * scale - boxSizePx) / 2f
                                        val maxOffsetY = (dispH * scale - boxSizePx) / 2f
                                        offset = Offset(
                                            x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                            y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                        )
                                    }
                            )
                            
                            Slider(
                                value = scale,
                                onValueChange = { newScale ->
                                    scale = newScale
                                    val maxOffsetX = (dispW * scale - boxSizePx) / 2f
                                    val maxOffsetY = (dispH * scale - boxSizePx) / 2f
                                    offset = Offset(
                                        x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                        y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                },
                                valueRange = 1f..5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            )

                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Zoom in",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        scale = (scale + 0.2f).coerceAtMost(5f)
                                        val maxOffsetX = (dispW * scale - boxSizePx) / 2f
                                        val maxOffsetY = (dispH * scale - boxSizePx) / 2f
                                        offset = Offset(
                                            x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                            y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                        )
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text("Hủy bỏ".t())
                            }

                            Button(
                                onClick = {
                                    val finalScale = initialScale * scale
                                    val left = (boxSizePx / 2f) + offset.x - (dispW * scale) / 2f
                                    val top = (boxSizePx / 2f) + offset.y - (dispH * scale) / 2f

                                    val cropX = (-left / finalScale).toInt().coerceIn(0, currentBitmap.width - 1)
                                    val cropY = (-top / finalScale).toInt().coerceIn(0, currentBitmap.height - 1)
                                    val cropW = (boxSizePx / finalScale).toInt().coerceAtMost(currentBitmap.width - cropX)
                                    val cropH = (boxSizePx / finalScale).toInt().coerceAtMost(currentBitmap.height - cropY)

                                    val finalCropSize = minOf(cropW, cropH)
                                    if (finalCropSize > 0) {
                                        try {
                                            val cropped = Bitmap.createBitmap(
                                                currentBitmap,
                                                cropX,
                                                cropY,
                                                finalCropSize,
                                                finalCropSize
                                            )
                                            val croppedUri = saveCroppedImage(context, cropped)
                                            if (croppedUri != null) {
                                                onConfirm(croppedUri)
                                            } else {
                                                Toast.makeText(context, "Lỗi lưu ảnh đã cắt".t(), Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Lỗi cắt ảnh".t(), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Xác nhận".t(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Không thể tải hình ảnh".t(), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}




@Composable
fun CustomerInvoiceCard(invoiceWithItems: InvoiceWithItems, searchQuery: String) {
    val inv = invoiceWithItems.invoice
    val items = invoiceWithItems.items
    var showProfit by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Đơn".t() + " #${inv.id} - ${formatDate(inv.timestamp)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = formatCurrency(inv.totalAmount),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Khách hàng: ".t() + inv.storeName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showProfit) {
                        Text(
                            text = "Tiền lãi: ".t() + formatCurrency(inv.profit),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (inv.profit >= 0) Color(0xFF4CAF50) else Color.Red
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = { showProfit = !showProfit },
                        modifier = Modifier.size(24.dp).padding(2.dp)
                    ) {
                        Icon(
                            imageVector = if (showProfit) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Profit",
                            tint = Color(0xFF4CAF50), // Green color for the eye
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Divider(
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Column headers
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tên sản phẩm".t(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
                Spacer(modifier = Modifier.width(9.dp))
                Text("SL".t(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(9.dp))
                Text("Đơn giá".t(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(9.dp))
                Text("Thành tiền".t(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
            }

            // List products inside this order
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    androidx.compose.material3.HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isProductMatched = searchQuery.isNotEmpty() && getRelevanceScore(item.productName, searchQuery) != null
                    Text(
                        text = item.productName,
                        fontSize = 12.sp,
                        fontWeight = if (isProductMatched) FontWeight.Bold else FontWeight.Normal,
                        color = if (isProductMatched) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.4f).padding(vertical = 2.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(horizontal = 4.dp).width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    Text(
                        text = "${item.quantity}",
                        fontSize = 11.sp,
                        modifier = Modifier.weight(0.5f).padding(vertical = 2.dp),
                        textAlign = TextAlign.Center, maxLines = 1
                    )
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(horizontal = 4.dp).width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    Text(
                        text = formatCurrency(item.sellPrice),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.0f).padding(vertical = 2.dp),
                        textAlign = TextAlign.Center, maxLines = 1
                    )
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(horizontal = 4.dp).width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    Text(
                        text = formatCurrency(item.sellPrice * item.quantity),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.2f).padding(vertical = 2.dp),
                        textAlign = TextAlign.Center, maxLines = 1
                    )
                }
            }
        }
    }
}
