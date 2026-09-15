sed -i 's/_cart.value = currentList/updateCart(currentList)/g' app/src/main/java/com/example/ui/MainViewModel.kt
sed -i 's/_cart.value = emptyList()/updateCart(emptyList())/g' app/src/main/java/com/example/ui/MainViewModel.kt
