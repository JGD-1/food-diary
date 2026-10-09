package nl.guido.foodtracker.feature.sync

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import nl.guido.foodtracker.feature.sync.engine.RejectedRowException
import nl.guido.foodtracker.feature.sync.engine.SyncTables
import nl.guido.foodtracker.feature.sync.remote.Http
import nl.guido.foodtracker.feature.sync.remote.HttpRequest
import nl.guido.foodtracker.feature.sync.remote.HttpResponse
import nl.guido.foodtracker.feature.sync.remote.SignedOutException
import nl.guido.foodtracker.feature.sync.remote.SupabaseApi
import nl.guido.foodtracker.feature.sync.remote.SupabaseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHttp(var response: HttpResponse) : Http {
    val sent = mutableListOf<HttpRequest>()
    override suspend fun send(request: HttpRequest): HttpResponse = response.also { sent += request }
}

class SupabaseApiTest {
    private val http = FakeHttp(HttpResponse(200, "[]"))
    private val api = SupabaseApi(SupabaseConfig("https://abc.supabase.co", "pk"), http)

    @Test
    fun `sign-in address asks for Google with the proof key`() {
        val url = api.googleSignInUrl("nl.guido.foodtracker://auth-callback", "CHALLENGE")
        assertEquals(
            "https://abc.supabase.co/auth/v1/authorize?provider=google" +
                "&redirect_to=nl.guido.foodtracker%3A%2F%2Fauth-callback&code_challenge=CHALLENGE&code_challenge_method=s256",
            url,
        )
    }

    @Test
    fun `code exchange returns tokens and the user`() = runTest {
        http.response = HttpResponse(
            200,
            """{"access_token":"a","refresh_token":"r","expires_in":3600,"token_type":"bearer",
               "user":{"id":"u1","email":"g@example.com","user_metadata":{"full_name":"Guido"}}}""",
        )
        val tokens = api.exchangeCode("code", "verifier")
        assertEquals("u1", tokens.user!!.id)
        assertEquals("https://abc.supabase.co/auth/v1/token?grant_type=pkce", http.sent.single().url)
        assertTrue(http.sent.single().body!!.contains("\"code_verifier\":\"verifier\""))
    }

    @Test(expected = SignedOutException::class)
    fun `a refused refresh means signed out`() = runTest {
        http.response = HttpResponse(400, """{"error":"invalid_grant"}""")
        api.refresh("old")
    }

    @Test
    fun `household info is read from the function result`() = runTest {
        http.response = HttpResponse(200, """[{"household_id":"h1","invite_code":"AB12CD34","member_count":2}]""")
        val info = api.ensureHousehold("token")
        assertEquals("AB12CD34", info.inviteCode)
        assertEquals("Bearer token", http.sent.single().headers["Authorization"])
    }

    @Test
    fun `upsert merges on id and a 400 means the rows were refused`() = runTest {
        api.upsert("t", SyncTables.weighIn, listOf(JsonObject(mapOf("id" to JsonPrimitive("w1")))))
        val sent = http.sent.single()
        assertEquals("https://abc.supabase.co/rest/v1/weigh_ins?on_conflict=id", sent.url)
        assertEquals("resolution=merge-duplicates,return=minimal", sent.headers["Prefer"])
        http.response = HttpResponse(403, "denied")
        var refused = false
        try { api.upsert("t", SyncTables.weighIn, emptyList()) } catch (e: RejectedRowException) { refused = true }
        assertTrue(refused)
    }

    @Test
    fun `changes are fetched after the last server time`() = runTest {
        api.changedAfter("t", SyncTables.recipe, "2026-10-09T10:00:00.5+00:00", 200)
        assertEquals(
            "https://abc.supabase.co/rest/v1/recipes?select=*&server_updated_at=gt.2026-10-09T10%3A00%3A00.5%2B00%3A00" +
                "&order=server_updated_at.asc,id.asc&limit=200",
            http.sent.single().url,
        )
    }
}
