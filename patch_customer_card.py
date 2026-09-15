import re

file_path = "app/src/main/java/com/example/ui/PosApp.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

target = """            // List products inside this order
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isProductMatched = searchQuery.isNotEmpty() && getRelevanceScore(item.productName, searchQuery) != null
                    Text(
                        text = item.productName,
                        fontSize = 12.sp,
                        fontWeight = if (isProductMatched) FontWeight.Bold else FontWeight.Normal,
                        color = if (isProductMatched) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.2f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.quantity}",
                        fontSize = 11.sp,
                        modifier = Modifier.weight(0.3f),
                        textAlign = TextAlign.Center, maxLines = 1
                    )
                    Text(
                        text = formatCurrency(item.sellPrice),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.1f),
                        textAlign = TextAlign.End, maxLines = 1
                    )
                    Text(
                        text = formatCurrency(item.sellPrice * item.quantity),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.4f),
                        textAlign = TextAlign.End, maxLines = 1
                    )
                }
            }"""

replacement = """            // List products inside this order
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isProductMatched = searchQuery.isNotEmpty() && getRelevanceScore(item.productName, searchQuery) != null
                    Text(
                        text = item.productName,
                        fontSize = 12.sp,
                        fontWeight = if (isProductMatched) FontWeight.Bold else FontWeight.Normal,
                        color = if (isProductMatched) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.2f).padding(vertical = 2.dp, end = 4.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    androidx.compose.foundation.layout.Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    Text(
                        text = "${item.quantity}",
                        fontSize = 11.sp,
                        modifier = Modifier.weight(0.3f).padding(vertical = 2.dp),
                        textAlign = TextAlign.Center, maxLines = 1
                    )
                    androidx.compose.foundation.layout.Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    Text(
                        text = formatCurrency(item.sellPrice),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.1f).padding(vertical = 2.dp, end = 4.dp),
                        textAlign = TextAlign.End, maxLines = 1
                    )
                    androidx.compose.foundation.layout.Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                    Text(
                        text = formatCurrency(item.sellPrice * item.quantity),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.4f).padding(vertical = 2.dp),
                        textAlign = TextAlign.End, maxLines = 1
                    )
                }
            }"""

if target in content:
    content = content.replace(target, replacement)
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Replaced successfully")
else:
    print("Could not find the target text")
