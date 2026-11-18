package app.childrensimulator

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.childrensimulator.audio.CrySound
import app.childrensimulator.data.AppPrefs
import app.childrensimulator.data.ContentRepository
import app.childrensimulator.data.RunRepository
import app.childrensimulator.data.RunStore
import app.childrensimulator.notify.AlarmScheduler
import app.childrensimulator.notify.Notifier
import app.childrensimulator.sim.engine.SimEngine
import app.childrensimulator.sim.profile.AgeProfiles

/** Manual dependency container (docs/11 §1: start with an app module and the simulation module). */
class AppContainer(app: Application) {
    val prefs = AppPrefs(app)
    val content = ContentRepository(app)
    val engine = SimEngine(AgeProfiles.parse(content.profilesJson))
    val notifier = Notifier(app)
    val scheduler = AlarmScheduler(app)
    val repository = RunRepository(app, engine, RunStore(app), notifier, scheduler, prefs)
    val crySound = CrySound(app)
}

class ChildSimApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        container.repository.init()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.repository.onForeground()
            override fun onStop(owner: LifecycleOwner) {
                container.repository.onBackground()
                container.crySound.stop()
            }
        })
    }
}
