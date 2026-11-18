package app.childrensimulator.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import android.util.Log
import app.childrensimulator.sim.engine.SimEngine
import app.childrensimulator.sim.model.World
import java.io.File

/**
 * Elapsed-realtime based clock (docs/11 §3.2): within one boot, time comes from the monotonic clock, so changing
 * the phone's clock cannot rewind or fast-forward a run. After a reboot it re-anchors on the wall clock.
 */
object TrustedClock {
    private var cachedBoot = Int.MIN_VALUE
    private var anchor = 0L

    @Synchronized
    fun now(context: Context): Long {
        val boot = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        } catch (_: Exception) {
            -1
        }
        if (boot != cachedBoot) {
            val prefs = context.getSharedPreferences("clock", Context.MODE_PRIVATE)
            val key = "anchor_$boot"
            anchor = if (boot >= 0 && prefs.contains(key)) prefs.getLong(key, 0L) else {
                val a = System.currentTimeMillis() - SystemClock.elapsedRealtime()
                prefs.edit().clear().putLong(key, a).apply()
                a
            }
            cachedBoot = boot
        }
        return anchor + SystemClock.elapsedRealtime()
    }
}

/** Atomic JSON snapshot of the current run (docs/11 §4; Room is planned once runs need history). */
class RunStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "run.json"))

    fun load(): World? = try {
        if (!file.baseFile.exists()) null else SimEngine.decode(String(file.readFully(), Charsets.UTF_8))
    } catch (e: Exception) {
        Log.w("RunStore", "Could not read the saved run; starting fresh", e)
        try {
            file.baseFile.copyTo(File(file.baseFile.parentFile, "run.corrupt.json"), overwrite = true)
        } catch (_: Exception) {
        }
        null
    }

    fun save(world: World) {
        val bytes = SimEngine.encode(world).toByteArray(Charsets.UTF_8)
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    fun delete() {
        file.delete()
    }
}

/** Player preferences that are not part of the simulation. */
class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()
    var soundOn: Boolean
        get() = p.getBoolean("sound", true)
        set(v) = p.edit().putBoolean("sound", v).apply()
    var hapticsOn: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = p.edit().putBoolean("haptics", v).apply()
    var reducedMotion: Boolean
        get() = p.getBoolean("reducedMotion", false)
        set(v) = p.edit().putBoolean("reducedMotion", v).apply()
    var showHints: Boolean
        get() = p.getBoolean("hints", false)
        set(v) = p.edit().putBoolean("hints", v).apply()
    var checkTipSeen: Boolean
        get() = p.getBoolean("checkTip", false)
        set(v) = p.edit().putBoolean("checkTip", v).apply()
    var exactNightAlarms: Boolean
        get() = p.getBoolean("exactNight", false)
        set(v) = p.edit().putBoolean("exactNight", v).apply()
    var developer: Boolean
        get() = p.getBoolean("developer", false)
        set(v) = p.edit().putBoolean("developer", v).apply()
    var practiceQuizPre: String?
        get() = p.getString("quizPre", null)
        set(v) = p.edit().putString("quizPre", v).apply()
    var viewedLessons: Set<String>
        get() = p.getStringSet("viewedLessons", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("viewedLessons", v).apply()
}
