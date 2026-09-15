import re

file_path = "app/src/main/java/com/example/ui/PosApp.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

target = """            // List products inside this order
            items.forEach { item ->
                androidx.compose.material3.HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Row("""

replacement = """            // List products inside this order
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    androidx.compose.material3.HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                Row("""

if target in content:
    content = content.replace(target, replacement)
    print("Replaced successfully")
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
else:
    print("Target not found")
