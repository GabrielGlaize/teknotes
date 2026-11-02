package dev.gabrielglaize.teknotes

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the account stands. A notebook works in every state; contributing needs [SignedIn]. */
sealed interface AccountState {
    /** No server configured (tests, offline build). */
    data object Unavailable : AccountState

    data object Loading : AccountState

    data object SignedOut : AccountState

    /** Signed in, but the pseudo shown to others isn't chosen yet. */
    data class NeedsPseudo(val email: String) : AccountState

    data class SignedIn(val email: String, val pseudo: String) : AccountState
}

/**
 * Email account: password, or a 6-digit code received by email (no link to open,
 * so it works the same on both phones). Apple and Google come once the developer
 * accounts exist.
 */
class Account internal constructor(
    private val remote: SupabaseClient?,
    private val scope: CoroutineScope,
    private val onSignedIn: () -> Unit,
    private val onSignedOut: () -> Unit,
) {
    private val mutableState = MutableStateFlow<AccountState>(if (remote == null) AccountState.Unavailable else AccountState.Loading)
    val state: StateFlow<AccountState> = mutableState.asStateFlow()

    init {
        remote?.let { client ->
            scope.launch {
                client.auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> refreshProfile()
                        is SessionStatus.NotAuthenticated -> {
                            onSignedOut()
                            mutableState.value = AccountState.SignedOut
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    /** Signed-in user id, when there is one. */
    internal fun userId(): String? = remote?.auth?.currentUserOrNull()?.id

    internal val canContribute: Boolean get() = state.value is AccountState.SignedIn

    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun signUp(email: String, password: String) = guard {
        client().auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun signIn(email: String, password: String) = guard {
        client().auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /** Sends a 6-digit code by email; creates the account if needed. */
    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun sendCode(email: String) = guard {
        client().auth.signInWith(OTP) {
            this.email = email.trim()
            createUser = true
        }
    }

    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun verifyCode(email: String, code: String) = guard {
        client().auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email.trim(), token = code.trim())
    }

    /** The only public information about a contributor. */
    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun choosePseudo(pseudo: String) = guard {
        val clean = pseudo.trim()
        if (clean.length !in 2..24) throw TeknotesException("Le pseudo fait entre 2 et 24 caractères.")
        val id = userId() ?: throw TeknotesException("Connecte-toi d'abord.")
        client().from("profiles").insert(ProfileRow(id, clean))
        refreshProfile()
    }

    /** Signs out. The notebook stays on the phone. */
    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun signOut() = guard { client().auth.signOut() }

    /**
     * Deletes the account and everything sent to the server with it (store requirement).
     * The notebook stays on the phone; entries added to the directory stay, without author.
     */
    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun deleteAccount() = guard {
        client().functions.invoke("delete-account")
        client().auth.signOut()
    }

    // For Swift: plain callbacks instead of Flows.
    fun watchState(onChange: (AccountState) -> Unit): Watcher = state.watch(onChange)

    private suspend fun refreshProfile() {
        val client = remote ?: return
        val user = client.auth.currentUserOrNull() ?: return
        val email = user.email.orEmpty()
        val profile = runCatching {
            client.from("profiles").select { filter { eq("id", user.id) } }.decodeSingleOrNull<ProfileRow>()
        }.getOrNull()
        val wasSignedIn = state.value is AccountState.SignedIn
        mutableState.value = if (profile == null) AccountState.NeedsPseudo(email) else AccountState.SignedIn(email, profile.pseudo)
        if (!wasSignedIn && profile != null) onSignedIn()
    }

    private fun client(): SupabaseClient = remote ?: throw TeknotesException("Le serveur n'est pas configuré.")

    private suspend fun guard(block: suspend () -> Unit) = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: TeknotesException) {
        throw error
    } catch (error: Exception) {
        throw TeknotesException(friendlyMessage(error), error)
    }
}

/** Server errors in plain French. */
internal fun friendlyMessage(error: Throwable): String {
    val text = (error.message.orEmpty() + " " + ((error as? RestException)?.error.orEmpty())).lowercase()
    return when {
        error is HttpRequestException -> "Pas de réseau. Réessaie quand tu captes."
        // Business errors raised by the server's functions are already written for the user.
        error is PostgrestRestException && error.code == "P0001" -> error.error
        "invalid login credentials" in text || "invalid_credentials" in text -> "Email ou mot de passe incorrect."
        "already registered" in text || "user_already_exists" in text -> "Un compte existe déjà avec cet email."
        "password" in text && ("at least" in text || "weak" in text) -> "Mot de passe trop court : 6 caractères minimum."
        "expired" in text || "invalid" in text && "otp" in text || "token" in text && "invalid" in text -> "Code incorrect ou expiré."
        "profiles_pseudo_unique" in text -> "Ce pseudo est déjà pris."
        "rate limit" in text || "too many" in text -> "Trop d'essais. Attends un peu avant de recommencer."
        else -> "Le serveur n'a pas répondu comme prévu. Réessaie plus tard."
    }
}
