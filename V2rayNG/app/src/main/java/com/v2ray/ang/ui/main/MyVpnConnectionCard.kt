package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType

/** Presentation of the main feature's service state; all operations stay in MainAction. */
@Composable
internal fun MyVpnConnectionCard(
    isRunning: Boolean,
    hasSelectedProfile: Boolean,
    isTesting: Boolean,
    testStatus: String?,
    compact: Boolean,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectionState = stringResource(
        if (isRunning) R.string.myvpn_connected else R.string.myvpn_disconnected
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(if (compact) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 40.dp else 52.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (!compact) {
                        Text(
                            text = stringResource(R.string.myvpn_connection_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        text = connectionState,
                        style = if (compact) MaterialTheme.typography.titleLarge
                            else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                IconButton(
                    onClick = { onAction(MainAction.TestCurrentServer) },
                    enabled = isRunning && !isTesting,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_flash_on_24dp),
                        contentDescription = stringResource(R.string.myvpn_test_connection),
                    )
                }
            }
            if (!compact) {
                Text(
                    text = stringResource(
                        if (isRunning) R.string.myvpn_running_hint
                        else if (hasSelectedProfile) R.string.myvpn_selected_profile_hint
                        else R.string.myvpn_select_profile_hint
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { onAction(MainAction.ToggleService) },
                    enabled = isRunning || hasSelectedProfile,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { stateDescription = connectionState },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (isRunning) R.string.myvpn_disconnect else R.string.myvpn_connect
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
                MyVpnAddProfileButton(
                    onAction = onAction,
                    modifier = Modifier.weight(1f),
                )
            }
            if (testStatus != null) {
                Text(
                    text = testStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MyVpnAddProfileButton(
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val menuScrollState = rememberScrollState()
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.myvpn_add_profile),
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            scrollState = menuScrollState,
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            ImportMenuContent { action ->
                menuExpanded = false
                onAction(action)
            }
        }
    }
}

@Composable
internal fun MyVpnProfilesHeading() {
    Text(
        text = stringResource(R.string.myvpn_profiles_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

@Composable
internal fun MyVpnEmptyProfiles(
    isSearch: Boolean,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (isSearch) R.drawable.ic_search_24dp else R.drawable.ic_subscriptions_24dp
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = stringResource(
                if (isSearch) R.string.myvpn_no_search_results else R.string.myvpn_empty_title
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(
                if (isSearch) R.string.myvpn_search_hint else R.string.myvpn_empty_hint
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!isSearch) {
            Button(
                onClick = { onAction(MainAction.ImportClipboard) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(painterResource(R.drawable.ic_copy), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.myvpn_paste_link))
            }
            OutlinedButton(
                onClick = { onAction(MainAction.ImportQRcode) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(painterResource(R.drawable.ic_scan_24dp), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.myvpn_scan_qr))
            }
            TextButton(
                onClick = {
                    onAction(MainAction.ImportManually(EConfigType.VLESS.value))
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.myvpn_create_vless),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
