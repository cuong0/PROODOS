import re

with open('app/src/main/java/com/example/ui/PosApp.kt', 'r') as f:
    content = f.read()

target_state = '''    var showCheckoutSuccess by remember { mutableStateOf(false) }'''
replacement_state = '''    var showCheckoutSuccess by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf("TM") }'''

if target_state in content:
    content = content.replace(target_state, replacement_state)
else:
    print("State target not found!")

target_button = '''                            Button(
                                onClick = {
                                    if (storeNameField.isBlank()) {
                                        Toast.makeText(context, "Vui lòng nhập tên khách hàng".t(), Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.checkout(
                                            customerName = storeNameField.trim(),
                                            customerPhone = null
                                        ) {
                                            showCheckoutSuccess = true
                                            onStoreNameChanged("")
                                        }
                                    }
                                },'''
replacement_button = '''                            Button(
                                onClick = {
                                    if (storeNameField.isBlank()) {
                                        Toast.makeText(context, "Vui lòng nhập tên khách hàng".t(), Toast.LENGTH_SHORT).show()
                                    } else {
                                        showPaymentDialog = true
                                    }
                                },'''

if target_button in content:
    content = content.replace(target_button, replacement_button)
else:
    print("Button target not found!")

with open('app/src/main/java/com/example/ui/PosApp.kt', 'w') as f:
    f.write(content)
