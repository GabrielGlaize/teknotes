package dev.gabrielglaize.teknotes.android

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

/** Real paths through the app, on an emulator. The account tests need the local Supabase. */
class NotebookFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, substring: Boolean = false, timeout: Long = 15_000) =
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    // Semantics click: injecting a touch fails while the keyboard window is animating in.
    private fun SemanticsNodeInteraction.click() = performSemanticsAction(SemanticsActions.OnClick)

    private fun clickText(text: String) {
        waitForText(text)
        compose.onNodeWithText(text).click()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasContentDescription(label))

    /** A fresh install starts with the tutorial; the tests go through it without a code. */
    @org.junit.Before
    fun passTheIntro() {
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Passer").fetchSemanticsNodes().isEmpty()) return
        clickText("Passer")
        clickText("Plus tard")
    }

    private fun signUpIfNeeded() {
        clickText("Profil")
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Créer un compte").fetchSemanticsNodes().isEmpty()) return
        clickText("Créer un compte")
        val id = Random.nextInt(1_000_000_000)
        waitForText("Email")
        field("Email").performTextInput("android$id@teknotes.test")
        field("Mot de passe (6 caractères minimum)").performTextInput("mot-de-passe-solide")
        clickText("Créer mon compte")
        waitForText("Pseudo")
        field("Pseudo").performTextInput("Android$id")
        compose.onNodeWithText("Valider").assertIsNotEnabled()
        compose.onAllNodes(isToggleable())[0].click()
        clickText("Valider")
    }

    @Test
    fun aNightIsNotedInTwoTapsEvenWithATypo() {
        clickText("J'y suis")
        waitForText("Artiste ou soundsystem")
        field("Artiste ou soundsystem").performTextInput("kaisson")
        waitForText("Rouille (Caisson Noir)")
        compose.onAllNodesWithText("Rouille (Caisson Noir)")[0].click()
        waitForText("Un mot sur ce set")
        compose.onAllNodesWithText("Rouille (Caisson Noir)")[0].assertIsDisplayed()
    }

    @Test
    fun aDuplicateIsSuggestedBeforeCreating() {
        clickText("Annuaire")
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Ajouter un soundsystem")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Ajouter un soundsystem")).click()
        waitForText("Nom")
        field("Nom").performTextInput("Teknokrate")
        waitForText("Teknokrates")
        compose.onNodeWithText("Ça existe peut-être déjà").assertIsDisplayed()
    }

    @Test
    fun anEntryIsEditedAndItsHistoryShown() {
        signUpIfNeeded()
        waitForText("Se déconnecter")
        clickText("Annuaire")
        clickText("Public")
        clickText("Basse Marée")
        waitForText("Modifier la fiche")
        compose.onNodeWithText("Modifier la fiche").performScrollTo().click()

        val alias = "Alias${Random.nextInt(1_000_000)}"
        waitForText("Aussi appelé", substring = true)
        field("Aussi appelé (séparés par des virgules)").performTextInput(alias)
        compose.onNodeWithText("Enregistrer").performScrollTo().click()
        waitForText(alias, substring = true)

        compose.onNodeWithText("Voir l'historique").performScrollTo().click()
        waitForText("Modifié : Aussi appelé", timeout = 20_000)
        compose.onAllNodesWithText("Modifié : Aussi appelé")[0].assertIsDisplayed()
    }

    @Test
    fun aReportOffersItsReasons() {
        signUpIfNeeded()
        waitForText("Se déconnecter")
        clickText("Annuaire")
        clickText("Public")
        clickText("Basse Marée")
        waitForText("Signaler un problème")
        compose.onNodeWithText("Signaler un problème").performScrollTo().click()
        waitForText("Contenu offensant")
    }

    @Test
    fun theTermsAndContactAreReachable() {
        clickText("Profil")
        waitForText("Conditions d'utilisation")
        compose.onNodeWithText("Conditions d'utilisation").performScrollTo().click()
        waitForText("Modération")
    }
}
