package no.mwm.yoda

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import no.mwm.yoda.engine.llm.OnDeviceModelEngine
import no.mwm.yoda.engine.rule.RuleEngine
import no.mwm.yoda.ui.YodaScreen
import no.mwm.yoda.ui.YodaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rule = RuleEngine()
        val llm = OnDeviceModelEngine(applicationContext)

        setContent {
            YodaTheme {
                YodaScreen(
                    engine = rule,
                    llmEngine = llm,
                    initialText = incomingText(intent)
                )
            }
        }
    }

    /** Text handed over by a share sheet or the text-selection menu. */
    private fun incomingText(intent: Intent?): String = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        Intent.ACTION_PROCESS_TEXT ->
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        else -> ""
    }
}
