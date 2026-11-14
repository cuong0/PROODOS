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
                subtitle = "Tạo đơn và tính tiền nhanh",
                roleDescription = "Chọn sản phẩm, quét mã vạch, tính tiền và thanh toán nhanh cho khách.",
                icon = Icons.Default.Storefront,
                themeColor = Color(0xFF2E7D32)
            ),
            // 2. Hóa đơn & Lập báo cáo
            OnboardingStepItem(
                targetKey = "tab_hoa_don",
                sectionTag = "HÓA ĐƠN",
                title = "Hóa đơn & Báo cáo",
                subtitle = "Quản lý đơn & thống kê lãi",
                roleDescription = "Tra cứu lịch sử đơn bán và tổng hợp doanh thu, lợi nhuận theo thời gian.",
                icon = Icons.Default.ReceiptLong,
                themeColor = Color(0xFF1565C0)
            ),
            // 3. Quản lý mặt hàng
            OnboardingStepItem(
                targetKey = "manage_products_card",
                sectionTag = "QUẢN LÝ",
                title = "Quản lý mặt hàng",
                subtitle = "Cài đặt sản phẩm & giá bán",
                roleDescription = "Thêm, sửa sản phẩm, phân loại danh mục, thiết lập giá vốn và giá bán.",
                icon = Icons.Default.Inventory,
                themeColor = Color(0xFFE65100)
            ),
            // 4. Quản lý hàng tồn
            OnboardingStepItem(
                targetKey = "manage_inventory_card",
                sectionTag = "QUẢN LÝ",
                title = "Quản lý hàng tồn",
                subtitle = "Theo dõi số lượng tồn kho",
                roleDescription = "Kiểm soát số lượng hàng còn trong kho và tổng giá trị tồn kho thực tế.",
                icon = Icons.Default.Assessment,
                themeColor = Color(0xFF00838F)
            ),
            // 5. Quản lý nhập hàng
            OnboardingStepItem(
                targetKey = "manage_import_card",
                sectionTag = "QUẢN LÝ",
                title = "Quản lý nhập hàng",
                subtitle = "Lập đơn nhập từ nhà cung cấp",
                roleDescription = "Ghi nhận phiếu nhập kho, nhà cung cấp, chi phí nhập và tự động cộng tồn kho.",
                icon = Icons.Default.LocalShipping,
                themeColor = Color(0xFF2E7D32)
            ),
            // 6. Quản lý công nợ
            OnboardingStepItem(
                targetKey = "manage_debt_card",
                sectionTag = "QUẢN LÝ",
                title = "Quản lý công nợ",
                subtitle = "Theo dõi các khoản nợ",
                roleDescription = "Quản lý danh sách khách nợ, lịch hẹn trả và ghi nhận thanh toán công nợ.",
                icon = Icons.Default.CreditCard,
                themeColor = Color(0xFFC2185B)
            ),
            // 7. Quản lý khách hàng
            OnboardingStepItem(
                targetKey = "manage_customers_card",
                sectionTag = "QUẢN LÝ",
                title = "Quản lý khách hàng",
                subtitle = "Lịch sử mua & công nợ khách",
                roleDescription = "Lưu thông tin khách hàng, xem nhanh các đơn đã mua và tổng chi tiêu.",
                icon = Icons.Default.People,
                themeColor = Color(0xFF0277BD)
            ),
            // 8. Cài đặt - Sao lưu
            OnboardingStepItem(
                targetKey = "settings_backup_card",
                sectionTag = "CÀI ĐẶT",
                title = "Sao lưu & Khôi phục",
                subtitle = "Bảo vệ dữ liệu cửa hàng",
                roleDescription = "Sao lưu và khôi phục toàn bộ dữ liệu bán hàng an toàn khi đổi máy.",
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
                    .widthIn(max = 420.dp)
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = step.themeColor.copy(alpha = 0.45f))
                        .testTag("floating_tooltip_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Top Row: Tag + Step Counter on left, compact Bỏ qua & Tiếp theo buttons on right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                if (stepIdx > 0) {
                                    IconButton(
                                        onClick = { viewModel.previousOnboardingStep() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Quay lại".t(),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                Surface(
                                    color = step.themeColor.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = step.sectionTag.t(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = step.themeColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${stepIdx + 1}/${steps.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Compact Action Buttons moved to top (half size)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.completeOnboardingTour() },
                                    modifier = Modifier
                                        .height(26.dp)
                                        .testTag("onboarding_skip_button"),
                                    shape = RoundedCornerShape(7.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "Bỏ qua".t(),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.5.sp
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
                                        .height(26.dp)
                                        .testTag("onboarding_next_button"),
                                    shape = RoundedCornerShape(7.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = step.themeColor
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = if (isLastStep) "Xong".t() else "Tiếp theo".t(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Title with Feature Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(step.themeColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = step.icon,
                                    contentDescription = null,
                                    tint = step.themeColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = step.title.t(),
                                    fontSize = 14.5.sp,
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

                        Spacer(modifier = Modifier.height(6.dp))

                        // Short role description only (removed "Tác dụng nổi bật")
                        Text(
                            text = step.roleDescription.t(),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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
