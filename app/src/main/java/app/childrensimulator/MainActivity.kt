package app.childrensimulator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import app.childrensimulator.sim.engine.Command
import app.childrensimulator.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ChildSimApp).container
        handleIntent(intent)
        val systemBars = WindowCompat.getInsetsController(window, window.decorView)
        setContent {
            AppRoot(container) { night ->
                systemBars.isAppearanceLightStatusBars = !night
                systemBars.isAppearanceLightNavigationBars = !night
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A tapped notification opens the current state and records the open (docs/05 §5.2). */
    private fun handleIntent(intent: Intent?) {
        val group = intent?.getStringExtra(EXTRA_GROUP) ?: return
        (application as ChildSimApp).container.repository.send(Command.AlertOpened(group))
        intent.removeExtra(EXTRA_GROUP)
    }

    companion object {
        const val EXTRA_GROUP = "group"
    }
}
