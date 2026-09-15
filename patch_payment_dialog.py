import re

with open('app/src/main/java/com/example/ui/PosApp.kt', 'r') as f:
    content = f.read()

target = '''            // Pay-out Success Modal overlay
            if (showCheckoutSuccess) {'''

replacement = '''            if (showPaymentDialog) {
                AlertDialog(
                    onDismissRequest = { showPaymentDialog = false },
                    title = { Text("Chọn phương thức thanh toán".t(), fontWeight = FontWeight.Bold) },
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val tmSelected = selectedPaymentMethod == "TM"
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(100.dp)
                                    .padding(end = 8.dp)
                                    .clickable { selectedPaymentMethod = "TM" },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (tmSelected) Color(0xFF81C784) else Color(0xFFC8E6C9)
                                ),
                                border = if (tmSelected) BorderStroke(2.dp, Color(0xFF2E7D32)) else null
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text("Tiền mặt".t(), fontWeight = if (tmSelected) FontWeight.Bold else FontWeight.Normal, color = Color(0xFF1B5E20))
                                }
                            }
                            
                            val ckSelected = selectedPaymentMethod == "CK"
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(100.dp)
                                    .padding(start = 8.dp)
                                    .clickable { selectedPaymentMethod = "CK" },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (ckSelected) Color(0xFF81C784) else Color(0xFFC8E6C9)
                                ),
                                border = if (ckSelected) BorderStroke(2.dp, Color(0xFF2E7D32)) else null
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text("Chuyển khoản".t(), fontWeight = if (ckSelected) FontWeight.Bold else FontWeight.Normal, color = Color(0xFF1B5E20))
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
            if (showCheckoutSuccess) {'''

if target in content:
    content = content.replace(target, replacement)
    with open('app/src/main/java/com/example/ui/PosApp.kt', 'w') as f:
        f.write(content)
    print("Patch applied")
else:
    print("TARGET NOT FOUND!")
