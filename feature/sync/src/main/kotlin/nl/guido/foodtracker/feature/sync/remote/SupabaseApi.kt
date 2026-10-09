package nl.guido.foodtracker.feature.sync.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nl.guido.foodtracker.feature.sync.engine.RejectedRowException
import nl.guido.foodtracker.feature.sync.engine.SyncEngine
import nl.guido.foodtracker.feature.sync.engine.SyncTable
import java.io.IOException
import java.net.URLEncoder

/** Supabase project address and public key (safe to ship in the app; access rules protect the data). */
data class SupabaseConfig(val url: String, val publicKey: String) {
    val isSet: Boolean get() = url.isNotBlank() && publicKey.isNotBlank()
}

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null,
    @SerialName("user_metadata") val metadata: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
)

@Serializable
data class AuthTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val user: AuthUser? = null,
)

@Serializable
data class HouseholdInfo(
    @SerialName("household_id") val householdId: String,
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("member_count") val memberCount: Int,
    /** join_household only: the household whose recipes came along, if any. */
    @SerialName("moved_from") val movedFrom: String? = null,
)

/** The server said no (not a connection problem). [code] is the HTTP status. */
class SupabaseException(val code: Int, message: String) : IOException("Supabase $code: $message")

/** Signed out on the server (refresh token no longer valid). */
class SignedOutException : IOException("signed out")

internal val supabaseJson = Json { ignoreUnknownKeys = true; explicitNulls = true }

/** Plain HTTP calls to Supabase Auth, the REST tables and the household functions. */
class SupabaseApi(private val config: SupabaseConfig, private val http: Http) {

    private fun headers(accessToken: String?) = buildMap {
        put("apikey", config.publicKey)
        if (accessToken != null) put("Authorization", "Bearer $accessToken")
        put("Accept", "application/json")
    }

    /** Address to open in the browser to sign in with Google. */
    fun googleSignInUrl(redirect: String, codeChallenge: String): String =
        "${config.url}/auth/v1/authorize?provider=google" +
            "&redirect_to=${enc(redirect)}" +
            "&code_challenge=${enc(codeChallenge)}&code_challenge_method=s256"

    suspend fun exchangeCode(code: String, verifier: String): AuthTokens = auth(
        "pkce",
        buildJsonObject { put("auth_code", code); put("code_verifier", verifier) },
    )

    suspend fun refresh(refreshToken: String): AuthTokens = try {
        auth("refresh_token", buildJsonObject { put("refresh_token", refreshToken) })
    } catch (e: SupabaseException) {
        if (e.code in 400..499) throw SignedOutException() else throw e
    }

    private suspend fun auth(grant: String, body: JsonObject): AuthTokens {
        val response = http.send(
            HttpRequest("POST", "${config.url}/auth/v1/token?grant_type=$grant", headers(null), body.toString()),
        )
        return supabaseJson.decodeFromString(AuthTokens.serializer(), response.ok())
    }

    suspend fun ensureHousehold(accessToken: String): HouseholdInfo = rpc(accessToken, "ensure_household", JsonObject(emptyMap()))

    suspend fun joinHousehold(accessToken: String, code: String): HouseholdInfo =
        rpc(accessToken, "join_household", buildJsonObject { put("code", code) })

    private suspend fun rpc(accessToken: String, name: String, args: JsonObject): HouseholdInfo {
        val response = http.send(HttpRequest("POST", "${config.url}/rest/v1/rpc/$name", headers(accessToken), args.toString()))
        return supabaseJson.decodeFromString(ListSerializer(HouseholdInfo.serializer()), response.ok()).single()
    }

    suspend fun upsert(accessToken: String, table: SyncTable, rows: List<JsonObject>) {
        val response = http.send(
            HttpRequest(
                "POST",
                "${config.url}/rest/v1/${table.remote}?on_conflict=id",
                headers(accessToken) + ("Prefer" to "resolution=merge-duplicates,return=minimal"),
                JsonArray(rows).toString(),
            ),
        )
        // 400s here mean the server refuses these rows (access rule, bad value); 401 means log in again.
        if (response.code in 400..499 && response.code != 401) throw RejectedRowException(response.body)
        response.ok()
    }

    suspend fun changedAfter(accessToken: String, table: SyncTable, after: String?, limit: Int): List<JsonObject> {
        val time = SyncEngine.SERVER_TIME
        val filter = after?.let { "&$time=gt.${enc(it)}" } ?: ""
        val response = http.send(
            HttpRequest(
                "GET",
                "${config.url}/rest/v1/${table.remote}?select=*$filter&order=$time.asc,id.asc&limit=$limit",
                headers(accessToken),
            ),
        )
        return supabaseJson.decodeFromString(ListSerializer(JsonObject.serializer()), response.ok())
    }

    private fun HttpResponse.ok(): String {
        if (code !in 200..299) throw SupabaseException(code, body.take(300))
        return body
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
