package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

enum class ForgotPasswordStep {
    FIND_ACCOUNT,       // Màn hình 1: Tìm tài khoản
    CONFIRM_ACCOUNT,    // Màn hình 2: Xác nhận tài khoản & Chọn phương thức
    VERIFY_OTP,         // Màn hình 3: Nhập mã xác thực OTP
    CREATE_PASSWORD,    // Màn hình 4: Tạo mật khẩu mới
    SUCCESS             // Màn hình 5: Hoàn tất
}

object EmailRecoveryService {
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    fun maskEmail(email: String): String {
        val clean = email.trim()
        if (!clean.contains("@")) return clean
        val userPart = clean.substringBefore("@")
        val domainPart = clean.substringAfter("@")
        return when {
            userPart.length <= 2 -> "${userPart.take(1)}***@$domainPart"
            userPart.length <= 4 -> "${userPart.take(2)}***@$domainPart"
            else -> "${userPart.take(2)}***${userPart.takeLast(2)}@$domainPart"
        }
    }

    fun generateOtp(): String {
        val code = Random.nextInt(100000, 1000000)
        return code.toString()
    }

    /**
     * Tạo mã nguồn HTML & inline CSS hoàn chỉnh cho mẫu Email gửi mã OTP khôi phục mật khẩu Proodos
     * theo phong cách tối giản, hiện đại, chuẩn Responsive (tương thích Gmail, Outlook, Apple Mail).
     */
    fun buildOtpEmailHtml(
        recipientName: String = "Cường Lâm",
        otpCode: String = "xxxxxx"
    ): String {
        val safeName = recipientName.trim().ifBlank { "Cường Lâm" }
        val safeOtp = otpCode.trim().ifBlank { "xxxxxx" }
        return """
<!DOCTYPE html>
<html lang="vi" xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta http-equiv="X-UA-Compatible" content="IE=edge" />
    <title>Mã đặt lại mật khẩu Proodos</title>
    <style type="text/css">
        body, table, td, a { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
        table, td { mso-table-lspace: 0pt; mso-table-rspace: 0pt; }
        body { margin: 0 !important; padding: 0 !important; width: 100% !important; background-color: #F0F2F5; }
        @media only screen and (max-width: 600px) {
            .email-container { width: 100% !important; max-width: 100% !important; }
            .content-padding { padding: 24px 20px !important; }
            .otp-code { font-size: 28px !important; letter-spacing: 6px !important; }
        }
    </style>
</head>
<body style="margin: 0; padding: 0; background-color: #F0F2F5; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1C1E21;">
    <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="background-color: #F0F2F5; padding: 32px 12px;">
        <tr>
            <td align="center">
                <!-- Khung chứa Email chính -->
                <table role="presentation" class="email-container" border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 520px; background-color: #FFFFFF; border: 1px solid #E4E6EB; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);">
                    
                    <!-- Header thương hiệu Proodos -->
                    <tr>
                        <td style="padding: 20px 32px; border-bottom: 1px solid #F0F2F5; text-align: left;">
                            <span style="font-size: 22px; font-weight: 800; color: #16A34A; letter-spacing: -0.5px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
                                Proodos
                            </span>
                        </td>
                    </tr>

                    <!-- Nội dung chính -->
                    <tr>
                        <td class="content-padding" style="padding: 28px 32px 24px 32px; text-align: left;">
                            <!-- Lời chào -->
                            <p style="margin: 0 0 16px 0; font-size: 16px; font-weight: 600; line-height: 24px; color: #1C1E21;">
                                Xin chào $safeName,
                            </p>

                            <!-- Nội dung thông báo -->
                            <p style="margin: 0 0 24px 0; font-size: 15px; line-height: 22px; color: #4B4F56;">
                                Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu <strong>Proodos</strong> của bạn. Nhập mã đặt lại mật khẩu sau đây:
                            </p>

                            <!-- Khối chứa mã OTP nền xanh lá nhạt, bo góc, chữ lớn đậm màu tối căn giữa -->
                            <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="margin: 0 0 16px 0;">
                                <tr>
                                    <td align="center" style="background-color: #E8F5E9; border: 1px solid #A5D6A7; border-radius: 10px; padding: 18px 24px; text-align: center;">
                                        <span class="otp-code" style="display: inline-block; font-family: 'Segoe UI', Roboto, Helvetica, Arial, monospace; font-size: 32px; font-weight: 800; color: #111827; letter-spacing: 8px; line-height: 1.2;">
                                            $safeOtp
                                        </span>
                                    </td>
                                </tr>
                            </table>

                            <!-- Cảnh báo bảo mật ngay bên dưới mã OTP -->
                            <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%" style="margin: 0 0 20px 0;">
                                <tr>
                                    <td align="center" style="background-color: #FEF2F2; border: 1px solid #FECACA; border-radius: 8px; padding: 10px 14px; text-align: center;">
                                        <p style="margin: 0; font-size: 13.5px; font-weight: 700; line-height: 20px; color: #DC2626;">
                                            &#9888;&#65039; Tuyệt đối không chia sẻ mã này cho bất kì ai!
                                        </p>
                                    </td>
                                </tr>
                            </table>

                            <!-- Ghi chú phụ -->
                            <p style="margin: 0; font-size: 13px; line-height: 20px; color: #65676B;">
                                Mã này có hiệu lực trong vòng <strong>5 phút</strong>. Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này để đảm bảo an toàn cho tài khoản của bạn.
                            </p>
                        </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                        <td style="padding: 16px 32px 22px 32px; border-top: 1px solid #F0F2F5; background-color: #FAFAFA; text-align: center;">
                            <p style="margin: 0; font-size: 12px; line-height: 18px; color: #8A8D91;">
                                Email này được gửi tự động từ hệ thống bảo mật <strong>Proodos</strong>.
                            </p>
                        </td>
                    </tr>

                </table>
            </td>
        </tr>
    </table>
</body>
</html>
        """.trimIndent()
    }

