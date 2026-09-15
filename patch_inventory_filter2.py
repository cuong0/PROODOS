import re

with open('app/src/main/java/com/example/ui/PosApp.kt', 'r') as f:
    content = f.read()

target1 = '''    // Filter and sort products
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

replacement1 = '''    // Filter and sort products
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
    }'''

if target1 in content:
    content = content.replace(target1, replacement1)
    print("PATCH 1 SUCCESS")
else:
    print("TARGET 1 NOT FOUND")

target2 = '''                actions = {
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
                }'''

replacement2 = '''                actions = {
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
                }'''

if target2 in content:
    content = content.replace(target2, replacement2)
    print("PATCH 2 SUCCESS")
else:
    print("TARGET 2 NOT FOUND")

with open('app/src/main/java/com/example/ui/PosApp.kt', 'w') as f:
    f.write(content)

