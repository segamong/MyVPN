package com.v2ray.ang.ui.server

import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox

import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity

class ThreeXUiClientActivity : BaseComponentActivity() {
    private val viewModel: ThreeXUiClientViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        if (state.complete) BackHandler { finish() }
        ThreeXUiClientScreen(state, viewModel::update, viewModel::loadInbounds, viewModel::createClient)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreeXUiClientScreen(
    state: ThreeXUiClientState,
    onUpdate: ((ThreeXUiClientState) -> ThreeXUiClientState) -> Unit,
    onLoad: () -> Unit,
    onCreate: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.myvpn_create_client)) }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(state.panelUrl, { value -> onUpdate { it.copy(panelUrl = value) } }, label = { Text(stringResource(R.string.myvpn_panel_url)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text(stringResource(R.string.myvpn_https_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(state.username, { value -> onUpdate { it.copy(username = value) } }, label = { Text(stringResource(R.string.myvpn_username)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(state.password, { value -> onUpdate { it.copy(password = value) } }, label = { Text(stringResource(R.string.myvpn_password)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(state.publicHost, { value -> onUpdate { it.copy(publicHost = value) } }, label = { Text(stringResource(R.string.myvpn_public_host)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = onLoad, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) stringResource(R.string.myvpn_working) else stringResource(R.string.myvpn_load_inbounds))
            }
            if (state.inbounds.isNotEmpty()) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = state.inbounds.firstOrNull { it.id == state.selectedInboundId }?.let { "${it.remark} · ${it.port}" }.orEmpty(),
                        onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.myvpn_vless_inbound)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        state.inbounds.forEach { inbound ->
                            DropdownMenuItem(
                                text = { Text("${inbound.remark} · ${inbound.port}") },
                                onClick = { onUpdate { it.copy(selectedInboundId = inbound.id) }; expanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(state.clientName, { value -> onUpdate { it.copy(clientName = value) } }, label = { Text(stringResource(R.string.myvpn_client_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = onCreate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) stringResource(R.string.myvpn_working) else stringResource(R.string.myvpn_create))
                }
            }
            state.message?.let { message ->
                Text(message, color = if (state.complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
