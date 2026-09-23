package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

data class OnboardingStepItem(
    val targetKey: String,
    val sectionTag: String,
    val title: String,
    val subtitle: String,
    val roleDescription: String,
    val featureBenefits: List<String>,
    val proTip: String,
    val icon: ImageVector,
    val themeColor: Color
)

@Composable
fun OnboardingTourScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val steps = remember {
        listOf(
            // 1. Bán hàng
            OnboardingStepItem(
                targetKey = "tab_ban_hang",
                sectionTag = "BÁN HÀNG",
                title = "Bán hàng & Thu ngân (POS)",
                subtitle = "Tạo giỏ hàng, tính tiền, in hóa đơn nhanh",
                roleDescription = "Là trung tâm thu ngân và giao dịch bán lẻ hằng ngày, giúp bạn chọn sản phẩm, tính tiền cho khách chính xác và in hóa đơn tại quầy.",
                featureBenefits = listOf(
                    "Tìm kiếm sản phẩm nhanh, quét mã vạch Barcode/QR bằng camera máy.",
                    "Tùy chỉnh số lượng, giảm giá chiết khấu, tính tiền thừa tự động.",
                    "Thanh toán tiền mặt, chuyển khoản và in hóa đơn nhanh chóng."
                ),
                proTip = "Nhấn biểu tượng máy ảnh trên thanh tìm kiếm để quét mã vạch sản phẩm tức thì.",
                icon = Icons.Default.Storefront,
                themeColor = Color(0xFF2E7D32)
            ),
            // 2. Hóa đơn & Lập báo cáo
            OnboardingStepItem(
                targetKey = "tab_hoa_don",
                sectionTag = "HÓA ĐƠN & BÁO CÁO",
                title = "Hóa đơn & Báo cáo doanh thu",
                subtitle = "Quản lý đơn hàng & xem thống kê lợi nhuận",
                roleDescription = "Lưu trữ đầy đủ lịch sử tất cả các đơn bán và tự động tổng hợp báo cáo kết quả kinh doanh của cửa hàng.",
                featureBenefits = listOf(
                    "Tra cứu toàn bộ danh sách đơn bán theo ngày, tuần, tháng hoặc khách hàng.",
                    "Xem chi tiết đơn, in lại hóa đơn, chỉnh sửa hoặc hủy đơn khi cần.",
                    "Tự động lập báo cáo doanh thu, tiền vốn và tiền lãi ròng thực tế."
                ),
                proTip = "Sử dụng bộ lọc thời gian để xem nhanh doanh thu và lợi nhuận bán hàng trong ngày.",
                icon = Icons.Default.ReceiptLong,
                themeColor = Color(0xFF1565C0)
            ),
            // 3. Quản lý mặt hàng
            OnboardingStepItem(
                targetKey = "manage_products_card",
                sectionTag = "QUẢN LÝ",
                title = "Quản lý mặt hàng & Giá bán",
                subtitle = "Thiết lập sản phẩm, giá vốn và giá bán lẻ",
                roleDescription = "Danh mục trung tâm quản lý toàn bộ các sản phẩm đang kinh doanh với đầy đủ thông tin chi tiết.",
                featureBenefits = listOf(
                    "Thêm mới, sửa sản phẩm kèm hình ảnh, tên, đơn vị tính và mã vạch.",
                    "Cài đặt giá nhập (giá vốn) và giá bán lẻ để tự động tính tiền lãi.",
                    "Phân loại hàng theo danh mục giúp tìm kiếm và quản lý khoa học."
                ),
                proTip = "Khai báo đầy đủ giá nhập giúp hệ thống tính toán chính xác 100% tiền lãi từng đơn.",
                icon = Icons.Default.Inventory,
                themeColor = Color(0xFFE65100)
            ),
            // 4. Quản lý hàng tồn
            OnboardingStepItem(
                targetKey = "manage_inventory_card",
                sectionTag = "QUẢN LÝ",
                title = "Quản lý kho & Hàng tồn",
                subtitle = "Theo dõi số lượng tồn kho & cảnh báo hết hàng",
                roleDescription = "Giúp bạn nắm chắc số lượng hàng hóa thực tế còn trong kho theo thời gian thực để chủ động kinh doanh.",
                featureBenefits = listOf(
                    "Tự động trừ tồn khi bán và tự động cộng thêm khi lập phiếu nhập.",
                    "Cảnh báo thông minh các sản phẩm sắp hết hàng hoặc đã hết hàng.",
                    "Hỗ trợ kiểm kê kho thực tế và cập nhật lại số lượng tồn kho chuẩn xác."
                ),
                proTip = "Bạn có thể bật/tắt tùy chọn theo dõi tồn kho cho từng sản phẩm tùy nhu cầu.",
                icon = Icons.Default.Assessment,
                themeColor = Color(0xFF00838F)
            ),
            // 5. Quản lý nhập hàng
            OnboardingStepItem(
                targetKey = "manage_import_card",
                sectionTag = "QUẢN LÝ",
                title = "Quản lý nhập hàng từ NCC",
                subtitle = "Ghi nhận nguồn nhập, giá nhập & phiếu nhập kho",
                roleDescription = "Quản lý các đợt nhập hàng từ xưởng hoặc nhà phân phối, kiểm soát chặt chẽ chi phí đầu vào.",
                featureBenefits = listOf(
                    "Lập phiếu nhập chi tiết gồm tên nhà cung cấp, số lượng và giá nhập.",
                    "Tự động cộng dồn số lượng vào kho ngay khi hoàn tất phiếu nhập.",
                    "Ghi nhận thanh toán (đã trả đủ hoặc ghi nợ NCC) để đối soát dòng tiền."
                ),
                proTip = "Lưu tên nhà cung cấp quen thuộc để dễ dàng tra cứu lịch sử biến động giá nhập.",
                icon = Icons.Default.LocalShipping,
                themeColor = Color(0xFF2E7D32)
            ),
            // 6. Quản lý công nợ
            OnboardingStepItem(
                targetKey = "manage_debt_card",
                sectionTag = "QUẢN LÝ",
                title = "Quản lý công nợ Khách & NCC",
                subtitle = "Kiểm soát các khoản phải thu & phải trả",
                roleDescription = "Theo dõi sát sao các khoản tiền khách còn nợ và tiền nợ cần trả cho nhà cung cấp, tránh thất thoát.",
                featureBenefits = listOf(
                    "Theo dõi danh sách khách hàng nợ tiền từ các đơn hàng chưa trả đủ.",
                    "Quản lý nợ phải trả cho nhà cung cấp khi nhập hàng gối đầu/trả chậm.",
                    "Ghi nhận các đợt trả nợ từng lần, lưu lại lịch sử chi tiết không lo quên."
                ),
                proTip = "Khi khách chỉ trả trước một phần, tiền còn thiếu sẽ tự động vào sổ nợ.",
                icon = Icons.Default.CreditCard,
                themeColor = Color(0xFFC2185B)
            ),
            // 7. Quản lý khách hàng
            OnboardingStepItem(
                targetKey = "manage_customers_card",
                sectionTag = "QUẢN LÝ",
                title = "Quản lý danh bạ & Khách hàng",
                subtitle = "Lưu thông tin, lịch sử mua sắm & công nợ từng khách",
                roleDescription = "Lưu trữ hồ sơ khách hàng để chăm sóc chu đáo, xây dựng tệp khách quen và bán hàng tiện lợi hơn.",
                featureBenefits = listOf(
                    "Lưu họ tên, số điện thoại, địa chỉ và ghi chú của từng khách.",
                    "Tra cứu toàn bộ lịch sử mua sắm và hóa đơn cũ của khách chỉ với 1 chạm.",
                    "Xem tổng doanh số mua hàng và tình trạng công nợ hiện tại của từng khách."
                ),
                proTip = "Chọn tên khách khi tạo đơn để hệ thống tự động tích lũy lịch sử mua hàng.",
                icon = Icons.Default.People,
                themeColor = Color(0xFF0277BD)
            ),
            // 8. Cài đặt - Cấp quyền hệ thống
            OnboardingStepItem(
                targetKey = "settings_permissions_card",
                sectionTag = "CÀI ĐẶT",
                title = "Cấp quyền hệ thống cần thiết",
                subtitle = "Kích hoạt quyền Camera, Lưu trữ & Thông báo",
                roleDescription = "Cho phép ứng dụng khai thác phần cứng điện thoại để phục vụ bán hàng trơn tru nhất.",
                featureBenefits = listOf(
                    "Quyền Camera: Quét mã vạch sản phẩm siêu tốc khi thanh toán và nhập hàng.",
                    "Quyền Lưu trữ / Hình ảnh: Chọn ảnh sản phẩm và xuất file sao lưu an toàn.",
                    "Quyền Thông báo: Nhận thông báo tiến trình sao lưu chạy nền và cảnh báo."
                ),
                proTip = "Vào Cài đặt > 'Cấp quyền hệ thống' bất cứ lúc nào để kiểm tra và cấp lại quyền.",
                icon = Icons.Default.Security,
                themeColor = Color(0xFF5D4037)
            ),
            // 9. Cài đặt - Sao lưu
            OnboardingStepItem(
                targetKey = "settings_backup_card",
                sectionTag = "CÀI ĐẶT",
                title = "Sao lưu & Khôi phục dữ liệu",
                subtitle = "Bảo vệ an toàn 100% dữ liệu kinh doanh cửa hàng",
                roleDescription = "Lá chắn an toàn bảo vệ toàn bộ dữ liệu bán hàng của bạn, không lo mất mát khi đổi điện thoại.",
                featureBenefits = listOf(
                    "Xuất tệp sao lưu toàn diện chứa mọi sản phẩm, hóa đơn, khách hàng, đơn nhập.",
                    "Dễ dàng khôi phục lại dữ liệu nguyên vẹn chỉ trong vài giây trên máy mới.",
                    "Hỗ trợ tính năng sao lưu tự động chạy ngầm, giữ dữ liệu luôn mới nhất."
                ),
                proTip = "Hãy định kỳ xuất file sao lưu và gửi về email hoặc lưu trên Google Drive.",
                icon = Icons.Default.Backup,
                themeColor = Color(0xFF283593)
            )
        )
    }

    val currentStepIndex by viewModel.onboardingStepIndex.collectAsStateWithLifecycle()
    val currentStep = steps[currentStepIndex.coerceIn(0, steps.size - 1)]
    val isLastStep = currentStepIndex >= steps.size - 1

    val targetBoundsMap = viewModel.onboardingTargetBounds
    val targetRect = targetBoundsMap[currentStep.targetKey]

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Pulsing animation for spotlight highlight ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    BackHandler {
        viewModel.previousOnboardingStep()
    }

    // Determine tooltip position: Above or Below target
    val isTargetInBottomHalf = targetRect?.let { it.center.y > screenHeightPx * 0.48f } ?: true
    val arrowPointingDown = isTargetInBottomHalf // If card is above, arrow points DOWN (▼) to target

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("onboarding_spotlight_overlay")
    ) {
        // 1. Semi-transparent backdrop with clear spotlight cutout & glowing border
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // Prevent clicks from passing through to underlying UI during tour
                }
        ) {
            // Draw darkened scrim
            drawRect(Color.Black.copy(alpha = 0.65f))

            // Punch hole for targetRect if present
            targetRect?.let { rect ->
                val pad = 6.dp.toPx()
                val corner = 14.dp.toPx()
                val holeLeft = (rect.left - pad).coerceAtLeast(0f)
                val holeTop = (rect.top - pad).coerceAtLeast(0f)
                val holeWidth = (rect.width + pad * 2f).coerceAtMost(size.width - holeLeft)
                val holeHeight = (rect.height + pad * 2f).coerceAtMost(size.height - holeTop)

                // Cutout hole
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(holeLeft, holeTop),
                    size = Size(holeWidth, holeHeight),
                    cornerRadius = CornerRadius(corner, corner),
                    blendMode = BlendMode.Clear
                )

                // Glowing border
                drawRoundRect(
                    color = currentStep.themeColor.copy(alpha = pulseAlpha),
                    topLeft = Offset(holeLeft, holeTop),
                    size = Size(holeWidth, holeHeight),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = (3.dp.toPx() * pulseScale))
                )
            }
        }

        // 2. Floating Callout Card ("Tin nổi lên chỉ vào phần cần giới thiệu")
        val cardPlacementModifier = remember(targetRect, isTargetInBottomHalf, density, configuration) {
            if (targetRect != null) {
                val targetTopDp = with(density) { targetRect.top.toDp() }
                val targetBottomDp = with(density) { targetRect.bottom.toDp() }

                if (isTargetInBottomHalf) {
                    // Place card ABOVE target
                    val bottomPadding = (configuration.screenHeightDp.dp - targetTopDp + 8.dp).coerceAtLeast(16.dp)
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding, start = 16.dp, end = 16.dp)
                } else {
                    // Place card BELOW target
                    val topPadding = (targetBottomDp + 8.dp).coerceAtLeast(16.dp)
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topPadding, start = 16.dp, end = 16.dp)
                }
            } else {
                // Fallback center
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
            }
        }

        // Compute arrow horizontal offset to point precisely at target center
        val arrowOffsetX = remember(targetRect, screenWidthPx, density) {
            if (targetRect != null) {
                val targetCenterX = targetRect.center.x
                val deltaX = targetCenterX - (screenWidthPx / 2f)
                with(density) { deltaX.toDp() }.coerceIn(-130.dp, 130.dp)
            } else {
                0.dp
            }
        }

        AnimatedContent(
            targetState = currentStepIndex,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)))
                    .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180)))
            },
            modifier = cardPlacementModifier,
            label = "FloatingTooltipAnimation"
        ) { stepIdx ->
            val step = steps[stepIdx.coerceIn(0, steps.size - 1)]

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
            ) {
                // If arrow points UP (▲), draw it at the top of the card
                if (!arrowPointingDown) {
                    TooltipArrowPointer(
                        pointsDown = false,
                        themeColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.offset(x = arrowOffsetX)
                    )
                }

                // Main Floating Bubble Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = step.themeColor.copy(alpha = 0.45f))
                        .testTag("floating_tooltip_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Header: Tag + Step Counter + Back icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (stepIdx > 0) {
                                    IconButton(
                                        onClick = { viewModel.previousOnboardingStep() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Quay lại".t(),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                Surface(
                                    color = step.themeColor.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = step.sectionTag.t(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = step.themeColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Step badge
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${stepIdx + 1} / ${steps.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Title with Feature Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(step.themeColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = step.icon,
                                    contentDescription = null,
                                    tint = step.themeColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = step.title.t(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = step.subtitle.t(),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Scrollable content if needed
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Section: VAI TRÒ (Role)
                            Text(
                                text = "Vai trò:".t(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = step.themeColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = step.roleDescription.t(),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Section: TÁC DỤNG & LỢI ÍCH (Benefits)
                            Text(
                                text = "Tác dụng nổi bật:".t(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = step.themeColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            step.featureBenefits.forEach { benefit ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 5.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(step.themeColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = benefit.t(),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons: Bỏ qua vs Tiếp theo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.completeOnboardingTour() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("onboarding_skip_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = "Bỏ qua".t(),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = {
                                    if (isLastStep) {
                                        viewModel.completeOnboardingTour()
                                    } else {
                                        viewModel.nextOnboardingStep()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(42.dp)
                                    .testTag("onboarding_next_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = step.themeColor
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = if (isLastStep) "Bắt đầu ngay".t() else "Tiếp theo".t(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // If arrow points DOWN (▼), draw it at the bottom of the card
                if (arrowPointingDown) {
                    TooltipArrowPointer(
                        pointsDown = true,
                        themeColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.offset(x = arrowOffsetX)
                    )
                }
            }
        }
    }
}

@Composable
private fun TooltipArrowPointer(
    pointsDown: Boolean,
    themeColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(width = 24.dp, height = 12.dp)
    ) {
        val path = Path()
        if (pointsDown) {
            // Triangle pointing down ▼
            path.moveTo(0f, 0f)
            path.lineTo(size.width, 0f)
            path.lineTo(size.width / 2f, size.height)
            path.close()
        } else {
            // Triangle pointing up ▲
            path.moveTo(size.width / 2f, 0f)
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
            path.close()
        }
        drawPath(path = path, color = themeColor)
    }
}
