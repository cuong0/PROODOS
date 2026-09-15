import re

file_path = "app/src/main/java/com/example/ui/PosApp.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

header_target = """            // Column headers
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tên sản phẩm".t(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.15f))
                Spacer(modifier = Modifier.width(9.dp))
                Text("SL".t(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(9.dp))
                Text("Đơn giá".t(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.15f), textAlign = TextAlign.End)
                Spacer(modifier = Modifier.width(9.dp))
                Text("Thành tiền".t(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End)
            }"""

header_replacement = """            // Column headers
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tên sản phẩm".t(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.15f))
                Spacer(modifier = Modifier.width(9.dp))
                Text("SL".t(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(9.dp))
                Text("Đơn giá".t(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.15f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(9.dp))
                Text("Thành tiền".t(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Center)
            }"""

rows_target = """                    Text(
                        text = formatCurrency(item.sellPrice),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.15f).padding(vertical = 2.dp),
                        textAlign = TextAlign.End, maxLines = 1
                    )
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(horizontal = 4.dp).width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    Text(
                        text = formatCurrency(item.sellPrice * item.quantity),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.3f).padding(vertical = 2.dp),
                        textAlign = TextAlign.End, maxLines = 1
                    )"""

rows_replacement = """                    Text(
                        text = formatCurrency(item.sellPrice),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.15f).padding(vertical = 2.dp),
                        textAlign = TextAlign.Center, maxLines = 1
                    )
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(horizontal = 4.dp).width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    Text(
                        text = formatCurrency(item.sellPrice * item.quantity),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.3f).padding(vertical = 2.dp),
                        textAlign = TextAlign.Center, maxLines = 1
                    )"""

if header_target in content:
    content = content.replace(header_target, header_replacement)
    print("Header replaced successfully")
if rows_target in content:
    content = content.replace(rows_target, rows_replacement)
    print("Rows replaced successfully")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
