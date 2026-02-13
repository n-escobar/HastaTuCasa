package com.example.hastatucasa.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hastatucasa.data.model.OrderStatus

// ─── Status Badge ─────────────────────────────────────────────────────────────

@Composable
fun OrderStatusBadge(
    status: OrderStatus,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor) = statusColors(status)
    val animatedBg by animateColorAsState(bgColor, animationSpec = tween(300), label = "bg")
    val animatedText by animateColorAsState(textColor, animationSpec = tween(300), label = "text")

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = animatedBg,
    ) {
        Text(
            text = status.displayName(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = animatedText,
                letterSpacing = 0.4.sp,
            )
        )
    }
}

@Composable
private fun statusColors(status: OrderStatus): Pair<Color, Color> = when (status) {
    OrderStatus.PENDING           -> Color(0xFFFFF3CD) to Color(0xFF856404)
    OrderStatus.CONFIRMED         -> Color(0xFFCCE5FF) to Color(0xFF004085)
    OrderStatus.PREPARING         -> Color(0xFFD4EDDA) to Color(0xFF155724)
    OrderStatus.READY_FOR_PICKUP  -> Color(0xFFD1ECF1) to Color(0xFF0C5460)
    OrderStatus.OUT_FOR_DELIVERY  -> Color(0xFFE2D9F3) to Color(0xFF3A1078)
    OrderStatus.DELIVERED         -> Color(0xFF198754).copy(alpha = 0.15f) to Color(0xFF0F5132)
    OrderStatus.CANCELLED         -> Color(0xFFF8D7DA) to Color(0xFF721C24)
}

// ─── Quantity Stepper ─────────────────────────────────────────────────────────

@Composable
fun QuantityStepper(
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
    minQuantity: Int = 0,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SmallIconButton(
            onClick = onDecrement,
            enabled = quantity > minQuantity,
            icon = { Icon(Icons.Default.Remove, contentDescription = "Remove") },
        )
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.widthIn(min = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        SmallIconButton(
            onClick = onIncrement,
            icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
        )
    }
}

@Composable
private fun SmallIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(30.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    ) {
        Box(Modifier.size(16.dp)) { icon() }
    }
}

// ─── Price Text ───────────────────────────────────────────────────────────────

@Composable
fun PriceText(
    price: String,
    originalPrice: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = price,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        )
        if (originalPrice != null) {
            Text(
                text = originalPrice,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.outline,
                    textDecoration = TextDecoration.LineThrough,
                )
            )
        }
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        trailing?.invoke()
    }
}

// ─── Avatar ───────────────────────────────────────────────────────────────────

@Composable
fun UserAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Int = 48,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = (size * 0.35f).sp,
            )
        )
    }
}