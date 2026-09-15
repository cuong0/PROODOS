import re

with open('app/src/main/java/com/example/ui/PosApp.kt', 'r') as f:
    content = f.read()

pattern = re.compile(
    r'listInvoices\.forEach \{ invoiceWithItems ->\s*val inv = invoiceWithItems\.invoice\s*val items = invoiceWithItems\.items\s*Card\(.*?modifier = Modifier\s*\.fillMaxWidth\(\)\s*\.padding\(vertical = 6\.dp\),.*?colors = CardDefaults\.cardColors\(.*?containerColor = MaterialTheme\.colorScheme\.surfaceVariant\.copy\(alpha = 0\.5f\)\s*\)\s*\)\s*\{\s*Column\(modifier = Modifier\.padding\(12\.dp\)\)\s*\{.*?Row\(.*?modifier = Modifier\.fillMaxWidth\(\),\s*horizontalArrangement = Arrangement\.SpaceBetween\s*\)\s*\{.*?Text\(\s*text = "Đơn"\.t\(\) \+ " #\$\{inv\.id\} - \$\{formatDate\(inv\.timestamp\)\}",.*?fontWeight = FontWeight\.Bold,\s*fontSize = 12\.sp\s*\).*?Text\(\s*text = formatCurrency\(inv\.totalAmount\),.*?fontWeight = FontWeight\.Bold,\s*fontSize = 12\.sp,\s*color = MaterialTheme\.colorScheme\.primary\s*\)\s*\}\s*Spacer\(modifier = Modifier\.height\(4\.dp\)\)\s*Row\(.*?modifier = Modifier\.fillMaxWidth\(\),\s*horizontalArrangement = Arrangement\.SpaceBetween,\s*verticalAlignment = Alignment\.CenterVertically\s*\)\s*\{.*?Text\(\s*text = "Khách hàng: "\.t\(\) \+ inv\.storeName,\s*fontSize = 11\.sp,\s*color = MaterialTheme\.colorScheme\.onSurfaceVariant\s*\).*?Text\(\s*text = "Tiền lãi: "\.t\(\) \+ formatCurrency\(inv\.profit\),.*?fontWeight = FontWeight\.Bold,\s*fontSize = 11\.sp,\s*color = if \(inv\.profit >= 0\) Color\(0xFF4CAF50\) else Color\.Red\s*\)\s*\}\s*Divider\(.*?modifier = Modifier\.padding\(vertical = 8\.dp\)\s*\).*?// List products inside this order\s*items\.forEach \{ item ->.*?Row\(.*?modifier = Modifier.*?\.fillMaxWidth\(\).*?\.padding\(vertical = 2\.dp\),.*?horizontalArrangement = Arrangement\.SpaceBetween\s*\)\s*\{.*?val isProductMatched = searchQuery\.isNotEmpty\(\) && getRelevanceScore\(item\.productName, searchQuery\) != null.*?Text\(\s*text = "• \$\{item\.productName\}",.*?fontSize = 12\.sp,\s*fontWeight = if \(isProductMatched\) FontWeight\.Bold else FontWeight\.Normal,.*?color = if \(isProductMatched\) MaterialTheme\.colorScheme\.error else MaterialTheme\.colorScheme\.onSurface,.*?modifier = Modifier\.weight\(1f\)\s*\).*?Text\(\s*text = "x\$\{item\.quantity\}  \$\{formatCurrency\(item\.sellPrice\)\}",.*?fontSize = 11\.sp,\s*color = MaterialTheme\.colorScheme\.onSurfaceVariant\s*\)\s*\}\s*\}\s*\}\s*\}\s*\}',
    re.DOTALL
)

replacement = '''listInvoices.forEach { invoiceWithItems ->
                                        CustomerInvoiceCard(invoiceWithItems, searchQuery)
                                    }'''

new_content = pattern.sub(replacement, content)

if new_content != content:
    print("PATCH SUCCESSFUL!")
    with open('app/src/main/java/com/example/ui/PosApp.kt', 'w') as f:
        f.write(new_content)
else:
    print("TARGET NOT FOUND!")
