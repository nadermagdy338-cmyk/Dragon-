/*
 * Copyright (C) 2026-2027 Zexshia
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

package nd.max.ui.viewmodel

import nd.max.MaxManagerProps


import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.RefreshRateReceiver
import nd.max.core.hardware.RootFileAccess
import nd.max.core.jni.ProbeBridge
import nd.max.ui.util.BackupManager
import nd.max.ui.util.ConfigBackupInventory
import nd.max.ui.util.MaxPrefsBundle
import nd.max.ui.util.PropertyUtils


class TweakViewModel : ViewModel() {
    data class ValidationResult(
        val isValid: Boolean, 
        val message: String, 
        val hasTweaks: Boolean, 
        val hasApplist: Boolean,
        val socType: String?,
        val data: Map<String, String>?,
        // `GAP-12`: قسما المظهر والتفضيلات — يُكتشفان من الحِزمة المُعلَنة لا بالتخمين.
        val hasAppearance: Boolean = false,
        val hasAppPrefs: Boolean = false,
    )
    
    var isUiLoaded by mutableStateOf(false)
        private set


    var liteState by mutableStateOf<Boolean?>(null)
    var availableGovernors by mutableStateOf<List<String>?>(null)
    var defaultGovIndex by mutableStateOf<Int?>(null)
    var powersaveGovIndex by mutableStateOf<Int?>(null)
    var performanceGovIndex by mutableStateOf<Int?>(null)
    var freqOffsetIndex by mutableStateOf<Float?>(null)
    val offsetLabels = listOf("Disabled", "90%", "80%", "70%", "60%", "50%", "40%") // These are used as values for PropertyUtils, so we should keep them as is or map them


    var availableIOSchedulers by mutableStateOf<List<String>?>(null)
    var balancedIOIndex by mutableStateOf<Int?>(null)
    var performanceIOIndex by mutableStateOf<Int?>(null)
    var powersaveIOIndex by mutableStateOf<Int?>(null)
    

    var isMaliGpuAvailable by mutableStateOf<Boolean?>(null)
    var availableMaliGovernors by mutableStateOf<List<String>?>(null)
    var balancedMaliGovIndex by mutableStateOf<Int?>(null)
    var performanceMaliGovIndex by mutableStateOf<Int?>(null)
    var powersaveMaliGovIndex by mutableStateOf<Int?>(null)
    


    var preloadState by mutableStateOf<Boolean?>(null)
    var memKillerState by mutableStateOf<Boolean?>(null)
    var appPriorState by mutableStateOf<Boolean?>(null)
    var dndState by mutableStateOf<Boolean?>(null)
    var fstrimState by mutableStateOf<Boolean?>(null)


    var currentRenderer by mutableStateOf<String?>(null)
    var currentRefreshRate by mutableStateOf<Int?>(null)
    // What MaxManager itself is enforcing ("default" = nothing forced), as
    // opposed to currentRefreshRate above which is just the display's live Hz.
    var currentRefreshRateReason by mutableStateOf("default")
        private set
    var thermalState by mutableStateOf<Boolean?>(null)
    var touchBoostState by mutableStateOf<Boolean?>(null)
    
    var isRendererLoading by mutableStateOf(false)
        private set
    
    var isRefreshRateLoading by mutableStateOf(false)
        private set
    
    private val configKeysToBackup = listOf(
        MaxManagerProps.General.SOC_TYPE,
        MaxManagerProps.Conf.CPU_LIMIT,
        MaxManagerProps.Conf.FREQ_OFFSET,
        MaxManagerProps.Conf.AUTO_PRELOAD,
        MaxManagerProps.Conf.CLEAR_BG,
        MaxManagerProps.Conf.IO_SCHED,
        MaxManagerProps.Conf.DND,
        MaxManagerProps.Conf.FSTRIM,
        MaxManagerProps.Conf.THERMAL_CORE,
        MaxManagerProps.Conf.SCHED_TUNES,
        MaxManagerProps.Conf.SFL,
        MaxManagerProps.Conf.JUST_IN_TIME,
        MaxManagerProps.Conf.FPS_GED,
        MaxManagerProps.Conf.MALI_SCHED,
        MaxManagerProps.Conf.WALT_TUNES,
        MaxManagerProps.Conf.DISABLE_TRACE,
        MaxManagerProps.Conf.LOGD,
        MaxManagerProps.Conf.SCHEME_CONFIG,
        MaxManagerProps.Conf.DYNAMIC_THERMAL,
        MaxManagerProps.Conf.USE_FPSGO,
        MaxManagerProps.Conf.BYPASS_CHARGE_THRESHOLD,
        MaxManagerProps.Conf.PRELOAD_BUDGET,
        MaxManagerProps.Governor.CPU_CUSTOM_DEFAULT,
        MaxManagerProps.Governor.CPU_CUSTOM_POWERSAVE,
        MaxManagerProps.Governor.CPU_CUSTOM_PERFORMANCE,
        MaxManagerProps.Governor.IO_CUSTOM_DEFAULT,
        MaxManagerProps.Governor.IO_CUSTOM_PERFORMANCE,
        MaxManagerProps.Governor.IO_CUSTOM_POWERSAVE,
        MaxManagerProps.Governor.MALIGPU_CUSTOM_DEFAULT,
        MaxManagerProps.Governor.MALIGPU_CUSTOM_PERFORMANCE,
        MaxManagerProps.Governor.MALIGPU_CUSTOM_POWERSAVE 
    )
    
    
    private val APPLIST_BACKUP_KEY = "__MAXMANAGER_APPLIST_DATA__"
    private val APPLIST_PATH = nd.max.MaxManagerPaths.APPLIST_JSON
    
    suspend fun createConfigFileBackup(
        context: Context, 
        uri: Uri, 
        backupTweaks: Boolean, 
        backupApplist: Boolean,
        backupAppearance: Boolean = false,
        backupAppPrefs: Boolean = false,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val propsMap = mutableMapOf<String, String>()
            

            propsMap[MaxManagerProps.General.SOC_TYPE] = PropertyUtils.get(MaxManagerProps.General.SOC_TYPE)

            if (backupTweaks) {
                configKeysToBackup.forEach { key ->
                    if (key != MaxManagerProps.General.SOC_TYPE) {
                        propsMap[key] = PropertyUtils.get(key)
                    }
                }
            }

            if (backupApplist) {

                val applistContent = RootFileAccess.read(APPLIST_PATH).orEmpty()
                if (applistContent.isNotBlank()) {
                    propsMap[APPLIST_BACKUP_KEY] = applistContent
                }
            }

            // أقسام التفضيلات: تُقرأ **بالقيم بأنواعها**، وتُرفض كل حِزمة إن تعذّر أحد ملفاتها.
            if (backupAppearance || backupAppPrefs) {
                val wanted = buildList {
                    if (backupAppearance) addAll(ConfigBackupInventory.prefFilesOf(ConfigBackupInventory.Section.APPEARANCE))
                    if (backupAppPrefs) addAll(ConfigBackupInventory.prefFilesOf(ConfigBackupInventory.Section.APP_PREFS))
                }
                val bundles = MaxPrefsBundle.read(context, wanted)
                if (bundles != null && bundles.isNotEmpty()) {
                    propsMap[ConfigBackupInventory.PREFS_KEY_PREFIX] =
                        ConfigBackupInventory.PrefCodec.encodeAll(bundles)
                }
                // تعذّرت القراءة ⇒ القسم **غائب**، ولا نكتب حِزمة نصفها مفقود.
            }

            val isSuccess = BackupManager.createBackup(context, uri, propsMap)
            
            delay(1500) 
            
            isSuccess 
        }
    }
    
    suspend fun validateAndRestoreFile(context: Context, uri: Uri): ValidationResult {
        return withContext(Dispatchers.IO) {
            val backupData = BackupManager.readBackup(context, uri)
            
            if (backupData == null) {
                return@withContext ValidationResult(false, context.getString(R.string.err_invalid_backup), false, false, null, null)
            }
            val backupSocType = backupData[MaxManagerProps.General.SOC_TYPE] 
                ?: backupData[MaxManagerProps.General.SOC_TYPE_DEBUG]
                
            val hasApplist = backupData.containsKey(APPLIST_BACKUP_KEY)
            
            val hasTweaks = backupData.keys.any { 
                it.startsWith(MaxManagerProps.General.MASTER) && 
                it != MaxManagerProps.General.SOC_TYPE &&
                it != MaxManagerProps.General.SOC_TYPE_DEBUG 
            }

            // حِزمة تفضيلات مشوّهة أو تحمل ملفًا محميًّا ⇒ **لا شيء يُطبَّق**، والقسم يُعلن غائبًا.
            val prefBundles = backupData[ConfigBackupInventory.PREFS_KEY_PREFIX]
                ?.let { ConfigBackupInventory.PrefCodec.decodeAll(it) }
            val appearanceFiles = ConfigBackupInventory.prefFilesOf(ConfigBackupInventory.Section.APPEARANCE)
            val appPrefFiles = ConfigBackupInventory.prefFilesOf(ConfigBackupInventory.Section.APP_PREFS)
    
            ValidationResult(
                isValid = true,
                message = "",
                hasTweaks = hasTweaks,
                hasApplist = hasApplist,
                socType = backupSocType,
                data = backupData,
                hasAppearance = prefBundles?.keys?.any { it in appearanceFiles } == true,
                hasAppPrefs = prefBundles?.keys?.any { it in appPrefFiles } == true,
            )
        }
    }

    suspend fun applyRestoreData(
        context: Context, 
        backupData: Map<String, String>, 
        restoreTweaks: Boolean, 
        restoreApplist: Boolean,
        restoreAppearance: Boolean = false,
        restoreAppPrefs: Boolean = false,
    ) {
        withContext(Dispatchers.IO) {
            if (restoreTweaks) {
                backupData.forEach { (key, value) ->
                    if (key != MaxManagerProps.General.SOC_TYPE && 
                        key != MaxManagerProps.General.SOC_TYPE_DEBUG && 
                        key != APPLIST_BACKUP_KEY && 
                        // حِزمة التفضيلات ليست خاصية نظام: تمريرها إلى `PropertyUtils.set` خطأ.
                        key != ConfigBackupInventory.PREFS_KEY_PREFIX &&
                        value.isNotEmpty()) {
                        
                        PropertyUtils.set(key, value)
                        
                        if (key == MaxManagerProps.Conf.FREQ_OFFSET) {
                            Shell.cmd("echo $value > /data/adb/.config/MaxManager/freqoffset").exec()
                        }
                    }
                }
            }

            if (restoreApplist && backupData.containsKey(APPLIST_BACKUP_KEY)) {
                val applistContent = backupData[APPLIST_BACKUP_KEY]!!
                RootFileAccess.atomicWriteText(APPLIST_PATH, applistContent)
            }

            // المظهر والتفضيلات: **دمج لا محو**، وبالقائمة البيضاء وحدها.
            if (restoreAppearance || restoreAppPrefs) {
                val wanted = buildSet {
                    if (restoreAppearance) addAll(ConfigBackupInventory.prefFilesOf(ConfigBackupInventory.Section.APPEARANCE))
                    if (restoreAppPrefs) addAll(ConfigBackupInventory.prefFilesOf(ConfigBackupInventory.Section.APP_PREFS))
                }
                val bundles = backupData[ConfigBackupInventory.PREFS_KEY_PREFIX]
                    ?.let { ConfigBackupInventory.PrefCodec.decodeAll(it) }
                val selected = bundles?.filterKeys { it in wanted }
                if (!selected.isNullOrEmpty()) {
                    MaxPrefsBundle.apply(context, selected)
                }
            }
            
            Shell.cmd("touch /data/adb/modules/MaxManager/reboot").exec()
            if (restoreTweaks) {
                loadAllConfiguration(context)
            }
            delay(1200) 
        }
    }
    
    fun loadAllConfiguration(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            
            launch {
                liteState = PropertyUtils.get(MaxManagerProps.Conf.CPU_LIMIT) == "1"
                
                val savedOffset = PropertyUtils.get(MaxManagerProps.Conf.FREQ_OFFSET, "Disabled")
                freqOffsetIndex = when (savedOffset) {
                    "90" -> 1f; "80" -> 2f; "70" -> 3f; "60" -> 4f; "50" -> 5f; "40" -> 6f; else -> 0f
                }            
                
                preloadState = PropertyUtils.get(MaxManagerProps.Conf.AUTO_PRELOAD) == "1"
                memKillerState = PropertyUtils.get(MaxManagerProps.Conf.CLEAR_BG) == "1"
                appPriorState = PropertyUtils.get(MaxManagerProps.Conf.IO_SCHED) == "1"
                dndState = PropertyUtils.get(MaxManagerProps.Conf.DND) == "1"
                fstrimState = PropertyUtils.get(MaxManagerProps.Conf.FSTRIM) == "1"
                thermalState = PropertyUtils.get(MaxManagerProps.Conf.THERMAL_CORE) == "1"
                touchBoostState = PropertyUtils.get(MaxManagerProps.Touch.BOOST) == "1"

                val rawRenderer = PropertyUtils.get("debug.hwui.renderer")
                val maxmanagerRenderer = PropertyUtils.get(MaxManagerProps.Conf.RENDERER)
                
                currentRenderer = when {
                    rawRenderer.isEmpty() && (maxmanagerRenderer.isEmpty() || maxmanagerRenderer.equals("default", ignoreCase = true)) -> "Default"
                    
                    maxmanagerRenderer.isEmpty() -> if (rawRenderer.equals("default", ignoreCase = true)) "Default" else rawRenderer
                    
                    rawRenderer.isEmpty() -> if (maxmanagerRenderer.equals("default", ignoreCase = true)) "Default" else maxmanagerRenderer
                    
                    else -> {
                        val isSameValue = rawRenderer.equals(maxmanagerRenderer, ignoreCase = true)
                        val isMaxManagerDefault = maxmanagerRenderer.equals("default", ignoreCase = true)
                        
                        when {
                            isSameValue -> if (rawRenderer.equals("default", ignoreCase = true)) "Default" else rawRenderer
                            isMaxManagerDefault -> "Default ($rawRenderer)"
                            else -> "$maxmanagerRenderer ($rawRenderer)"
                        }
                    }
                }

                currentRefreshRateReason = PropertyUtils.get(MaxManagerProps.Conf.REFRESH_RATE, "default")

                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                currentRefreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display.refreshRate.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.refreshRate.toInt()
                }
            }

            val govJob = async { loadGovernorsInternal() }
            val ioJob = async { loadIOSchedulersInternal() }
            val maliJob = async { loadMaliGovernorsInternal() }

            govJob.await()
            ioJob.await()
            maliJob.await()

            withContext(Dispatchers.Main) {
                isUiLoaded = true
            }
        }
    }

    /**
     * المعرّف الرقمي للملف الشخصي المطبَّق الآن (ملفٌ يقرأه الخادم ويكتبه).
     *
     * وهذه العقدة الواحدة تُقرأ **تسع مرّات** في هذا الملف (عند كل تغيير حاكم/جدولة)، وكانت
     * كل قراءة صدفة كاملة (`cat`) — صارت قراءةً واحدة عبر الطبقة الموحّدة:
     * قارئ أصلي ← IPC الجذر ← ملف ← صدفة.
     */
    private fun currentProfileId(): String? =
        RootFileAccess.read("/data/adb/.config/MaxManager/API/current_profile")

    private fun loadGovernorsInternal() {
        val govs = RootFileAccess.read("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors")
            ?.split("\\s+".toRegex())
            .orEmpty()
        if (govs.isNotEmpty()) {
            val currentDefault = PropertyUtils.get(MaxManagerProps.Governor.CPU_CUSTOM_DEFAULT).ifEmpty {
                PropertyUtils.get(MaxManagerProps.Governor.CPU_DEFAULT)
            }
            val currentPowersave = PropertyUtils.get(MaxManagerProps.Governor.CPU_CUSTOM_POWERSAVE)
            val currentPerformance = PropertyUtils.get(MaxManagerProps.Governor.CPU_CUSTOM_PERFORMANCE)

            availableGovernors = govs
            defaultGovIndex = govs.indexOf(currentDefault).coerceAtLeast(0)
            powersaveGovIndex = govs.indexOf(currentPowersave).coerceAtLeast(0)
            performanceGovIndex = govs.indexOf(currentPerformance).coerceAtLeast(0)
        } else {
            availableGovernors = emptyList()
        }
    }

    private fun loadIOSchedulersInternal() {
        val candidates = listOf("mmcblk0", "mmcblk1", "sda", "sdb", "sdc")
        var validBlock = ""
        for (block in candidates) {
            if (RootFileAccess.exists("/sys/block/$block/queue/scheduler")) {
                validBlock = block
                break
            }
        }
        if (validBlock.isNotEmpty()) {
            val rawOut = RootFileAccess.read("/sys/block/$validBlock/queue/scheduler")
            if (rawOut != null) {
                val schedulers = rawOut.replace("[", "").replace("]", "").trim().split("\\s+".toRegex())

                val currentBal = PropertyUtils.get(MaxManagerProps.Governor.IO_CUSTOM_DEFAULT).ifEmpty {
                    PropertyUtils.get(MaxManagerProps.Governor.IO_DEFAULT)
                }
                val currentPerf = PropertyUtils.get(MaxManagerProps.Governor.IO_CUSTOM_PERFORMANCE)
                val currentEco = PropertyUtils.get(MaxManagerProps.Governor.IO_CUSTOM_POWERSAVE)

                availableIOSchedulers = schedulers
                balancedIOIndex = schedulers.indexOf(currentBal).coerceAtLeast(0)
                performanceIOIndex = schedulers.indexOf(currentPerf).coerceAtLeast(0)
                powersaveIOIndex = schedulers.indexOf(currentEco).coerceAtLeast(0)
            } else {
                availableIOSchedulers = emptyList()
            }
        } else {
            availableIOSchedulers = emptyList()
        }
    }

    /**
     * حكام mali المتاحون من العقد مباشرة: أسماء `/sys/class/devfreq` ← التي تنتهي بـ`.mali`
     * ← أول عقدة `available_governors` غير فارغة.
     *
     * و`null` تعني «لا جواب» (مكتبة أصلية غائبة، أو لا مسار mali، أو لا تُقرأ العقدة من uid
     * التطبيق) — فيسأل المتصل الصدفة كما كانت. والقائمة الفارغة ليست `null`: هي «قرأت فلم أجد».
     */
    private fun nativeMaliGovernors(): List<String>? {
        val dirs = ProbeBridge.listNames("/sys/class/devfreq")?.filter { it.endsWith(".mali") }
        if (dirs.isNullOrEmpty()) return null
        val readings = ProbeBridge.readMany(dirs.map { "/sys/class/devfreq/$it/available_governors" })
            ?: return null
        val text = readings.firstOrNull { !it.isNullOrBlank() } ?: return null
        return text.trim().split("\\s+".toRegex())
            .filterNot { it.startsWith("apu", ignoreCase = true) }
    }

    /** الاحتياطي المصرَّح: صدفة `cat` مع glob — و`null` عند فشلها (دلالة `isSuccess` القديمة). */
    private fun legacyMaliGovernors(): List<String>? {
        val govResult = Shell.cmd("cat /sys/class/devfreq/*.mali/available_governors").exec()
        if (!govResult.isSuccess) return null
        return govResult.out.firstOrNull()?.trim()?.split("\\s+".toRegex())
            ?.filterNot { it.startsWith("apu", ignoreCase = true) } ?: emptyList()
    }

    private fun loadMaliGovernorsInternal() {

        val checkResult = Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf checkmalipath").exec()
        val hasMali = checkResult.out.joinToString("").trim() == "true"

        if (hasMali) {
            isMaliGpuAvailable = true

            // القراءة الأصلية: اسم المجلد ثم عقدته داخل العملية — بدل صدفة `cat` مع glob
            // (مقيس: ٢٣٦٦ ميكرو للصدفة الواحدة). و`null` تعني «لا مسار mali» أو «تعذّرت
            // القراءة من uid التطبيق» — فتحتاط بالصدفة كما كانت، وبنفس التصفير أدناه.
            val govs = nativeMaliGovernors() ?: legacyMaliGovernors()

            if (govs != null) {
                val currentBal = PropertyUtils.get(MaxManagerProps.Governor.MALIGPU_CUSTOM_DEFAULT).ifEmpty {
                    PropertyUtils.get(MaxManagerProps.Governor.MALIGPU_DEFAULT)
                }
                val currentPerf = PropertyUtils.get(MaxManagerProps.Governor.MALIGPU_CUSTOM_PERFORMANCE)
                val currentEco = PropertyUtils.get(MaxManagerProps.Governor.MALIGPU_CUSTOM_POWERSAVE)

                availableMaliGovernors = govs
                balancedMaliGovIndex = govs.indexOf(currentBal).coerceAtLeast(0)
                performanceMaliGovIndex = govs.indexOf(currentPerf).coerceAtLeast(0)
                powersaveMaliGovIndex = govs.indexOf(currentEco).coerceAtLeast(0)
            } else {
                // نفس مسار `cat` الفاشل تمامًا: القائمة تُصفَّر، والفهارس لا تُمسّ.
                availableMaliGovernors = emptyList()
            }
        } else {
            isMaliGpuAvailable = false
        }
    }

    

    fun updateLiteMode(checked: Boolean) {
        liteState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Conf.CPU_LIMIT, if (checked) "1" else "0")
        }
    }

    fun updateDefaultGovernor(index: Int) {
        defaultGovIndex = index
        val selectedGov = availableGovernors?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Governor.CPU_CUSTOM_DEFAULT, selectedGov)
            val currentProfile = currentProfileId()
            if (currentProfile == "2") {
                Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf setsgov $selectedGov").exec()
            }
        }
    }

    fun updatePowersaveGovernor(index: Int) {
        powersaveGovIndex = index
        val selectedGov = availableGovernors?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Governor.CPU_CUSTOM_POWERSAVE, selectedGov)
            val currentProfile = currentProfileId()
            if (currentProfile == "3") {
                Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf setsgov $selectedGov").exec()
            }
        }
    }
    
    fun updatePerformanceGovernor(index: Int) {
        performanceGovIndex = index
        val selectedGov = availableGovernors?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Governor.CPU_CUSTOM_PERFORMANCE, selectedGov)
            val currentProfile = currentProfileId()
            if (currentProfile == "3") {
                Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf setsgov $selectedGov").exec()
            }
        }
    }

    fun saveFreqOffset(value: Float) {
        freqOffsetIndex = value
        val index = value.roundToInt()
        val propValue = if (index == 0) "Disabled" else offsetLabels[index].replace("%", "")
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Conf.FREQ_OFFSET, propValue)
            Shell.cmd("echo $propValue > /data/adb/.config/MaxManager/freqoffset").exec()
        }
    }

    fun updateBalancedIO(index: Int) {
        balancedIOIndex = index
        val selectedIO = availableIOSchedulers?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Governor.IO_CUSTOM_DEFAULT, selectedIO)
            val currentProfile = currentProfileId()
            if (currentProfile == "2") {
                Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf setsIO $selectedIO").exec()
            }
        }
    }

    fun updatePerformanceIO(index: Int) {
        performanceIOIndex = index
        val selectedIO = availableIOSchedulers?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Governor.IO_CUSTOM_PERFORMANCE, selectedIO)
            val currentProfile = currentProfileId()
            if (currentProfile == "1") {
                Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf setsIO $selectedIO").exec()
            }
        }
    }

    fun updatePowersaveIO(index: Int) {
        powersaveIOIndex = index
        val selectedIO = availableIOSchedulers?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Governor.IO_CUSTOM_POWERSAVE, selectedIO)
            val currentProfile = currentProfileId()
            if (currentProfile == "3") {
                Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf setsIO $selectedIO").exec()
            }
        }
    }
    
    fun updateBalancedMaliGov(index: Int) {
        balancedMaliGovIndex = index
        val selectedGov = availableMaliGovernors?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Governor.MALIGPU_CUSTOM_DEFAULT, selectedGov)

            val currentProfile = currentProfileId()
            if (currentProfile == "2") {
                Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf setsMaliGov $selectedGov").exec()
            }
        }
    }

    fun updatePerformanceMaliGov(index: Int) {
        performanceMaliGovIndex = index
        val selectedGov = availableMaliGovernors?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Governor.MALIGPU_CUSTOM_PERFORMANCE, selectedGov)
            val currentProfile = currentProfileId()
            if (currentProfile == "1") {
                Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf setsMaliGov $selectedGov").exec()
            }
        }
    }

    fun updatePowersaveMaliGov(index: Int) {
        powersaveMaliGovIndex = index
        val selectedGov = availableMaliGovernors?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Governor.MALIGPU_CUSTOM_POWERSAVE, selectedGov)
            val currentProfile = currentProfileId()
            if (currentProfile == "3") {
                Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf setsMaliGov $selectedGov").exec()
            }
        }
    }

    fun updatePreloadMode(checked: Boolean) {
        preloadState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Conf.AUTO_PRELOAD, if (checked) "1" else "0")
        }
    }

    fun updateMemoryKiller(checked: Boolean) {
        memKillerState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Conf.CLEAR_BG, if (checked) "1" else "0")
        }
    }

    fun updateAppPriority(checked: Boolean) {
        appPriorState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Conf.IO_SCHED, if (checked) "1" else "0")
        }
    }

    fun updateDndMode(checked: Boolean) {
        dndState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Conf.DND, if (checked) "1" else "0")
        }
    }

    fun updateFstrim(checked: Boolean) {
        fstrimState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Conf.FSTRIM, if (checked) "1" else "0")
        }
    }

    fun updateThermalCore(checked: Boolean) {
        thermalState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Conf.THERMAL_CORE, if (checked) "1" else "0")
            Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf setthermalcore ${if (checked) "1" else "0"}").exec()
        }
    }

    fun executeSetRenderer(reason: String, context: Context) {
        isRendererLoading = true
        Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf setrender $reason && setprop ${MaxManagerProps.Conf.RENDERER} $reason").submit {
            viewModelScope.launch {
                delay(1000)
                loadAllConfiguration(context)
                isRendererLoading = false
            }
        }
    }
    
    fun executeSetRefreshRates(reason: String, context: Context) {
        val isDefault = reason.equals("default", ignoreCase = true)
        val fps = reason.toIntOrNull()

        // Unknown, non-numeric, non-"default" reason: ignore instead of
        // silently forcing 60Hz.
        if (!isDefault && fps == null) return

        isRefreshRateLoading = true

        val intent = Intent(context, RefreshRateReceiver::class.java).apply {
            action = "nd.max.SET_FPS"
            if (isDefault) {
                putExtra("reset", true)
            } else {
                putExtra("fps", fps!!)
            }
        }
        context.sendBroadcast(intent)

        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set(MaxManagerProps.Conf.REFRESH_RATE, if (isDefault) "default" else reason)
        }

        viewModelScope.launch {
            delay(1000)
            loadAllConfiguration(context)
            isRefreshRateLoading = false
        }
    }

}
