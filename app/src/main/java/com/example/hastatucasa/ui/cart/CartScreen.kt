package com.example.hastatucasa.ui.cart

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.hastatucasa.data.model.DeliverySlot
import com.example.hastatucasa.ui.components.PriceText
import com.example.hastatucasa.ui.components.QuantityStepper
import com.example.hastatucasa.ui.components.SectionHeader
import java.time.format.DateTimeFormatter
import java.time.ZoneId

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onNavigateBack: () -> Unit = {},
    onOrderPlaced: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.checkoutSuccess) {
        if (state.checkoutSuccess) {
            viewModel.onCheckoutSuccessConsumed()
            onOrderPlaced()
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.onSnackbarDismissed()
        }
    }

    // Slot picker dialog
    if (state.isSlotPickerOpen) {
        SlotPickerDialog(
            availableSlots = state.availableSlots,
            onSlotConfirmed = viewModel::onSlotSelected,
            onDismiss = viewModel::onSlotPickerDismissed,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CartTopBar(
                itemCount = state.itemCount,
                onNavigateBack = onNavigateBack,
            )
        },
        modifier = modifier,
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.isEmpty -> {
                EmptyCartState(modifier = Modifier.padding(padding))
            }
            else -> {
                CartContent(
                    state = state,
                    onAddItem = { viewModel.onAddItem(it.product) },
                    onRemoveItem = { viewModel.onRemoveItem(it.product.id) },
                    onRemoveItemCompletely = { viewModel.onRemoveItemCompletely(it.product.id) },
                    onDeliverNow = viewModel::onDeliverNow,
                    onScheduleDelivery = viewModel::onScheduleDelivery,
                    onConfirmScheduled = viewModel::onConfirmScheduled,
                    onChangeSlot = viewModel::onChangeSlot,
                    contentPadding = padding,
                )
            }
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartTopBar(
    itemCount: Int,
    onNavigateBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "My Cart",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                )
                AnimatedVisibility(visible = itemCount > 0) {
                    Text(
                        text = "$itemCount ${if (itemCount == 1) "item" else "items"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline,
                        )
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

// ─── Content ──────────────────────────────────────────────────────────────────

@Composable
private fun CartContent(
    state: CartUiState,
    onAddItem: (CartLineItem) -> Unit,
    onRemoveItem: (CartLineItem) -> Unit,
    onRemoveItemCompletely: (CartLineItem) -> Unit,
    onDeliverNow: () -> Unit,
    onScheduleDelivery: () -> Unit,
    onConfirmScheduled: () -> Unit,
    onChangeSlot: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 8.dp, bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionHeader(title = "Items")
                Spacer(Modifier.height(4.dp))
            }

            items(state.lineItems, key = { it.product.id }) { lineItem ->
                CartLineItemCard(
                    lineItem = lineItem,
                    onAdd = { onAddItem(lineItem) },
                    onRemove = { onRemoveItem(lineItem) },
                    onDelete = { onRemoveItemCompletely(lineItem) },
                    modifier = Modifier.animateItem(),
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                DeliveryAddressRow(address = state.deliveryAddress)
            }
        }

        OrderSummaryCard(
            state = state,
            onDeliverNow = onDeliverNow,
            onScheduleDelivery = onScheduleDelivery,
            onConfirmScheduled = onConfirmScheduled,
            onChangeSlot = onChangeSlot,
        )
    }
}

// ─── Cart Line Item Card ──────────────────────────────────────────────────────

