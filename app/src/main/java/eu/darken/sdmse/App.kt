package eu.darken.sdmse

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.coil.CoilTempFiles
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.AutomaticBugReporter
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.DebugSettings
import eu.darken.sdmse.common.debug.logging.LogCatLogger
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.memory.MemoryMonitor
import eu.darken.sdmse.common.debug.recorder.core.RecorderModule
import eu.darken.sdmse.common.theming.Theming
import eu.darken.sdmse.common.updater.UpdateService
import eu.darken.sdmse.main.core.CurriculumVitae
import eu.darken.sdmse.main.core.GeneralSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
open class App : Application(), Configuration.Provider {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var bugReporter: AutomaticBugReporter
    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var recorderModule: RecorderModule
    @Inject lateinit var imageLoaderFactory: ImageLoaderFactory
    @Inject lateinit var debugSettings: DebugSettings
    @Inject lateinit var curriculumVitae: CurriculumVitae
    @Inject lateinit var updateService: UpdateService
    @Inject lateinit var theming: Theming
    @Inject lateinit var coilTempFiles: CoilTempFiles
    @Inject lateinit var memoryMonitor: MemoryMonitor

    private val logCatLogger = LogCatLogger()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfigWrap.DEBUG) {
            Logging.install(logCatLogger)
            log(TAG) { "BuildConfigWrap.DEBUG=true" }
        }
        log(TAG) { "Fingerprint: ${BuildWrap.FINGERPRINT}" }

        combine(
            debugSettings.isDebugMode.flow,
            debugSettings.isTraceMode.flow,
            debugSettings.isDryRunMode.flow,
            recorderModule.state,
        ) { isDebug, isTrace, isDryRun, recorder ->
            log(TAG) { "isDebug=$isDebug, isTrace=$isTrace, isDryRun=$isDryRun, recorder=$recorder" }

            if (isDebug) {
                Logging.install(logCatLogger)
            } else {
                Logging.remove(logCatLogger)
            }

            Bugs.isDebug = isDebug || recorder.isRecording
            Bugs.isTrace = isDebug && isTrace
            Bugs.isDryRun = isDebug && isDryRun
        }.launchIn(appScope)

        bugReporter.setup(this)

        recorderModule.state
            .onEach { log(TAG) { "RecorderModule: $it" } }
            .launchIn(appScope)

        theming.setup()

        memoryMonitor.register()

        appScope.launch { coilTempFiles.cleanUp() }
        Coil.setImageLoader(imageLoaderFactory)

        curriculumVitae.updateAppLaunch()

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(TAG, ERROR) { "UNCAUGHT EXCEPTION: ${throwable.asLog()}" }

            if (throwable is OutOfMemoryError) {
                log(TAG, ERROR) { "OutOfMemoryError detected! Memory risk: ${memoryMonitor.getMemoryPressureRisk()}" }
                memoryMonitor.logCurrentMemoryState()
            }

            if (oldHandler != null) oldHandler.uncaughtException(thread, throwable) else exitProcess(1)
            Thread.sleep(100)
        }

        log(TAG) { "onCreate() done! ${Exception().asLog()}" }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(
                when {
                    BuildConfigWrap.DEBUG -> android.util.Log.VERBOSE
                    BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.DEV -> android.util.Log.DEBUG
                    BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.BETA -> android.util.Log.INFO
                    BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE -> android.util.Log.WARN
                    else -> android.util.Log.VERBOSE
                }
            )
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        internal val TAG = logTag("App")
    }
}
