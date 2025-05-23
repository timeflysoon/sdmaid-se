package eu.darken.sdmse.common.areas.modules.pub

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.modules.DataAreaModule
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.canRead
import eu.darken.sdmse.common.files.listFiles
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.lookup
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.storage.PathMapper
import java.io.IOException
import javax.inject.Inject

@Reusable
class PublicDataModule @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val pathMapper: PathMapper,
    private val adbManager: AdbManager,
    private val rootManager: RootManager,
) : DataAreaModule {

    override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> {
        val sdcardAreas = firstPass.filter { it.type == DataArea.Type.SDCARD }

        val areas = sdcardAreas
            .mapNotNull { parentArea ->
                val accessPath = try {
                    parentArea.determineAndroidData()
                } catch (e: IOException) {
                    log(TAG, WARN) { "Failed to determine accessPath for $parentArea: ${e.asLog()}" }
                    null
                }

                accessPath?.let { parentArea to it }
            }
            .filter { (area, path) ->
                if (!path.canRead(gatewaySwitch)) {
                    log(TAG) { "Can't read $area" }
                    return@filter false
                }

                try {
                    path.lookup(gatewaySwitch)
                } catch (e: IOException) {
                    log(TAG, ERROR) { "Failed lookup() for $area: ${e.asLog()}" }
                    return@filter false
                }

                try {
                    path.listFiles(gatewaySwitch)
                } catch (e: IOException) {
                    log(TAG, ERROR) { "Failed listFiles() for $area: ${e.asLog()}" }
                    return@filter false
                }

                true
            }
            .map { (parentArea, path) ->
                DataArea(
                    type = DataArea.Type.PUBLIC_DATA,
                    path = path,
                    flags = parentArea.flags,
                    userHandle = parentArea.userHandle,
                )
            }

        log(TAG, VERBOSE) { "secondPass(): $areas" }

        return areas
    }

    private suspend fun DataArea.determineAndroidData(): APath? {
        val parentArea = this
        val target = this.path.child("Android", "data")

        return when {
            hasApiLevel(33) -> {
                when {
                    // If we have root, we need to convert any SAFPath back
                    rootManager.canUseRootNow() || adbManager.canUseAdbNow() -> {
                        when (target) {
                            is LocalPath -> target
                            is SAFPath -> pathMapper.toLocalPath(target)
                            else -> null
                        }
                    }

                    else -> {
                        log(TAG, INFO) { "Skipping Android/data (API33, no root/adb): $parentArea" }
                        null
                    }
                }
            }

            hasApiLevel(30) -> {
                when {
                    // Can't use SAFPath with Shizuku or Root
                    rootManager.canUseRootNow() || adbManager.canUseAdbNow() -> when (target) {
                        is SAFPath -> pathMapper.toLocalPath(target)
                        else -> target
                    }
                    // On API30 we can do the direct SAF grant workaround
                    else -> when (target) {
                        is LocalPath -> pathMapper.toSAFPath(target)
                        is SAFPath -> target
                        else -> null
                    }
                }
            }

            else -> target
        }
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: PublicDataModule): DataAreaModule
    }

    companion object {
        val TAG: String = logTag("DataArea", "Module", "Public", "Data")
    }
}