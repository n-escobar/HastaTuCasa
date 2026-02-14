package com.example.hastatucasa.ui.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hastatucasa.data.model.Order
import com.example.hastatucasa.data.model.OrderItem
import com.example.hastatucasa.data.model.OrderStatus
import com.example.hastatucasa.data.model.OrderType
import com.example.hastatucasa.data.model.StatusChange
import com.example.hastatucasa.data.model.User
import com.example.hastatucasa.ui.components.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.onSnackbarDismissed()
        }
    }

    // Edit profile dialog
    if (state.isEditingProfile && state.user != null) {
        EditProfileDialog(
            user = state.user!!,
            onSave = viewModel::onSaveProfile,
            onDismiss = viewModel::onCancelEditProfile,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            ProfileContent(
                state = state,
                onFilterSelected = viewModel::onFilterSelected,
                onOrderExpandToggled = viewModel::onOrderExpandToggled,
                onCancelOrder = viewModel::onCancelOrder,
                onEditProfile = viewModel::onStartEditProfile,
                contentPadding = padding,
            )
        }
    }
}

// ─── Content ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onFilterSelected: (OrderFilter) -> Unit,
    onOrderExpandToggled: (String) -> Unit,
    onCancelOrder: (String, String) -> Unit,
    onEditProfile: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        // Profile header
        item {
            ProfileHeader(
                user = state.user,
                activeOrderCount = state.activeOrderCount,
                onEditProfile = onEditProfile,
            )
        }

        // Stats row
        item {
            OrderStatsRow(
                orders = state.orders,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // Order section header + filter
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SectionHeader(title = "My Orders")
                Spacer(Modifier.height(10.dp))
                OrderFilterRow(
                    selected = state.selectedFilter,
                    onFilterSelected = onFilterSelected,
                )
            }
        }

        // Orders
        val displayed = state.filteredOrders
        if (displayed.isEmpty()) {
            item {
                EmptyOrdersState(
                    filter = state.selectedFilter,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            items(displayed, key = { it.orderId }) { order ->
                OrderCard(
                    order = order,
                    isExpanded = state.expandedOrderId == order.orderId,
                    onExpandToggle = { onOrderExpandToggled(order.orderId) },
                    onCancel = { reason -> onCancelOrder(order.orderId, reason) },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .animateItem(),
                )
            }
        }
    }
}

// ─── Profile Header ───────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    user: User?,
    activeOrderCount: Int,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            val initials = user?.name
                ?.split(" ")
                ?.take(2)
                ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }
                ?.joinToString("")
                ?: "?"
            UserAvatar(initials = initials, size = 72)
            Spacer(Modifier.height(12.dp))
            Text(
                text = user?.name ?: "—",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = user?.email ?: "",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.outline
                )
            )
            if (!user?.deliveryAddress.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = user!!.deliveryAddress,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.outline
                        ),
                        maxLines = 1,
                    )
                }
            }
            if (activeOrderCount > 0) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "$activeOrderCount active ${if (activeOrderCount == 1) "order" else "orders"}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    )
                }
            }
        }
        IconButton(
            onClick = onEditProfile,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit profile",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─── Stats Row ────────────────────────────────────────────────────────────────

@Composable
private fun OrderStatsRow(orders: List<Order>, modifier: Modifier = Modifier) {
    val total = orders.size
    val delivered = orders.count { it.status == OrderStatus.DELIVERED }
    val totalSpent = orders
        .filter { it.status == OrderStatus.DELIVERED }
        .sumOf { it.totalPrice }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatItem(label = "Total Orders", value = total.toString())
        VerticalDivider(modifier = Modifier.height(36.dp))
        StatItem(label = "Delivered", value = delivered.toString())
        VerticalDivider(modifier = Modifier.height(36.dp))
        StatItem(label = "Total Spent", value = "$${"%.2f".format(totalSpent)}")
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.outline,
            )
        )
    }
}

