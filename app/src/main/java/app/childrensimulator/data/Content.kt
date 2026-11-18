package app.childrensimulator.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LessonSource(val title: String, val url: String)

@Serializable
data class Lesson(val id: String, val title: String, val body: String, val sources: List<LessonSource> = emptyList(), val module: Int)

@Serializable
data class LessonModule(val number: Int, val title: String, val unlocks: String)

@Serializable
data class FirstTrialMoment(val moment: String, val skill: String, val feedback: String, val lessons: List<String>)

@Serializable
data class LessonContent(
    val source: String,
    val status: String,
    val review: String,
    val modules: List<LessonModule>,
    val lessons: List<Lesson>,
    val firstTrial: List<FirstTrialMoment>,
    val talkAboutIt: List<String>,
)

@Serializable
data class QuizOption(val id: String, val text: String)

@Serializable
data class QuizQuestion(val id: String, val text: String, val options: List<QuizOption>, val answer: String, val explain: String, val lesson: String)

@Serializable
data class QuizContent(val source: String, val review: String, val pre: List<QuizQuestion>, val post: List<QuizQuestion>)

/** Versioned learning content bundled as data (docs/09 §5). */
class ContentRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    val lessons: LessonContent = json.decodeFromString(LessonContent.serializer(), context.assets.open("content/lessons.json").bufferedReader().readText())
    val quiz: QuizContent = json.decodeFromString(QuizContent.serializer(), context.assets.open("content/quiz.json").bufferedReader().readText())
    val profilesJson: String = context.assets.open("age-profiles.json").bufferedReader().readText()

    fun lesson(id: String): Lesson? = lessons.lessons.firstOrNull { it.id == id }
    fun module(number: Int): LessonModule? = lessons.modules.firstOrNull { it.number == number }

    fun score(answers: Map<String, String>, post: Boolean): Pair<Int, Int> {
        val qs = if (post) quiz.post else quiz.pre
        val answered = qs.filter { answers.containsKey(it.id) }
        return answered.count { answers[it.id] == it.answer } to qs.size
    }
}
