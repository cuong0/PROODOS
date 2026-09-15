import re

file_path = "app/src/main/java/com/example/ui/PosApp.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

target = """            // List products inside this order
            items.forEach { item ->
                Row("""

replacement = """            // List products inside this order
            items.forEach { item ->
                androidx.compose.material3.HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Row("""

if target in content:
    content = content.replace(target, replacement)
    print("Dividers added successfully")
else:
    print("Target not found")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
