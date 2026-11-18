package app.childrensimulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.childrensimulator.sim.engine.Mouth
import app.childrensimulator.sim.engine.Pose
import app.childrensimulator.sim.model.Caregiver
import app.childrensimulator.sim.model.FeedingRoute
import app.childrensimulator.sim.model.LogEntry
import app.childrensimulator.sim.model.LogType
import app.childrensimulator.sim.model.Place
import app.childrensimulator.sim.model.SessionKind
import app.childrensimulator.sim.model.Technique
import java.time.Instant
import java.time.ZoneId

/**
 * Moments: snapshots collected from real events in the care log, like postcards in a pet game. Locked moments show
 * what is still ahead. They describe the run and never score it (docs/10 §6.4).
 */
private data class MomentDef(
    val title: String,
    val match: (LogEntry) -> Boolean,
    val scene: (skin: Int, route: FeedingRoute) -> BabyVisual,
)

private fun pose(
    asleep: Boolean = false,
    mouth: Mouth = Mouth.NEUTRAL,
    eyesOpen: Boolean = !asleep,
    onTummy: Boolean = false,
    redFace: Float = 0f,
) = Pose(asleep, eyesOpen, mouth, false, false, 0f, false, redFace, false, false, onTummy)

private val mine = { e: LogEntry -> e.who == Caregiver.PLAYER }

private val MOMENTS = listOf(
    MomentDef("Hello, little one", { it.type == LogType.RUN_STARTED }) { skin, _ ->
        BabyVisual(pose(mouth = Mouth.SMILE), Place.ARMS, Caregiver.PLAYER, emptySet(), skin, room = true, hour = 10f)
    },
    MomentDef("First feed together", { it.type == LogType.SESSION_ENDED && mine(it) && (it.tag == SessionKind.BOTTLE_FEED.name || it.tag == SessionKind.BREASTFEED.name) }) { skin, route ->
        BabyVisual(
            pose(mouth = Mouth.SUCKING), Place.ARMS, Caregiver.PLAYER, emptySet(), skin, room = true, hour = 14f,
            activity = if (route == FeedingRoute.FORMULA) BabyActivity.BOTTLE_FEED else BabyActivity.BREASTFEED,
        )
    },
    MomentDef("A good burp", { it.type == LogType.NEED_RESOLVED && it.tag == "GAS" && mine(it) }) { skin, _ ->
        BabyVisual(pose(mouth = Mouth.SMILE), Place.ARMS, Caregiver.PLAYER, emptySet(), skin, room = true, hour = 15f, activity = BabyActivity.BURP)
    },
    MomentDef("First diaper change", { it.type == LogType.CARE_DONE && it.tag == "change" && mine(it) }) { skin, _ ->
        BabyVisual(pose(), Place.CHANGING_TABLE, null, emptySet(), skin, room = true, hour = 11f, activity = BabyActivity.DIAPER_FRESH)
    },
    MomentDef("Asleep in your arms", { it.type == LogType.ASLEEP && mine(it) && it.tag == Place.ARMS.name }) { skin, _ ->
        BabyVisual(pose(asleep = true), Place.ARMS, Caregiver.PLAYER, setOf(Technique.ROCK), skin, room = true, hour = 16f)
    },
    MomentDef("Safe and sound", { (it.type == LogType.ASLEEP && it.who != Caregiver.HELPER && it.tag == Place.BASSINET.name) || (it.type == LogType.CARE_DONE && it.tag == "transfer" && mine(it)) }) { skin, _ ->
        BabyVisual(pose(asleep = true), Place.BASSINET, null, emptySet(), skin, room = true, hour = 13f)
    },
    MomentDef("Face to face", { it.type == LogType.SESSION_ENDED && mine(it) && it.tag == SessionKind.PLAY_TALK.name }) { skin, _ ->
        BabyVisual(pose(mouth = Mouth.SMILE), Place.PLAY_MAT, null, emptySet(), skin, room = true, hour = 10f, activity = BabyActivity.PLAY)
    },
    MomentDef("Tummy time", { it.type == LogType.SESSION_ENDED && mine(it) && it.tag == SessionKind.TUMMY_TIME.name }) { skin, _ ->
        BabyVisual(pose(onTummy = true), Place.PLAY_MAT, null, emptySet(), skin, room = true, hour = 11f, activity = BabyActivity.TUMMY)
    },
    MomentDef("A night wake", { it.type == LogType.SESSION_STARTED && mine(it) && it.night }) { skin, _ ->
        BabyVisual(pose(), Place.ARMS, Caregiver.PLAYER, setOf(Technique.CALM_ROOM), skin, night = true, room = true, hour = 3f)
    },
    MomentDef("A long evening", { it.type == LogType.SCENARIO && it.text.startsWith("Evening:") }) { skin, _ ->
        BabyVisual(pose(mouth = Mouth.FUSS, redFace = 0.2f), Place.ARMS, Caregiver.PLAYER, setOf(Technique.ROCK, Technique.SHUSH), skin, room = true, hour = 19f)
    },
    MomentDef("Asking for help", { it.type == LogType.HANDOFF && mine(it) && !it.text.startsWith("You took over") }) { skin, _ ->
        BabyVisual(pose(asleep = true), Place.ARMS, Caregiver.HELPER, emptySet(), skin, room = true, hour = 21f)
    },
    MomentDef("48 hours together", { it.type == LogType.RUN_ENDED }) { skin, _ ->
        BabyVisual(pose(asleep = true), Place.BASSINET, null, emptySet(), skin, room = true, hour = 9f)
    },
)

private data class Moment(val def: MomentDef, val at: LogEntry?)

private fun dayAndTime(start: Long?, wall: Long): String {
    val z = Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault())
    val day = if (start == null) 1 else ((wall - start) / 86_400_000L + 1).toInt()
    return "Day $day · %02d:%02d".format(z.hour, z.minute)
}

@Composable
fun MomentsSection(log: List<LogEntry>, skinTone: Int, route: FeedingRoute) {
    val moments = remember(log.size) { MOMENTS.map { def -> Moment(def, log.firstOrNull(def.match)) } }
    val start = log.firstOrNull { it.type == LogType.RUN_STARTED }?.wall
    val found = moments.count { it.at != null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Title("Moments together · $found of ${moments.size}")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(moments.withIndex().toList(), key = { it.index }) { (i, m) ->
                Polaroid(m, skinTone, route, start, tilt = if (i % 2 == 0) -2f else 2f)
            }
        }
    }
}

@Composable
private fun Polaroid(m: Moment, skin: Int, route: FeedingRoute, start: Long?, tilt: Float) {
    val unlocked = m.at != null
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFFFFCF6),
        shadowElevation = 3.dp,
        modifier = Modifier.width(150.dp).padding(vertical = 6.dp).rotate(tilt),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                if (unlocked) {
                    val visual = remember(skin, route) { m.def.scene(skin, route) }
                    BabyCanvas(visual, m.def.title, reducedMotion = true, modifier = Modifier.fillMaxWidth().height(118.dp))
                } else {
                    Surface(color = Color(0xFFE9E1D8), modifier = Modifier.fillMaxWidth().height(118.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("?", style = MaterialTheme.typography.displaySmall, color = Color(0xFFB4A89C), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text(
                if (unlocked) m.def.title else "Still ahead",
                style = MaterialTheme.typography.labelLarge, color = Color(0xFF4A3B35), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                m.at?.let { dayAndTime(start, it.wall) } ?: " ",
                style = MaterialTheme.typography.labelSmall, color = Color(0xFF8A7B70),
            )
        }
    }
}
