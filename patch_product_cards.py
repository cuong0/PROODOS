import re

with open('app/src/main/java/com/example/ui/PosApp.kt', 'r') as f:
    content = f.read()

# Patch ProductGridCard signature
content = content.replace(
    "fun ProductGridCard(\n    product: Product,\n    onClick: () -> Unit,",
    "fun ProductGridCard(\n    product: Product,\n    cartQuantity: Int = 0,\n    onClick: () -> Unit,"
)

# Patch ProductGridCard stock calculation
content = content.replace(
    'val stockText = if (product.trackInventory) "Tồn: ".t() + product.stockQuantity else "Tồn: ".t() + "∞"\n            val isOutOfStock = product.trackInventory && product.stockQuantity <= 0\n            val isLowStock = product.trackInventory && product.stockQuantity <= 5',
    'val remainingStock = product.stockQuantity - cartQuantity\n            val stockText = if (product.trackInventory) "Tồn: ".t() + remainingStock else "Tồn: ".t() + "∞"\n            val isOutOfStock = product.trackInventory && remainingStock <= 0\n            val isLowStock = product.trackInventory && remainingStock <= 5'
)

# Patch ProductListCard signature
content = content.replace(
    "fun ProductListCard(\n    product: Product,\n    onClick: () -> Unit,",
    "fun ProductListCard(\n    product: Product,\n    cartQuantity: Int = 0,\n    onClick: () -> Unit,"
)

# Patch ProductListCard stock calculation
content = content.replace(
    'val stockText = if (product.trackInventory) "Tồn: ".t() + product.stockQuantity else "Tồn: ".t() + "∞"\n                    val isOutOfStock = product.trackInventory && product.stockQuantity <= 0\n                    val isLowStock = product.trackInventory && product.stockQuantity <= 5',
    'val remainingStock = product.stockQuantity - cartQuantity\n                    val stockText = if (product.trackInventory) "Tồn: ".t() + remainingStock else "Tồn: ".t() + "∞"\n                    val isOutOfStock = product.trackInventory && remainingStock <= 0\n                    val isLowStock = product.trackInventory && remainingStock <= 5'
)

with open('app/src/main/java/com/example/ui/PosApp.kt', 'w') as f:
    f.write(content)
