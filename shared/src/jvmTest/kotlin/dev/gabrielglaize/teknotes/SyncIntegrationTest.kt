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
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Properties
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

    /**
     * A fresh phone. Its database is a real file, as on a phone: the in-memory driver shares a
     * single connection between threads, which the background sync would trip over.
     */
    private fun phone(): Teknotes {
        val file = File.createTempFile("teknotes-test", ".db").apply { deleteOnExit() }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.path}", Properties().apply { put("busy_timeout", "10000") })
        TeknotesDatabase.Schema.create(driver)
        return Teknotes(driver, SupabaseConfig(url, key), { kotlin.time.Clock.System.now() }, inMemorySession = true)
    }

    private suspend fun Teknotes.waitFor(predicate: (AccountState) -> Boolean) =
        withTimeout(15_000) { account.state.first(predicate) }

    private val PASSWORD = "mot-de-passe-solide"

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

    /** A fresh phone with a signed-up account and a pseudo. */
    private suspend fun signedUp(email: String = email()): Teknotes {
        val app = phone()
        app.account.signUp(email, PASSWORD)
        app.waitFor { it is AccountState.NeedsPseudo }
        app.account.choosePseudo("T${Random.nextInt(100_000_000)}")
        app.waitFor { it is AccountState.SignedIn }
        return app
    }

    @Test
    fun anEntryIsEditedLikeAWikiAndCanBeRestored() = runBlocking {
        if (url == null || key == null) return@runBlocking
        val author = signedUp()
        val first = "Wiki ${Random.nextInt(1_000_000)}"
        val entry = author.directory.createSoundsystem(first, emptyList(), "59", listOf(Style.TRIBE, Style.ACID), emptyList())
        author.sync.syncNow()

        val editor = signedUp()
        editor.sync.syncNow()
        // The styles only change order: not a change worth showing.
        editor.directory.updateSoundsystem(entry.id, "$first bis", listOf("Alias"), "59", listOf(Style.ACID, Style.TRIBE), emptyList())
        editor.sync.syncNow()

        author.sync.syncNow()
        assertEquals("$first bis", author.directory.soundsystem(entry.id)?.name, "the edit reaches everyone")

        val history = author.community.history(DirectoryHit.Kind.SOUNDSYSTEM, entry.id)
        assertEquals(2, history.size)
        assertEquals(listOf("Nom", "Aussi appelé"), history.first().changes)
        assertTrue(history.first().authorPseudo!!.startsWith("T"))
        assertEquals(emptyList(), history.last().changes, "the first version changed nothing")

        author.community.restore(DirectoryHit.Kind.SOUNDSYSTEM, entry.id, history.last())
        author.sync.syncNow()
        editor.sync.syncNow()
        assertEquals(first, editor.directory.soundsystem(entry.id)?.name, "the old version is back")
        assertEquals(3, editor.community.history(DirectoryHit.Kind.SOUNDSYSTEM, entry.id).size, "and nothing was lost")
    }

    @Test
    fun threePeopleMergeADuplicate() = runBlocking {
        if (url == null || key == null) return@runBlocking
        val a = signedUp()
        val tag = Random.nextInt(1_000_000)
        val kept = a.directory.createSoundsystem("Gardé $tag", emptyList(), null, listOf(Style.ACID), emptyList())
        val copy = a.directory.createSoundsystem("Copie $tag", listOf("Double $tag"), "29", listOf(Style.DUB), emptyList())
        val night = a.notebook.checkIn(null)
        a.notebook.addEntry(night.id, DirectoryHit(copy.id, DirectoryHit.Kind.SOUNDSYSTEM, copy.name, null, 100))
        a.sync.syncNow()

        a.community.proposeMerge(DirectoryHit.Kind.SOUNDSYSTEM, kept.id, copy.id)
        val alreadyProposed = assertFailsWith<TeknotesException> { a.community.proposeMerge(DirectoryHit.Kind.SOUNDSYSTEM, copy.id, kept.id) }
        assertEquals("Cette fusion est déjà proposée.", alreadyProposed.message)

        val b = signedUp()
        b.sync.syncNow()
        val open = b.community.mergeProposals(DirectoryHit.Kind.SOUNDSYSTEM, kept.id).single()
        assertEquals(copy.name, open.otherName)
        assertTrue(open.keepsThisEntry)
        assertEquals(1, open.approvals)
        assertEquals(false, open.approvedByMe)
        assertEquals(false, b.community.approveMerge(open.id))

        val c = signedUp()
        c.sync.syncNow()
        assertEquals(true, c.community.approveMerge(open.id))

        a.sync.syncNow()
        assertEquals(null, a.directory.soundsystem(copy.id), "the copy left the directory")
        val merged = a.directory.soundsystem(kept.id)!!
        assertEquals("29", merged.department)
        assertEquals(setOf(copy.name, "Double $tag"), merged.aliases.toSet())
        assertEquals(setOf(Style.ACID, Style.DUB), merged.styles.toSet())
        assertEquals(kept.id, a.notebook.entries(night.id).first().single().soundsystemId, "notebooks follow the merge")
        assertEquals(kept.name, a.notebook.entries(night.id).first().single().label)
        assertTrue(a.community.mergeProposals(DirectoryHit.Kind.SOUNDSYSTEM, kept.id).isEmpty())
    }

    @Test
    fun threeReportsHideAnEntryAndOneOnlyCountsOnce() = runBlocking {
        if (url == null || key == null) return@runBlocking
        val a = signedUp()
        val entry = a.directory.createArtist("Abus ${Random.nextInt(1_000_000)}", emptyList(), emptyList(), emptyList(), emptyList())
        a.sync.syncNow()

        a.community.report(DirectoryHit.Kind.ARTIST, entry.id, ReportReason.OFFENSIVE)
        val twice = assertFailsWith<TeknotesException> { a.community.report(DirectoryHit.Kind.ARTIST, entry.id, ReportReason.OTHER) }
        assertEquals("Tu as déjà signalé cette fiche.", twice.message)
        signedUp().community.report(DirectoryHit.Kind.ARTIST, entry.id, ReportReason.FALSE)
        a.sync.syncNow()
        assertTrue(a.directory.artist(entry.id) != null, "two reports aren't enough")
        signedUp().community.report(DirectoryHit.Kind.ARTIST, entry.id, ReportReason.SPAM)
        a.sync.syncNow()
        assertEquals(null, a.directory.artist(entry.id), "hidden for everyone after the third")
    }

    @Test
    fun aSetKeepsItsArtistAndSoundsystemOnAnotherPhone() = runBlocking {
        if (url == null || key == null) return@runBlocking
        val a = signedUp()
        val soundsystem = a.directory.createSoundsystem("Son ${Random.nextInt(1_000_000)}", emptyList(), null, listOf(Style.fromLabel("Hard groove")!!), emptyList())
        val artist = a.directory.createArtist("Artiste ${Random.nextInt(1_000_000)}", emptyList(), emptyList(), emptyList(), listOf(soundsystem.id))
        val night = a.notebook.createNight("2026-02-14", null, "")
        a.notebook.addSet(night.id, SetChoice(artist.id, artist.name, soundsystem.id, soundsystem.name))
        a.sync.syncNow()
        assertEquals(null, a.sync.state.value.error, "the server accepts a set and a new style")

        a.sync.syncNow()
        assertEquals(listOf("Hard groove"), a.directory.soundsystem(soundsystem.id)?.styles?.map { it.label }, "the new style comes back from the server")
    }

    @Test
    fun sharedNightsShowOnTheEntryUntilThePersonIsBlocked() = runBlocking {
        if (url == null || key == null) return@runBlocking
        val a = signedUp()
        val entry = a.directory.createSoundsystem("Vu ${Random.nextInt(1_000_000)}", emptyList(), null, emptyList(), emptyList())
        val hit = DirectoryHit(entry.id, DirectoryHit.Kind.SOUNDSYSTEM, entry.name, null, 100)
        val past = a.notebook.createNight("2026-01-10", "59", "")
        a.notebook.addEntry(past.id, hit)
        a.notebook.updateNight(past.id, "59", "", "", shared = true)
        val secret = a.notebook.createNight("2026-01-11", "62", "")
        a.notebook.addEntry(secret.id, hit)
        val tonight = a.notebook.createNight("2099-01-01", "75", "")
        a.notebook.addEntry(tonight.id, hit)
        a.notebook.updateNight(tonight.id, "75", "", "", shared = true)
        a.sync.syncNow()
        val author = a.community.sightings(DirectoryHit.Kind.SOUNDSYSTEM, entry.id)
        assertTrue(author.isEmpty(), "your own nights aren't listed back to you")

        val b = signedUp()
        b.sync.syncNow()
        val seen = b.community.sightings(DirectoryHit.Kind.SOUNDSYSTEM, entry.id).single()
        assertEquals("2026-01-10", seen.date, "only shared nights that are over")
        assertEquals("59", seen.department)

        b.community.block(seen.userId)
        assertEquals(listOf(seen.pseudo), b.community.blockedUsers().map { it.pseudo })
        assertTrue(b.community.sightings(DirectoryHit.Kind.SOUNDSYSTEM, entry.id).isEmpty(), "blocked")
        assertTrue(b.community.history(DirectoryHit.Kind.SOUNDSYSTEM, entry.id).isEmpty(), "their edits disappear too")

        b.community.unblock(seen.userId)
        assertEquals(1, b.community.sightings(DirectoryHit.Kind.SOUNDSYSTEM, entry.id).size)
        assertTrue(b.community.blockedUsers().isEmpty())
    }

    @Test
    fun aDeletionFollowsToTheOtherPhoneButSigningOutKeepsTheNotebook() = runBlocking {
        if (url == null || key == null) return@runBlocking
        val email = email()
        val first = signedUp(email)
        val kept = first.notebook.createNight("2026-02-01", "59", "")
        val gone = first.notebook.createNight("2026-02-02", "59", "")
        first.sync.syncNow()

        val second = phone()
        second.account.signIn(email, PASSWORD)
        second.waitFor { it is AccountState.SignedIn }
        second.sync.syncNow()
        assertEquals(2, second.notebook.nights().first().size)

        first.notebook.deleteNight(gone.id)
        first.sync.syncNow()
        second.sync.syncNow()
        assertEquals(listOf(kept.id), second.notebook.nights().first().map { it.id }, "deleted on the other phone")

        second.account.signOut()
        second.waitFor { it is AccountState.SignedOut }
        assertEquals(1, second.notebook.nights().first().size, "the notebook stays after signing out")

        // Someone else signs in on the same phone: nothing is deleted, the notebook joins their account.
        second.account.signUp(email(), PASSWORD)
        second.waitFor { it is AccountState.NeedsPseudo }
        second.account.choosePseudo("T${Random.nextInt(100_000_000)}")
        second.waitFor { it is AccountState.SignedIn }
        second.sync.syncNow()
        assertEquals(1, second.notebook.nights().first().size)
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
