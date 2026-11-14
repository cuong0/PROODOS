package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class CroppedAvatarResult(
    val bitmap: Bitmap,
    val localUri: String,
    val base64DataUri: String
)

/**
 * Hiển thị ảnh đại diện hỗ trợ mọi nguồn dữ liệu: Bitmap, chuỗi Data URI Base64, file://, content://, hoặc web URL
 */
@Composable
fun UserAvatarView(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    avatarBitmap: Bitmap? = null
) {
    if (avatarBitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = avatarBitmap.asImageBitmap(),
            contentDescription = "Avatar",
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        return
    }

    if (avatarUrl.isNullOrBlank()) {
        Surface(
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(size * 0.78f),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        return
    }

    if (avatarUrl.startsWith("data:image/") || avatarUrl.startsWith("data:application/")) {
        val decodedBitmap = remember(avatarUrl) {
            try {
                val base64Part = avatarUrl.substringAfter("base64,")
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }

        if (decodedBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = decodedBitmap.asImageBitmap(),
                contentDescription = "Avatar",
                modifier = modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Avatar",
                        modifier = Modifier.size(size * 0.78f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    } else {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Avatar",
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    viewModel: MainViewModel,
    account: RegisteredAccount,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Form states
    var storeNameInput by remember(account) { mutableStateOf(account.storeName) }
    var pendingAvatarBase64 by remember { mutableStateOf<String?>(null) }
    var pendingAvatarBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Password change states
    var oldPasswordInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var oldPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var passwordErrorMessage by remember { mutableStateOf<String?>(null) }

    // Dialog & navigation states
    var showCropDialog by remember { mutableStateOf(false) }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showForgotPasswordFlow by remember { mutableStateOf(false) }
    var showDeleteVerifyDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteVerifyPasswordInput by remember { mutableStateOf("") }
    var deleteVerifyPasswordVisible by remember { mutableStateOf(false) }
    var deleteVerifyErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    // Photo picker launcher (standard zero-permission Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            pickedImageUri = uri
            showCropDialog = true
        }
    }

    BackHandler {
        if (showForgotPasswordFlow) {
            showForgotPasswordFlow = false
        } else {
            onClose()
        }
    }

    if (showForgotPasswordFlow) {
        ForgotPasswordFlow(
            viewModel = viewModel,
            onBack = { showForgotPasswordFlow = false },
            onRecoverySuccess = { updatedAcc ->
                showForgotPasswordFlow = false
                Toast.makeText(context, "Đặt lại mật khẩu thành công!".t(), Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Cài đặt tài khoản".t(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại".t()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                val hasAvatarChange = pendingAvatarBase64 != null
                val hasNameChange = storeNameInput.trim() != account.storeName.trim()
                val hasPasswordChange = newPasswordInput.isNotBlank() || oldPasswordInput.isNotBlank()
                val hasAnyChange = hasAvatarChange || hasNameChange || hasPasswordChange

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            // Validate password change if entered
                            if (hasPasswordChange) {
                                if (oldPasswordInput.isBlank()) {
                                    passwordErrorMessage = "Vui lòng nhập mật khẩu cũ".t()
                                    return@Button
                                }
                                val oldHash = AccountAuthManager.hashPassword(oldPasswordInput, account.salt)
                                if (oldHash != account.passwordHash) {
                                    passwordErrorMessage = "Mật khẩu cũ không chính xác".t()
                                    return@Button
                                }
                                val validResult = AccountAuthManager.validatePassword(newPasswordInput)
                                if (!validResult.isValid) {
                                    passwordErrorMessage = "Mật khẩu mới cần tối thiểu 8 ký tự, gồm ít nhất 1 chữ in hoa và 1 số".t()
                                    return@Button
                                }
                                if (newPasswordInput != confirmPasswordInput) {
                                    passwordErrorMessage = "Mật khẩu mới không trùng khớp".t()
                                    return@Button
                                }
                                passwordErrorMessage = null
                            }

                            isSaving = true
                            viewModel.updateCurrentAccountProfile(
                                context = context,
                                newStoreName = if (hasNameChange) storeNameInput.trim() else null,
                                newAvatarUrl = pendingAvatarBase64,
                                newPassword = if (hasPasswordChange) newPasswordInput else null
                            ) { success, err ->
                                isSaving = false
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "Đã lưu và đồng bộ thay đổi thành công lên đám mây!".t(),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // Reset password fields
                                    oldPasswordInput = ""
                                    newPasswordInput = ""
                                    confirmPasswordInput = ""
                                    pendingAvatarBase64 = null
                                    pendingAvatarBitmap = null
                                    onClose()
                                } else {
                                    Toast.makeText(
                                        context,
                                        err?.t() ?: "Không thể lưu thay đổi".t(),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = hasAnyChange && !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_account_changes_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Đang đồng bộ hóa...".t(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lưu thay đổi".t(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. PHẦN AVATAR
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val activeAvatar = pendingAvatarBase64 ?: account.avatarUrl
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier.size(96.dp)
                    ) {
                        UserAvatarView(
                            avatarUrl = activeAvatar,
                            avatarBitmap = pendingAvatarBitmap,
                            size = 96.dp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )

                        // Camera change icon badge
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .shadow(4.dp, CircleShape)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Đổi ảnh đại diện".t(),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (pendingAvatarBitmap != null) "Chọn lại ảnh khác".t() else "Đổi ảnh đại diện".t(),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (pendingAvatarBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Đã chỉnh sửa ảnh (Nhấn 'Lưu thay đổi' để cập nhật)".t(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 2. PHẦN TÊN TÀI KHOẢN / CỬA HÀNG
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Thông tin tài khoản".t(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = account.email,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Email tài khoản".t()) },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = storeNameInput,
                        onValueChange = { storeNameInput = it.capitalizeWords() },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        label = { Text("Tên cửa hàng / Tên hiển thị".t()) },
                        placeholder = { Text("Nhập tên hiển thị mới...".t()) },
                        leadingIcon = {
                            Icon(Icons.Default.Storefront, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // 3. PHẦN ĐỔI MẬT KHẨU
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Đổi mật khẩu".t(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Dòng 1: Mật khẩu cũ
                    OutlinedTextField(
                        value = oldPasswordInput,
                        onValueChange = {
                            oldPasswordInput = it
                            passwordErrorMessage = null
                        },
                        label = { Text("Nhập mật khẩu cũ".t()) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { oldPasswordVisible = !oldPasswordVisible }) {
                                Icon(
                                    imageVector = if (oldPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (oldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("old_password_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Dòng 2: Mật khẩu mới
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = {
                            newPasswordInput = it
                            passwordErrorMessage = null
                        },
                        label = { Text("Nhập mật khẩu mới".t()) },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Icon(
                                    imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_password_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Yêu cầu mật khẩu
                    val hasMinLength = newPasswordInput.length >= 8
                    val hasUpper = newPasswordInput.any { it.isUpperCase() }
                    val hasDigit = newPasswordInput.any { it.isDigit() }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PasswordRequirementChip(label = "8+ ký tự".t(), satisfied = hasMinLength)
                        PasswordRequirementChip(label = "1 chữ in hoa".t(), satisfied = hasUpper)
                        PasswordRequirementChip(label = "1 chữ số".t(), satisfied = hasDigit)
                    }

                    // Dòng 3: Nhập lại mật khẩu mới
                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = {
                            confirmPasswordInput = it
                            passwordErrorMessage = null
                        },
                        label = { Text("Nhập lại mật khẩu mới".t()) },
                        leadingIcon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_new_password_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (passwordErrorMessage != null) {
                        Text(
                            text = passwordErrorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Quên mật khẩu nằm ở dưới phần đổi mật khẩu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showForgotPasswordFlow = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("forgot_password_button")
                        ) {
                            Text(
                                text = "Quên mật khẩu?".t(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 4. MỤC XÓA TÀI KHOẢN (NỀN HỒNG NHẠT, CHỮ ĐỎ)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFEE2E2) // Nền màu hồng nhạt
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        deleteVerifyPasswordInput = ""
                        deleteVerifyErrorMessage = null
                        showDeleteVerifyDialog = true
                    }
                    .testTag("delete_account_section")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFDC2626).copy(alpha = 0.12f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = Color(0xFFDC2626), // Chữ & icon màu đỏ
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Xóa tài khoản".t(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626) // Chữ màu đỏ
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Xóa vĩnh viễn tài khoản và toàn bộ dữ liệu trên đám mây".t(),
                            fontSize = 12.sp,
                            color = Color(0xFFB91C1C) // Chữ màu đỏ trầm dễ đọc
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFDC2626)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // DIALOG 1: Cắt & Chỉnh sửa ảnh (1:1, Khung tròn mờ, Zoom to nhỏ tức thì)
    if (showCropDialog && pickedImageUri != null) {
        AvatarCropDialog(
            imageUri = pickedImageUri!!,
            onDismiss = {
                showCropDialog = false
                pickedImageUri = null
            },
            onCropConfirmed = { result ->
                pendingAvatarBitmap = result.bitmap
                pendingAvatarBase64 = result.base64DataUri
                showCropDialog = false
                pickedImageUri = null
                Toast.makeText(context, "Đã chọn và cắt ảnh đại diện thành công".t(), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // DIALOG 2: Bước 1 Xóa tài khoản - Điền mật khẩu đăng nhập đúng mới cho xác nhận
    if (showDeleteVerifyDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showDeleteVerifyDialog = false
                    deleteVerifyPasswordInput = ""
                    deleteVerifyErrorMessage = null
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Xác thực xóa tài khoản".t(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Vui lòng nhập mật khẩu đăng nhập của tài khoản để tiếp tục:".t(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = deleteVerifyPasswordInput,
                        onValueChange = {
                            deleteVerifyPasswordInput = it
                            deleteVerifyErrorMessage = null
                        },
                        label = { Text("Mật khẩu của bạn".t()) },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { deleteVerifyPasswordVisible = !deleteVerifyPasswordVisible }) {
                                Icon(
                                    imageVector = if (deleteVerifyPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (deleteVerifyPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_verify_password_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (deleteVerifyErrorMessage != null) {
                        Text(
                            text = deleteVerifyErrorMessage ?: "",
                            color = Color(0xFFDC2626),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val computedHash = AccountAuthManager.hashPassword(deleteVerifyPasswordInput, account.salt)
                        if (computedHash != account.passwordHash) {
                            deleteVerifyErrorMessage = "Mật khẩu không chính xác. Vui lòng thử lại!".t()
                        } else {
                            deleteVerifyErrorMessage = null
                            showDeleteVerifyDialog = false
                            showDeleteConfirmDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_verify_password_button")
                ) {
                    Text("Xác nhận".t(), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteVerifyDialog = false
                        deleteVerifyPasswordInput = ""
                        deleteVerifyErrorMessage = null
                    }
                ) {
                    Text("Hủy".t())
                }
            }
        )
    }

    // DIALOG 3: Bước 2 Xóa tài khoản - Xác nhận xóa vĩnh viễn trên đám mây
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) showDeleteConfirmDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "XÁC NHẬN XÓA TÀI KHOẢN VĨNH VIỄN".t(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFFDC2626),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Bạn có chắc chắn muốn xóa tài khoản này không?".t(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Ngay lập tức tài khoản sẽ được xóa khỏi hệ thống trên đám mây và toàn bộ dữ liệu (sản phẩm, hóa đơn, đơn nhập, công nợ, cài đặt) sẽ bị XÓA VĨNH VIỄN trên đám mây và KHÔNG THỂ KHÔI PHỤC LẠI.".t(),
                        fontSize = 13.sp,
                        color = Color(0xFFB91C1C),
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        viewModel.deleteCurrentAccount(context) { success ->
                            isDeleting = false
                            showDeleteConfirmDialog = false
                            if (success) {
                                Toast.makeText(
                                    context,
                                    "Đã xóa vĩnh viễn tài khoản và toàn bộ dữ liệu!".t(),
                                    Toast.LENGTH_LONG
                                ).show()
                                onClose()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Đã xóa tài khoản trên thiết bị, một số dữ liệu đám mây có thể cần mạng để dọn sạch.".t(),
                                    Toast.LENGTH_LONG
                                ).show()
                                onClose()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    enabled = !isDeleting,
                    modifier = Modifier.testTag("final_delete_account_button")
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Đang xóa...".t())
                    } else {
                        Text("XÓA VĨNH VIỄN".t(), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !isDeleting
                ) {
                    Text("Hủy bỏ".t())
                }
            }
        )
    }
}

@Composable
private fun PasswordRequirementChip(label: String, satisfied: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (satisfied) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (satisfied) Icons.Default.Check else Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = if (satisfied) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (satisfied) FontWeight.SemiBold else FontWeight.Normal,
                color = if (satisfied) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Dialog chỉnh sửa ảnh với tỉ lệ 1:1, khung tròn mờ (scrim overlay) và bộ điều khiển zoom to nhỏ trực tiếp
 */
@Composable
fun AvatarCropDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropConfirmed: (CroppedAvatarResult) -> Unit
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var viewportPx by remember { mutableFloatStateOf(0f) }
    var isLoadingImage by remember { mutableStateOf(true) }

    LaunchedEffect(imageUri) {
        isLoadingImage = true
        withContext(Dispatchers.IO) {
            try {
                // Đọc kích thước trước để downsample nếu ảnh điện thoại quá lớn (>2048px)
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }

                val maxDimension = 2048
                var sampleSize = 1
                while (boundsOptions.outWidth / sampleSize > maxDimension || boundsOptions.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                val raw = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }

                if (raw != null) {
                    // Kiểm tra hướng xoay EXIF của ảnh
                    val orientation = try {
                        context.contentResolver.openFileDescriptor(imageUri, "r")?.use { pfd ->
                            val exif = android.media.ExifInterface(pfd.fileDescriptor)
                            exif.getAttributeInt(
                                android.media.ExifInterface.TAG_ORIENTATION,
                                android.media.ExifInterface.ORIENTATION_NORMAL
                            )
                        } ?: android.media.ExifInterface.ORIENTATION_NORMAL
                    } catch (e: Throwable) {
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    }

                    val matrix = Matrix()
                    when (orientation) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    }

                    val corrected = if (orientation == 6 || orientation == 3 || orientation == 8) {
                        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                    } else {
                        raw
                    }
                    sourceBitmap = corrected
                }
            } catch (e: Exception) {
                sourceBitmap = null
            } finally {
                isLoadingImage = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Chỉnh sửa ảnh đại diện".t(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Kéo để di chuyển, dùng thanh trượt để thu phóng hoặc nút xoay ảnh".t(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // VIEWPORT TỈ LỆ 1:1 VỚI KHUNG TRÒN MỜ (TRANSLUCENT CIRCULAR MASK)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E1E1E))
                        .onGloballyPositioned { coords ->
                            viewportPx = coords.size.width.toFloat()
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1.0f, 4.0f)
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = sourceBitmap
                    if (bmp != null) {
                        // Render ảnh với graphicsLayer để phóng to thu nhỏ, xoay và kéo thả theo thời gian thực
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Chỉnh sửa avatar".t(),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                    rotationZ = rotationDegrees
                                },
                            contentScale = ContentScale.Fit
                        )

                        // Khung tròn mờ: che mờ phần ngoài hình tròn (dim mask)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val radius = (minOf(w, h) / 2f) - 6f
                            val center = Offset(w / 2f, h / 2f)

                            val maskPath = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(0f, 0f, w, h))
                                addOval(Rect(center = center, radius = radius))
                            }
                            // Lớp phủ mờ 60% ở ngoài hình tròn
                            drawPath(maskPath, color = Color.Black.copy(alpha = 0.60f))

                            // Vòng tròn viền hướng dẫn
                            drawCircle(
                                color = Color.White.copy(alpha = 0.90f),
                                radius = radius,
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    } else if (isLoadingImage) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Không thể tải ảnh. Vui lòng thử lại.".t(),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // THANH ĐIỀU CHỈNH ZOOM VÀ NÚT XOAY ẢNH TRÊN CÙNG MỘT DÒNG (BÊN TAY PHẢI)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { scale = (scale - 0.25f).coerceAtLeast(1.0f) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = "Thu nhỏ".t(),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Slider(
                        value = scale,
                        onValueChange = { newScale -> scale = newScale },
                        valueRange = 1.0f..4.0f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .testTag("avatar_zoom_slider")
                    )

                    IconButton(
                        onClick = { scale = (scale + 0.25f).coerceAtMost(4.0f) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Phóng to".t(),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    VerticalDivider(
                        modifier = Modifier.height(28.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Nút xoay ảnh bên tay phải (xoay 90° mỗi lần ấn)
                    FilledTonalIconButton(
                        onClick = {
                            rotationDegrees = (rotationDegrees + 90f) % 360f
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("avatar_rotate_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Xoay ảnh 90 độ".t(),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${"Thu phóng".t()}: ${(scale * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "${"Góc xoay".t()}: ${rotationDegrees.toInt()}°",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // NÚT HỦY VÀ XÁC NHẬN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Hủy".t())
                    }

                    Button(
                        onClick = {
                            val bmp = sourceBitmap ?: return@Button
                            val viewDim = if (viewportPx > 0f) viewportPx else 600f
                            val result = cropBitmapToSquare(
                                context = context,
                                source = bmp,
                                scale = scale,
                                offsetX = offset.x,
                                offsetY = offset.y,
                                rotationDegrees = rotationDegrees,
                                viewSizePx = viewDim,
                                outputDim = 300
                            )
                            onCropConfirmed(result)
                        },
                        enabled = sourceBitmap != null,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_crop_avatar_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Xác nhận".t(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Cắt và nén bitmap theo thông số biến đổi thành Bitmap hiển thị ngay và Data URI Base64 cho Cloud Firestore
 */
private fun cropBitmapToSquare(
    context: Context,
    source: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    rotationDegrees: Float = 0f,
    viewSizePx: Float,
    outputDim: Int = 300
): CroppedAvatarResult {
    val output = Bitmap.createBitmap(outputDim, outputDim, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val viewDim = if (viewSizePx > 0f) viewSizePx else 600f
    val fitScale = minOf(viewDim / source.width, viewDim / source.height)
    val totalScale = fitScale * scale
    val outRatio = outputDim.toFloat() / viewDim

    val matrix = Matrix()
    matrix.postTranslate(-source.width / 2f, -source.height / 2f)
    matrix.postRotate(rotationDegrees)
    val finalScale = totalScale * outRatio
    matrix.postScale(finalScale, finalScale)
    matrix.postTranslate(outputDim / 2f + offsetX * outRatio, outputDim / 2f + offsetY * outRatio)

    canvas.drawBitmap(source, matrix, paint)

    // 1. Lưu file ảnh cục bộ
    val avatarDir = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
    val avatarFile = File(avatarDir, "avatar_${System.currentTimeMillis()}.jpg")
    try {
        FileOutputStream(avatarFile).use { out ->
            output.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
    } catch (_: Exception) {}
    val localUri = Uri.fromFile(avatarFile).toString()

    // 2. Mã hóa chuỗi Data URI Base64 để đồng bộ Firestore
    val stream = ByteArrayOutputStream()
    output.compress(Bitmap.CompressFormat.JPEG, 85, stream)
    val base64Bytes = stream.toByteArray()
    val base64String = "data:image/jpeg;base64," + Base64.encodeToString(base64Bytes, Base64.NO_WRAP)

    return CroppedAvatarResult(
        bitmap = output,
        localUri = localUri,
        base64DataUri = base64String
    )
}
