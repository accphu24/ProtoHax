package dev.sora.relay.session.listener.xbox

import coelho.msftauth.api.oauth20.OAuth20Token
import coelho.msftauth.api.xbox.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.sora.relay.cheat.config.AbstractConfigManager
import dev.sora.relay.session.MinecraftRelaySession
import dev.sora.relay.session.listener.RelayListenerEncryptedSession
import dev.sora.relay.session.listener.xbox.cache.IXboxIdentityTokenCache
import dev.sora.relay.session.listener.xbox.cache.XboxIdentityToken
import dev.sora.relay.utils.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket
import java.io.Reader
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RelayListenerXboxLogin(val accessToken: () -> String, val deviceInfo: XboxDeviceInfo) : RelayListenerEncryptedSession() {

    constructor(accessToken: () -> String, deviceInfo: XboxDeviceInfo, session: MinecraftRelaySession) : this(accessToken, deviceInfo) {
        this.session = session
    }

var tokenCache: IXboxIdentityTokenCache? = null

    private var identityToken = XboxIdentityToken("", 0)
        get() {
            if (field.notAfter < System.currentTimeMillis() / 1000) {
                field = tokenCache?.checkCache(deviceInfo)?.also {
                    logInfo("token cache hit")
                } ?: fetchIdentityToken(accessToken(), deviceInfo).also {
                    tokenCache?.let { cache ->
                        logInfo("saving token cache")
                        cache.cache(deviceInfo, it)
                    }
                }
            }

            return field
        }
    private val chain: List<String>
        get() = fetchChain(identityToken.token, keyPair)

    fun forceFetchChain() {
        chain
    }

    override fun onPacketOutbound(packet: BedrockPacket): Boolean {
        if (packet is LoginPacket) {
session.keyPair = keyPair
            try {
                packet.chain.clear()
                packet.chain.addAll(chain)
packet.extra = signJWT(packet.extra.split('.')[1], keyPair, base64Encoded = true)
            } catch (e: Throwable) {
                session.inboundPacket(DisconnectPacket().apply {
                    kickMessage = e.toString()
                })
                logError("login failed", e)
            }
            logInfo("login success")
        }

        return true
    }


    companion object {

/**
 * this key used to sign the post content
 */
val deviceKey = XboxDeviceKey()

        fun fetchIdentityToken(accessToken: String, deviceInfo: XboxDeviceInfo): XboxIdentityToken {
            var userToken: XboxToken? = null
            val userRequestThread = thread {
                userToken = XboxUserAuthRequest(
                    "http://auth.xboxlive.com", "JWT", "RPS",
                    "user.auth.xboxlive.com", "t=$accessToken"
                ).request()
            }
            val deviceToken = XboxDeviceAuthRequest(
                "http://auth.xboxlive.com", "JWT", deviceInfo.deviceType,
                "0.0.0.0", deviceKey
            ).request()

            val device = XboxDevice(deviceKey, deviceToken)

            val codeVerifier = generateCodeVerifier()
            val codeChallenge = codeChallengeS256(codeVerifier)
            val state = UUID.randomUUID().toString()

            val sisuRequest = XboxSISUAuthenticateRequest(
                deviceInfo.appId, device, "service::user.auth.xboxlive.com::MBI_SSL",
                codeChallenge, "S256", state, "RETAIL"
            ).request()

            val titleToken = try {
                XboxSISUAuthorizeRequest(
                    OAuth20Token("bearer", 0L, "", accessToken, "", "", ""),
                    deviceInfo.appId, device, "RETAIL",
                    sisuRequest.sessionId, "user.auth.xboxlive.com"
                ).request().titleToken
            } catch (e: IllegalStateException) {
                throw XboxGamerTagException("https://social.xboxlive.com/setup?sid=${sisuRequest.sessionId}")
            }

            if (userRequestThread.isAlive)
                userRequestThread.join()
            if (userToken == null) error("failed to fetch xbox user token")
            val xstsToken = XboxXSTSAuthRequest(
                "https://multiplayer.minecraft.net/",
                "JWT",
                "RETAIL",
                listOf(userToken),
                titleToken,
                device
            ).request()

            return XboxIdentityToken(xstsToken.toIdentityToken(), Instant.parse(xstsToken.notAfter).epochSecond)
        }

        private fun generateCodeVerifier(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        private fun codeChallengeS256(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        fun fetchRawChain(identityToken: String, publicKey: PublicKey): Reader {
            val data = JsonObject().apply {
                addProperty("identityPublicKey", Base64.getEncoder().withoutPadding().encodeToString(publicKey.encoded))
            }
val request = Request.Builder()
.url("https://multiplayer.minecraft.net/authentication")
.post(AbstractConfigManager.DEFAULT_GSON.toJson(data).toRequestBody("application/json".toMediaType()))
.header("Client-Version", "1.20.10")
.header("Authorization", identityToken)
.build()
val response = HttpUtils.client.newCall(request).execute()

assert(response.code == 200) { "Http code ${response.code}" }

return response.body!!.charStream()
        }

        fun fetchChain(identityToken: String, keyPair: KeyPair): List<String> {
            val rawChain = JsonParser.parseReader(fetchRawChain(identityToken, keyPair.public)).asJsonObject
            val chains = rawChain.get("chain").asJsonArray

            val identityPubKey = JsonParser.parseString(base64Decode(chains.get(0).asString.split(".")[0]).toString(Charsets.UTF_8)).asJsonObject

            val jwt = signJWT(AbstractConfigManager.DEFAULT_GSON.toJson(JsonObject().apply {
addProperty("certificateAuthority", true)
addProperty("exp", (Instant.now().epochSecond + TimeUnit.HOURS.toSeconds(6)).toInt())
addProperty("nbf", (Instant.now().epochSecond - TimeUnit.HOURS.toSeconds(6)).toInt())
addProperty("identityPublicKey", identityPubKey.get("x5u").asString)
}), keyPair)

            val list = mutableListOf(jwt)
list.addAll(chains.map { it.asString })
            return list
        }
    }
}
