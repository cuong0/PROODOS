import re

with open('app/src/main/java/com/example/ui/PosApp.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'ProductGridCard(\n                                product = item,',
    'ProductGridCard(\n                                product = item,\n                                cartQuantity = cart.find { it.product.id == item.id }?.quantity ?: 0,'
)

content = content.replace(
    'ProductListCard(\n                                product = item,',
    'ProductListCard(\n                                product = item,\n                                cartQuantity = cart.find { it.product.id == item.id }?.quantity ?: 0,'
)

with open('app/src/main/java/com/example/ui/PosApp.kt', 'w') as f:
    f.write(content)
