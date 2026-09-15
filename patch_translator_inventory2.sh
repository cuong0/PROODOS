sed -i 's/"Tồn nhiều đến ít" to "Stock: High to Low"/"Tồn kho từ nhiều đến ít" to "Stock: High to Low"/g' app/src/main/java/com/example/ui/Translator.kt
sed -i 's/"Giá từ thấp đến cao" to "Price: Low to High"/"Tổng tồn từ thấp đến cao" to "Total stock: Low to High"/g' app/src/main/java/com/example/ui/Translator.kt
sed -i 's/"Giá từ cao đến thấp" to "Price: High to Low"/"Tổng tồn từ cao đến thấp" to "Total stock: High to Low"/g' app/src/main/java/com/example/ui/Translator.kt

sed -i 's/"Tồn nhiều đến ít" to "库存: 从多到少"/"Tồn kho từ nhiều đến ít" to "库存: 从多到少"/g' app/src/main/java/com/example/ui/Translator.kt
sed -i 's/"Giá từ thấp đến cao" to "价格: 从低到高"/"Tổng tồn từ thấp đến cao" to "总库存价值: 从低到高"/g' app/src/main/java/com/example/ui/Translator.kt
sed -i 's/"Giá từ cao đến thấp" to "价格: 从高到低"/"Tổng tồn từ cao đến thấp" to "总库存价值: 从高到低"/g' app/src/main/java/com/example/ui/Translator.kt
