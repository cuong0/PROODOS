sed -i '1720,1746c\
                Row(\
                    modifier = Modifier\
                        .fillMaxSize()\
                        .padding(16.dp),\
                    verticalAlignment = Alignment.CenterVertically\
                ) {\
                    val greenColor = if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32)\
                    Icon(\
                        Icons.Default.LocalShipping,\
                        contentDescription = null,\
                        modifier = Modifier.size(48.dp),\
                        tint = greenColor\
                    )\
                    Spacer(modifier = Modifier.width(16.dp))\
                    Column {\
                        Text(\
                            text = "Quản lý nhập hàng".t(),\
                            fontSize = 16.sp,\
                            fontWeight = FontWeight.Bold,\
                            color = greenColor\
                        )\
                        Text(\
                            text = "Quản lý đơn nhập hàng, thêm nhà cung cấp, cập nhật giá vốn và số lượng tồn kho.".t(),\
                            fontSize = 12.sp,\
                            color = greenColor.copy(alpha = 0.8f)\
                        )\
                    }\
                }' app/src/main/java/com/example/ui/PosApp.kt
