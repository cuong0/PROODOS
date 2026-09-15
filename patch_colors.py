import re

with open('app/src/main/java/com/example/ui/PosApp.kt', 'r') as f:
    content = f.read()

target1 = '''                            Text("Tổng tiền hàng:".t())
                            Text(
                                formatCurrency(invoice.totalAmount),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )'''

replacement1 = '''                            Text("Tổng tiền hàng:".t())
                            Text(
                                formatCurrency(invoice.totalAmount),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color.Red
                            )'''

content = content.replace(target1, replacement1)

target2 = '''val profitColor = if (displayedProfit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error'''
replacement2 = '''val profitColor = if (displayedProfit >= 0) Color(0xFF4CAF50) else Color.Red'''

content = content.replace(target2, replacement2)

target3 = '''                                                    Text(
                                                        text = "Tiền lãi: ".t() + formatCurrency(inv.profit),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )'''

replacement3 = '''                                                    Text(
                                                        text = "Tiền lãi: ".t() + formatCurrency(inv.profit),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = if (inv.profit >= 0) Color(0xFF4CAF50) else Color.Red
                                                    )'''

content = content.replace(target3, replacement3)

with open('app/src/main/java/com/example/ui/PosApp.kt', 'w') as f:
    f.write(content)
