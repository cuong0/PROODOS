package com.example.ui

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun GoogleIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            drawCircle(color = Color(0xFF4285F4), radius = w / 2f)
        }
        Text(
            text = "G",
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            color = Color(0xFF4285F4)
        )
    }
}

@Composable
private fun PasswordCheckItem(label: String, passed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (passed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (passed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = if (passed) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    viewModel: MainViewModel,
    onAuthSuccess: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    // Thông tin đăng nhập
    var loginEmail by remember { mutableStateOf(AccountAuthManager.getLastRegisteredIdentifier(context)) }
    var localAccounts by remember { mutableStateOf(AccountAuthManager.getAllLocalAccounts(context)) }
    var loginPassword by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var isForgotPasswordActive by remember { mutableStateOf(false) }

    // Thông tin đăng ký
    var isRegisterMode by remember { mutableStateOf(false) }
    var registeredGoogleUser by remember { mutableStateOf<FirebaseUser?>(null) }
    var regEmailInput by remember { mutableStateOf("") }
    var checkingStatusText by remember { mutableStateOf<String?>(null) }
    var regStoreName by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var showRegPassword by remember { mutableStateOf(false) }
    var showRegConfirmPassword by remember { mutableStateOf(false) }
    var regError by remember { mutableStateOf<String?>(null) }
    var isRegistering by remember { mutableStateOf(false) }
    var isAuthActionCompletedSuccessfully by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (!isAuthActionCompletedSuccessfully && AccountAuthManager.getActiveAccount(context) == null) {
                try {
                    Firebase.auth.signOut()
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(Unit) {
        if (AccountAuthManager.getActiveAccount(context) == null && Firebase.auth.currentUser != null) {
            try {
                Firebase.auth.signOut()
            } catch (_: Exception) {}
            registeredGoogleUser = null
        }
    }

    suspend fun checkCloudAccountForEmail(email: String, userId: String? = null): RegisteredAccount? {
        val clean = email.lowercase().trim()
        if (clean.isBlank()) return null
        return try {
            var acc = AccountAuthManager.getLocalAccount(context, clean)
                ?: (if (!userId.isNullOrBlank()) AccountAuthManager.getLocalAccountByUserId(context, userId) else null)
                ?: AccountAuthManager.getLocalAccountByUsernameOrEmail(context, clean)

            if (acc == null) {
                acc = withTimeoutOrNull(4500L) {
                    AccountAuthManager.fetchAccountByInputFromFirestore(viewModel.syncManager, clean)
                        ?: (if (!userId.isNullOrBlank()) AccountAuthManager.fetchAccountFromFirestore(viewModel.syncManager, userId) else null)
                }
            }
            acc
        } catch (e: Exception) {
            android.util.Log.e("SignInScreen", "Error checking account for email $clean", e)
            null
        }
    }

    fun handleSelectedGoogleAccount(rawEmail: String, authenticatedUser: FirebaseUser? = null) {
        var clean = rawEmail.lowercase().trim()
        if (clean.isBlank()) {
            // Mở thẳng màn hình tạo tài khoản để người dùng tự nhập hoặc chọn lại Gmail
            isRegistering = false
            checkingStatusText = null
            isRegisterMode = true
            return
        }
        if (!clean.contains("@")) {
            clean = "$clean@gmail.com"
        }

        isRegistering = true
        checkingStatusText = "Đang kiểm tra tài khoản...".t()
        regError = null
        loginError = null

        coroutineScope.launch {
            try {
                val uid = authenticatedUser?.uid
                    ?: (if (Firebase.auth.currentUser?.email.equals(clean, ignoreCase = true)) Firebase.auth.currentUser?.uid else null)
                    ?: AccountAuthManager.deterministicUserIdForEmail(clean)
                val existingAccount = checkCloudAccountForEmail(clean, uid)

                if (existingAccount != null) {
                    // Đã có tài khoản trên hệ thống: điền sẵn vào màn hình Đăng nhập
                    AccountAuthManager.saveAccountLocally(context, existingAccount)
                    localAccounts = AccountAuthManager.getAllLocalAccounts(context)
                    loginEmail = existingAccount.email.ifBlank { clean }
                    loginPassword = ""
                    loginError = null
                    registeredGoogleUser = null
                    regEmailInput = ""
                    isRegisterMode = false
                    Toast.makeText(
                        context,
                        "Tài khoản Gmail này đã được đăng ký trước đó. Đã điền sẵn để bạn nhập mật khẩu đăng nhập.".t(),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // Chưa từng đăng ký: chuyển ngay sang màn hình Tạo tài khoản & Mật khẩu
                    registeredGoogleUser = authenticatedUser
                    regEmailInput = clean
                    isRegisterMode = true
                }
            } catch (e: Exception) {
                // Kể cả khi mạng chậm hoặc lỗi kiểm tra đám mây, vẫn mở màn hình Tạo tài khoản với Gmail đã chọn
                registeredGoogleUser = authenticatedUser
                regEmailInput = clean
                isRegisterMode = true
            } finally {
                isRegistering = false
                checkingStatusText = null
            }
        }
    }

    // Trình chọn tài khoản Google gốc của hệ điều hành Android (AccountManager)
    // Hoạt động 100% trên mọi bản dựng Google Play Console (Internal / Closed / Production) mà không phụ thuộc SHA-1
    val googleAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim().orEmpty()
            if (pickedEmail.isNotBlank()) {
                handleSelectedGoogleAccount(pickedEmail, null)
            } else {
                isRegistering = false
                checkingStatusText = null
                isRegisterMode = true
            }
        } else {
            // Nếu người dùng đóng hộp thoại chọn tài khoản hoặc máy chưa có tài khoản Google,
            // vẫn chuyển sang màn hình Tạo tài khoản để người dùng có thể nhập Gmail trực tiếp
            isRegistering = false
            checkingStatusText = null
            isRegisterMode = true
        }
    }

    fun launchSystemGoogleAccountPicker() {
        try {
            @Suppress("DEPRECATION")
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                false,
                null,
                null,
                null,
                null
            )
            googleAccountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            isRegistering = false
            checkingStatusText = null
            isRegisterMode = true
        }
    }

    fun startGoogleRegistrationFlow() {
        isRegistering = true
        checkingStatusText = "Đang kết nối Google...".t()
        regError = null
        loginError = null

        // Trên bản dựng Google Play Console (Internal / Closed Testing), Google Play tự động ký lại ứng dụng
        // bằng khóa Play App Signing có SHA-1 khác với debug.keystore nên CredentialManager sẽ trả về RESULT_CANCELED.
        // Do đó nếu phát hiện chữ ký khác debug.keystore, mở trực tiếp hộp thoại chọn tài khoản Google của hệ thống Android.
        if (!AuthManager.isGoogleSignInCertRegistered(context)) {
            launchSystemGoogleAccountPicker()
            return
        }

        AuthManager.onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                val user = Firebase.auth.currentUser
                val email = user?.email.orEmpty()
                if (email.isNotBlank()) {
                    handleSelectedGoogleAccount(email, user)
                } else {
                    launchSystemGoogleAccountPicker()
                }
            },
            onAuthError = { _ ->
                // Tự động chuyển sang trình chọn tài khoản hệ thống nếu CredentialManager báo lỗi cấu hình SHA-1
                launchSystemGoogleAccountPicker()
            },
            scope = coroutineScope,
            onAuthCancelled = {
                // Tự động mở trình chọn tài khoản hệ thống / màn hình đăng ký thay vì quay về màn hình đăng nhập trống
                launchSystemGoogleAccountPicker()
            }
        )
    }

    if (isForgotPasswordActive) {
        ForgotPasswordFlow(
            viewModel = viewModel,
            onBack = { isForgotPasswordActive = false },
            onRecoverySuccess = { updatedAcc ->
                isAuthActionCompletedSuccessfully = true
                isForgotPasswordActive = false
                loginEmail = updatedAcc.email
                loginPassword = ""
                onAuthSuccess()
            }
        )
        return
    }

    val isShowingRegisterScreen = isRegisterMode || registeredGoogleUser != null

    BackHandler {
        if (isShowingRegisterScreen) {
            try {
                Firebase.auth.signOut()
            } catch (_: Exception) {}
            isRegisterMode = false
            registeredGoogleUser = null
            regEmailInput = ""
            regStoreName = ""
            regPassword = ""
            regConfirmPassword = ""
            regError = null
            isRegistering = false
            checkingStatusText = null
        } else if (onBack != null) {
            if (AccountAuthManager.getActiveAccount(context) == null) {
                try {
                    Firebase.auth.signOut()
                } catch (_: Exception) {}
            }
            onBack()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isShowingRegisterScreen) "Tạo tài khoản mới".t() else "Đăng nhập".t(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isShowingRegisterScreen) {
                                try {
                                    Firebase.auth.signOut()
                                } catch (_: Exception) {}
                                isRegisterMode = false
                                registeredGoogleUser = null
                                regEmailInput = ""
                                regStoreName = ""
                                regPassword = ""
                                regConfirmPassword = ""
                                regError = null
                                isRegistering = false
                                checkingStatusText = null
                            } else if (onBack != null) {
                                if (AccountAuthManager.getActiveAccount(context) == null) {
                                    try {
                                        Firebase.auth.signOut()
                                    } catch (_: Exception) {}
                                }
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("signin_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Trở về".t()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isShowingRegisterScreen) {
                // =========================================================================
                // PHẦN ĐĂNG NHẬP (Gmail đã đăng ký + Mật khẩu)
                // =========================================================================
                Spacer(modifier = Modifier.height(10.dp))

                Image(
                    painter = painterResource(id = R.drawable.logo_8386),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Đăng nhập tài khoản".t(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Trường nhập Tài khoản hoặc Gmail
                OutlinedTextField(
                    value = loginEmail,
                    onValueChange = {
                        loginEmail = it
                        loginError = null
                    },
                    label = { Text("Nhập Gmail đã đăng kí".t()) },
                    placeholder = { Text("Nhập Gmail đã đăng kí...".t()) },
                    leadingIcon = {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_email_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (localAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gợi ý tài khoản:".t(),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        localAccounts.take(4).forEach { acc ->
                            SuggestionChip(
                                onClick = {
                                    loginEmail = acc.email
                                    loginError = null
                                },
                                label = {
                                    Text(
                                        text = acc.storeName.ifBlank { acc.email.substringBefore("@") },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Trường nhập Mật khẩu
                OutlinedTextField(
                    value = loginPassword,
                    onValueChange = {
                        loginPassword = it
                        loginError = null
                    },
                    label = { Text("Mật khẩu".t()) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showLoginPassword = !showLoginPassword }) {
                            Icon(
                                imageVector = if (showLoginPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showLoginPassword) "Ẩn mật khẩu" else "Hiện mật khẩu"
                            )
                        }
                    },
                    visualTransformation = if (showLoginPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Báo lỗi đăng nhập
                AnimatedVisibility(visible = loginError != null) {
                    loginError?.let { msg ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = msg,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Nút Đăng nhập chính bằng tài khoản đã tạo trên app
                Button(
                    onClick = {
                        val inputTrim = loginEmail.trim()
                        val passTrim = loginPassword
                        if (inputTrim.isBlank() || passTrim.isBlank()) {
                            loginError = "Vui lòng điền đầy đủ tài khoản/Gmail và mật khẩu".t()
                            return@Button
                        }

                        isLoggingIn = true
                        loginError = null

                        coroutineScope.launch {
                            try {
                                // 1. Kiểm tra tài khoản đã lưu cục bộ trên thiết bị
                                var account = AccountAuthManager.getLocalAccount(context, inputTrim)
                                    ?: AccountAuthManager.getLocalAccountByUsernameOrEmail(context, inputTrim)

                                // 2. Nếu chưa có trên máy này, tự động tìm trên đám mây Firestore (giới hạn 4.5 giây)
                                if (account == null) {
                                    account = withTimeoutOrNull(4500L) {
                                        AccountAuthManager.fetchAccountByInputFromFirestore(viewModel.syncManager, inputTrim)
                                    }
                                }

                                if (account == null) {
                                    val known = AccountAuthManager.getAllLocalAccounts(context)
                                    val hint = if (known.isNotEmpty()) {
                                        "\n(Gợi ý tài khoản trên máy: " + known.joinToString(", ") { it.storeName.ifBlank { it.email } } + ")"
                                    } else ""
                                    loginError = "Tài khoản hoặc Gmail '$inputTrim' chưa được tìm thấy trên hệ thống. Vui lòng kiểm tra lại tên tài khoản, Gmail đã đăng ký hoặc bấm Đăng ký ngay bên dưới.$hint".t()
                                    return@launch
                                }

                                // 3. Xác thực mật khẩu đã đăng ký
                                val computedHash = AccountAuthManager.hashPassword(passTrim, account.salt)
                                if (computedHash != account.passwordHash) {
                                    loginError = "Mật khẩu không chính xác. Vui lòng kiểm tra lại.".t()
                                    return@launch
                                }

                                // 4. Sao lưu dữ liệu khách trên máy trước khi chuyển sang tài khoản
                                viewModel.prepareLoginForExistingAccount(context)

                                // 5. Đăng nhập thành công! Lưu tài khoản trên thiết bị này và kích hoạt đồng bộ
                                val effectiveUserId = account.userId.ifBlank {
                                    AccountAuthManager.deterministicUserIdForEmail(account.email)
                                }
                                val finalAccount = account.copy(userId = effectiveUserId)
                                AccountAuthManager.saveAccountLocally(context, finalAccount)
                                AccountAuthManager.saveActiveAccount(context, finalAccount)
                                viewModel.setLoggedInAccount(finalAccount, shouldMigrate = false)

                                if (finalAccount.storeName.isNotBlank()) {
                                    viewModel.setStoreName(finalAccount.storeName)
                                }

                                viewModel.syncManager.startSync(effectiveUserId, shouldMigrateLocalData = false)

                                isAuthActionCompletedSuccessfully = true
                                Toast.makeText(context, "Đăng nhập thành công! Toàn bộ dữ liệu tài khoản đã được đồng bộ hóa tức thì.".t(), Toast.LENGTH_SHORT).show()
                                onAuthSuccess()
                            } catch (e: Exception) {
                                loginError = "Lỗi đăng nhập: ".t() + (e.localizedMessage ?: "")
                            } finally {
                                isLoggingIn = false
                            }
                        }
                    },
                    enabled = !isLoggingIn && !isRegistering,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("login_submit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Đang đăng nhập...".t(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Đăng nhập".t(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hợp nhất mục đăng ký vào mục Đăng ký ngay
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Bạn chưa có tài khoản?".t(),
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    TextButton(
                        onClick = {
                            startGoogleRegistrationFlow()
                        },
                        enabled = !isRegistering && !isLoggingIn,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("register_now_btn")
                    ) {
                        if (isRegistering) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = checkingStatusText ?: "Đang xử lý...".t(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Text(
                                text = "Đăng ký ngay".t(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Mục Quên mật khẩu ở sát ngay bên dưới phần đăng ký ngay căn lề giữa
                TextButton(
                    onClick = {
                        isForgotPasswordActive = true
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("forgot_password_btn")
                ) {
                    Text(
                        text = "Quên mật khẩu?".t(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

            } else {
                // =========================================================================
                // PHẦN TẠO TÀI KHOẢN (Khi đã chọn tài khoản Google hoặc bấm Đăng ký ngay)
                // =========================================================================
                val effectiveGoogleEmail = registeredGoogleUser?.email?.takeIf { it.isNotBlank() } ?: regEmailInput
                val isEmailVerifiedByGoogle = registeredGoogleUser?.email?.isNotBlank() == true || regEmailInput.contains("@")
                val passValidation = remember(regPassword) { AccountAuthManager.validatePassword(regPassword) }
                val isPassMatch = remember(regPassword, regConfirmPassword) {
                    regPassword.isNotEmpty() && regPassword == regConfirmPassword
                }
                val isFormValid = regStoreName.isNotBlank() &&
                        effectiveGoogleEmail.trim().isNotBlank() &&
                        passValidation.isValid &&
                        isPassMatch

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_8386),
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Tạo tài khoản & Mật khẩu".t(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Vui lòng hoàn tất thông tin để lưu trữ tài khoản và kích hoạt sao lưu đám mây.".t(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 1. Tên tài khoản / Cửa hàng
                        OutlinedTextField(
                            value = regStoreName,
                            onValueChange = {
                                regStoreName = it.capitalizeWords()
                                regError = null
                            },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            label = { Text("Tên tài khoản / Cửa hàng".t()) },
                            placeholder = { Text("Nhập tên cửa hàng của bạn...".t()) },
                            leadingIcon = {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_store_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. Tài khoản Gmail (Cho phép chọn từ Google trên máy hoặc nhập trực tiếp)
                        OutlinedTextField(
                            value = effectiveGoogleEmail,
                            onValueChange = {
                                if (registeredGoogleUser == null) {
                                    regEmailInput = it
                                    regError = null
                                }
                            },
                            readOnly = registeredGoogleUser != null,
                            label = {
                                Text(
                                    if (isEmailVerifiedByGoogle) "Tài khoản Gmail (Đã liên kết Google)".t()
                                    else "Tài khoản Gmail đăng ký".t()
                                )
                            },
                            placeholder = { Text("nhapemail@gmail.com".t()) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Email,
                                    contentDescription = null,
                                    tint = if (isEmailVerifiedByGoogle) Color(0xFF34A853) else MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (isEmailVerifiedByGoogle) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = "Đã xác thực".t(),
                                        tint = Color(0xFF34A853)
                                    )
                                } else {
                                    IconButton(onClick = { launchSystemGoogleAccountPicker() }) {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = "Chọn tài khoản Google".t(),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_gmail_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 3. Nhập mật khẩu
                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = {
                                regPassword = it
                                regError = null
                            },
                            label = { Text("Nhập mật khẩu".t()) },
                            placeholder = { Text("Tối thiểu 8 ký tự, 1 hoa, 1 số".t()) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showRegPassword = !showRegPassword }) {
                                    Icon(
                                        imageVector = if (showRegPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (showRegPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_password_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Checklist điều kiện mật khẩu
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PasswordCheckItem("Bao gồm ít nhất 8 ký tự".t(), passValidation.hasMinLength)
                            PasswordCheckItem("Có ít nhất 1 ký tự in hoa (A-Z)".t(), passValidation.hasUppercase)
                            PasswordCheckItem("Có ít nhất 1 ký tự là số (0-9)".t(), passValidation.hasDigit)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 4. Nhập lại mật khẩu
                        OutlinedTextField(
                            value = regConfirmPassword,
                            onValueChange = {
                                regConfirmPassword = it
                                regError = null
                            },
                            label = { Text("Nhập lại mật khẩu".t()) },
                            placeholder = { Text("Khớp với mật khẩu phía trên".t()) },
                            leadingIcon = {
                                Icon(Icons.Default.LockReset, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showRegConfirmPassword = !showRegConfirmPassword }) {
                                    Icon(
                                        imageVector = if (showRegConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (showRegConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_confirm_password_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (regConfirmPassword.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPassMatch) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (isPassMatch) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPassMatch) "Mật khẩu khớp chính xác".t() else "Mật khẩu nhập lại không khớp".t(),
                                    fontSize = 11.sp,
                                    color = if (isPassMatch) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        AnimatedVisibility(visible = regError != null) {
                            regError?.let { msg ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Nút Tạo tài khoản: Chỉ cho phép ấn khi điền đầy đủ và đúng thông tin
                        Button(
                            onClick = {
                                if (!isFormValid) {
                                    regError = "Vui lòng điền đầy đủ thông tin và đảm bảo mật khẩu khớp tiêu chuẩn".t()
                                    return@Button
                                }

                                val rawEmail = effectiveGoogleEmail.lowercase().trim()
                                val cleanEmail = if (rawEmail.contains("@")) rawEmail else "$rawEmail@gmail.com"
                                val cleanStore = regStoreName.trim()
                                val user = registeredGoogleUser
                                val effectiveUserId = user?.uid?.takeIf { it.isNotBlank() }
                                    ?: AccountAuthManager.deterministicUserIdForEmail(cleanEmail)

                                isRegistering = true
                                regError = null

                                coroutineScope.launch {
                                    try {
                                        // Kiểm tra nhanh xem email này đã tồn tại trên hệ thống chưa
                                        val existingAcc = checkCloudAccountForEmail(cleanEmail, effectiveUserId)
                                        if (existingAcc != null) {
                                            AccountAuthManager.saveAccountLocally(context, existingAcc)
                                            localAccounts = AccountAuthManager.getAllLocalAccounts(context)
                                            loginEmail = existingAcc.email
                                            loginPassword = ""
                                            isRegisterMode = false
                                            registeredGoogleUser = null
                                            Toast.makeText(
                                                context,
                                                "Tài khoản Gmail này đã được đăng ký trước đó. Vui lòng nhập mật khẩu để đăng nhập.".t(),
                                                Toast.LENGTH_LONG
                                            ).show()
                                            return@launch
                                        }

                                        val salt = java.util.UUID.randomUUID().toString().take(12)
                                        val passHash = AccountAuthManager.hashPassword(regPassword, salt)
                                        val account = RegisteredAccount(
                                            email = cleanEmail,
                                            storeName = cleanStore,
                                            passwordHash = passHash,
                                            salt = salt,
                                            userId = effectiveUserId
                                        )

                                        // 1. Lưu thông tin tài khoản cục bộ trên thiết bị hiện tại
                                        AccountAuthManager.saveAccountLocally(context, account)
                                        AccountAuthManager.saveActiveAccount(context, account)
                                        localAccounts = AccountAuthManager.getAllLocalAccounts(context)

                                        // 2. Tạo/cập nhật user profile trên Firestore (giới hạn 3 giây, không làm treo UI)
                                        try {
                                            viewModel.userRepository.createOrUpdateProfileOnSignIn(
                                                userId = effectiveUserId,
                                                email = cleanEmail,
                                                displayName = user?.displayName ?: cleanStore,
                                                photoURL = user?.photoUrl?.toString()
                                            )
                                        } catch (e: Exception) {
                                            android.util.Log.w("SignInScreen", "Could not create user profile document", e)
                                        }

                                        // 3. Làm sạch dữ liệu tồn đọng trên đám mây (nếu tài khoản từng bị xóa trước đó) và đặt lại nguồn tiền về 0 nếu chưa có đơn hàng/hàng hóa
                                        viewModel.prepareForNewAccountRegistration(context, effectiveUserId)

                                        // 4. Lưu tài khoản vào Firestore (song song, tối đa 3.5 giây)
                                        AccountAuthManager.saveAccountToFirestore(viewModel.syncManager, effectiveUserId, account)

                                        // Cập nhật tài khoản, tên cửa hàng và kích hoạt đồng bộ cho tài khoản mới
                                        viewModel.setStoreName(cleanStore)
                                        viewModel.setLoggedInAccount(account, shouldMigrate = true)

                                        isAuthActionCompletedSuccessfully = true
                                        Toast.makeText(
                                            context,
                                            "Tạo tài khoản thành công! Dữ liệu trên máy đã được đồng bộ vào tài khoản mới. Tên đăng nhập: $cleanEmail hoặc $cleanStore".t(),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onAuthSuccess()
                                    } catch (e: Exception) {
                                        regError = "Lỗi tạo tài khoản: ".t() + (e.localizedMessage ?: "")
                                    } finally {
                                        isRegistering = false
                                        checkingStatusText = null
                                    }
                                }
                            },
                            enabled = isFormValid && !isRegistering,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = Color(0xFFE0E0E0),
                                disabledContentColor = Color(0xFF9E9E9E)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("create_account_submit_button")
                        ) {
                            if (isRegistering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Đang tạo tài khoản...".t(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "Tạo tài khoản".t(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Đổi tài khoản Google khác nếu muốn
                        OutlinedButton(
                            onClick = {
                                try {
                                    Firebase.auth.signOut()
                                } catch (_: Exception) {}
                                registeredGoogleUser = null
                                regError = null
                                launchSystemGoogleAccountPicker()
                            },
                            enabled = !isRegistering,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("change_google_account_btn")
                        ) {
                            Text("Chọn / Đổi tài khoản Google khác".t(), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
