/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.core.privilege

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * يجمع طبقتَي الامتياز (الجذر + Shizuku) في حكم واحد معلن (`AR-20`).
 *
 * **القراءة السلبية أولًا:** [cachedRootGranted] يقرأ الصدفة المُخزَّنة فقط ولا يستدعي
 * `su` — فلا تظهر نافذة صلاحية لمجرد فتح شاشة. طلب الجذر فعل صريح عبر [requestRoot].
 */
object PrivilegeManager {

    data class Snapshot(
        val level: PrivilegeLevel = PrivilegeLevel.NONE,
        val shizuku: ShizukuState = ShizukuState(),
        val rootGranted: Boolean = false,
    )

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    /** جذر مُخزَّن مسبقًا — بلا استدعاء صلاحية. */
    fun cachedRootGranted(): Boolean = runCatching {
        Shell.getCachedShell()?.isRoot == true
    }.getOrDefault(false)

    /** فعل صريح من المستخدم: يطلب الجذر (قد تُظهر نافذة الصلاحية). */
    fun requestRoot(): Boolean = runCatching {
        val cached = Shell.getCachedShell()
        if (cached != null && !cached.isRoot) cached.close()
        Shell.getShell().isRoot
    }.getOrDefault(false)

    fun start() {
        ShizukuGateway.start()
        refresh()
    }

    fun stop() {
        ShizukuGateway.stop()
    }

    /** يقرأ الطبقتين ويحدّث [snapshot]. الطبقة الأعلى المتاحة هي [Snapshot.level]. */
    fun refresh(): Snapshot {
        val root = cachedRootGranted()
        val shizuku = ShizukuGateway.refresh()
        val level = when {
            root -> PrivilegeLevel.ROOT
            shizuku.ready -> PrivilegeLevel.SHIZUKU
            else -> PrivilegeLevel.NONE
        }
        val next = Snapshot(level = level, shizuku = shizuku, rootGranted = root)
        _snapshot.value = next
        return next
    }
}
