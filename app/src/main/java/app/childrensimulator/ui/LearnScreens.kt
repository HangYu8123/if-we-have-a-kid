package app.childrensimulator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.childrensimulator.data.RunUi
import app.childrensimulator.sim.engine.Coaching
import app.childrensimulator.sim.engine.Command
import app.childrensimulator.sim.engine.PracticeDrill

/** Learn tab: the trial lessons, the Academy modules, practice drills and knowledge checks (docs/09 §1-2). */
@Composable
fun LearnScreen(state: AppState, ui: RunUi) {
    val content = state.content.lessons
    val viewed = remember(ui.log.size) { state.prefs.viewedLessons }
    val seenInRun = remember(ui.log.size) { ui.log.mapNotNull { it.lesson }.toSet() }
    var openModule by rememberSaveable { mutableStateOf(-1) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Learn", style = MaterialTheme.typography.headlineSmall)
        Text(content.review, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Title("Your trial lessons")
        SectionCard {
            content.firstTrial.forEach { m ->
                Text(m.moment, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                m.lessons.forEach { id ->
                    val l = state.content.lesson(id) ?: return@forEach
                    val mark = if (id in seenInRun || id in viewed) "✓ " else "○ "
                    Text(mark + "$id ${l.title}", modifier = Modifier.fillMaxWidth().clickable { state.nav.navigate("lesson/$id") }.padding(vertical = 4.dp))
                }
            }
        }

        Title("Practice Room")
        Text("Short drills with a local clock. No reminders, nothing counts against your trial.", style = MaterialTheme.typography.bodyMedium)
        PracticeDrill.entries.forEach { d ->
            SectionCard(modifier = Modifier.clickable { state.nav.navigate("practice/${d.id}") }) {
                Text(d.title, style = MaterialTheme.typography.titleMedium)
                Text(d.summary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Title("Knowledge check")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val (preRight, preTotal) = state.content.score(ui.quizPre, post = false)
            val (postRight, postTotal) = state.content.score(ui.quizPost, post = true)
            Button(onClick = { state.nav.navigate("quiz/pre") }) { Text(if (ui.quizPre.isEmpty()) "Before the trial" else "Before: $preRight/$preTotal") }
            Button(onClick = { state.nav.navigate("quiz/post") }) { Text(if (ui.quizPost.isEmpty()) "After the trial" else "After: $postRight/$postTotal") }
        }

        Title("Academy")
        content.modules.forEach { m ->
            SectionCard(modifier = Modifier.clickable { openModule = if (openModule == m.number) -1 else m.number }) {
                Text("Module ${m.number} · ${m.title}", style = MaterialTheme.typography.titleMedium)
                Text("Unlocks: ${m.unlocks}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (openModule == m.number) {
                    content.lessons.filter { it.module == m.number }.forEach { l ->
                        Text("${l.id} ${l.title}", modifier = Modifier.fillMaxWidth().clickable { state.nav.navigate("lesson/${l.id}") }.padding(vertical = 6.dp))
                    }
                }
            }
        }

        Title("Talk about it")
        SectionCard {
            content.talkAboutIt.forEach { Text("• $it") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun LessonScreen(state: AppState, id: String, onBack: () -> Unit) {
    val lesson = state.content.lesson(id)
    val context = LocalContext.current
    LaunchedEffect(id) { state.prefs.viewedLessons = state.prefs.viewedLessons + id }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        if (lesson == null) {
            Text("Lesson $id isn't available.")
            return@Column
        }
        val module = state.content.module(lesson.module)
        Text("${lesson.id} · ${module?.title ?: ""}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(lesson.title, style = MaterialTheme.typography.headlineSmall)
        val inTrial = Coaching.cards.values.filter { it.lessonId == id }
        if (inTrial.isNotEmpty()) {
            SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text("In the trial", style = MaterialTheme.typography.titleMedium)
                inTrial.forEach { Text("${it.title}: ${it.text}") }
            }
        }
        SectionCard {
            Text("Key points (curriculum draft)", style = MaterialTheme.typography.titleMedium)
            Text(boldMarkup(lesson.body), style = MaterialTheme.typography.bodyLarge)
        }
        if (lesson.sources.isNotEmpty()) {
            Text("Sources", style = MaterialTheme.typography.titleMedium)
            lesson.sources.forEach { s ->
                Text(
                    s.title,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url))) }.padding(vertical = 4.dp),
                )
            }
        }
        Text(
            "${state.content.lessons.status}. ${state.content.lessons.review} Region: US English draft.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun QuizScreen(state: AppState, ui: RunUi, post: Boolean, onBack: () -> Unit) {
    val questions = if (post) state.content.quiz.post else state.content.quiz.pre
    val saved = if (post) ui.quizPost else ui.quizPre
    val answers = remember { mutableStateMapOf<String, String>().apply { putAll(saved) } }
    var submitted by rememberSaveable { mutableStateOf(saved.isNotEmpty()) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        QuizStep(questions, answers, reveal = submitted)
        if (!submitted) {
            Button(onClick = {
                answers.forEach { (q, a) -> state.repo.send(Command.QuizAnswer(post, q, a)) }
                submitted = true
            }, enabled = answers.isNotEmpty()) { Text("Check my answers") }
        } else {
            val right = questions.count { answers[it.id] == it.answer }
            Text("$right of ${questions.size} matched the draft guidance.", fontWeight = FontWeight.SemiBold)
            Text(state.content.quiz.review, style = MaterialTheme.typography.bodySmall)
        }
    }
}
