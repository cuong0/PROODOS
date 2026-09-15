import re

with open('app/src/main/java/com/example/ui/PosApp.kt', 'r') as f:
    content = f.read()

target = '''                    Text(
                        text = "Vốn: ".t() + "${formatCurrency(cartItem.importPrice)}/" + "sp".t(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }'''

replacement = '''                    Text(
                        text = "Vốn: ".t() + "${formatCurrency(cartItem.importPrice)}/" + "sp".t(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (cartItem.product.trackInventory) {
                        Text(
                            text = "Tồn còn lại: ".t() + (cartItem.product.stockQuantity - cartItem.quantity),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }'''

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/ui/PosApp.kt', 'w') as f:
    f.write(content)
