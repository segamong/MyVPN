package com.v2ray.ang.ui.server

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class PanelInbound(val id: Long, val remark: String, val port: Int, val raw: String)

/** Session-only 3x-ui client. Panel credentials and cookies are never persisted or logged. */
internal class ThreeXUiClientApi(private val panelUrl: String, username: String, password: String) {
    private val cookies = object : CookieJar {
        private val values = mutableMapOf<String, List<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { values[url.host] = cookies }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = values[url.host].orEmpty()
    }
    private val http = OkHttpClient.Builder()
        .cookieJar(cookies)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val root = panelUrl.trim().trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    init {
        require(root.startsWith("https://", true)) { "Use the panel's HTTPS address to protect the login and password" }
        val body = "{\"username\":${quote(username)},\"password\":${quote(password)}}"
        request("POST", "$root/login", body, requireSuccess = true)
    }

    fun inbounds(): List<PanelInbound> {
        val obj = api("GET", "$root/panel/api/inbounds/list")
        val list = if (obj.isJsonArray) obj.asJsonArray else obj.asJsonObject.getAsJsonArray("inbounds")
        return list.mapNotNull { element ->
            val item = element.asJsonObject
            if (item.has("enable") && !item.get("enable").asBoolean) return@mapNotNull null
            if (item.get("protocol")?.asString != "vless") return@mapNotNull null
            PanelInbound(
                id = item.get("id").asLong,
                remark = item.get("remark")?.asString.orEmpty().ifBlank { "VLESS ${item.get("port")?.asString.orEmpty()}" },
                port = item.get("port").asInt,
                raw = item.toString()
            )
        }
    }

    fun create(inbound: PanelInbound, label: String, publicHost: String): String {
        val email = label.trim()
        require(email.isNotBlank()) { "Client name is required" }
        val request = "{\"client\":{\"email\":${quote(email)},\"enable\":true},\"inboundIds\":[${inbound.id}]}"
        val created = request("POST", "$root/panel/api/clients/add", request, requireSuccess = false)
        if (created.first) {
            val links = runCatching {
                api("GET", "$root/panel/api/clients/links/${enc(email)}")
            }.getOrNull()
            if (links != null) {
                val values = if (links.isJsonArray) links.asJsonArray else links.asJsonObject.getAsJsonArray("links")
                values.firstOrNull()?.asString?.let { return it }
            }
            error("The client was created, but this panel did not return a share link")
        } else if (created.second !in listOf(404, 405)) {
            error(created.third.ifBlank { "3x-ui could not create the client" })
        }

        // Compatibility for older panels that expose only /inbounds/addClient.
        val uuid = UUID.randomUUID().toString()
        val host = publicHost.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        require(host.isNotBlank()) { "Public server address is required" }
        val shareLink = buildLegacyVlessLink(inbound, email, uuid, host)
        val flow = if (shareLink.contains("security=reality")) "xtls-rprx-vision" else ""
        val client = "{\"id\":${quote(uuid)},\"email\":${quote(email)},\"enable\":true,\"flow\":${quote(flow)}}"
        val body = "{\"id\":${inbound.id},\"settings\":${quote("{\"clients\":[$client]}")}}"
        val legacy = request("POST", "$root/panel/api/inbounds/addClient", body, requireSuccess = false)
        if (!legacy.first) error(legacy.third.ifBlank { "This panel does not expose the supported client API" })
        return shareLink
    }

    private fun buildLegacyVlessLink(inbound: PanelInbound, email: String, uuid: String, host: String): String {
        val config = JsonParser.parseString(inbound.raw).asJsonObject
        val stream = config.objectValue("streamSettings")
        val network = stream?.get("network")?.asString ?: "tcp"
        val security = stream?.get("security")?.asString ?: "none"
        val query = mutableListOf("type=${enc(network)}", "encryption=none")
        if (security == "reality") {
            val realitySettings = stream.objectValue("realitySettings")
            val reality = realitySettings?.objectValue("settings")
            val publicKey = reality?.get("publicKey")?.asString.orEmpty()
            val shortId = realitySettings?.getAsJsonArray("shortIds")?.firstOrNull()?.asString.orEmpty()
            val sni = realitySettings?.getAsJsonArray("serverNames")?.firstOrNull()?.asString.orEmpty()
            require(publicKey.isNotBlank()) { "The older panel API did not return a share link. Use a current 3x-ui panel." }
            query += listOf("security=reality", "pbk=${enc(publicKey)}", "sid=${enc(shortId)}", "sni=${enc(sni)}")
        } else if (security == "tls") {
            val sni = stream.objectValue("tlsSettings")?.get("serverName")?.asString.orEmpty()
            query += listOf("security=tls", "sni=${enc(sni)}")
        } else query += "security=none"
        val fragment = enc(email).replace("+", "%20")
        return "vless://$uuid@$host:${inbound.port}?${query.joinToString("&")}#$fragment"
    }

    private fun api(method: String, url: String): com.google.gson.JsonElement {
        val response = request(method, url, null, requireSuccess = true).third
        val json = JsonParser.parseString(response).asJsonObject
        check(json.get("success")?.asBoolean == true) { json.get("msg")?.asString ?: "3x-ui request failed" }
        return json.get("obj") ?: error("Empty 3x-ui response")
    }

    private fun request(method: String, url: String, body: String?, requireSuccess: Boolean): Triple<Boolean, Int, String> {
        val builder = Request.Builder().url(url)
        if (body != null) builder.method(method, body.toRequestBody(jsonType)) else builder.method(method, null)
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val ok = response.isSuccessful && runCatching {
                JsonParser.parseString(text).asJsonObject.get("success")?.asBoolean != false
            }.getOrDefault(response.isSuccessful)
            if (requireSuccess && !ok) error(runCatching {
                JsonParser.parseString(text).asJsonObject.get("msg")?.asString
            }.getOrNull() ?: "3x-ui request failed (${response.code})")
            val message = runCatching {
                JsonParser.parseString(text).asJsonObject.get("msg")?.asString
            }.getOrNull().orEmpty()
            return Triple(ok, response.code, if (message.isBlank()) text else message)
        }
    }

    private fun quote(value: String) = com.google.gson.Gson().toJson(value)
    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun JsonObject?.objectValue(name: String): JsonObject? {
        val value = this?.get(name) ?: return null
        return if (value.isJsonObject) value.asJsonObject else if (value.isJsonPrimitive) {
            runCatching { JsonParser.parseString(value.asString).asJsonObject }.getOrNull()
        } else null
    }
}
