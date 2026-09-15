import re

file_path = "app/src/main/java/com/example/ui/PosApp.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Target 1: Add showCustomerProfit to items block
items_target = "val totalProfit = listInvoices.sumOf { it.invoice.profit }"
items_replacement = "val totalProfit = listInvoices.sumOf { it.invoice.profit }\n                        var showCustomerProfit by remember { mutableStateOf(false) }"

if items_target in content:
    content = content.replace(items_target, items_replacement)
else:
    print("Could not find items_target")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Patch 2 applied.")
