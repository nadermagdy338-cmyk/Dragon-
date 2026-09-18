/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.core.privilege

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuSystemProperties

/** حالة جسر Shizuku كما يراها التطبيق الآن. */
data class ShizukuState(
    val available: Boolean = false,
    val permissionGranted: Boolean = false,
    val version: Int = -1,
    val uid: Int = -1,
) {
    val ready: Boolean get() = available && permissionGranted
}

/**
 * جسر Shizuku (`AR-20`) — طبقة الامتياز الثانية بجانب الجذر.
 *
 * **لماذا Shizuku تحديدًا:** جزء معتبر من أدواتنا (الكثافة · DNS · تجميد/تعطيل الحزم
 * عبر `pm` · `AppOps` · قراءة البطارية · السجلات) لا يحتاج جذرًا أصلًا، بل امتياز ADB.
 * فتح هذه الطبقة يضاعف من يستطيع استخدام التطبيق **ويضيّق** سطح الجذر معًا.
 *
 * **قواعد السلامة:** كل استدعاء لـShizuku محاط بـ[runCatching] — فلا يسقط التطبيق إن
 * غاب الجسر أو تغيّر إصداره. ولا يُطلب امتياز إلا بفعل صريح من المستخدم.
 */
object ShizukuGateway {

    /** رمز طلب إذن Shizuku — ثابت داخل التطبيق، ولا يُشارَك مع أحد. */
    const val PERMISSION_REQUEST_CODE = 4213

    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private var listening = false

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) refresh()
        }

    /** يُسجّل المستمعين مرة واحدة، ثم يحدّث الحالة. آمن للاستدعاء المتكرر. */
    fun start() {
        if (!listening) {
            listening = runCatching {
                Shizuku.addBinderReceivedListenerSticky(binderReceived)
                Shizuku.addBinderDeadListener(binderDead)
                Shizuku.addRequestPermissionResultListener(permissionResult)
                true
            }.getOrDefault(false)
        }
        refresh()
    }

    fun stop() {
        if (!listening) return
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
        listening = false
    }

    /** يقرأ الحالة الحالية ويحدّث [state]. يعود بالحالة المقروءة. */
    fun refresh(): ShizukuState {
        val current = runCatching { read() }.getOrElse { ShizukuState() }
        _state.value = current
        return current
    }

    private fun read(): ShizukuState {
        if (!Shizuku.pingBinder()) return ShizukuState()
        val version = runCatching { Shizuku.getVersion() }.getOrDefault(-1)
        val granted = if (Shizuku.isPreV11()) {
            false
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
        val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        return ShizukuState(
            available = true,
            permissionGranted = granted,
            version = version,
            uid = uid,
        )
    }

    /** يطلب الإذن بفعل صريح من المستخدم فقط. لا يُنادى عند الإقلاع. */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
    }

    /**
     * يقرأ خاصية نظام بامتياز Shizuku — عبر الواجهة **العامة** `ShizukuSystemProperties`.
     * يعود بـ`null` إن غاب الامتياز أو فشلت القراءة؛ والمستدعي يعلن الفشل.
     */
    fun getProp(key: String, def: String = ""): String? = runCatching {
        ShizukuSystemProperties.get(key, def)
    }.getOrNull()

    // ── حدّ معروف ومُعلَن (ADR-07) ──────────────────────────────────────────────
    // تنفيذ أوامر shell بامتياز Shizuku **ليس** ممكنًا عبر واجهة 13.x العامة:
    // فـ`Shizuku.newProcess` خاصّ (غير عام) — تحقّقنا من هذا بـ`javap` على
    // `dev.rikka.shizuku:api:13.1.5`، ولهذا لا نُعلن قدرة لا نملكها.
    // المسار الصحيح لإكمال الطبقة هو **UserService** (`Shizuku.bindUserService`
    // + AIDL + خدمة تعمل بصلاحية الشيزوكو)، وهو الخطوة التالية المُعلَنة في
    // `docs/ai/UNIMPLEMENTED-PROPOSALS.md` — لا يُدَّعى الآن.
}
