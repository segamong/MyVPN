package com.v2ray.ang.ui.server

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID
import com.v2ray.ang.handler.AngConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ThreeXUiClientState(
    val panelUrl: String = "",
    val username: String = "",
    val password: String = "",
    val publicHost: String = "13.143.172.92",
    val clientName: String = "",
    val inbounds: List<PanelInbound> = emptyList(),
    val selectedInboundId: Long? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val complete: Boolean = false,
)

internal class ThreeXUiClientViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ThreeXUiClientState())
    val state = mutableState.asStateFlow()
    private var api: ThreeXUiClientApi? = null

    fun update(transform: (ThreeXUiClientState) -> ThreeXUiClientState) = mutableState.update(transform)

    fun loadInbounds() {
        val values = mutableState.value
        if (values.panelUrl.isBlank() || values.username.isBlank() || values.password.isBlank()) {
            mutableState.update { it.copy(message = getApplication<Application>().getString(com.v2ray.ang.R.string.myvpn_required_panel_credentials)) }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val panel = ThreeXUiClientApi(values.panelUrl, values.username, values.password)
                    panel to panel.inbounds()
                }
            }.onSuccess { (panel, items) ->
                api = panel
                mutableState.update {
                    it.copy(
                        inbounds = items,
                        selectedInboundId = items.firstOrNull()?.id,
                        password = "",
                        busy = false,
                    message = if (items.isEmpty()) getApplication<Application>().getString(com.v2ray.ang.R.string.myvpn_no_vless_inbounds) else null
                    )
                }
            }.onFailure { error ->
                api = null
                mutableState.update { it.copy(password = "", busy = false, message = getApplication<Application>().getString(com.v2ray.ang.R.string.myvpn_connect_failed) + " " + (error.message ?: "")) }
            }
        }
    }

    fun createClient() {
        val values = mutableState.value
        val inbound = values.inbounds.firstOrNull { it.id == values.selectedInboundId }
        if (inbound == null || values.clientName.isBlank() || values.publicHost.isBlank()) {
            mutableState.update { it.copy(message = getApplication<Application>().getString(com.v2ray.ang.R.string.myvpn_required_client_fields)) }
            return
        }
        val panel = api
        if (panel == null) {
            mutableState.update { it.copy(message = getApplication<Application>().getString(com.v2ray.ang.R.string.myvpn_load_first)) }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val link = panel.create(inbound, values.clientName, values.publicHost)
                    val result = AngConfigManager.importBatchConfig(link, DEFAULT_SUBSCRIPTION_ID, true)
                    check(result.first > 0) { getApplication<Application>().getString(com.v2ray.ang.R.string.myvpn_profile_import_failed) }
                }
            }.onSuccess {
                mutableState.update { it.copy(busy = false, message = getApplication<Application>().getString(com.v2ray.ang.R.string.myvpn_client_created), complete = true) }
            }.onFailure { error ->
                mutableState.update { it.copy(busy = false, message = getApplication<Application>().getString(com.v2ray.ang.R.string.myvpn_create_failed) + " " + (error.message ?: "")) }
            }
        }
    }
}