@Composable
private fun CartLineItemCard(
    lineItem: CartLineItem,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(lineItem.product.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = lineItem.product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (lineItem.product.hasDiscount) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondary,
                    ) {
                        Text(
                            text = "-${lineItem.product.discountPercent.toInt()}%",
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = lineItem.product.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                PriceText(
                    price = "$${lineItem.product.effectivePrice}${lineItem.unitLabel}",
                    originalPrice = if (lineItem.product.hasDiscount)
                        "$${lineItem.product.price}${lineItem.unitLabel}"
                    else null,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuantityStepper(
                        quantity = lineItem.quantity,
                        onIncrement = onAdd,
                        onDecrement = onRemove,
                        minQuantity = 1,
                    )
                    Text(
                        text = "$${lineItem.subtotal}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Remove ${lineItem.product.name}",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ─── Delivery Address Row ─────────────────────────────────────────────────────

@Composable
private fun DeliveryAddressRow(address: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 1.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text(
                text = "Delivering to",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.outline,
                )
            )
            Text(
                text = address.ifBlank { "No address set — update in Profile" },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (address.isBlank()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    }
}

// ─── Order Summary Card ───────────────────────────────────────────────────────

@Composable
private fun OrderSummaryCard(
    state: CartUiState,
    onDeliverNow: () -> Unit,
    onScheduleDelivery: () -> Unit,
    onConfirmScheduled: () -> Unit,
    onChangeSlot: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(title = "Order Summary")
            Spacer(Modifier.height(2.dp))

            SummaryRow(label = "Subtotal", value = "$${state.subtotal}")
            SummaryRow(label = "Delivery fee", value = "$${state.deliveryFee}")

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "$${state.total}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                )
            }

            Spacer(Modifier.height(4.dp))

            // Selected slot confirmation row
            AnimatedVisibility(
                visible = state.selectedSlot != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                state.selectedSlot?.let { slot ->
                    SelectedSlotRow(
                        slot = slot,
                        onChangeSlot = onChangeSlot,
                    )
                }
            }

            // Delivery mode buttons
            DeliveryModeButtons(
                state = state,
                onDeliverNow = onDeliverNow,
                onScheduleDelivery = onScheduleDelivery,
                onConfirmScheduled = onConfirmScheduled,
            )
        }
    }
}

// ─── Selected Slot Row ────────────────────────────────────────────────────────

@Composable
private fun SelectedSlotRow(
    slot: DeliverySlot,
    onChangeSlot: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = slot.label,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    )
                    Text(
                        text = slot.date.format(
                            DateTimeFormatter.ofPattern("EEEE, MMM d")
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    )
                }
            }
            TextButton(
                onClick = onChangeSlot,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                )
            }
        }
    }
}

// ─── Delivery Mode Buttons ────────────────────────────────────────────────────

@Composable
private fun DeliveryModeButtons(
    state: CartUiState,
    onDeliverNow: () -> Unit,
    onScheduleDelivery: () -> Unit,
    onConfirmScheduled: () -> Unit,
) {
    val isDisabled = state.isCheckingOut || state.isEmpty

    if (state.selectedSlot != null) {
        // Slot chosen → show "Confirm Scheduled" + "Deliver Now" side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onDeliverNow,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isDisabled,
            ) {
                AnimatedCheckoutContent(isCheckingOut = false) {
                    Icon(
                        Icons.Default.FlashOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Now",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                        )
                    )
                }
            }
            Button(
                onClick = onConfirmScheduled,
                modifier = Modifier
                    .weight(2f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isDisabled,
            ) {
                AnimatedCheckoutContent(isCheckingOut = state.isCheckingOut) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Place Scheduled · $${state.total}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                        )
                    )
                }
            }
        }
    } else {
        // No slot chosen → "Deliver Now" (primary) + "Schedule" (outlined)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onDeliverNow,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isDisabled,
            ) {
                AnimatedCheckoutContent(isCheckingOut = state.isCheckingOut) {
                    Icon(
                        Icons.Default.FlashOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Deliver Now",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                        )
                    )
                }
            }
            OutlinedButton(
                onClick = onScheduleDelivery,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isDisabled,
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Schedule",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                    )
                )
            }
        }
    }
}

@Composable
private fun AnimatedCheckoutContent(
    isCheckingOut: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    AnimatedContent(
        targetState = isCheckingOut,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
        label = "checkout_content",
    ) { checking ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                content()
            }
        }
    }
}

// ─── Summary Row ─────────────────────────────────────────────────────────────

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.outline,
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        )
    }
}

// ─── Empty Cart State ─────────────────────────────────────────────────────────

@Composable
private fun EmptyCartState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(50),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Your cart is empty",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add items from the shop\nto get started",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        )
    }
}