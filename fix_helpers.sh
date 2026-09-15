sed -i '/jsonProduct.put("barcode", p.barcode)/d' app/src/main/java/com/example/ui/MainViewModel.kt
sed -i '/jsonProduct.put("timestamp", p.timestamp)/d' app/src/main/java/com/example/ui/MainViewModel.kt
sed -i 's/jsonProduct.put("imageUri", p.imageUri)/jsonProduct.put("imageUri", p.imageUri)\n                    jsonProduct.put("supplierName", p.supplierName)/g' app/src/main/java/com/example/ui/MainViewModel.kt
sed -i '/barcode = jsonProduct.getString("barcode"),/d' app/src/main/java/com/example/ui/MainViewModel.kt
sed -i '/timestamp = jsonProduct.getLong("timestamp")/d' app/src/main/java/com/example/ui/MainViewModel.kt
sed -i 's/imageUri = jsonProduct.getString("imageUri"),/imageUri = if (jsonProduct.has("imageUri") \&\& !jsonProduct.isNull("imageUri")) jsonProduct.getString("imageUri") else null,\n                    supplierName = jsonProduct.optString("supplierName", ""),/g' app/src/main/java/com/example/ui/MainViewModel.kt
