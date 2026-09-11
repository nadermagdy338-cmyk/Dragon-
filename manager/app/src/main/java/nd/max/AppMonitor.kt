/*
 * Copyright (C) 2026 Rem01Gaming
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max


import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import org.lsposed.hiddenapibypass.HiddenApiBypass
import android.hardware.display.DisplayManager
import android.view.Display
import android.provider.Settings
import android.media.AudioManager
import android.net.wifi.WifiManager
import nd.max.ui.util.PerAppKernelUtil
import nd.max.ui.util.ProfilePresetStore
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.GpuHardwareBackend
import nd.max.core.hardware.RootFileAccess
import nd.max.core.hardware.PerAppControlRegistry
import nd.max.core.hardware.PerAppRecoveryStore
import nd.max.core.hardware.PerAppFrequencyController
import nd.max.core.hardware.VerifiedControl
import nd.max.ui.util.decodePerAppCpuPolicyControls
import nd.max.ui.viewmodel.TouchBoostViewModel


@SuppressLint("StaticFieldLeak", "DiscouragedPrivateApi", "PrivateApi")
object AppMonitor {
    private const val POLL_INTERVAL_MS = 500L
    // How often reassertDriftedKnobs() re-verifies GPU/CPU knobs against
    // what the per-app config actually asked for. Deliberately much coarser
    // than POLL_INTERVAL_MS: this is a safety net against vendor thermal
    // daemons undoing our writes, not a tight control loop.
    private const val DRIFT_CHECK_INTERVAL_MS = 10_000L
    private val DRIFT_RETRY_DELAYS_MS = longArrayOf(10_000L, 60_000L, 300_000L, 900_000L)
    private const val PID_RETRY_INTERVAL_MS = 50L
    private const val UNKNOWN_APP = "unknown 0 0"
    private const val NONE_APP = "none 0 0"
    // مهلة السماح قبل التراجع عن تعديلات تطبيق مغادر نحو غير مُدار:
    // التنقل السريع (إشعار ثم عودة) لا يخفق التعديلات، وموت العملية
    // يُعجّل التراجع فورًا دون انتظار. 10 ثوانٍ توافق مهلة الوحدة
    // الأصلية عند إطفاء الشاشة.
    private const val PERAPP_GRACE_MS = 10_000L

    private val FOREGROUND_METHOD_CANDIDATES = listOf(
        "getFocusedRootTaskInfo",
        "getFocusedRootTask",
        "getFocusedTaskInfo",
        "getFocusedStackInfo",
        "getTopActivity",
        "getTasks",
        "getRunningTasks"
    )

    private val COMPONENT_NAME_FIELDS = listOf(
        "topActivity",
        "topActivityComponent",
        "realActivity",
        "baseActivity",
        "origActivity",
        "activity"
    )

    private var systemContext: Context? = null

    private var activityTaskManager: Any? = null
    private var foregroundMethod: Method? = null
    private var powerManager: PowerManager? = null
    private var activityManager: ActivityManager? = null
    private var notificationManager: Any? = null
    private var batteryManager: BatteryManager? = null
    private var getZenModeMethod: Method? = null

    private var bruteForceCandidates: List<Method>? = null

    @Volatile
    private var lastStatus = ""
    
    @Volatile
    private var lastBackgroundApps = ""

    private var outputPath = ""
    private var cachedGameListModified = -1L
    private var backgroundOutputPath = ""
    private var lockFilePath: String? = null

    // ── Per-App Config state ──────────────────────────────────────────────
    @Volatile private var lastAppliedPkg = ""

    // هل تعديلات per-app حيّة على العتاد الآن؟ يُنشر في app_status
    // (perapp_active) كي يتحول محرك MAX AI إلى وضع المراقبة أثناء
    // ملكية ملف التطبيق — قيمة فعلية لا مؤقت.
    @Volatile private var perAppOverridesActive = false

    // مهلة السماح عند مغادرة تطبيق مُدار نحو غير مُدار (المشغّل أو
    // الشاشة الرئيسية): التراجع مؤجل — الرجوع السريع خلال المهلة لا
    // يخفق التعديلات، وموت التطبيق فعليًا (عملية غير موجودة) يُعجّل
    // التراجع دون انتظار المؤقت. الحالة الحقيقية للتطبيق هي المرجع.
    @Volatile private var gracePkg: String? = null
    @Volatile private var graceDeadlineMs: Long = 0L
    // Correlation id for the app switch currently being applied/reverted, e.g.
    // "sw-1798...". Regenerated every time the focused app changes (see
    // buildStatus()) and written into app_status so the native daemon's
    // read_app_status() picks it up and can tag its own log_zenith() calls
    // for the same switch with the same id -- see AppMonitorLogger.kt's
    // header comment and Batch 3 of the logging plan for the full picture.
    @Volatile private var currentSwitchId = ""
    private var savedGpuGovernor = ""
    private var savedCpuGovernors = mutableMapOf<String, String>()
    private var savedCpuMinFreqs = mutableMapOf<String, String>()
    private var savedCpuMaxFreqs = mutableMapOf<String, String>()
    // Baseline is captured once before the first per-app override. It must not
    // be overwritten on every app switch, otherwise switching from an explicit
    // governor app to `default` would snapshot the previous app's governor and
    // carry it into the next app.
    private var baselineGpuGovernor = ""
    private var baselineCpuGovernors = mutableMapOf<String, String>()
    private var baselineCaptured = false
    private var savedGpuNode = ""
    private var savedGpuMinFreq = ""
    private var savedGpuMaxFreq = ""
    private var savedThermalProfile = ""
    private var savedZenMode: Int? = null
    private var savedPeakRefreshRate = ""
    private var savedMinRefreshRate = ""
    private var savedVendorRefreshSnapshot: PerAppRefreshRateController.Snapshot? = null
    private var wasZenSet = false
    private val hardwareControlRegistry = PerAppControlRegistry()
    private var activePerAppCpuPackage = ""


    // ── App Settings fields (wired to backend here) ─────────────────────────
    // touch_boost is read every poll (buildStatus runs every 500ms, not just
    // on focus change) because applyTouchBoost() needs to react to
    // screen-on/off too. "default" means "fall back to the existing
    // isKnownGameApp() auto-detect heuristic", matching pre-existing
    // behavior for apps that never set this field. Set from the per-app
    // config in applyPerAppConfig() below, reset in revertPerAppConfig().
    @Volatile private var touchBoostOverride = "default"
    private var forcedHwUi = false
    private var savedHwUiProp: String? = null
    private var savedDisableHwProp: String? = null
    private var cpuBoostThread: Thread? = null
    private var cpuBoostOriginalMins = mutableMapOf<String, String>()
    @Volatile private var cpuBoostGeneration = 0L
    private var savedHapticFeedbackEnabled: String? = null
    private var wasNotifStreamMuted = false
    private var wifiNoSleepLock: WifiManager.WifiLock? = null

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 2) {
            AppMonitorLogger.fatal("Usage: <status_output_path> <background_output_path> [lock_file_path] -- missing required output paths")
            return
        }
        
        outputPath = args[0]
        backgroundOutputPath = args[1]

        if (args.size >= 3) {
            lockFilePath = args[2]
        }

        bypassHiddenApiRestrictions()
        setupSystemContext()

        if (systemContext == null) {
            AppMonitorLogger.fatal("System context is null after setupSystemContext() -- cannot continue")
            return
        }

        if (!initializeServices()) {
            AppMonitorLogger.fatal("Failed to initialize services (ActivityTaskManager/PowerManager/etc.), exiting")
            return
        }

        recoverStalePerAppState()
        AppMonitorLogger.i("AppMonitor companion started (pid=${android.os.Process.myPid()})")

        val lockChannel = acquireLock()

        val monitorThread = Thread.currentThread()
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { revertPerAppConfig() }
            runCatching { shellExec("setprop sys.maxmanager.perapp.governor_isolation 0") }
            lockChannel?.close()
            monitorThread.interrupt()
        })

        runMonitorLoop()
    }

    private fun acquireLock(): FileChannel? {
        val path = lockFilePath ?: return null
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()

            val channel = FileChannel.open(
                file.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            )

            val lock: FileLock? = channel.tryLock()
            if (lock == null) {
                AppMonitorLogger.fatal("Another AppMonitor instance already holds the lock at '$path'")
                channel.close()
                System.exit(1)
                null
            } else {
                channel
            }
        } catch (e: Exception) {
            AppMonitorLogger.fatal("Failed to acquire lock at '$path'", e)
            System.exit(1)
            null
        }
    }

    private fun runMonitorLoop() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                serviceDeferredRevert()
                writeStatus()
                writeBackgroundApps()
                reassertDriftedKnobs()
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (t: Throwable) {
                AppMonitorLogger.e("Uncaught error in monitor loop iteration", t)
            }
        }
    }

    /**
     * خدمة مهلة السماح: تُنفَّذ كل دورة (500 مللي). يُتراجع فورًا إذا
     * ماتت عملية التطبيق المغادر (حالة حقيقية من ActivityManager)، أو
     * عند انقضاء المهلة (10 ثوانٍ — نفس مهلة الوحدة الأصلية عند
     * إطفاء الشاشة) — أيهما أسبق.
     */
    private fun serviceDeferredRevert() {
        val pkg = gracePkg ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        val died = !isAppProcessAlive(pkg)
        if (died || now >= graceDeadlineMs) {
            AppMonitorLogger.i(
                "EVENT=PERAPP_DEFERRED_REVERT pkg=$pkg reason=${if (died) "app_died" else "grace_expired"} sw=$currentSwitchId"
            )
            runCatching { revertPerAppConfig() }
                .onFailure { AppMonitorLogger.e("EVENT=REVERT_FAILED pkg=$pkg sw=$currentSwitchId (deferred)", it) }
            gracePkg = null
        }
    }

    /** هل ما زالت عملية التطبيق حية؟ فشل الاستعلام يُعامل كحي (ننتظر المؤقت). */
    private fun isAppProcessAlive(pkg: String): Boolean = runCatching {
        activityManager?.runningAppProcesses?.any { p ->
            p.processName == pkg || p.pkgList?.contains(pkg) == true
        } == true
    }.getOrDefault(true)

    private var lastDriftCheckAt = 0L
    private data class DriftRetry(var attempts: Int, var dueAtMs: Long)
    private val driftRetries = mutableMapOf<String, DriftRetry>()

    private fun canRetryDrift(key: String, now: Long): Boolean =
        driftRetries[key]?.let { now >= it.dueAtMs } ?: true

    private fun recordDriftFailure(key: String, now: Long) {
        val previous = driftRetries[key]?.attempts ?: 0
        val attempt = (previous + 1).coerceAtMost(DRIFT_RETRY_DELAYS_MS.size)
        driftRetries[key] = DriftRetry(attempt, now + DRIFT_RETRY_DELAYS_MS[attempt - 1])
    }

    private fun clearDriftRetry(key: String): Boolean = driftRetries.remove(key) != null

    /**
     * applyPerAppConfig() only runs once, at the moment the foreground app
     * changes -- it has no way to notice if something *else* undoes its
     * work afterward. On Xiaomi devices in particular, mi_thermald
     * periodically republishes its own CPU/GPU frequency ceilings on a
     * timer that has nothing to do with MaxManager, silently pulling a
     * chosen GPU/thermal profile back within seconds to minutes. From the
     * person's side this looks exactly like "I picked a profile and it did
     * nothing" even though the initial write succeeded.
     *
     * This re-checks the live value against what the per-app config
     * actually asked for, roughly every DRIFT_CHECK_INTERVAL_MS while an
     * app with a non-default override is in the foreground, and only
     * re-applies (and logs EVENT=APPLY_DRIFT) when they've actually
     * diverged -- not on every single poll, which would just be fighting
     * the vendor daemon in a tight loop for no benefit.
     */
    private fun reassertDriftedKnobs() {
        val pkg = lastAppliedPkg
        if (pkg.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastDriftCheckAt < DRIFT_CHECK_INTERVAL_MS) return
        lastDriftCheckAt = now

        runCatching {
            val profile = readAppConfigField(pkg, "gpu_profile").ifEmpty {
                val legacy = readAppConfigField(pkg, "thermal_profile")
                when (legacy) { "powersave" -> "power"; else -> legacy }
            }
            val node = savedGpuNode.takeIf { it.isNotBlank() } ?: return@runCatching
            val caps = PerAppKernelUtil.readGpuCapabilities()
            val explicitFreq = readAppConfigField(pkg, "gpu_max_freq").toLongOrNull()
            val target = explicitFreq ?: PerAppKernelUtil.pickProfileFrequency(caps.frequencies, profile, ProfilePresetStore.percentFor(systemContext, profile)) ?: return@runCatching
            val mtkIndex = PerAppKernelUtil.mtkOppIndexForFrequency(caps, target)
            val retryKey = "$pkg|$node|$target|${mtkIndex ?: "devfreq"}"
            if (mtkIndex != null) {
                val liveLock = PerAppKernelUtil.currentMtkGpuLockIndex()
                if (liveLock != mtkIndex && canRetryDrift(retryKey, now)) {
                    val applied = PerAppKernelUtil.applyGpuFixedFrequency(node, caps, target)
                    val verified = applied && PerAppKernelUtil.currentMtkGpuLockIndex() == mtkIndex
                    if (!verified) {
                        if (!driftRetries.containsKey(retryKey)) {
                            AppMonitorLogger.w("EVENT=APPLY_DRIFT_REASSERT_FAILED knob=gpu_opp_lock pkg=$pkg expected_index=$mtkIndex sw=$currentSwitchId")
                        }
                        recordDriftFailure(retryKey, now)
                    } else if (clearDriftRetry(retryKey)) {
                        AppMonitorLogger.i("EVENT=APPLY_DRIFT_RECOVERED knob=gpu_opp_lock pkg=$pkg expected_index=$mtkIndex sw=$currentSwitchId")
                    }
                } else if (liveLock == mtkIndex) clearDriftRetry(retryKey)
            } else {
                val liveMaxFreq = shellRead("cat '$node/max_freq' 2>/dev/null").toLongOrNull()
                if (liveMaxFreq != null && liveMaxFreq != target && canRetryDrift(retryKey, now)) {
                    val applied = PerAppKernelUtil.applyGpuFixedFrequency(node, caps, target)
                    val verified = applied && shellRead("cat '$node/max_freq' 2>/dev/null").toLongOrNull() == target
                    if (!verified) {
                        if (!driftRetries.containsKey(retryKey)) {
                            AppMonitorLogger.w("EVENT=APPLY_DRIFT_REASSERT_FAILED knob=gpu_profile pkg=$pkg expected=$target live=$liveMaxFreq sw=$currentSwitchId")
                        }
                        recordDriftFailure(retryKey, now)
                    } else if (clearDriftRetry(retryKey)) {
                        AppMonitorLogger.i("EVENT=APPLY_DRIFT_RECOVERED knob=gpu_profile pkg=$pkg expected=$target sw=$currentSwitchId")
                    }
                } else if (liveMaxFreq == target) clearDriftRetry(retryKey)
            }
        }.onFailure { AppMonitorLogger.e("EVENT=DRIFT_CHECK_FAILED knob=gpu_profile pkg=$pkg sw=$currentSwitchId", it) }

        runCatching {
            val cpuGovernor = readAppConfigField(pkg, "cpu_governor")
            if (cpuGovernor.isNotBlank() && cpuGovernor != "default") {
                hardwareControlRegistry.ownGovernor(
                    key = "cpu_governor",
                    desired = cpuGovernor,
                    apply = { value -> CpuHardwareBackend.setGovernor(value).successful },
                    read = {
                        val values = CpuHardwareBackend.policies().mapNotNull { it.governor }.distinct()
                        values.singleOrNull()
                    }
                )
            } else hardwareControlRegistry.release("cpu_governor")

            val gpuGovernor = readAppConfigField(pkg, "gpu_governor")
            if (gpuGovernor.isNotBlank() && gpuGovernor != "default") {
                val gpuNode = savedGpuNode.takeIf { it.isNotBlank() } ?: PerAppKernelUtil.findGpuNode()
                hardwareControlRegistry.ownGovernor(
                    key = "gpu_governor",
                    desired = gpuGovernor,
                    apply = { value ->
                        val generic = gpuNode?.let { path ->
                            GpuHardwareBackend.devices().firstOrNull { it.path == path }
                        }
                        if (generic != null && value in generic.governors) {
                            GpuHardwareBackend.setGovernor(generic, value).successful
                        } else {
                            PerAppKernelUtil.applyGpuGovernor(gpuNode, value)
                        }
                    },
                    read = {
                        gpuNode?.let { path ->
                            GpuHardwareBackend.devices().firstOrNull { it.path == path }?.governor
                                ?: RootFileAccess.read("$path/governor")
                        }?.takeIf { it.isNotBlank() }
                    }
                )
            } else hardwareControlRegistry.release("gpu_governor")

            hardwareControlRegistry.verifyAndRepair().forEach { result ->
                if (!result.successful) {
                    AppMonitorLogger.w("EVENT=APPLY_DRIFT_REASSERT_FAILED knob=hardware_registry pkg=$pkg expected=${result.requested} live=${result.actual ?: "none"} sw=$currentSwitchId")
                } else if (result.attempts > 1) {
                    AppMonitorLogger.i("EVENT=APPLY_DRIFT_REPAIRED knob=hardware_registry pkg=$pkg expected=${result.requested} attempts=${result.attempts} sw=$currentSwitchId")
                }
            }
        }.onFailure { AppMonitorLogger.e("EVENT=DRIFT_CHECK_FAILED knob=hardware_registry pkg=$pkg sw=$currentSwitchId", it) }

        // Refresh-rate is another vendor-owned setting that can be reset after
        // an app switch (display/HAL policy changes are enough to do it). Verify
        // only explicit per-app requests and reapply them at the same coarse
        // cadence as the CPU/GPU drift guard. This intentionally does not fight
        // the system when the app profile says "default".
        runCatching {
            val requested = readAppConfigField(pkg, "refresh_rate")
                .takeIf { it.isNotBlank() && it != "default" }
                ?.toFloatOrNull()
                ?.toInt()
                ?: return@runCatching
            val context = systemContext ?: return@runCatching
            val normalized = PerAppRefreshRateController.normalizeRequestedRate(context, requested)
                ?: return@runCatching
            val live = PerAppRefreshRateController.currentEnforcedRate(context)
            if (live != null && live != normalized) {
                AppMonitorLogger.w("EVENT=APPLY_DRIFT knob=refresh_rate pkg=$pkg expected=$normalized live=$live sw=$currentSwitchId reassert=true")
                if (!PerAppRefreshRateController.apply(context, normalized)) {
                    AppMonitorLogger.w("EVENT=APPLY_DRIFT_REASSERT_FAILED knob=refresh_rate pkg=$pkg expected=$normalized sw=$currentSwitchId")
                }
            }
        }.onFailure { AppMonitorLogger.e("EVENT=DRIFT_CHECK_FAILED knob=refresh_rate pkg=$pkg sw=$currentSwitchId", it) }
    }

    private fun writeStatus() {
        val focusedApp = waitForValidFocusedApp() ?: return
        val currentStatus = runCatching { buildStatus(focusedApp) }
            .onFailure { AppMonitorLogger.e("buildStatus() failed for focused app '$focusedApp'", it) }
            .getOrNull() ?: return
        if (currentStatus == lastStatus) return

        try {
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            
            val tmpFile = File("$outputPath.tmp")

            FileOutputStream(tmpFile).use { fos ->
                fos.write(currentStatus.toByteArray(Charsets.UTF_8))
                fos.fd.sync()
            }

            tmpFile.renameTo(file)
            
            lastStatus = currentStatus
        } catch (e: Exception) {
            AppMonitorLogger.e("Failed to write status file '$outputPath'", e)
        }
    }

    /**
     * MENGAMBIL DAFTAR PACKAGE YANG ADA DI RECENT APPS (TASK MANAGER)
     */
    @Suppress("DEPRECATION")
    private fun getRecentAppPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        try {
            val recentTasks = activityManager?.getRecentTasks(30, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            recentTasks?.forEach { task ->
                val pkg = task.baseIntent.component?.packageName ?: task.topActivity?.packageName
                if (pkg != null) {
                    packages.add(pkg)
                }
            }
            
            val currentFocused = lastStatus.substringAfter("focused_app ").substringBefore(" ")
            if (currentFocused.isNotBlank() && currentFocused != "unknown" && currentFocused != "none") {
                packages.add(currentFocused)
            }
        } catch (e: Exception) {
            AppMonitorLogger.e("getRecentAppPackages() failed", e)
        }
        return packages
    }


    private fun writeBackgroundApps() {
        val processes = activityManager?.runningAppProcesses ?: return
        
        val recentPackages = getRecentAppPackages()

        val currentApps = buildString {
            for (process in processes) {
                val pkgName = process.pkgList?.firstOrNull() ?: process.processName
                
                if (recentPackages.contains(pkgName)) {
                    appendLine("$pkgName ${process.pid} ${process.uid}")
                }
            }
        }

        if (currentApps == lastBackgroundApps) return

        try {
            val file = File(backgroundOutputPath)
            file.parentFile?.mkdirs()
            
            val tmpFile = File("$backgroundOutputPath.tmp")

            FileOutputStream(tmpFile).use { fos ->
                fos.write(currentApps.toByteArray(Charsets.UTF_8))
                fos.fd.sync()
            }

            tmpFile.renameTo(file)

            lastBackgroundApps = currentApps
        } catch (e: Exception) {
            AppMonitorLogger.e("Failed to write background apps file '$backgroundOutputPath'", e)
        }
    }


    private fun waitForValidFocusedApp(): String? {
        var focusedApp = getFocusedAppInfo()
        if (!hasMissingPid(focusedApp)) return focusedApp

        val deadline = System.currentTimeMillis() + POLL_INTERVAL_MS
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(PID_RETRY_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
            focusedApp = getFocusedAppInfo()
            if (!hasMissingPid(focusedApp)) return focusedApp
        }

        return focusedApp
    }

    private fun hasMissingPid(appInfo: String): Boolean =
        appInfo != NONE_APP && appInfo.endsWith(" 0 0")

    private fun recoverStalePerAppState() {
        runCatching {
            val stale = PerAppRecoveryStore.read() ?: return@runCatching
            val currentBoot = PerAppRecoveryStore.bootId()
            if (currentBoot.isBlank() || stale.bootId != currentBoot) {
                PerAppRecoveryStore.clear()
                return@runCatching
            }
            if (PerAppRecoveryStore.restore(stale)) {
                AppMonitorLogger.w("EVENT=PERAPP_RECOVERY_RESTORED pkg=${stale.packageName} cpu=${stale.cpuGovernors.size} gpu=${stale.gpuNode.isNotBlank()}")
            } else {
                AppMonitorLogger.e("EVENT=PERAPP_RECOVERY_PARTIAL pkg=${stale.packageName}")
            }
            // MTK fixed-index locks are intentionally released through the existing
            // vendor-aware adapter; the journal stores generic devfreq state only.
            runCatching { PerAppKernelUtil.releaseGpuFixedFrequency() }
            PerAppRecoveryStore.clear()
        }.onFailure { AppMonitorLogger.e("EVENT=PERAPP_RECOVERY_FAILED", it) }
    }

    private fun buildStatus(focusedApp: String): String {
        val screenAwake = if (powerManager?.isInteractive == true) 1 else 0
        val batterySaver = if (powerManager?.isPowerSaveMode == true) 1 else 0
        val zenMode = getZenMode()
        val batteryLevel = getBatteryLevel()
        val isCharging = getChargingStatus()
        val pkgName = focusedApp.substringBefore(" ")
        val appName = getAppName(pkgName)
        val currentRefreshRate = getCurrentRefreshRate()
        val maxRefreshRate = getMaxRefreshRate()

        // ── Apply per-app config when focused app changes ─────────────────
        // Wrapped in runCatching: apply/revert touch privileged APIs
        // (WRITE_SECURE_SETTINGS-gated Settings.Global/System calls) that
        // throw SecurityException on ROMs that don't grant it (MIUI/HyperOS
        // and friends). Without this guard, one failure here used to abort
        // buildStatus() entirely -> app_status never got written again ->
        // lastAppliedPkg never advanced -> the same failing call retried
        // forever on every poll. lastAppliedPkg is now updated unconditionally
        // so the daemon always moves on, even when apply/revert partially fail.
        if (pkgName != lastAppliedPkg) {
            // عاد التطبيق المُدار خلال مهلة السماح؟ التعديلات ما زالت
            // حية على العتاد — إلغاء التراجع المؤجل بلا خفقان ولا
            // إعادة تطبيق.
            if (gracePkg != null && pkgName == gracePkg) {
                AppMonitorLogger.i("EVENT=PERAPP_GRACE_ABORTED pkg=$pkgName sw=$currentSwitchId reason=refocused")
                gracePkg = null
            } else {
                currentSwitchId = "sw-${System.currentTimeMillis()}"
                AppMonitorLogger.i("EVENT=APP_SWITCH pkg=$pkgName prev=$lastAppliedPkg sw=$currentSwitchId")
                // قرار "مُدار/غير مُدار" يحتاج الكاش طازجًا: بلا هذا
                // الإنعاش كانت أول إحالة بعد إقلاع الرفيق تمر بالمسار
                // السريع فلا تنشط مهلة السماح إلا من التبديل الثاني.
                refreshConfigCacheIfChanged()
                val prevPkg = lastAppliedPkg
                val prevManaged = prevPkg.isNotBlank() &&
                    cachedGameListText?.contains("\"$prevPkg\":") == true
                val newManaged = pkgName.isNotBlank() && pkgName != "unknown" && pkgName != "none" &&
                    cachedGameListText?.contains("\"$pkgName\":") == true

                if (newManaged || !prevManaged) {
                    // مسار سريع: تطبيق مُدار جديد (يأخذ الملكية فورًا)،
                    // أو مغادرة تطبيق غير مُدار (لا شيء مؤجل أصلًا).
                    gracePkg = null
                    if (prevPkg.isNotBlank()) {
                        runCatching { revertPerAppConfig() }
                            .onFailure { AppMonitorLogger.e("EVENT=REVERT_FAILED pkg=$prevPkg sw=$currentSwitchId", it) }
                    }
                    if (pkgName.isNotBlank() && pkgName != "unknown" && pkgName != "none") {
                        runCatching { applyPerAppConfig(pkgName) }
                            .onFailure { AppMonitorLogger.e("EVENT=APPLY_FAILED pkg=$pkgName sw=$currentSwitchId reason=no_per_app_overrides_applied", it) }
                    }
                } else {
                    // مغادرة تطبيق مُدار نحو غير مُدار: مهلة سماح — لا
                    // تراجع فوريًا. التنقل السريع ذهابًا وإيابًا لا
                    // يهز العتاد، وموت التطبيق يُعجّل التراجع (أعلاه).
                    graceDeadlineMs = android.os.SystemClock.elapsedRealtime() + PERAPP_GRACE_MS
                    gracePkg = prevPkg
                    AppMonitorLogger.i(
                        "EVENT=PERAPP_GRACE_BEGIN pkg=$prevPkg duration_ms=$PERAPP_GRACE_MS sw=$currentSwitchId"
                    )
                }
            }
            lastAppliedPkg = pkgName
            val parts = focusedApp.split(" ")
            val focusedPid = parts.getOrNull(1) ?: "0"
            val focusedUid = parts.getOrNull(2) ?: "0"
            val managed = cachedGameListText?.contains("\"$pkgName\":") == true
            if (managed) {
                writeAppGameInfo(pkgName, focusedPid, focusedUid)
                updateActiveAppNotification(pkgName, focusedApp)
            } else {
                writeAppGameInfo("", "0", "0")
                clearActiveAppNotification()
            }
            baselineCaptured = if (managed) baselineCaptured else false
        }

        // If the user changes the current app's JSON profile while the app is already
        // in the foreground, apply the new values without requiring an app restart.
        // This is deliberately keyed to the file mtime, so normal 500 ms polling does
        // not repeatedly revert/reapply the same configuration.
        if (pkgName == lastAppliedPkg) {
            val configFile = File(MaxManagerPaths.APPLIST_JSON)
            val modified = if (configFile.exists()) configFile.lastModified() else -1L
            if (modified != cachedGameListModified) {
                runCatching {
                    revertPerAppConfig()
                    applyPerAppConfig(pkgName)
                    lastAppliedPkg = pkgName
                    updateActiveAppNotification(pkgName, focusedApp)
                }.onFailure { AppMonitorLogger.e("EVENT=LIVE_CONFIG_REAPPLY_FAILED pkg=$pkgName sw=$currentSwitchId", it) }
            }
        }

        // Per-app touch policy explicitly overrides the ROM default only while
        // it is set. Otherwise the ROM-wide Touch Boost control remains the
        // source of truth (with the legacy game heuristic as a compatibility
        // fallback when that global property has not been configured yet).
        val globalTouchBoost = shellRead("getprop persist.sys.maxmanager.custom_touch_boost") == "1"
        val touchBoostDecision = when (touchBoostOverride) {
            "true" -> true
            "false" -> false
            else -> globalTouchBoost || isKnownGameApp(pkgName)
        }
        runCatching {
            TouchBoostViewModel.applyBestEffortBoost(screenAwake == 1 && touchBoostDecision)
            XiaomiVendorFeatures.applyAodColorOverride(
                context = systemContext,
                screenAwake = screenAwake == 1,
                aodEnabled = isAodEnabled(),
            )
        }.onFailure { AppMonitorLogger.e("Xiaomi vendor extras (touch boost / AOD colour) failed for '$pkgName' sw=$currentSwitchId", it) }

        return buildString {
            appendLine("focused_app $focusedApp")
            appendLine("screen_awake $screenAwake")
            appendLine("battery_saver $batterySaver")
            appendLine("zen_mode $zenMode")
            appendLine("battery_level $batteryLevel")
            appendLine("is_charging $isCharging")
            appendLine("app_name $appName")
            appendLine("refresh_rate $currentRefreshRate")
            appendLine("max_refresh_rate $maxRefreshRate")
            appendLine("switch_id $currentSwitchId")
            // تعديلات per-app حيّة الآن (أثناء التطبيق أو مهلة السماح):
            // يقرؤها محرك MAX AI فيتحول إلى وضع المراقبة بلا تدخل.
            appendLine("perapp_active ${if (perAppOverridesActive) 1 else 0}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Per-App Config Helpers (reads APPLIST_JSON, applies/reverts settings)
    // ─────────────────────────────────────────────────────────────────────

    private fun shellExec(cmd: String) {
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd)).apply {
                waitFor()
                destroy()
            }
        } catch (_: Exception) {}
    }

    private fun shellRead(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val result = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            p.destroy()
            result
        } catch (_: Exception) { "" }
    }

    /**
     * كتابة عقدة sysfs عبر رقصة chmod نفسها التي تستخدمها ثنائيات الوحدة
     * (binutils setsgov / binprofiles write_unlock_core): على HyperOS 3
     * (المُختبر الجاهز rodin/Dimensity 8400 Ultra) تُرفض الكتابة الجذرية
     * المباشرة على عقد 0444 بـEACCES بينما تنجح الكتابة بعد chmod. بدون
     * هذه الرقصة فشلت كل كتابات scaling_governor/min/max هنا بصمت — سجل
     * الجهاز الحقيقي أظهر live-value-mismatch في كل PERAPP_COMMIT. يُستعاد
     * الوضع الأصلي للعقدة بعد الكتابة حفاظًا على حالة العقدة كما كانت.
     */
    private fun sysfsWrite(path: String, value: String) {
        shellExec(
            "m=\$(stat -c %a '$path' 2>/dev/null); " +
                "chmod 644 '$path' 2>/dev/null; " +
                "echo '$value' > '$path' 2>/dev/null; " +
                "chmod \$m '$path' 2>/dev/null"
        )
    }

    private fun refreshConfigCacheIfChanged() {
        runCatching {
            val file = File(MaxManagerPaths.APPLIST_JSON)
            val modified = if (file.exists()) file.lastModified() else -1L
            if (modified != cachedGameListModified) {
                cachedGameListText = if (file.exists()) file.readText() else null
                cachedGameListModified = modified
            }
        }.onFailure { AppMonitorLogger.e("EVENT=APPLIST_CACHE_REFRESH_FAILED", it) }
    }

    private fun readAppConfigField(pkgName: String, field: String): String {
        refreshConfigCacheIfChanged()
        val json = cachedGameListText ?: return ""
        return try {
            // Fast regex-based extraction: find the package block and the field inside it
            val pkgPattern = Regex(""""$pkgName"\s*:\s*\{([^}]+)\}""")
            val pkgBlock = pkgPattern.find(json)?.groupValues?.getOrNull(1) ?: return ""
            val fieldPattern = Regex(""""$field"\s*:\s*"([^"]*)"|\b$field\b\s*:\s*([^,}\n]+)""")
            fieldPattern.find(pkgBlock)?.groupValues?.let { it[1].ifEmpty { it[2].trim() } } ?: ""
        } catch (_: Exception) { "" }
    }

    @Synchronized
    private fun applyPerAppConfig(pkgName: String) {
        hardwareControlRegistry.beginApp(pkgName)
        // Refresh cache and verify this package has an enabled Per-App entry.
        refreshConfigCacheIfChanged()
        if (cachedGameListText?.contains("\"$pkgName\":") != true) return

        // Save the live kernel state before any per-app override.
        savedGpuNode = PerAppKernelUtil.findGpuNode().orEmpty()
        savedGpuGovernor = if (savedGpuNode.isNotBlank()) shellRead("cat '$savedGpuNode/governor' 2>/dev/null") else ""
        savedGpuMinFreq = if (savedGpuNode.isNotBlank()) shellRead("cat '$savedGpuNode/min_freq' 2>/dev/null") else ""
        savedGpuMaxFreq = if (savedGpuNode.isNotBlank()) shellRead("cat '$savedGpuNode/max_freq' 2>/dev/null") else ""
        savedCpuGovernors.clear()
        shellRead("for p in /sys/devices/system/cpu/cpufreq/policy*; do [ -f \"\$p/scaling_governor\" ] && echo \"\$p=\$(cat \$p/scaling_governor)\"; done").split("\n").forEach { line ->
            val eq = line.indexOf('=')
            if (eq > 0) savedCpuGovernors[line.substring(0, eq)] = line.substring(eq + 1).trim()
        }
        savedCpuMinFreqs.clear()
        savedCpuMaxFreqs.clear()
        CpuHardwareBackend.policies().forEach { policy ->
            policy.minKHz?.let { savedCpuMinFreqs[policy.path] = it.toString() }
            policy.maxKHz?.let { savedCpuMaxFreqs[policy.path] = it.toString() }
        }

        if (!baselineCaptured) {
            baselineGpuGovernor = savedGpuGovernor
            baselineCpuGovernors.clear()
            baselineCpuGovernors.putAll(savedCpuGovernors)
            baselineCaptured = true
            AppMonitorLogger.i("EVENT=PERAPP_GOV_BASELINE_CAPTURED cpu=${baselineCpuGovernors.size} gpu=$baselineGpuGovernor sw=$currentSwitchId")
            val baselineJournal = linkedMapOf<String, String>()
            baselineCpuGovernors.forEach { (path, value) -> baselineJournal["cpu_governor:$path"] = value }
            if (savedGpuNode.isNotBlank()) baselineJournal["gpu_node"] = savedGpuNode
            if (baselineGpuGovernor.isNotBlank()) baselineJournal["gpu_governor"] = baselineGpuGovernor
            if (savedGpuMinFreq.isNotBlank()) baselineJournal["gpu_min_freq"] = savedGpuMinFreq
            if (savedGpuMaxFreq.isNotBlank()) baselineJournal["gpu_max_freq"] = savedGpuMaxFreq
            PerAppRecoveryStore.markActive(pkgName, baselineJournal)
        }
        savedThermalProfile = shellRead("getprop sys.thermal.profile")
        // These 4 reads used to call the Settings ContentProvider API directly with
        // no runCatching around them at all. On this device/ROM the process's faked
        // system Context isn't a real app registered with ActivityManagerService, so
        // EVERY Settings.* ContentProvider call throws:
        //   SecurityException: Unable to find app for caller
        //   android.app.IApplicationThread$Stub$Proxy@... when getting content provider settings
        // Because these 4 lines were unguarded, that exception aborted
        // applyPerAppConfig() right here on every single app switch -- before any of
        // the per-app knobs below (GPU profile, CPU governor, touch boost, etc.) ever
        // ran. The daemon still correctly saw the app switch (app_status was written
        // fine by writeStatus()/buildStatus()), but literally none of the per-app
        // overrides ever got applied on this ROM, which is what looked like "the
        // daemon doesn't know which app is open."
        // Fixed the same way PerAppRefreshRateController.writeSetting() already does
        // elsewhere in this project: try the API, fall back to the `settings` shell
        // command (a legitimate caller, so it works even when the API path doesn't),
        // and never let one failing read take the rest of the function down with it.
        savedZenMode = runCatching {
            systemContext?.contentResolver?.let { Settings.Global.getInt(it, "zen_mode", 0) }
        }.getOrNull() ?: shellRead("settings get global zen_mode").toIntOrNull()
        savedPeakRefreshRate = runCatching {
            systemContext?.contentResolver?.let { Settings.System.getString(it, "peak_refresh_rate") }
        }.getOrNull()
            ?: shellRead("settings get system peak_refresh_rate").takeIf { it.isNotEmpty() && it != "null" }
            ?: ""
        savedMinRefreshRate = runCatching {
            systemContext?.contentResolver?.let { Settings.System.getString(it, "min_refresh_rate") }
        }.getOrNull()
            ?: shellRead("settings get system min_refresh_rate").takeIf { it.isNotEmpty() && it != "null" }
            ?: ""
        savedVendorRefreshSnapshot = runCatching { systemContext?.let { PerAppRefreshRateController.snapshot(it) } }.getOrNull()

        runCatching {
            PerAppRecoveryStore.capture(
                PerAppRecoveryStore.Snapshot(
                    bootId = PerAppRecoveryStore.bootId(),
                    packageName = pkgName,
                    gpuNode = savedGpuNode,
                    gpuGovernor = savedGpuGovernor,
                    gpuMin = savedGpuMinFreq,
                    gpuMax = savedGpuMaxFreq,
                    cpuGovernors = savedCpuGovernors.toMap(),
                    cpuMinFreqs = savedCpuMinFreqs.toMap(),
                    cpuMaxFreqs = savedCpuMaxFreqs.toMap(),
                    zenMode = savedZenMode?.toString().orEmpty(),
                    peakRefresh = savedPeakRefreshRate,
                    minRefresh = savedMinRefreshRate,
                    vendorRefreshNamespace = savedVendorRefreshSnapshot?.vendorNamespace.orEmpty(),
                    vendorRefreshKey = savedVendorRefreshSnapshot?.vendorKey.orEmpty(),
                    vendorRefreshValue = savedVendorRefreshSnapshot?.vendorValue.orEmpty(),
                    vendorRefreshAltKey = savedVendorRefreshSnapshot?.vendorAltKey.orEmpty(),
                    vendorRefreshAltValue = savedVendorRefreshSnapshot?.vendorAltValue.orEmpty(),
                    thermalProfile = savedThermalProfile,
                    hwUiProp = shellRead("getprop persist.sys.ui.hw"),
                    disableHwProp = shellRead("getprop debug.viewroot.disableHW"),
                    hapticEnabled = shellRead("settings get system haptic_feedback_enabled").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
                )
            )
        }.onFailure { AppMonitorLogger.w("EVENT=PERAPP_RECOVERY_CAPTURE_FAILED pkg=$pkgName sw=$currentSwitchId", it) }


        // Each knob below is independent: if one throws (e.g. a
        // WRITE_SECURE_SETTINGS SecurityException on ROMs that don't grant
        // it), the rest still get a chance to apply instead of the whole
        // per-app config silently aborting partway through.
        // Register the concrete hardware knobs with the ownership layer before applying them.
        // This makes drift repair use the same desired value and prevents a later subsystem
        // from silently becoming the owner of a per-app override.
        runCatching {
            val cpuGovernor = readAppConfigField(pkgName, "cpu_governor")
            if (cpuGovernor.isNotBlank() && cpuGovernor != "default") {
                CpuHardwareBackend.policies().forEach { policy ->
                    val baseline = policy.governor
                    hardwareControlRegistry.ownGovernor(
                        key = "cpu_governor:${policy.name}",
                        desired = cpuGovernor,
                        apply = { value -> CpuHardwareBackend.setPolicyGovernor(policy.path, value).successful },
                        read = { CpuHardwareBackend.policies().firstOrNull { it.name == policy.name }?.governor },
                        baseline = baseline,
                        restore = { value -> CpuHardwareBackend.setPolicyGovernor(policy.path, value).successful },
                    )
                }
            }

            val gpuGovernor = readAppConfigField(pkgName, "gpu_governor")
            if (gpuGovernor.isNotBlank() && gpuGovernor != "default") {
                val gpuNode = savedGpuNode.takeIf { it.isNotBlank() }
                    ?: PerAppKernelUtil.findGpuNode()
                val generic = gpuNode?.let { path ->
                    GpuHardwareBackend.devices().firstOrNull { it.path == path }
                }
                if (generic != null && gpuGovernor in generic.governors) {
                    val baseline = generic.governor
                    hardwareControlRegistry.ownGovernor(
                        key = "gpu_governor:${generic.name}",
                        desired = gpuGovernor,
                        apply = { value -> GpuHardwareBackend.setGovernor(generic, value).successful },
                        read = { GpuHardwareBackend.devices().firstOrNull { it.path == generic.path }?.governor },
                        baseline = baseline,
                        restore = { value -> GpuHardwareBackend.setGovernor(generic, value).successful },
                    )
                }
            }
        }.onFailure { AppMonitorLogger.e("ownership: governor registration failed for '$pkgName' sw=$currentSwitchId", it) }

        runCatching {
            val encodedPolicyControls = readAppConfigField(pkgName, "cpu_policy_controls")
            val policyControls = decodePerAppCpuPolicyControls(encodedPolicyControls)
            if (encodedPolicyControls.isNotBlank() && policyControls.isEmpty()) {
                activePerAppCpuPackage = pkgName
                writePerAppCpuStatus(pkgName, "failed", "CPU controls are invalid")
                AppMonitorLogger.w("EVENT=PERAPP_CPU_FAILED pkg=$pkgName reason=invalid-controls sw=$currentSwitchId")
            } else if (policyControls.isNotEmpty()) {
                activePerAppCpuPackage = pkgName
                writePerAppCpuStatus(pkgName, "applying", "Applying CPU controls")
                val policies = CpuHardwareBackend.policies().associateBy { it.name }
                var firstFailure: String? = null
                val validated = policyControls.mapNotNull { control ->
                    val policy = policies[control.policyName]
                    if (policy == null) {
                        if (firstFailure == null) firstFailure = "${control.policyName} is unavailable"
                        return@mapNotNull null
                    }
                    val supported = policy.availableFrequenciesKHz
                    val provenMin = policy.hwMinKHz ?: supported.firstOrNull()
                    val provenMax = policy.hwMaxKHz ?: supported.lastOrNull()
                    when {
                        supported.isNotEmpty() && (control.minKHz !in supported || control.maxKHz !in supported) -> {
                            if (firstFailure == null) firstFailure = "${control.policyName} frequency is unavailable"
                            null
                        }
                        provenMin == null || provenMax == null || control.minKHz < provenMin || control.maxKHz > provenMax -> {
                            if (firstFailure == null) firstFailure = "${control.policyName} range is unsupported"
                            null
                        }
                        else -> control to policy
                    }
                }
                val acquiredKeys = mutableListOf<String>()
                if (firstFailure == null && validated.size == policyControls.size) validated.forEach { (control, policy) ->
                    if (firstFailure != null) return@forEach
                    val requested = "${control.minKHz}:${control.maxKHz}"
                    val liveRange = "${policy.minKHz ?: ""}:${policy.maxKHz ?: ""}"
                    val key = "cpu_limits:${policy.name}"
                    val owned = hardwareControlRegistry.ownValue(
                        key = key,
                        desired = requested,
                        apply = { value ->
                            val parts = value.split(":", limit = 2)
                            CpuHardwareBackend.setPolicyLimits(policy.path, parts[0].toLongOrNull(), parts[1].toLongOrNull()).successful
                        },
                        read = {
                            CpuHardwareBackend.policies().firstOrNull { it.name == policy.name }?.let {
                                "${it.minKHz ?: ""}:${it.maxKHz ?: ""}"
                            }
                        },
                        baseline = liveRange,
                        restore = { value ->
                            val parts = value.split(":", limit = 2)
                            CpuHardwareBackend.setPolicyLimits(policy.path, parts[0].toLongOrNull(), parts[1].toLongOrNull()).successful
                        },
                    )
                    val verified = CpuHardwareBackend.policies().firstOrNull { it.name == policy.name }?.let {
                        "${it.minKHz ?: ""}:${it.maxKHz ?: ""}" == requested
                    } == true
                    if (owned && verified) {
                        acquiredKeys += key
                    } else if (firstFailure == null) {
                        firstFailure = "${policy.name} was not verified"
                    }
                }
                if (firstFailure != null) acquiredKeys.asReversed().forEach(hardwareControlRegistry::release)
                if (firstFailure == null) {
                    writePerAppCpuStatus(pkgName, "applied", "CPU controls verified")
                    AppMonitorLogger.i("EVENT=PERAPP_CPU_APPLIED pkg=$pkgName policies=${policyControls.size} sw=$currentSwitchId")
                } else {
                    writePerAppCpuStatus(pkgName, "failed", firstFailure)
                    AppMonitorLogger.w("EVENT=PERAPP_CPU_FAILED pkg=$pkgName reason=$firstFailure sw=$currentSwitchId")
                }
            }
        }.onFailure {
            activePerAppCpuPackage = pkgName
            writePerAppCpuStatus(pkgName, "failed", "CPU control failed")
            AppMonitorLogger.e("ownership: per-app CPU control failed for '$pkgName' sw=$currentSwitchId", it)
        }

        runCatching {
            val cpuMin = readAppConfigField(pkgName, "cpu_min_freq").toLongOrNull()
            val cpuMax = readAppConfigField(pkgName, "cpu_max_freq").toLongOrNull()
            val hasPolicyControls = readAppConfigField(pkgName, "cpu_policy_controls").isNotBlank()
            if (!hasPolicyControls && (cpuMin != null || cpuMax != null)) {
                CpuHardwareBackend.policies().forEach { policy ->
                    val baseline = "${policy.minKHz ?: ""}:${policy.maxKHz ?: ""}"
                    val desired = "${cpuMin ?: ""}:${cpuMax ?: ""}"
                    hardwareControlRegistry.ownValue(
                        key = "cpu_limits:${policy.name}",
                        desired = desired,
                        apply = { value ->
                            val parts = value.split(":", limit = 2)
                            val min = parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                            val max = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                            CpuHardwareBackend.setPolicyLimits(policy.path, min, max).successful
                        },
                        read = {
                            CpuHardwareBackend.policies().firstOrNull { it.name == policy.name }?.let {
                                "${it.minKHz ?: ""}:${it.maxKHz ?: ""}"
                            }
                        },
                        baseline = baseline,
                        restore = { value ->
                            val parts = value.split(":", limit = 2)
                            val min = parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                            val max = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                            CpuHardwareBackend.setPolicyLimits(policy.path, min, max).successful
                        },
                    )
                }
            }
        }.onFailure { AppMonitorLogger.e("ownership: CPU frequency registration failed for '$pkgName' sw=$currentSwitchId", it) }

        // Per-app GPU profile: frequency ceiling only. Default is a true no-op.
        runCatching {
            val profile = readAppConfigField(pkgName, "gpu_profile").ifEmpty {
                val legacy = readAppConfigField(pkgName, "thermal_profile")
                when (legacy) { "powersave" -> "power"; else -> legacy }
            }
            val node = savedGpuNode.takeIf { it.isNotBlank() }
            val caps = PerAppKernelUtil.readGpuCapabilities()
            val explicitFreq = readAppConfigField(pkgName, "gpu_max_freq").toLongOrNull()
            val target = explicitFreq ?: PerAppKernelUtil.pickProfileFrequency(caps.frequencies, profile, ProfilePresetStore.percentFor(systemContext, profile))
            if (target != null) {
                PerAppKernelUtil.applyGpuFixedFrequency(node, caps, target)
                val mtkIndex = PerAppKernelUtil.mtkOppIndexForFrequency(caps, target)
                if (mtkIndex != null) {
                    val liveLock = PerAppKernelUtil.currentMtkGpuLockIndex()
                    if (liveLock != mtkIndex) {
                        AppMonitorLogger.w("EVENT=APPLY_VERIFY_FAILED knob=gpu_opp_lock pkg=$pkgName expected_index=$mtkIndex live_index=${liveLock ?: "none"} expected_hz=$target sw=$currentSwitchId")
                    }
                } else {
                    val liveMaxFreq = if (!node.isNullOrBlank()) shellRead("cat '$node/max_freq' 2>/dev/null").toLongOrNull() else null
                    if (liveMaxFreq != null && liveMaxFreq != target) {
                        AppMonitorLogger.w("EVENT=APPLY_VERIFY_FAILED knob=gpu_profile pkg=$pkgName expected=$target live=$liveMaxFreq sw=$currentSwitchId")
                    }
                }
            }
        }.onFailure { AppMonitorLogger.e("gpu_profile knob failed for '$pkgName' sw=$currentSwitchId", it) }

        runCatching {
            val explicitGpuMax = readAppConfigField(pkgName, "gpu_max_freq").toLongOrNull()
            val caps = PerAppKernelUtil.readGpuCapabilities()
            val mtkAuthoritative = explicitGpuMax != null && PerAppKernelUtil.mtkOppIndexForFrequency(caps, explicitGpuMax) != null
            val generic = GpuHardwareBackend.devices().firstOrNull()
            if (explicitGpuMax != null && generic != null && !mtkAuthoritative) {
                val baseline = RootFileAccess.read("${generic.path}/max_freq")
                hardwareControlRegistry.ownValue(
                    key = "gpu_max_freq:${generic.name}",
                    desired = explicitGpuMax.toString(),
                    apply = { value -> value.toLongOrNull()?.let { PerAppFrequencyController.applyGpuCeiling(it).let { result -> result.applied && result.verified } } ?: false },
                    read = { RootFileAccess.read("${generic.path}/max_freq")?.trim() },
                    baseline = baseline,
                    restore = { value ->
                        val target = value.toLongOrNull()
                        if (target == null) false else VerifiedControl.apply(
                            requested = target,
                            write = { RootFileAccess.write("${generic.path}/max_freq", it.toString()) },
                            read = { RootFileAccess.read("${generic.path}/max_freq")?.toLongOrNull() },
                        ).successful
                    },
                )
            }
        }.onFailure { AppMonitorLogger.e("ownership: GPU frequency registration failed for '$pkgName' sw=$currentSwitchId", it) }

        // Governors are now applied by the ownership registry below. This keeps the
        // initial apply path and the drift-repair path on exactly the same verified state.

        runCatching {
            val boostNode = CpuHardwareBackend.boostNode()
            val requestedBoost = readAppConfigField(pkgName, "cpu_boost") == "true"
            if (boostNode != null && readAppConfigField(pkgName, "cpu_boost").isNotBlank()) {
                val baseline = RootFileAccess.read(boostNode)?.trim()
                hardwareControlRegistry.ownValue(
                    key = "cpu_boost",
                    desired = if (requestedBoost) "1" else "0",
                    apply = { value -> CpuHardwareBackend.setBoost(value == "1").successful },
                    read = { RootFileAccess.read(boostNode)?.trim() },
                    baseline = baseline,
                    restore = { value -> CpuHardwareBackend.setBoost(value == "1").successful },
                )
            }
            stopCpuBoostAndRestore()
            if (requestedBoost) {
                cpuBoostOriginalMins.clear()
                val snapshot = readCpuMinFrequencies()
                cpuBoostOriginalMins.putAll(snapshot)
                cpuBoostGeneration++
                val generation = cpuBoostGeneration
                cpuBoostThread = Thread {
                    try {
                        snapshot.keys.forEach { path ->
                            val max = shellRead("cat '$path/cpuinfo_max_freq' 2>/dev/null")
                            if (max.isNotBlank()) sysfsWrite("$path/scaling_min_freq", max)
                        }
                        Thread.sleep(3000L)
                        if (!Thread.currentThread().isInterrupted && generation == cpuBoostGeneration) {
                            snapshot.forEach { (path, min) -> sysfsWrite("$path/scaling_min_freq", min) }
                        }
                    } catch (_: InterruptedException) {
                        // Revert path restores the saved values synchronously.
                    }
                }.apply { isDaemon = true; start() }
            }
        }.onFailure { AppMonitorLogger.e("cpu_boost knob failed for '$pkgName' sw=$currentSwitchId", it) }

        runCatching {
            val dnd = readAppConfigField(pkgName, "dnd_on_gaming")
            if (dnd == "true") {
                wasZenSet = true
                val changed = runCatching {
                    systemContext?.contentResolver?.let { Settings.Global.putInt(it, "zen_mode", 1) } == true
                }.getOrDefault(false)
                if (!changed) shellExec("settings put global zen_mode 1")
            }
        }.onFailure { AppMonitorLogger.e("dnd_on_gaming knob failed for '$pkgName' sw=$currentSwitchId", it) }

        runCatching {
            val killBg = readAppConfigField(pkgName, "kill_bg_apps")
            if (killBg == "true") {
                shellExec("am kill-all")
            }
        }.onFailure { AppMonitorLogger.e("kill_bg_apps knob failed for '$pkgName' sw=$currentSwitchId", it) }

        runCatching {
            val requestedRefresh = readAppConfigField(pkgName, "refresh_rate").toIntOrNull()
            val context = systemContext
            if (requestedRefresh != null && context != null) {
                val normalized = PerAppRefreshRateController.normalizeRequestedRate(context, requestedRefresh)
                val baseline = PerAppRefreshRateController.currentEnforcedRate(context)?.toString()
                if (normalized != null) {
                    hardwareControlRegistry.ownValue(
                        key = "refresh_rate",
                        desired = normalized.toString(),
                        apply = { value -> value.toIntOrNull()?.let { PerAppRefreshRateController.apply(context, it) } ?: false },
                        read = { PerAppRefreshRateController.currentEnforcedRate(context)?.toString() },
                        baseline = baseline,
                        restore = { value ->
                            val hz = value.toIntOrNull()
                            hz != null && PerAppRefreshRateController.apply(context, hz)
                        },
                    )
                }
            }
        }.onFailure { AppMonitorLogger.e("ownership: refresh registration failed for '$pkgName' sw=$currentSwitchId", it) }

        // refresh_rate is applied through the ownership registry together with its baseline.

        // One verified commit point for all generic owned controls registered above.
        // This avoids separate writers racing each other and makes the initial apply path
        // identical to the periodic drift-repair path. Vendor-specific OPP/thermal controls
        // remain outside this generic registry because their adapters own their own protocol.
        runCatching {
            val commitResults = hardwareControlRegistry.verifyAndRepair()
            commitResults.forEach { result ->
                AppMonitorLogger.i(
                    "EVENT=PERAPP_COMMIT pkg=$pkgName knob=${result.key} requested=${result.requested} " +
                        "applied=${result.applied} verified=${result.verified} attempts=${result.attempts} " +
                        "live=${result.actual ?: "none"} error=${result.error ?: "none"} sw=$currentSwitchId"
                )
            }
            if (activePerAppCpuPackage == pkgName) {
                val failure = commitResults.firstOrNull { it.key.startsWith("cpu_limits:") && !it.successful }
                if (failure != null) {
                    writePerAppCpuStatus(pkgName, "failed", "${failure.key.removePrefix("cpu_limits:")} was not verified")
                    AppMonitorLogger.w("EVENT=PERAPP_CPU_FAILED pkg=$pkgName reason=${failure.error ?: "live-value-mismatch"} sw=$currentSwitchId")
                }
            }
        }.onFailure { AppMonitorLogger.e("ownership: verified commit failed for '$pkgName' sw=$currentSwitchId", it) }

        // touch_boost: per-app override. `default` deliberately follows the
        // ROM's global touch-boost setting; explicit true/false wins only for
        // this foreground app. This prevents the per-app screen from silently
        // re-enabling a global feature the user intentionally turned off.
        runCatching {
            val touchBoost = readAppConfigField(pkgName, "touch_boost")
            touchBoostOverride = if (touchBoost in setOf("true", "false")) touchBoost else "default"
        }.onFailure { AppMonitorLogger.e("touch_boost knob failed for '$pkgName' sw=$currentSwitchId", it) }

        // force_hw_ui: mirrors the "Force GPU rendering" developer option
        // (persist.sys.ui.hw + debug.viewroot.disableHW). Like the renderer
        // prop archdaemon manages, HWUI reads this at window/process init, so
        // an already-running process may not visibly change until its next
        // cold start — this sets the prop so it takes effect from then on,
        // without force-restarting the app the user just switched into.
        runCatching {
            when (readAppConfigField(pkgName, "force_hw_ui")) {
                "true", "false" -> {
                    if (!forcedHwUi) {
                        savedHwUiProp = shellRead("getprop persist.sys.ui.hw").takeIf { it.isNotBlank() }
                        savedDisableHwProp = shellRead("getprop debug.viewroot.disableHW").takeIf { it.isNotBlank() }
                    }
                    if (readAppConfigField(pkgName, "force_hw_ui") == "true") {
                        shellExec("setprop persist.sys.ui.hw true; setprop debug.viewroot.disableHW false")
                    } else {
                        shellExec("setprop persist.sys.ui.hw false; setprop debug.viewroot.disableHW true")
                    }
                    forcedHwUi = true
                }
            }
        }.onFailure { AppMonitorLogger.e("force_hw_ui knob failed for '$pkgName' sw=$currentSwitchId", it) }

        // haptic_feedback: "true" in the per-app config means "Reduce Haptic
        // Feedback" is ON, i.e. vibration gets disabled. Save the user's real
        // setting first so revert restores their actual preference.
        runCatching {
            if (readAppConfigField(pkgName, "haptic_feedback") == "true") {
                val context = systemContext
                if (context != null) {
                    savedHapticFeedbackEnabled = context.contentResolver?.let {
                        Settings.System.getString(it, "haptic_feedback_enabled")
                    }
                    val changed = runCatching {
                        context.contentResolver?.let { Settings.System.putInt(it, "haptic_feedback_enabled", 0) } == true
                    }.getOrDefault(false)
                    if (!changed) shellExec("settings put system haptic_feedback_enabled 0")
                } else {
                    shellExec("settings put system haptic_feedback_enabled 0")
                }
            }
        }.onFailure { AppMonitorLogger.e("haptic_feedback knob failed for '$pkgName' sw=$currentSwitchId", it) }

        // disable_notifs: mutes the notification audio stream (sounds) while
        // this app is focused. This is deliberately independent from
        // dnd_on_gaming above (which flips zen_mode globally) — it only
        // silences this one stream, so it can be layered on top of DND or
        // used alone. Notification badges/heads-up visuals aren't touched
        // here, only sound.
        runCatching {
            if (readAppConfigField(pkgName, "disable_notifs") == "true") {
                val am = systemContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (am != null && !am.isStreamMute(AudioManager.STREAM_NOTIFICATION)) {
                    am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                    wasNotifStreamMuted = true
                }
            }
        }.onFailure { AppMonitorLogger.e("disable_notifs knob failed for '$pkgName' sw=$currentSwitchId", it) }

        // wifi_no_sleep: standard high-perf WifiLock, held only while this
        // app stays focused and released the moment focus moves away
        // (revertPerAppConfig / next applyPerAppConfig call).
        runCatching {
            if (readAppConfigField(pkgName, "wifi_no_sleep") == "true") {
                val wm = systemContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiNoSleepLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MaxManager:wifiNoSleep")
                    ?.apply {
                        setReferenceCounted(false)
                        acquire()
                    }
            }
        }.onFailure { AppMonitorLogger.e("wifi_no_sleep knob failed for '$pkgName' sw=$currentSwitchId", it) }

        // تعديلات هذا التطبيق حيّة على العتاد الآن: يُعلن في app_status
        // (perapp_active 1) فيدخل محرك MAX AI وضع المراقبة.
        perAppOverridesActive = true
    }

    private fun readCpuMinFrequencies(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        shellRead("for p in /sys/devices/system/cpu/cpufreq/policy*; do [ -f \"\$p/scaling_min_freq\" ] && echo \"\$p=\$(cat \"\$p/scaling_min_freq\")\"; done")
            .split("\n").forEach { line ->
                val eq = line.indexOf('=')
                if (eq > 0) result[line.substring(0, eq)] = line.substring(eq + 1).trim()
            }
        return result
    }

    private fun stopCpuBoostAndRestore() {
        cpuBoostGeneration++
        cpuBoostThread?.interrupt()
        cpuBoostThread = null
        cpuBoostOriginalMins.forEach { (path, min) ->
            sysfsWrite("$path/scaling_min_freq", min)
        }
        cpuBoostOriginalMins.clear()
    }

    private fun restoreProp(name: String, value: String?) {
        if (value.isNullOrBlank()) shellExec("resetprop -n '$name'")
        else shellExec("setprop '$name' '${value.replace("'", "")}'")
    }

    /**
     * استرجاع مفتاح Settings.System مع بديل shell عند رفض المزوّد.
     * القيمة الفارغة تعني حذف المفتاح (نفس دلالة putString(key, null)).
     */
    private fun restoreSystemSettingWithShellFallback(key: String, value: String) {
        val apiOk = runCatching {
            systemContext?.contentResolver?.let {
                Settings.System.putString(it, key, value.ifEmpty { null })
            } == true
        }.getOrDefault(false)
        if (apiOk) return
        if (value.isEmpty()) shellExec("settings delete system '$key'")
        else shellExec("settings put system '$key' '${value.replace("'", "")}'")
    }

    private fun writeAppGameInfo(pkg: String, pid: String, uid: String) {
        runCatching {
            val file = File("/data/data/nd.max/API/gameinfo")
            file.parentFile?.mkdirs()
            if (pkg.isBlank() || pkg == "unknown" || pkg == "none") {
                file.writeText("NULL 0 0\nTime: --:--:--\n")
                return
            }
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            file.writeText("$pkg ${pid.toIntOrNull() ?: 0} ${uid.toIntOrNull() ?: 0}\nTime: $time\n")
        }.onFailure { AppMonitorLogger.e("EVENT=GAME_INFO_WRITE_FAILED pkg=$pkg sw=$currentSwitchId", it) }
    }

    private fun updateActiveAppNotification(pkg: String, focusedApp: String) {
        runCatching {
            val context = systemContext ?: return@runCatching
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? AndroidNotificationManager ?: return@runCatching
            val channelId = "maxmanager_per_app"
            nm.createNotificationChannel(NotificationChannel(channelId, "Per-App Manager", AndroidNotificationManager.IMPORTANCE_LOW))
            val pid = focusedApp.substringAfter(" ", "0").substringBefore(" ")
            val profile = readAppConfigField(pkg, "gpu_profile").ifBlank { "default" }
            val thermal = readAppConfigField(pkg, "thermal_profile").ifBlank { "default" }
            val cpuGov = readAppConfigField(pkg, "cpu_governor").ifBlank { "default" }
            val gpuGov = readAppConfigField(pkg, "gpu_governor").ifBlank { "default" }
            val gpuFreq = readAppConfigField(pkg, "gpu_max_freq").ifBlank { "default" }
            val gpuProfile = if (profile == "default") thermal else profile
            val appLabel = getAppName(pkg).ifBlank { pkg }
            val liveCpuGov = shellRead("cat /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null").ifBlank { "N/A" }
            val liveGpuGov = savedGpuNode.takeIf { it.isNotBlank() }?.let { shellRead("cat '$it/governor' 2>/dev/null") }.orEmpty().ifBlank { "N/A" }
            val body = "PID: $pid\nThermal/GPU: $gpuProfile\nCPU Governor: $cpuGov (live: $liveCpuGov)\nGPU Governor: $gpuGov (live: $liveGpuGov)\nGPU Frequency: $gpuFreq"
            val intent = Intent().setClassName("nd.max", "nd.max.MainActivity")
            val pi = PendingIntent.getActivity(context, 2409, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder = Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("MaxManager • $appLabel")
                .setContentText("Per-App active • PID $pid • GPU $gpuProfile")
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
            nm.notify(2409, builder.build())
        }.onFailure { AppMonitorLogger.e("EVENT=PERAPP_NOTIFICATION_FAILED pkg=$pkg sw=$currentSwitchId", it) }
    }

    private fun clearActiveAppNotification() {
        runCatching {
            val context = systemContext ?: return@runCatching
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? AndroidNotificationManager)?.cancel(2409)
        }
    }

    private fun writePerAppCpuStatus(pkgName: String, state: String, message: String) {
        val safeMessage = message.replace('\n', ' ').replace('\r', ' ').take(140)
        RootFileAccess.atomicWriteText(
            MaxManagerPaths.PER_APP_CPU_STATUS,
            "package=$pkgName\nstate=$state\nmessage=$safeMessage\n"
        )
    }

    private fun restoreGlobalMaxManagerProfile(): Boolean {
        val profile = shellRead("cat /data/adb/.config/MaxManager/API/current_profile 2>/dev/null")
        if (profile !in setOf("1", "2", "3")) return false
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "/data/adb/modules/MaxManager/system/bin/sys.maxmanager-service --profile '$profile' >/dev/null 2>&1"))
            process.waitFor() == 0
        }.getOrDefault(false)
    }

    @Synchronized
    private fun revertPerAppConfig() {
        hardwareControlRegistry.releaseAll()
        stopCpuBoostAndRestore()
        // Always release any MediaTek gpufreqv2/legacy OPP-index lock first, unconditionally.
        // restoreGlobalMaxManagerProfile() below only reapplies the Global Tweaks page state
        // (devfreq nodes and props) - it doesn't know about the MTK proc interface, so
        // skipping this would leave the GPU pinned at the app's fixed frequency indefinitely
        // after the app closes, even once the "global restored" path succeeds.
        runCatching { PerAppKernelUtil.releaseGpuFixedFrequency() }
            .onFailure { AppMonitorLogger.e("revert: releaseGpuFixedFrequency() failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }
        // Reapply the current Global MaxManager profile first when possible.
        // This restores the state defined in the main Tweaks page instead of
        // blindly resetting nodes to hard-coded defaults.
        var globalRestored = false
        runCatching { globalRestored = restoreGlobalMaxManagerProfile() }
            .onFailure { AppMonitorLogger.e("revert: restoreGlobalMaxManagerProfile() failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        // If the global profile cannot be re-applied, restore the exact live state snapshot.
        if (!globalRestored) {
            runCatching {
                if (savedGpuNode.isNotBlank()) {
                    if (savedGpuGovernor.isNotBlank()) sysfsWrite("$savedGpuNode/governor", savedGpuGovernor)
                    if (savedGpuMinFreq.isNotBlank()) sysfsWrite("$savedGpuNode/min_freq", savedGpuMinFreq)
                    if (savedGpuMaxFreq.isNotBlank()) sysfsWrite("$savedGpuNode/max_freq", savedGpuMaxFreq)
                }
            }.onFailure { AppMonitorLogger.e("revert: GPU node snapshot restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }
        }

        // When the daemon successfully reapplies the user's current Global MaxManager
        // profile, that profile is the authoritative owner of CPU/GPU governors. Do not
        // write the old pre-app baseline over it: doing so made the Global profile look
        // broken immediately after leaving a per-app override. The baseline is only a
        // fallback for the case where the daemon profile could not be restored.
        if (!globalRestored) {
            runCatching {
                if (baselineGpuGovernor.isNotBlank() && savedGpuNode.isNotBlank()) {
                    sysfsWrite("$savedGpuNode/governor", baselineGpuGovernor)
                }
                baselineCpuGovernors.forEach { (path, gov) ->
                    sysfsWrite("$path/scaling_governor", gov)
                }
                savedCpuMaxFreqs.forEach { (path, max) ->
                    sysfsWrite("$path/scaling_max_freq", max)
                }
                savedCpuMinFreqs.forEach { (path, min) ->
                    sysfsWrite("$path/scaling_min_freq", min)
                }
            }.onFailure { AppMonitorLogger.e("revert: baseline governor restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }
        }
        if (activePerAppCpuPackage.isNotBlank()) {
            writePerAppCpuStatus(activePerAppCpuPackage, "restored", "CPU controls released")
            activePerAppCpuPackage = ""
        }

        runCatching {
            if (savedThermalProfile.isNotEmpty()) shellExec("setprop sys.thermal.profile '$savedThermalProfile'")
        }.onFailure { AppMonitorLogger.e("revert: thermal_profile restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            savedZenMode?.let { value ->
                val changed = runCatching {
                    systemContext?.contentResolver?.let { Settings.Global.putInt(it, "zen_mode", value) } == true
                }.getOrDefault(false)
                if (!changed) shellExec("settings put global zen_mode $value")
            }
            savedZenMode = null
            wasZenSet = false
        }.onFailure { AppMonitorLogger.e("revert: zen_mode restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            // Restore the vendor-specific Xiaomi path and the generic nodes.
            // The old generic nodes are intentionally retained as fallbacks.
            val context = systemContext
            val snapshot = savedVendorRefreshSnapshot
            if (context != null && snapshot != null) {
                PerAppRefreshRateController.restore(context, snapshot)
            } else if (context != null) {
                // نفس عقد zen_mode/haptic أعلاه: محاولة API أولًا ثم بديل shell.
                // الاسترجاع يحدث لحظة مغادرة التطبيق للمقدمة، وهناك يرفض مزوّد
                // الإعدادات (settings provider) المتصلات من الخلفية بـ
                // SecurityException "Unable to find app for caller" — بينما
                // مسار shell ينجح دائمًا. بدون هذا البديل كان الفشل يتكرر
                // عند كل تبديل تطبيق (سجل الجهاز: 30+ تكرارًا في دقيقة واحدة).
                restoreSystemSettingWithShellFallback("peak_refresh_rate", savedPeakRefreshRate)
                restoreSystemSettingWithShellFallback("min_refresh_rate", savedMinRefreshRate)
            }
            savedVendorRefreshSnapshot = null
            savedPeakRefreshRate = ""
            savedMinRefreshRate = ""
        }.onFailure { AppMonitorLogger.e("revert: refresh rate restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            // touch_boost: back to auto-detect for whatever app comes next
            touchBoostOverride = "default"
        }.onFailure { AppMonitorLogger.e("revert: touch_boost reset failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            if (forcedHwUi) {
                restoreProp("persist.sys.ui.hw", savedHwUiProp)
                restoreProp("debug.viewroot.disableHW", savedDisableHwProp)
                savedHwUiProp = null
                savedDisableHwProp = null
                forcedHwUi = false
            }
        }.onFailure { AppMonitorLogger.e("revert: force_hw_ui restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            savedHapticFeedbackEnabled?.let { saved ->
                val ok = runCatching {
                    systemContext?.contentResolver?.let { Settings.System.putString(it, "haptic_feedback_enabled", saved) } == true
                }.getOrDefault(false)
                if (!ok) shellExec("settings put system haptic_feedback_enabled '${saved.replace("'", "")}'")
            }
            savedHapticFeedbackEnabled = null
        }.onFailure { AppMonitorLogger.e("revert: haptic_feedback restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            if (wasNotifStreamMuted) {
                val am = systemContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                wasNotifStreamMuted = false
            }
        }.onFailure { AppMonitorLogger.e("revert: disable_notifs restore failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        runCatching {
            wifiNoSleepLock?.let { if (it.isHeld) it.release() }
            wifiNoSleepLock = null
        }.onFailure { AppMonitorLogger.e("revert: wifi_no_sleep lock release failed while leaving '$lastAppliedPkg' sw=$currentSwitchId", it) }

        savedGpuGovernor = ""
        savedGpuNode = ""
        savedGpuMinFreq = ""
        savedGpuMaxFreq = ""
        savedThermalProfile = ""
        savedCpuGovernors.clear()
        savedCpuMinFreqs.clear()
        savedCpuMaxFreqs.clear()
        baselineGpuGovernor = ""
        baselineCpuGovernors.clear()
        baselineCaptured = false
        lastDriftCheckAt = 0L
        driftRetries.clear()
        // ملكية per-app انتهت: يُعاد الإعلان (perapp_active 0) في أول
        // writeStatus تالية، فيستأنف محرك MAX AI إدارته.
        perAppOverridesActive = false
        writeAppGameInfo("", "0", "0")
        PerAppRecoveryStore.clear()
        clearActiveAppNotification()
    }



    
    private fun getZenMode(): Int {
        return try {
            getZenModeMethod?.invoke(notificationManager) as? Int ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun getFocusedAppInfo(): String {
        return try {
            val result = invokeForegroundMethod() ?: return UNKNOWN_APP
            if (result is List<*>) {
                getFocusedAppFromList(result)
            } else {
                resolveAppInfoFromObject(result)
            }
        } catch (e: Exception) {
            AppMonitorLogger.e("getFocusedAppInfo() failed", e)
            UNKNOWN_APP
        }
    }

    private fun getFocusedAppFromList(list: List<*>): String {
        if (list.isEmpty()) return NONE_APP
        list.forEach { element ->
            extractComponentName(element)?.let { return buildAppInfo(it.packageName) }
        }
        return resolveAppInfoFromObject(list[0]!!)
    }

    private fun resolveAppInfoFromObject(obj: Any): String {
        extractComponentName(obj)?.let { return buildAppInfo(it.packageName) }
        return findPackageLikeString(obj)?.let { buildAppInfo(it) } ?: UNKNOWN_APP
    }

    private fun invokeForegroundMethod(): Any? {
        val method = foregroundMethod ?: return null
        return tryInvokeForegroundMethod(method) ?: bruteForceForegroundMethod()
    }

    private fun tryInvokeForegroundMethod(method: Method): Any? {
        val name = method.name
        return try {
            when {
                name == "getTasks" || name == "getRunningTasks" -> {
                    tryInvokeWithArgs(
                        method,
                        activityTaskManager!!,
                        arrayOf(1),
                        arrayOf(1, 0),
                        arrayOf(1, false, false)
                    )
                }

                method.parameterTypes.isEmpty() -> method.invoke(activityTaskManager)
                else -> tryInvokeWithArgs(method, activityTaskManager!!, arrayOf(0))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryInvokeWithArgs(method: Method, target: Any, vararg argSets: Array<Any>): Any? {
        for (args in argSets) {
            try {
                return method.invoke(target, *args)
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun bruteForceForegroundMethod(): Any? {
        return try {
            val candidates =
                bruteForceCandidates ?: getDeclaredMethods(activityTaskManager!!.javaClass)
                    .filter {
                        val name = it.name.lowercase()
                        name.contains("focus") || name.contains("top") || name.contains("task")
                    }
                    .onEach { it.isAccessible = true }
                    .also { bruteForceCandidates = it }

            candidates.firstNotNullOfOrNull { method ->
                when {
                    method.parameterTypes.isEmpty() ->
                        tryInvokeQuietly { method.invoke(activityTaskManager) }

                    method.parameterTypes.size == 1 && method.parameterTypes[0] == Int::class.java ->
                        tryInvokeQuietly { method.invoke(activityTaskManager, 1) }

                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private inline fun tryInvokeQuietly(block: () -> Any?): Any? {
        return try {
            block()
        } catch (_: Exception) {
            null
        }
    }

    private fun extractComponentName(obj: Any?): ComponentName? {
        if (obj == null) return null
        if (obj is ComponentName) return obj

        COMPONENT_NAME_FIELDS.forEach { fieldName ->
            getComponentNameFromField(obj, obj.javaClass, fieldName)?.let { return it }
        }

        return scanHierarchyForComponentName(obj)
    }

    private fun getComponentNameFromField(
        obj: Any,
        cls: Class<*>,
        fieldName: String
    ): ComponentName? {
        return try {
            val field = cls.getDeclaredField(fieldName).apply { isAccessible = true }
            field.get(obj) as? ComponentName
        } catch (_: Exception) {
            null
        }
    }

    private fun scanHierarchyForComponentName(obj: Any): ComponentName? {
        var cls: Class<*>? = obj.javaClass
        while (cls != null && cls != Any::class.java) {
            getInstanceFields(cls).forEach { field ->
                try {
                    field.isAccessible = true
                    val value = field.get(obj)
                    if (value is ComponentName) return value
                } catch (_: Exception) {
                }
            }
            cls = cls.superclass
        }
        return null
    }

    private fun findPackageLikeString(obj: Any?): String? {
        if (obj == null) return null
        extractPackageName(obj.toString())?.let { return it }

        getInstanceFields(obj.javaClass).forEach { field ->
            if (field.type == String::class.java) {
                try {
                    field.isAccessible = true
                    (field.get(obj) as? String)?.let { str ->
                        extractPackageName(str)?.let { return it }
                    }
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    private fun extractPackageName(input: String?): String? {
        if (input == null || input.indexOf('.') <= 0) return null
        val normalized = input.lowercase().replace(Regex("[^a-z0-9._-]"), " ")
        return normalized.split(Regex("\\s+")).find {
            it.contains(".") && it.matches(Regex("[a-z0-9]+(\\.[a-z0-9]+)+"))
        }
    }

    private fun buildAppInfo(pkg: String): String {
        val pidUid = getPidUid(pkg)
        return "$pkg $pidUid"
    }

    private fun getPidUid(pkg: String): String {
        return try {
            activityManager?.runningAppProcesses
                ?.find { it.processName == pkg || it.pkgList?.contains(pkg) == true }
                ?.let { "${it.pid} ${it.uid}" }
                ?: run {
                    "0 0"
                }
        } catch (e: Exception) {
            "0 0"
        }
    }

    private fun setupSystemContext() {
        try {
            val looperClass = Class.forName("android.os.Looper")
            if (looperClass.getMethod("getMainLooper").invoke(null) == null) {
                looperClass.getMethod("prepareMainLooper").invoke(null)
            }
    
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            var thread: Any? = null
    
            try {
                thread = activityThreadClass.getMethod("systemMain").invoke(null)
            } catch (t: Throwable) {
                val cause = generateSequence(t) { it.cause }.lastOrNull()
                AppMonitorLogger.w("systemMain() failed (${t.javaClass.simpleName}): ${cause?.message}")
            }
    
            if (thread == null) {
                try {
                    thread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
                } catch (t: Throwable) {
                    AppMonitorLogger.w("currentActivityThread() also failed: ${t.message}")
                }
            }

            thread ?: error("Both systemMain() and currentActivityThread() returned null")
    
            systemContext = activityThreadClass.getMethod("getSystemContext").invoke(thread) as? Context
                ?: error("getSystemContext() returned null")
    
        } catch (e: Exception) {
            AppMonitorLogger.e("setupSystemContext() failed", e)
        }
    }

    private fun bypassHiddenApiRestrictions() {
        HiddenApiBypass.addHiddenApiExemptions("")
    }

    private fun initializeServices(): Boolean {
        return try {
            val ctx = systemContext ?: return false
            powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            batteryManager = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            initActivityTaskManager()
            initNotificationManager()
            true
        } catch (e: Exception) {
            AppMonitorLogger.e("initializeServices() failed", e)
            false
        }
    }

    private fun initActivityTaskManager() {
        val binder = getSystemService(resolveAtmServiceName())
            ?: error("ServiceManager returned null binder for '${resolveAtmServiceName()}'")
        val atm = bindInterface("${resolveAtmInterfaceName()}\$Stub", binder)
        activityTaskManager = atm
        foregroundMethod = findForegroundMethod(atm)
    }

    private fun findForegroundMethod(atm: Any): Method? {
        val methods = getDeclaredMethods(atm.javaClass).associateBy { it.name }

        return FOREGROUND_METHOD_CANDIDATES
            .mapNotNull { candidate -> methods[candidate] }
            .find { method ->
                method.parameterTypes.isEmpty() ||
                        (method.parameterTypes.size == 1 && method.parameterTypes[0] == Int::class.java) ||
                        method.name == "getTasks" || method.name == "getRunningTasks"
            }
            ?.apply { isAccessible = true }
    }

    private fun initNotificationManager() {
        val binder = getSystemService(Context.NOTIFICATION_SERVICE)
            ?: error("ServiceManager returned null binder for notification service")
        notificationManager = bindInterface("android.app.INotificationManager\$Stub", binder)
        notificationManager?.let { manager ->
            getDeclaredMethods(manager.javaClass).forEach { member ->
                if (member.name == "getZenMode" && member.parameterTypes.isEmpty()) {
                    getZenModeMethod = member
                }
            }
        }
    }

    private fun resolveAtmServiceName() =
        if (Build.VERSION.SDK_INT >= 29) "activity_task" else Context.ACTIVITY_SERVICE

    private fun resolveAtmInterfaceName() =
        if (Build.VERSION.SDK_INT >= 29) "android.app.IActivityTaskManager" else "android.app.IActivityManager"

    private fun getSystemService(name: String): IBinder? {
        val serviceManager = Class.forName("android.os.ServiceManager")
        return serviceManager.getMethod("getService", String::class.java)
            .invoke(null, name) as? IBinder
    }

    private fun bindInterface(stubClassName: String, binder: IBinder): Any {
        return Class.forName(stubClassName)
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
            ?: error("asInterface returned null for $stubClassName")
    }

    private fun getDeclaredMethods(cls: Class<*>): List<Method> {
        return HiddenApiBypass.getDeclaredMethods(cls).filterIsInstance<Method>()
    }

    private fun getInstanceFields(cls: Class<*>): List<Field> {
        return HiddenApiBypass.getInstanceFields(cls).filterIsInstance<Field>()
    }
    
    // Cached raw text of MaxManager's own curated game list, reused here so
    // XiaomiVendorFeatures doesn't need a second copy of this list. The
    // file is small and rarely changes, so a plain substring check
    // (matching the same package-key style used by the daemon) is
    // enough — no need to pull in a JSON parsing dependency for this.
    private var cachedGameListText: String? = null
    private var cachedGameListPath: String? = null

    private fun isKnownGameApp(pkgName: String): Boolean {
        if (pkgName.isBlank() || pkgName == "unknown" || pkgName == "none") return false
        val path = MaxManagerPaths.APPLIST_JSON
        if (cachedGameListText == null || cachedGameListPath != path) {
            cachedGameListText = runCatching { File(path).readText() }.getOrNull()
            cachedGameListPath = path
        }
        return cachedGameListText?.contains("\"$pkgName\":") == true
    }

    /** Mirrors AmbientDisplayConfiguration.alwaysOnEnabled() used by ColorService.kt. */
    private fun isAodEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(
                systemContext?.contentResolver, "doze_always_on", 0,
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    private fun getAppName(pkgName: String): String {
        if (pkgName == "unknown" || pkgName == "none" || pkgName.isBlank()) {
            return "Unknown"
        }
        return try {
            val pm = systemContext?.packageManager ?: return "Unknown"
            val appInfo = pm.getApplicationInfo(pkgName, 0)
            appInfo.loadLabel(pm).toString()
        } catch (e: Exception) {


            pkgName 
        }
    }
    
    private fun getBatteryLevel(): Int {
        return try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (e: Exception) {
            -1
        }
    }
    
    private fun getChargingStatus(): Int {
        return try {
            val status = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
            if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) 1 else 0
        } catch (e: Exception) {
            0
        }
    }
    
    private fun getCurrentRefreshRate(): Int {
        return try {
            val dm = systemContext?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            
            val refreshRate = display?.refreshRate ?: 60.0f
            Math.round(refreshRate)
        } catch (e: Exception) {
            -1
        }
    }
    
    private fun getMaxRefreshRate(): Int {
        return try {
            val dm = systemContext?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            
            val modes = display?.supportedModes
            if (modes != null && modes.isNotEmpty()) {
                val maxRate = modes.maxOf { it.refreshRate }
                Math.round(maxRate)
            } else {
                60
            }
        } catch (e: Exception) {
            -1
        }
    }
}
