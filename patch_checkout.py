import re

with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'r') as f:
    content = f.read()

target = '''    fun checkout(customerName: String?, customerPhone: String?, onComplete: () -> Unit) {
        val cartItems = _cart.value
        if (cartItems.isEmpty()) return
        viewModelScope.launch {
            repository.createInvoice(
                storeName = storeName.value,
                customerName = customerName,
                customerPhone = customerPhone,
                cartItems = cartItems
            )
            clearCart()
            onComplete()
        }
    }'''

replacement = '''    fun checkout(customerName: String?, customerPhone: String?, paymentMethod: String = "TM", onComplete: () -> Unit) {
        val cartItems = _cart.value
        if (cartItems.isEmpty()) return
        viewModelScope.launch {
            val invoiceId = repository.createInvoice(
                storeName = storeName.value,
                customerName = customerName,
                customerPhone = customerPhone,
                cartItems = cartItems
            )
            val prefs = getApplication<android.app.Application>().getSharedPreferences("pos_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("invoice_payment_method_$invoiceId", paymentMethod).apply()
            clearCart()
            onComplete()
        }
    }'''

if target in content:
    content = content.replace(target, replacement)
    with open('app/src/main/java/com/example/ui/MainViewModel.kt', 'w') as f:
        f.write(content)
    print("MainViewModel updated successfully.")
else:
    print("TARGET NOT FOUND in MainViewModel.kt!")
