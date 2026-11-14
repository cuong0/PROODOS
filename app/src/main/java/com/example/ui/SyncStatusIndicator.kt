package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SyncState

@Composable
fun SyncStatusBadge(
    syncState: SyncState,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Màu cam khi đang đồng bộ, Màu xanh lá khi đồng bộ xong
    val (bgColor, dotColor, labelText, desc) = when (syncState) {
        SyncState.SYNCED -> Quad(
            Color(0xFFDCFCE7),
            Color(0xFF16A34A),
            "Đã đồng bộ trực tuyến".t(),
            "Dữ liệu đã được đồng bộ trực tuyến chắc chắn và an toàn trên đám mây.".t()
        )
        SyncState.SYNCING -> Quad(
            Color(0xFFFFEDD5),
            Color(0xFFEA580C),
            "Đang đồng bộ...".t(),
            "Đang đồng bộ hóa dữ liệu với đám mây theo thời gian thực...".t()
        )
        SyncState.OFFLINE -> Quad(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.outline,
            "Ngoại tuyến".t(),
            "Chưa đăng nhập. Dữ liệu đang được lưu cục bộ trên thiết bị này.".t()
        )
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (onClick != null) {
                    onClick()
                } else {
                    Toast.makeText(context, desc, Toast.LENGTH_SHORT).show()
                }
            }
            .testTag("sync_status_badge"),
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (syncState == SyncState.SYNCING) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    modifier = Modifier
                        .size(10.dp)
                        .rotate(rotation),
                    tint = dotColor
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }

            if (showLabel) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = labelText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (syncState == SyncState.OFFLINE) MaterialTheme.colorScheme.onSurfaceVariant else dotColor
                )
            }
        }
    }
}

@Composable
fun SyncStatusDot(
    syncState: SyncState,
    modifier: Modifier = Modifier,
    dotSize: androidx.compose.ui.unit.Dp = 8.dp,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "sync_dot_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    // Đang đồng bộ hóa: MÀU CAM; Đồng bộ xong: MÀU XANH
    val (dotColor, haloColor, desc) = when (syncState) {
        SyncState.SYNCED -> Triple(
            Color(0xFF16A34A), // MÀU XANH
            Color(0x2816A34A),
            "Đã đồng bộ trực tuyến thành công. Dữ liệu an toàn trên đám mây.".t()
        )
        SyncState.SYNCING -> Triple(
            Color(0xFFEA580C), // MÀU CAM
            Color(0x40EA580C),
            "Đang đồng bộ hóa dữ liệu với đám mây...".t()
        )
        SyncState.OFFLINE -> Triple(
            Color(0xFF9E9E9E),
            Color(0x1F9E9E9E),
            "Ngoại tuyến. Dữ liệu lưu cục bộ trên thiết bị.".t()
        )
    }

    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable {
                if (onClick != null) {
                    onClick()
                } else {
                    Toast.makeText(context, desc, Toast.LENGTH_SHORT).show()
                }
            }
            .testTag("sync_status_dot"),
        contentAlignment = Alignment.Center
    ) {
        // Vòng quầng nhẹ bao quanh
        Box(
            modifier = Modifier
                .size(if (syncState == SyncState.SYNCING) (dotSize + 6.dp) * pulseScale else (dotSize + 6.dp))
                .clip(CircleShape)
                .background(haloColor)
        )
        // Điểm trạng thái: Màu cam khi đang đồng bộ, Màu xanh khi hoàn tất
        if (syncState == SyncState.SYNCING) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = desc,
                modifier = Modifier
                    .size(dotSize + 4.dp)
                    .rotate(spinAngle),
                tint = dotColor
            )
        } else {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

@Composable
fun SyncActionButton(
    syncState: SyncState,
    isManualSyncing: Boolean,
    manualSyncProgress: Float,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSyncInProgress = isManualSyncing || syncState == SyncState.SYNCING

    val infiniteTransition = rememberInfiniteTransition(label = "sync_button_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    // Chuyển đổi màu sắc: Đang đồng bộ -> MÀU CAM (0xFFEA580C); Đã đồng bộ -> MÀU XANH (0xFF16A34A)
    val buttonBgColor by animateColorAsState(
        targetValue = when {
            isSyncInProgress -> Color(0xFFEA580C) // Màu cam khi đang đồng bộ
            syncState == SyncState.SYNCED -> Color(0xFF16A34A) // Màu xanh khi đồng bộ xong
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(400),
        label = "syncButtonBgColor"
    )

    val buttonContentColor by animateColorAsState(
        targetValue = when {
            isSyncInProgress || syncState == SyncState.SYNCED -> Color.White
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(400),
        label = "syncButtonTextColor"
    )

    Button(
        onClick = onSyncClick,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("sync_all_data_button"),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonBgColor,
            contentColor = buttonContentColor
        ),
        shape = RoundedCornerShape(12.dp),
        enabled = !isManualSyncing
    ) {
        if (isSyncInProgress) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(spinAngle),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isManualSyncing) "Đang đồng bộ (${(manualSyncProgress * 100).toInt()}%)...".t()
                else "Đang đồng bộ hóa dữ liệu...".t(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White
            )
        } else if (syncState == SyncState.SYNCED) {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Đã đồng bộ hóa dữ liệu (Nhấn để làm mới)".t(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White
            )
        } else {
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Đồng bộ hóa tất cả ảnh & dữ liệu".t(),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun SyncStatusTopBar(
    syncState: SyncState,
    storeName: String = "",
    currentUser: com.google.firebase.auth.FirebaseUser? = null,
    userProfile: com.example.data.UserProfile? = null,
    onAvatarClick: () -> Unit = {},
    currentTab: MainTab = MainTab.BAN_HANG,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 16.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            SyncStatusDot(
                syncState = syncState,
                dotSize = 8.dp
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
