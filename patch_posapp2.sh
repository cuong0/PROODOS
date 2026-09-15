sed -i 's/"Vốn: " + formatCurrency(product.importPrice)/"Vốn: ".t() + formatCurrency(product.importPrice)/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/"Vốn: ${formatCurrency(product.importPrice)}"/"Vốn: ".t() + formatCurrency(product.importPrice)/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/"Tồn: ${product.stockQuantity}"/"Tồn: ".t() + product.stockQuantity/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/"Tồn: ∞"/"Tồn: ".t() + "∞"/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/listOf("Tiếng Việt", "Tiếng Anh", "Tiếng Trung Quốc", "Khác")/listOf("Tiếng Việt", "Tiếng Anh".t(), "Tiếng Trung Quốc".t(), "Khác".t())/g' app/src/main/java/com/example/ui/PosApp.kt
sed -i 's/"Vốn: ${formatCurrency(cartItem.importPrice)}\/sp"/"Vốn: ".t() + "${formatCurrency(cartItem.importPrice)}\/" + "sp".t()/g' app/src/main/java/com/example/ui/PosApp.kt
