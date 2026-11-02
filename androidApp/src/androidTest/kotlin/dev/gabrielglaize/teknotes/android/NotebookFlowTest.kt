package dev.gabrielglaize.teknotes.android

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

/** Real paths through the app, on an emulator. */
class NotebookFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun aNightIsNotedInTwoTapsEvenWithATypo() {
        compose.onNodeWithText("J'y suis").performClick()
        waitForText("Soundsystem ou artiste entendu")
        compose.onNodeWithText("Soundsystem ou artiste entendu").performTextInput("kaisson")
        waitForText("Caisson Noir")
        // Semantics click: injecting a touch fails while the keyboard window is animating in.
        compose.onNodeWithText("Caisson Noir").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Un mot sur ce set")
        compose.onAllNodesWithText("Caisson Noir")[0].assertIsDisplayed()
    }

    @Test
    fun aDuplicateIsSuggestedBeforeCreating() {
        compose.onNodeWithText("Annuaire").performClick()
        waitForText("Ajouter un soundsystem", contains = true)
        compose.onNode(androidx.compose.ui.test.hasText("Ajouter un soundsystem", substring = true)).performClick()
        waitForText("Nom")
        compose.onNodeWithText("Nom").performTextInput("Teknokrate")
        waitForText("Teknokrates")
        compose.onNodeWithText("Ça existe peut-être déjà").assertIsDisplayed()
    }

    private fun waitForText(text: String, contains: Boolean) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = contains).fetchSemanticsNodes().isNotEmpty() }
}
