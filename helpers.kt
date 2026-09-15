
    private fun updateCart(newList: List<CartItem>) {
        _cart.value = newList
        saveCartToPrefs()
    }

    private fun saveCartToPrefs() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cartList = _cart.value
                val jsonArray = org.json.JSONArray()
                for (item in cartList) {
                    val jsonItem = org.json.JSONObject()
                    jsonItem.put("quantity", item.quantity)
                    jsonItem.put("sellPrice", item.sellPrice)
                    jsonItem.put("importPrice", item.importPrice)
                    
                    val p = item.product
                    val jsonProduct = org.json.JSONObject()
                    jsonProduct.put("id", p.id)
                    jsonProduct.put("name", p.name)
                    jsonProduct.put("barcode", p.barcode)
                    jsonProduct.put("importPrice", p.importPrice)
                    jsonProduct.put("sellPrice", p.sellPrice)
                    jsonProduct.put("stockQuantity", p.stockQuantity)
                    jsonProduct.put("imageUri", p.imageUri)
                    jsonProduct.put("trackInventory", p.trackInventory)
                    p.categoryId?.let { jsonProduct.put("categoryId", it) }
                    jsonProduct.put("timestamp", p.timestamp)
                    
                    jsonItem.put("product", jsonProduct)
                    jsonArray.put(jsonItem)
                }
                prefs.edit().putString("saved_cart_items", jsonArray.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadCartFromPrefs() {
        val jsonString = prefs.getString("saved_cart_items", null) ?: return
        try {
            val list = mutableListOf<CartItem>()
            val jsonArray = org.json.JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonItem = jsonArray.getJSONObject(i)
                val jsonProduct = jsonItem.getJSONObject("product")
                
                val p = Product(
                    id = jsonProduct.getInt("id"),
                    name = jsonProduct.getString("name"),
                    barcode = jsonProduct.getString("barcode"),
                    importPrice = jsonProduct.getDouble("importPrice"),
                    sellPrice = jsonProduct.getDouble("sellPrice"),
                    stockQuantity = jsonProduct.getInt("stockQuantity"),
                    imageUri = jsonProduct.getString("imageUri"),
                    trackInventory = jsonProduct.getBoolean("trackInventory"),
                    categoryId = if (jsonProduct.has("categoryId")) jsonProduct.getInt("categoryId") else null,
                    timestamp = jsonProduct.getLong("timestamp")
                )
                val item = CartItem(
                    product = p,
                    quantity = jsonItem.getInt("quantity"),
                    sellPrice = jsonItem.getDouble("sellPrice"),
                    importPrice = jsonItem.getDouble("importPrice")
                )
                list.add(item)
            }
            _cart.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