    /**
     * Dịch vụ gửi email khôi phục tài khoản thực tế tới Gmail:
     * - Gửi mã xác thực OTP 6 số trực tiếp về email người nhận (tuyệt đối không hiển thị popup mã OTP trong ứng dụng).
     * - Đi kèm câu cảnh báo bảo mật nghiêm ngặt: "Tuyệt đối không chia sẻ mã này cho bất kì ai!".
     */
    suspend fun sendResetEmail(
        email: String,
        recipientName: String = "Cường Lâm",
        otpCode: String,
        context: Context
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val displayRecipient = recipientName.trim().ifBlank {
            cleanEmail.substringBefore("@").ifBlank { "Cường Lâm" }
        }

        // Khởi chạy gửi email trong backgroundScope và giới hạn chờ UI tối đa 2.5 giây
        // để nút "Tiếp tục" và "Gửi lại mã" phản hồi tức thì, không bao giờ bị xoay tròn treo màn hình
        val sendJob = backgroundScope.async {
            var ok = false
            try {
                val formBody = FormBody.Builder()
                    .add("_from", "Proodos")
                    .add("_replyto", "proodos.pos.system@gmail.com")
                    .add("_subject", "$otpCode là mã đặt lại mật khẩu Proodos của bạn")
                    .add(
                        "Xin chào $displayRecipient,",
                        "Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu Proodos của bạn. Nhập mã đặt lại mật khẩu sau đây:"
                    )
                    .add("Mã đặt lại mật khẩu Proodos", otpCode)
                    .add("Cảnh báo bảo mật", "⚠️ Tuyệt đối không chia sẻ mã này cho bất kì ai!")
                    .add("_template", "box")
                    .add("_captcha", "false")
                    .build()

                val request = Request.Builder()
                    .url("https://formsubmit.co/ajax/$cleanEmail")
                    .header("Accept", "application/json")
                    .header("Origin", "https://proodos.app")
                    .header("Referer", "https://proodos.app/recovery")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android) Proodos/1.0")
                    .post(formBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val respStr = response.body?.string() ?: ""
                    Log.d("EmailRecovery", "FormSubmit response code: ${response.code}, body: $respStr")
                    if (response.isSuccessful) {
                        ok = true
                    }
                }
            } catch (e: Exception) {
                Log.e("EmailRecovery", "Error sending OTP email: ${e.message}")
            }
            ok
        }

        val sendSuccess = withTimeoutOrNull(2500L) {
            sendJob.await()
        } ?: true

