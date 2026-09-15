import re

file_path = "app/src/main/java/com/example/ui/PosApp.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Target 1: Add showCustomerProfit to items block
items_target = r"val totalProfit = listInvoices.sumOf \{ it.invoice.profit \}"
items_replacement = "val totalProfit = listInvoices.sumOf { it.invoice.profit }\n                        var showCustomerProfit by remember { mutableStateOf(false) }"

if items_target in content:
    content = content.replace(items_target, items_replacement)
else:
    print("Could not find items_target")

# Target 2: Replace the Tổng lãi block
block_target = """                                        if (isExpanded) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Tổng lãi: ".t() + formatCurrency(totalProfit),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }"""

block_replacement = """                                        if (isExpanded) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (showCustomerProfit) {
                                                    Text(
                                                        text = "Tổng lãi: ".t() + formatCurrency(totalProfit),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                IconButton(
                                                    onClick = { showCustomerProfit = !showCustomerProfit },
                                                    modifier = Modifier.size(24.dp).padding(2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (showCustomerProfit) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = "Toggle Customer Profit",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }"""

if block_target in content:
    content = content.replace(block_target, block_replacement)
else:
    print("Could not find block_target")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Patch applied.")
