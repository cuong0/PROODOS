import re

with open('app/src/main/java/com/example/ui/PosApp.kt', 'r') as f:
    content = f.read()

target = '''    // Filter and sort products (highest stock quantity first, lowest stock quantity last)
    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) {
            products.sortedByDescending { it.stockQuantity }
        } else {
            products.mapNotNull { prod ->
                val score = getRelevanceScore(prod.name, searchQuery)
                if (score != null) prod to score else null
            }.sortedWith(
                compareByDescending<Pair<com.example.data.Product, Double>> { it.second }
                    .thenByDescending { it.first.stockQuantity }
            ).map { it.first }
        }
    }'''

replacement = '''    var sortOption by remember { mutableStateOf(0) }
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
        
        if (searchQuery.isBlank()) {
            when (sortOption) {
                1 -> baseList.sortedBy { it.sellPrice }
                2 -> baseList.sortedByDescending { it.sellPrice }
                else -> baseList.sortedByDescending { it.stockQuantity }
            }
        } else {
            // Keep search relevance as primary if searching, or apply sort if preferred.
            // A simple stable sort on top of relevance is:
            when (sortOption) {
                1 -> baseList.sortedBy { it.sellPrice }
                2 -> baseList.sortedByDescending { it.sellPrice }
                else -> baseList // Already sorted by relevance, secondary by nothing needed or we can do stockQuantity
            }
        }
    }'''

if target in content:
    content = content.replace(target, replacement)
    print("PATCH 1 SUCCESS")
else:
    print("TARGET 1 NOT FOUND")

target2 = '''            TopAppBar(
                title = { Text("Quản lý hàng tồn".t()) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.selectSubScreen(null) },
                        modifier = Modifier.testTag("inventory_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về".t())
                    }
                }
            )'''

replacement2 = '''            TopAppBar(
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(18.dp)
                                )
                                Icon(
                                    imageVector = Icons.Default.FilterAlt,
                                    contentDescription = "Lọc".t(),
                                    tint = Color.Red,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Tồn nhiều đến ít".t(), fontWeight = if (sortOption == 0) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { sortOption = 0; showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Giá từ thấp đến cao".t(), fontWeight = if (sortOption == 1) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { sortOption = 1; showSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Giá từ cao đến thấp".t(), fontWeight = if (sortOption == 2) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { sortOption = 2; showSortMenu = false }
                            )
                        }
                    }
                }
            )'''

if target2 in content:
    content = content.replace(target2, replacement2)
    print("PATCH 2 SUCCESS")
else:
    print("TARGET 2 NOT FOUND")

with open('app/src/main/java/com/example/ui/PosApp.kt', 'w') as f:
    f.write(content)