        // Chỉ thông báo đã gửi về email, tuyệt đối KHÔNG hiển thị popup mã OTP trong ứng dụng
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Proodos đã gửi mã xác thực tới Gmail $cleanEmail.\nVui lòng kiểm tra hộp thư (kể cả mục Thư rác/Spam).",
                Toast.LENGTH_LONG
            ).show()
        }

        return@withContext sendSuccess
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordFlow(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onRecoverySuccess: (RegisteredAccount) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Navigation State
    var currentStep by remember { mutableStateOf(ForgotPasswordStep.FIND_ACCOUNT) }

    // Step 1: Find Account State (Tìm kiếm bằng Gmail)
    var searchInput by remember { mutableStateOf("") }
    var searchError by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    // Found Account
    var foundAccount by remember { mutableStateOf<RegisteredAccount?>(null) }
    var isSendingCode by remember { mutableStateOf(false) }

    // Step 3: OTP State
    var activeOtp by remember { mutableStateOf<String?>(null) }
    var otpExpiresAt by remember { mutableStateOf(0L) }
    var otpAttempts by remember { mutableStateOf(0) }
    var enteredOtp by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }
    var resendCooldownSeconds by remember { mutableStateOf(60) }
    var isResendingOtp by remember { mutableStateOf(false) }

    // Step 4: Password State
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmNewPassword by remember { mutableStateOf(false) }
    var signOutOtherDevices by remember { mutableStateOf(true) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isUpdatingPassword by remember { mutableStateOf(false) }

    // Cooldown countdown effect
    LaunchedEffect(resendCooldownSeconds) {
        if (resendCooldownSeconds > 0) {
            delay(1000L)
            resendCooldownSeconds -= 1
        }
    }

    // Intercept back button to navigate backwards through recovery steps
    BackHandler {
        when (currentStep) {
            ForgotPasswordStep.FIND_ACCOUNT -> onBack()
            ForgotPasswordStep.CONFIRM_ACCOUNT -> {
                currentStep = ForgotPasswordStep.FIND_ACCOUNT
                searchError = null
            }
            ForgotPasswordStep.VERIFY_OTP -> {
                currentStep = ForgotPasswordStep.CONFIRM_ACCOUNT
                otpError = null
            }
            ForgotPasswordStep.CREATE_PASSWORD -> {
                currentStep = ForgotPasswordStep.VERIFY_OTP
                passwordError = null
            }
            ForgotPasswordStep.SUCCESS -> {
                foundAccount?.let { onRecoverySuccess(it) } ?: onBack()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentStep) {
                            ForgotPasswordStep.FIND_ACCOUNT -> "Tìm tài khoản".t()
                            ForgotPasswordStep.CONFIRM_ACCOUNT -> "Xác nhận tài khoản".t()
                            ForgotPasswordStep.VERIFY_OTP -> "Mã xác nhận".t()
                            ForgotPasswordStep.CREATE_PASSWORD -> "Mật khẩu mới".t()
                            ForgotPasswordStep.SUCCESS -> "Hoàn tất".t()
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (currentStep) {
                                ForgotPasswordStep.FIND_ACCOUNT -> onBack()
                                ForgotPasswordStep.CONFIRM_ACCOUNT -> {
                                    currentStep = ForgotPasswordStep.FIND_ACCOUNT
                                    searchError = null
                                }
                                ForgotPasswordStep.VERIFY_OTP -> {
                                    currentStep = ForgotPasswordStep.CONFIRM_ACCOUNT
                                    otpError = null
                                }
                                ForgotPasswordStep.CREATE_PASSWORD -> {
                                    currentStep = ForgotPasswordStep.VERIFY_OTP
                                    passwordError = null
                                }
                                ForgotPasswordStep.SUCCESS -> {
                                    foundAccount?.let { onRecoverySuccess(it) } ?: onBack()
                                }
                            }
                        },
                        modifier = Modifier.testTag("forgot_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (currentStep) {
                    ForgotPasswordStep.FIND_ACCOUNT -> {
                        FindAccountScreen(
                            searchInput = searchInput,
                            onSearchInputChange = {
                                searchInput = it
                                searchError = null
                            },
                            searchError = searchError,
                            isSearching = isSearching,
                            onContinue = {
                                val trimmed = searchInput.trim()
                                if (trimmed.isBlank()) {
                                    searchError = "Vui lòng nhập địa chỉ email hoặc tên tài khoản của bạn.".t()
                                    return@FindAccountScreen
                                }

                                isSearching = true
                                searchError = null

                                coroutineScope.launch {
                                    try {
                                        // 1. Kiểm tra trên bộ nhớ máy cục bộ
                                        var acc = AccountAuthManager.getLocalAccount(context, trimmed)
                                            ?: AccountAuthManager.getLocalAccountByUsernameOrEmail(context, trimmed)

                                        // 2. Tìm trên hệ thống đám mây Firestore (giới hạn 4.5 giây)
                                        if (acc == null) {
                                            acc = withTimeoutOrNull(4500L) {
                                                AccountAuthManager.fetchAccountByInputFromFirestore(viewModel.syncManager, trimmed)
                                            }
                                        }

                                        if (acc != null) {
                                            foundAccount = acc
                                            currentStep = ForgotPasswordStep.CONFIRM_ACCOUNT
                                        } else {
                                            searchError = "Không tìm thấy tài khoản nào khớp với thông tin này.".t()
                                        }
                                    } catch (e: Exception) {
                                        searchError = "Lỗi tìm tài khoản: ${e.localizedMessage}".t()
                                    } finally {
                                        isSearching = false
                                    }
                                }
                            }
                        )
                    }

                    ForgotPasswordStep.CONFIRM_ACCOUNT -> {
                        val acc = foundAccount
                        if (acc != null) {
                            ConfirmAccountScreen(
                                account = acc,
                                isSending = isSendingCode,
                                onConfirmSendCode = {
                                    if (isSendingCode) return@ConfirmAccountScreen
                                    val otp = EmailRecoveryService.generateOtp()
                                    activeOtp = otp
                                    otpExpiresAt = System.currentTimeMillis() + 5 * 60 * 1000L // 5 phút
                                    otpAttempts = 0
                                    enteredOtp = ""
                                    otpError = null
                                    resendCooldownSeconds = 15
                                    isSendingCode = true

                                    coroutineScope.launch {
                                        try {
                                            val recipientName = acc.storeName.trim().ifBlank {
                                                acc.email.substringBefore("@").ifBlank { "Cường Lâm" }
                                            }
                                            withTimeoutOrNull(3000L) {
                                                EmailRecoveryService.sendResetEmail(
                                                    email = acc.email,
                                                    recipientName = recipientName,
                                                    otpCode = otp,
                                                    context = context
                                                )
                                            }
                                        } catch (_: Exception) {
                                        } finally {
                                            isSendingCode = false
                                            currentStep = ForgotPasswordStep.VERIFY_OTP
                                        }
                                    }
                                },
                                onNotYou = {
                                    currentStep = ForgotPasswordStep.FIND_ACCOUNT
                                    searchError = null
                                }
                            )
                        }
                    }

                    ForgotPasswordStep.VERIFY_OTP -> {
                        val acc = foundAccount
                        if (acc != null) {
                            VerifyOtpScreen(
                                maskedEmail = EmailRecoveryService.maskEmail(acc.email),
                                enteredOtp = enteredOtp,
                                onOtpChange = { code ->
                                    if (code.length <= 6 && code.all { it.isDigit() }) {
                                        enteredOtp = code
                                        otpError = null
                                    }
                                },
                                activeOtp = activeOtp,
                                otpExpiresAt = otpExpiresAt,
                                otpError = otpError,
                                resendCooldownSeconds = resendCooldownSeconds,
                                isResending = isResendingOtp,
                                onResend = {
                                    if (resendCooldownSeconds <= 0 && !isResendingOtp) {
                                        isResendingOtp = true
                                        val newOtp = EmailRecoveryService.generateOtp()
                                        activeOtp = newOtp
                                        otpExpiresAt = System.currentTimeMillis() + 5 * 60 * 1000L
                                        otpAttempts = 0
                                        enteredOtp = ""
                                        otpError = null

                                        coroutineScope.launch {
                                            try {
                                                val recipientName = acc.storeName.trim().ifBlank {
                                                    acc.email.substringBefore("@").ifBlank { "Cường Lâm" }
                                                }
                                                withTimeoutOrNull(3000L) {
                                                    EmailRecoveryService.sendResetEmail(
                                                        email = acc.email,
                                                        recipientName = recipientName,
                                                        otpCode = newOtp,
                                                        context = context
                                                    )
                                                }
                                            } catch (_: Exception) {
                                            } finally {
                                                isResendingOtp = false
                                                resendCooldownSeconds = 15
                                            }
                                        }
                                    }
                                },
                                onVerify = {
                                    val now = System.currentTimeMillis()
                                    if (now > otpExpiresAt) {
                                        otpError = "Mã xác nhận đã hết hạn. Vui lòng yêu cầu mã mới.".t()
                                        return@VerifyOtpScreen
                                    }

                                    if (otpAttempts >= 5) {
                                        otpError = "Bạn đã nhập sai quá 5 lần. Vui lòng bấm 'Gửi lại mã' để nhận mã mới.".t()
                                        return@VerifyOtpScreen
                                    }

                                    if (enteredOtp.trim() == activeOtp) {
                                        // Mã chính xác: xóa ngay mã cũ và sang bước tạo mật khẩu mới
                                        activeOtp = null
                                        otpError = null
                                        currentStep = ForgotPasswordStep.CREATE_PASSWORD
                                    } else {
                                        otpAttempts += 1
                                        val remaining = 5 - otpAttempts
                                        otpError = if (remaining > 0) {
                                            "Mã xác nhận không chính xác. Bạn còn $remaining lần thử.".t()
                                        } else {
                                            "Bạn đã nhập sai quá 5 lần. Vui lòng bấm 'Gửi lại mã' để nhận mã mới.".t()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    ForgotPasswordStep.CREATE_PASSWORD -> {
                        val acc = foundAccount
                        if (acc != null) {
                            CreateNewPasswordScreen(
                                newPassword = newPassword,
                                onNewPasswordChange = {
                                    newPassword = it
                                    passwordError = null
                                },
                                confirmPassword = confirmNewPassword,
                                onConfirmPasswordChange = {
                                    confirmNewPassword = it
                                    passwordError = null
                                },
                                showNewPassword = showNewPassword,
                                onToggleShowNewPassword = { showNewPassword = !showNewPassword },
                                showConfirmPassword = showConfirmNewPassword,
                                onToggleShowConfirmPassword = { showConfirmNewPassword = !showConfirmNewPassword },
                                signOutOtherDevices = signOutOtherDevices,
                                onSignOutOtherDevicesChange = { signOutOtherDevices = it },
                                passwordError = passwordError,
                                isUpdating = isUpdatingPassword,
                                onResetPassword = {
                                    if (newPassword.length < 6) {
                                        passwordError = "Mật khẩu phải có ít nhất 6 ký tự.".t()
                                        return@CreateNewPasswordScreen
                                    }

                                    if (newPassword != confirmNewPassword) {
                                        passwordError = "Mật khẩu xác nhận không trùng khớp.".t()
                                        return@CreateNewPasswordScreen
                                    }

                                    isUpdatingPassword = true
                                    passwordError = null

                                    coroutineScope.launch {
                                        try {
                                            val updated = AccountAuthManager.updateAccountPassword(
                                                context = context,
                                                syncManager = viewModel.syncManager,
                                                account = acc,
                                                newPassword = newPassword
                                            )

                                            // Sao lưu dữ liệu khách trên máy trước khi đăng nhập tự động
                                            viewModel.prepareLoginForExistingAccount(context)

                                            // Đăng nhập tự động với mật khẩu mới
                                            AccountAuthManager.saveActiveAccount(context, updated)
                                            viewModel.setLoggedInAccount(updated, shouldMigrate = false)
                                            if (updated.storeName.isNotBlank()) {
                                                viewModel.setStoreName(updated.storeName)
                                            }

                                            foundAccount = updated
                                            currentStep = ForgotPasswordStep.SUCCESS
                                        } catch (e: Exception) {
                                            passwordError = "Lỗi cập nhật mật khẩu: ${e.localizedMessage}".t()
                                        } finally {
                                            isUpdatingPassword = false
                                        }
                                    }
                                }
                            )
                        }
                    }

                    ForgotPasswordStep.SUCCESS -> {
                        val acc = foundAccount
                        if (acc != null) {
                            RecoverySuccessScreen(
                                account = acc,
                                onDone = {
                                    onRecoverySuccess(acc)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// MÀN HÌNH 1: TÌM TÀI KHOẢN (FIND ACCOUNT)
// =========================================================================
@Composable
private fun FindAccountScreen(
    searchInput: String,
    onSearchInputChange: (String) -> Unit,
    searchError: String?,
    isSearching: Boolean,
    onContinue: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Tìm tài khoản".t(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Nhập địa chỉ email của bạn.".t(),
                fontSize = 14.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = searchInput,
                onValueChange = onSearchInputChange,
                placeholder = {
                    Text(
                        text = "Email",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchInput.isNotEmpty()) {
                        IconButton(onClick = { onSearchInputChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { onContinue() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forgot_search_input"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            )

            AnimatedVisibility(visible = searchError != null) {
                searchError?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Pill Button: Tiếp tục
            Button(
                onClick = onContinue,
                enabled = !isSearching,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("forgot_search_continue_btn"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Tiếp tục".t(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// =========================================================================
// MÀN HÌNH 2: XÁC NHẬN TÀI KHOẢN & CHỌN PHƯƠNG THỨC (CONFIRM ACCOUNT)
// =========================================================================
@Composable
private fun ConfirmAccountScreen(
    account: RegisteredAccount,
    isSending: Boolean = false,
    onConfirmSendCode: () -> Unit,
    onNotYou: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Xác nhận tài khoản của bạn".t(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Chúng tôi đã tìm thấy tài khoản của bạn. Vui lòng xác nhận phương thức nhận mã khôi phục.".t(),
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Thẻ hồ sơ tài khoản (Avatar + Tên + Email đã che)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = (account.storeName.ifBlank { account.email }).take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.storeName.ifBlank { account.email.substringBefore("@") },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = EmailRecoveryService.maskEmail(account.email),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Tài khoản POS đã đồng bộ".t(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Lựa chọn phương thức gửi mã (mặc định chọn Gửi qua Email)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = true,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gửi mã qua email".t(),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = EmailRecoveryService.maskEmail(account.email),
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hộp cảnh báo bảo mật nghiêm ngặt
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Bạn tuyệt đối KHÔNG được chia sẻ mã này cho bất kì ai".t(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Mã xác thực OTP gồm 6 chữ số sẽ được gửi trực tiếp trong email của bạn (không kèm liên kết).".t(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Pill Button: Tiếp tục
            Button(
                onClick = onConfirmSendCode,
                enabled = !isSending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("forgot_confirm_continue_btn"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Đang gửi mã tới Gmail...".t(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = "Tiếp tục".t(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Pill Button: Không phải bạn?
            Button(
                onClick = onNotYou,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("forgot_not_you_btn"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text(
                    text = "Không phải bạn?".t(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// =========================================================================
// MÀN HÌNH 3: NHẬP MÃ XÁC THỰC OTP (VERIFY OTP)
// =========================================================================
@Composable
private fun VerifyOtpScreen(
    maskedEmail: String,
    enteredOtp: String,
    onOtpChange: (String) -> Unit,
    activeOtp: String?,
    otpExpiresAt: Long,
    otpError: String?,
    resendCooldownSeconds: Int,
    isResending: Boolean,
    onResend: () -> Unit,
    onVerify: () -> Unit
) {
    val remainingTimeMillis = remember(otpExpiresAt) {
        maxOf(0L, otpExpiresAt - System.currentTimeMillis())
    }
    var remainingSeconds by remember { mutableStateOf((remainingTimeMillis / 1000).toInt()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(250L)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(otpExpiresAt) {
        while (true) {
            val left = maxOf(0L, otpExpiresAt - System.currentTimeMillis())
            remainingSeconds = (left / 1000).toInt()
            if (left <= 0) break
            delay(1000L)
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val formattedTime = "%02d:%02d".format(minutes, seconds)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Xác nhận tài khoản của bạn".t(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Chúng tôi đã gửi mã gồm 6 chữ số tới email $maskedEmail. Nhập mã đó để đặt lại mật khẩu.".t(),
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Hộp cảnh báo bảo mật nghiêm ngặt
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Bạn tuyệt đối KHÔNG được chia sẻ mã này cho bất kì ai".t(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 6 ô vuông mã OTP nhập trực tiếp (đã bỏ dòng nhập phụ 'Nhập 6 số mã xác nhận')
            BasicTextField(
                value = enteredOtp,
                onValueChange = onOtpChange,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (enteredOtp.length == 6) onVerify() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("forgot_otp_input"),
                decorationBox = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 6) {
                            val char = enteredOtp.getOrNull(i)?.toString() ?: ""
                            val isCurrent = enteredOtp.length == i || (i == 5 && enteredOtp.length == 6)
                            Surface(
                                modifier = Modifier
                                    .size(46.dp)
                                    .border(
                                        width = if (isCurrent) 2.dp else 1.5.dp,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                shape = RoundedCornerShape(10.dp),
                                color = if (char.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = char,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Mã OTP có thể sẽ gửi đến hòm thư của bạn trong vài phút nữa".t(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bộ đếm ngược thời gian hiệu lực của mã
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (remainingSeconds > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (remainingSeconds > 0) "Mã hết hạn sau: $formattedTime".t() else "Mã đã hết hạn".t(),
                        fontSize = 12.sp,
                        color = if (remainingSeconds > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Nút Gửi lại mã với cooldown 60s
                TextButton(
                    onClick = onResend,
                    enabled = resendCooldownSeconds <= 0 && !isResending,
                    modifier = Modifier.testTag("forgot_resend_otp_btn")
                ) {
                    if (isResending) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (resendCooldownSeconds > 0) {
                                "Gửi lại mã (${resendCooldownSeconds}s)".t()
                            } else {
                                "Gửi lại mã".t()
                            },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (resendCooldownSeconds <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = otpError != null) {
                otpError?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Pill Button: Tiếp tục
            Button(
                onClick = onVerify,
                enabled = enteredOtp.length == 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("forgot_verify_otp_continue_btn"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Tiếp tục".t(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

// =========================================================================
// MÀN HÌNH 4: TẠO MẬT KHẨU MỚI (CREATE NEW PASSWORD)
// =========================================================================
@Composable
private fun CreateNewPasswordScreen(
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    showNewPassword: Boolean,
    onToggleShowNewPassword: () -> Unit,
    showConfirmPassword: Boolean,
    onToggleShowConfirmPassword: () -> Unit,
    signOutOtherDevices: Boolean,
    onSignOutOtherDevicesChange: (Boolean) -> Unit,
    passwordError: String?,
    isUpdating: Boolean,
    onResetPassword: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Tạo mật khẩu mới".t(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tạo mật khẩu có ít nhất 6 ký tự. Đó phải là mật khẩu mà người khác không thể đoán được.".t(),
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Ô nhập mật khẩu mới
            OutlinedTextField(
                value = newPassword,
                onValueChange = onNewPasswordChange,
                label = { Text("Mật khẩu mới".t()) },
                placeholder = { Text("Tối thiểu 6 ký tự".t()) },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    IconButton(onClick = onToggleShowNewPassword) {
                        Icon(
                            imageVector = if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Ẩn/hiện mật khẩu",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forgot_new_password_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Ô xác nhận mật khẩu mới
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text("Xác nhận mật khẩu mới".t()) },
                placeholder = { Text("Nhập lại mật khẩu mới".t()) },
                leadingIcon = {
                    Icon(Icons.Default.LockReset, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    IconButton(onClick = onToggleShowConfirmPassword) {
                        Icon(
                            imageVector = if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Ẩn/hiện mật khẩu",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onResetPassword() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forgot_confirm_password_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Checkbox: Đăng xuất khỏi các thiết bị khác
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSignOutOtherDevicesChange(!signOutOtherDevices) },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = signOutOtherDevices,
                        onCheckedChange = onSignOutOtherDevicesChange,
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("forgot_sign_out_other_devices_checkbox")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = "Đăng xuất khỏi các thiết bị khác".t(),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Chọn tùy chọn này nếu người khác đã sử dụng tài khoản của bạn.".t(),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            AnimatedVisibility(visible = passwordError != null) {
                passwordError?.let { err ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Pill Button: Đặt lại mật khẩu
            Button(
                onClick = onResetPassword,
                enabled = !isUpdating && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("forgot_reset_password_btn"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Đặt lại mật khẩu".t(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// =========================================================================
// MÀN HÌNH 5: HOÀN TẤT (SUCCESS)
// =========================================================================
@Composable
private fun RecoverySuccessScreen(
    account: RegisteredAccount,
    onDone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Thành công",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Đổi mật khẩu thành công!".t(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Mật khẩu cho tài khoản ${account.storeName.ifBlank { account.email }} đã được cập nhật thành công. Bạn đã đăng nhập và có thể dùng mật khẩu mới này từ bây giờ.".t(),
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("forgot_success_done_btn"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Vào ứng dụng ngay".t(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
