package dev.gabrielglaize.teknotes.android.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val MicIcon: ImageVector by lazy {
    ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 14f)
            curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
            verticalLineTo(5f)
            curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
            reflectiveCurveTo(9f, 3.34f, 9f, 5f)
            verticalLineToRelative(6f)
            curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
            close()
            moveTo(17f, 11f)
            curveToRelative(0f, 2.76f, -2.24f, 5f, -5f, 5f)
            reflectiveCurveToRelative(-5f, -2.24f, -5f, -5f)
            horizontalLineTo(5f)
            curveToRelative(0f, 3.53f, 2.61f, 6.43f, 6f, 6.92f)
            verticalLineTo(21f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-3.08f)
            curveToRelative(3.39f, -0.49f, 6f, -3.39f, 6f, -6.92f)
            horizontalLineToRelative(-2f)
            close()
        }
    }.build()
}

/**
 * Dictation for noisy, one-handed moments, using the phone's own speech recognition
 * screen: no microphone permission in the app. Hidden if the phone has none.
 */
@Composable
fun DictationButton(onText: (String) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
        }
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
    if (intent.resolveActivity(context.packageManager) == null) return
    IconButton(onClick = { launcher.launch(intent) }) { Icon(MicIcon, contentDescription = "Dicter") }
}
