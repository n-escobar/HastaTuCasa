package com.example.hastatucasa.ui.deliverer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderItem
import com.example.hastatucasa.data.model.OrderStatus
import com.example.hastatucasa.data.model.OrderType
import com.example.hastatucasa.data.model.StatusChange
import com.example.hastatucasa.ui.components.OrderStatusBadge
import com.example.hastatucasa.ui.components.SectionHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DelivererOrdersScreen(
    modifier: Modifier = Modifier,
    viewModel: DelivererOrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.onSnackbarDismissed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { DelivererTopBar(activeCount = state.activeOrders.size) },
        modifier = modifier,
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            DelivererContent(
                state = state,
                onTabSelected = viewModel::onTabSelected,
                onExpandToggle = viewModel::onOrderExpandToggled,
                onAdvanceStatus = viewModel::onAdvanceStatus,
                contentPadding = padding,
            )
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelivererTopBar(activeCount: Int) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "HastaTuCasa",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                )
                Text(
                    text = if (activeCount == 0) "No active orders"
                    else "$activeCount active ${if (activeCount == 1) "order" else "orders"}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.outline,
                    )
                )
            }
        },
        actions = {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(end = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.DeliveryDining,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Deliverer",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

// ─── Content ──────────────────────────────────────────────────────────────────

@Composable
private fun DelivererContent(
    state: DelivererOrdersUiState,
    onTabSelected: (DelivererTab) -> Unit,
    onExpandToggle: (String) -> Unit,
    onAdvanceStatus: (Order) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        // ── Tab row ───────────────────────────────────────────────────────────
        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            Tab(
                selected = state.selectedTab == DelivererTab.ACTIVE,
                onClick = { onTabSelected(DelivererTab.ACTIVE) },
                icon = { Icon(Icons.Default.Inventory, contentDescription = null) },
                text = { Text("Active (${state.activeOrders.size})") },
            )
            Tab(
                selected = state.selectedTab == DelivererTab.HISTORY,
                onClick = { onTabSelected(DelivererTab.HISTORY) },
                icon = { Icon(Icons.Default.History, contentDescription = null) },
                text = { Text("History (${state.completedOrders.size})") },
            )
        }

        // ── Order list ────────────────────────────────────────────────────────
        val orders = state.displayedOrders

        AnimatedContent(
            targetState = state.selectedTab,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith
                        (slideOutVertically { -it } + fadeOut())
            },
            label = "tab_switch",
        ) { tab ->
            if (orders.isEmpty()) {
                EmptyOrdersState(
                    tab = tab,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(orders, key = { it.orderId }) { order ->
                        DelivererOrderCard(
                            order = order,
                            isExpanded = state.expandedOrderId == order.orderId,
                            isActionPending = state.pendingActionOrderId == order.orderId,
                            onExpandToggle = { onExpandToggle(order.orderId) },
                            onAdvanceStatus = { onAdvanceStatus(order) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

// ─── Order Card ───────────────────────────────────────────────────────────────

@Composable
private fun DelivererOrderCard(
    order: Order,
    isExpanded: Boolean,
    isActionPending: Boolean,
    onExpandToggle: () -> Unit,
    onAdvanceStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "chevron",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onExpandToggle,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header row ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Order #${order.orderId.takeLast(5).uppercase()}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                        )
                    )
                    Text(
                        text = formatInstant(order.createdAt),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.outline,
                        )
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OrderStatusBadge(order.status)
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(chevronAngle),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Summary row ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val totalQty = order.items.sumOf { it.quantity.toInt() }
                Column {
                    Text(
                        text = "$totalQty ${if (totalQty == 1) "item" else "items"} · $${order.totalPrice}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.outline,
                        )
                    )
                    // Shopper address
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = order.deliveryAddress,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.outline,
                            ),
                            maxLines = 1,
                        )
                    }
                }

                // Order type chip
                if (order.orderType == OrderType.SCHEDULED) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = "Scheduled",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            )
                        }
                    }
                }
            }

            // ── Expanded detail ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    // Items
                    SectionHeader(title = "Items")
                    Spacer(Modifier.height(8.dp))
                    order.items.forEach { item ->
                        OrderItemRow(item = item, modifier = Modifier.padding(vertical = 3.dp))
                    }

                    // Shopper info
                    if (order.shopperId.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        InfoRow(
                            icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline) },
                            label = "Shopper ID",
                            value = order.shopperId,
                        )
                    }

                    // Deliverer
                    if (order.delivererId != null) {
                        InfoRow(
                            icon = { Icon(Icons.Default.DeliveryDining, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline) },
                            label = "Assigned to",
                            value = order.delivererId!!,
                        )
                    }

                    // Scheduled time
                    if (order.scheduledFor != null) {
                        InfoRow(
                            icon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary) },
                            label = "Scheduled for",
                            value = formatInstant(order.scheduledFor),
                        )
                    }

                    // Status timeline
                    if (order.statusHistory.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        SectionHeader(title = "Status History")
                        Spacer(Modifier.height(8.dp))
                        StatusTimeline(history = order.statusHistory)
                    }

                    // Action button — only for non-terminal active orders
                    val actionLabel = order.nextActionLabel()
                    if (actionLabel != null) {
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onAdvanceStatus,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isActionPending,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = actionButtonColor(order.status),
                            ),
                        ) {
                            AnimatedContent(
                                targetState = isActionPending,
                                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                                label = "action_btn",
                            ) { pending ->
                                if (pending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text(
                                        text = actionLabel,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Supporting composables ───────────────────────────────────────────────────

@Composable
private fun OrderItemRow(item: OrderItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Text(
                    text = "× ${item.quantity.stripTrailingZeros().toPlainString()}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.outline,
                    )
                )
            }
        }
        Text(
            text = "$${item.subtotal}",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun InfoRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.outline,
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
            )
        )
    }
}

@Composable
private fun StatusTimeline(history: List<StatusChange>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        history.forEachIndexed { index, change ->
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (index == history.lastIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(50),
                            )
                    )
                    if (index < history.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = change.toStatus.displayName(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        )
                    )
                    Text(
                        text = "${formatInstant(change.changedAt)} · by ${change.changedBy}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline,
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyOrdersState(tab: DelivererTab, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (tab == DelivererTab.ACTIVE) Icons.Default.CheckCircle
            else Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (tab == DelivererTab.ACTIVE) "No active orders right now"
            else "No completed deliveries yet",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.SemiBold,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (tab == DelivererTab.ACTIVE) "New orders from shoppers will appear here"
            else "Delivered and cancelled orders will appear here",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.outline,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun actionButtonColor(status: OrderStatus) = when (status) {
    OrderStatus.PENDING          -> MaterialTheme.colorScheme.primary
    OrderStatus.CONFIRMED        -> MaterialTheme.colorScheme.primary
    OrderStatus.PREPARING        -> MaterialTheme.colorScheme.secondary
    OrderStatus.READY_FOR_PICKUP -> MaterialTheme.colorScheme.secondary
    OrderStatus.OUT_FOR_DELIVERY -> Color(0xFF2E7D32)
    else                         -> MaterialTheme.colorScheme.primary
}

private val dateTimeFormatter = DateTimeFormatter
    .ofPattern("MMM d, h:mm a")
    .withZone(ZoneId.systemDefault())

private fun formatInstant(instant: Instant): String = dateTimeFormatter.format(instant)