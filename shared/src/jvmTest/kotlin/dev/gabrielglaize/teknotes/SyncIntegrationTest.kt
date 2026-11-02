package dev.gabrielglaize.teknotes

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.gabrielglaize.teknotes.db.TeknotesDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Real round trips against the local Supabase (supabase start). Skipped unless
 * TEKNOTES_SUPABASE_URL and TEKNOTES_SUPABASE_KEY are set; TEKNOTES_MAIL_URL
 * (Mailpit) enables the email code test.
 */
class SyncIntegrationTest {
    private val url = System.getenv("TEKNOTES_SUPABASE_URL")
    private val key = System.getenv("TEKNOTES_SUPABASE_KEY")
    private val mail = System.getenv("TEKNOTES_MAIL_URL")

    private fun phone(): Teknotes {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TeknotesDatabase.Schema.create(driver)
        return Teknotes(driver, SupabaseConfig(url, key), { kotlin.time.Clock.System.now() }, inMemorySession = true)
    }

    private suspend fun Teknotes.waitFor(predicate: (AccountState) -> Boolean) =
        withTimeout(15_000) { account.state.first(predicate) }

    private fun email() = "test-${Random.nextLong(1_000_000_000)}@teknotes.test"

    @Test
    fun aNotebookWrittenOfflineReachesAnotherPhoneOfTheSameAccount() = runBlocking {
        if (url == null || key == null) return@runBlocking
        val email = email()

        // Phone 1: notes a night without an account, then signs up.
        val first = phone()
        first.directory.seedDemoIfEmpty()
        val night = first.notebook.checkIn("29")
        val entry = first.notebook.addEntry(night.id, first.directory.search("Lunatek").first())
        first.notebook.updateEntry(entry, 5, "Montée de malade")
        val created = first.directory.createSoundsystem("Son ${Random.nextInt(100_000)}", listOf("Alias"), "13", listOf(Style.ACID), emptyList())
        first.notebook.addEntry(night.id, DirectoryHit(created.id, DirectoryHit.Kind.SOUNDSYSTEM, created.name, null, 100))

        first.account.signUp(email, "mot-de-passe-solide")
        first.waitFor { it is AccountState.NeedsPseudo }
        first.account.choosePseudo("Testeur${Random.nextInt(100_000)}")
        first.waitFor { it is AccountState.SignedIn }
        first.sync.syncNow()
        assertEquals(0, first.sync.state.value.pending, "everything was sent")

        // Phone 2: a fresh install signs in and gets the notebook and the new soundsystem.
        val second = phone()
        second.account.signIn(email, "mot-de-passe-solide")
        second.waitFor { it is AccountState.SignedIn }
        second.sync.syncNow()

        val nights = second.notebook.nights().first()
        assertEquals(listOf(night.date), nights.map { it.date })
        assertEquals("29", nights.single().department)
        val entries = second.notebook.entries(night.id).first()
        assertEquals(setOf("Lunatek", created.name), entries.map { it.label }.toSet())
        assertEquals(5, entries.first { it.label == "Lunatek" }.rating)
        assertEquals(listOf("Alias"), second.directory.soundsystem(created.id)?.aliases)

        // Deleting the account removes it from the server; the phone keeps its notebook.
        second.account.deleteAccount()
        second.waitFor { it is AccountState.SignedOut }
        assertEquals(1, second.notebook.nights().first().size)
        val third = phone()
        assertFailsWith<TeknotesException> { third.account.signIn(email, "mot-de-passe-solide") }
    }

    @Test
    fun wrongPasswordsAndTakenPseudosGetAPlainMessage() = runBlocking {
        if (url == null || key == null) return@runBlocking
        val app = phone()
        val wrong = assertFailsWith<TeknotesException> { app.account.signIn(email(), "pas-le-bon") }
        assertEquals("Email ou mot de passe incorrect.", wrong.message)

        val pseudo = "Pris${Random.nextInt(100_000)}"
        app.account.signUp(email(), "mot-de-passe-solide")
        app.waitFor { it is AccountState.NeedsPseudo }
        app.account.choosePseudo(pseudo)
        val other = phone()
        other.account.signUp(email(), "mot-de-passe-solide")
        other.waitFor { it is AccountState.NeedsPseudo }
        val taken = assertFailsWith<TeknotesException> { other.account.choosePseudo(pseudo.uppercase()) }
        assertEquals("Ce pseudo est déjà pris.", taken.message)
    }

    @Test
    fun aCodeReceivedByEmailSignsIn() = runBlocking {
        if (url == null || key == null || mail == null) return@runBlocking
        val email = email()
        val app = phone()
        app.account.sendCode(email)

        val code = withTimeout(15_000) {
            var found: String? = null
            while (found == null) {
                found = latestCode(email)
                if (found == null) kotlinx.coroutines.delay(500)
            }
            found
        }
        app.account.verifyCode(email, code)
        assertTrue(app.waitFor { it is AccountState.NeedsPseudo } is AccountState.NeedsPseudo)
    }

    /** Reads the last code sent to [email] from Mailpit. */
    private fun latestCode(email: String): String? {
        val http = HttpClient.newHttpClient()
        val list = http.send(
            HttpRequest.newBuilder(URI("$mail/api/v1/search?query=to:${email}")).build(),
            HttpResponse.BodyHandlers.ofString(),
        ).body()
        val messages = Json.parseToJsonElement(list).jsonObject["messages"] as? JsonArray ?: return null
        val id = messages.firstOrNull()?.jsonObject?.get("ID")?.jsonPrimitive?.content ?: return null
        val body = http.send(HttpRequest.newBuilder(URI("$mail/api/v1/message/$id")).build(), HttpResponse.BodyHandlers.ofString()).body()
        // Read the decoded text: in the raw JSON the code follows a "\n", which hides its word boundary.
        val text = Json.parseToJsonElement(body).jsonObject["Text"]?.jsonPrimitive?.content ?: return null
        return Regex("""\b(\d{6})\b""").find(text)?.groupValues?.get(1)
    }
}