// ─── Filter Row ───────────────────────────────────────────────────────────────

@Composable
private fun OrderFilterRow(
    selected: OrderFilter,
    onFilterSelected: (OrderFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OrderFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercaseChar() }) },
                leadingIcon = if (filter == selected) {
                    { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                } else null,
            )
        }
    }
}

// ─── Order Card ───────────────────────────────────────────────────────────────

@Composable
private fun OrderCard(
    order: Order,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "chevron",
    )
    var showCancelDialog by remember { mutableStateOf(false) }

    if (showCancelDialog) {
        CancelOrderDialog(
            onConfirm = { reason ->
                showCancelDialog = false
                onCancel(reason)
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onExpandToggle,
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Order #${order.orderId.takeLast(5).uppercase()}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = formatInstant(order.createdAt),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.outline
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

            // Summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val totalQuantity = order.items.sumOf { it.quantity.toInt() }
                Text(
                    text = "$totalQuantity ${if (totalQuantity == 1) "item" else "items"}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.outline
                    )
                )
                Text(
                    text = "$${order.totalPrice}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                )
            }

            // Scheduled badge
            if (order.orderType == OrderType.SCHEDULED && order.scheduledFor != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule, contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Scheduled for ${formatInstant(order.scheduledFor)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    )
                }
            }

            // Expanded content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    // Items list
                    Text(
                        "Items",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(8.dp))
                    order.items.forEach { item ->
                        OrderItemRow(item, Modifier.padding(vertical = 3.dp))
                    }

                    // Delivery address
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.LocationOn, contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = order.deliveryAddress,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    // Status timeline
                    if (order.statusHistory.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Status History",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(Modifier.height(8.dp))
                        StatusTimeline(history = order.statusHistory)
                    }

                    // Cancel button
                    if (!order.status.isTerminal() && order.status == OrderStatus.PENDING) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.error,
                                        MaterialTheme.colorScheme.error,
                                    )
                                )
                            )
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Cancel Order")
                        }
                    }
                }
            }
        }
    }
}

// ─── Order Item Row ───────────────────────────────────────────────────────────

@Composable
private fun OrderItemRow(item: OrderItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                        color = MaterialTheme.colorScheme.outline
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

// ─── Status Timeline ──────────────────────────────────────────────────────────

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
                                RoundedCornerShape(50)
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
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = formatInstant(change.changedAt),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline
                        )
                    )
                    change.reason?.let {
                        Text(
                            text = "Reason: $it",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Light,
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyOrdersState(filter: OrderFilter, modifier: Modifier = Modifier) {
    val message = when (filter) {
        OrderFilter.ALL       -> "You haven't placed any orders yet"
        OrderFilter.ACTIVE    -> "No active orders right now"
        OrderFilter.COMPLETED -> "No completed orders yet"
        OrderFilter.CANCELLED -> "No cancelled orders"
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.ShoppingBag,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.outline),
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Cancel Dialog ────────────────────────────────────────────────────────────

@Composable
private fun CancelOrderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Cancel, contentDescription = null) },
        title = { Text("Cancel Order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Are you sure you want to cancel this order?")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.ifBlank { "Cancelled by shopper" }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                )
            ) { Text("Cancel Order") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep Order") }
        }
    )
}

// ─── Edit Profile Dialog ──────────────────────────────────────────────────────

@Composable
private fun EditProfileDialog(
    user: User,
    onSave: (name: String, address: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(user.name) }
    var address by remember { mutableStateOf(user.deliveryAddress) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Edit Profile",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Delivery Address") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onSave(name, address) },
                        enabled = name.isNotBlank(),
                    ) { Text("Save") }
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private val dateTimeFormatter = DateTimeFormatter
    .ofPattern("MMM d, h:mm a")
    .withZone(ZoneId.systemDefault())

private fun formatInstant(instant: Instant): String = dateTimeFormatter.format(instant)