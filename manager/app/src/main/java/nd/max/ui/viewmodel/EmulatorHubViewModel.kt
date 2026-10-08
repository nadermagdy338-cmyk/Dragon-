/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nd.max.core.emuhub.RomEntry
import nd.max.core.emuhub.RomIndexStore
import nd.max.core.emuhub.RomLibraryAccess
import nd.max.core.emuhub.groupDiscSets
import javax.inject.Inject

/**
 * حالة مركز المحاكيات. كل العمل الثقيل (المشي · القراءة) على `IO` تحت قفل واحد، والواجهة تقرأ
 * حالة واحدة — فلا سباق بين مسحة ومسحة، ولا قائمة نصف مُحدَّثة تُرسم.
 */
@HiltViewModel
class EmulatorHubViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    data class State(
        val entries: List<RomEntry> = emptyList(),
        val outcome: RomLibraryAccess.ScanOutcome = RomLibraryAccess.ScanOutcome.EMPTY,
        val hasFolders: Boolean = false,
        val hasDocuments: Boolean = false,
        val loading: Boolean = true,
        val folderLimitReached: Boolean = false,
    )

    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                mutable.value = mutable.value.copy(loading = true)
                try {
                    val folders = RomLibraryAccess.folders(context)
                    val documents = RomLibraryAccess.documents(context)
                    val result = RomLibraryAccess.scan(context, folders)
                    mutable.value = State(
                        entries = groupDiscSets(result.files),
                        outcome = result.outcome,
                        hasFolders = folders.isNotEmpty(),
                        hasDocuments = documents.isNotEmpty(),
                        loading = false,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutable.value = mutable.value.copy(
                        loading = false,
                        outcome = RomLibraryAccess.ScanOutcome.FAILED,
                    )
                }
            }
        }
    }

    /** يضيف مجلدًا ممنوحًا ويحتفظ بصلاحيته. الرفض **يُعلَن** ([folderLimitReached] أو نتيجة المسح). */
    fun addFolder(uri: Uri) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val folders = RomLibraryAccess.folders(context)
            if (folders.size >= RomIndexStoreLimit) {
                mutable.value = mutable.value.copy(folderLimitReached = true)
                return@withContext
            }
            if (!RomLibraryAccess.keep(context, uri)) {
                mutable.value = mutable.value.copy(outcome = RomLibraryAccess.ScanOutcome.NO_ACCESS)
                return@withContext
            }
            val next = folders + uri.toString()
            if (!RomLibraryAccess.saveSources(context, next, RomLibraryAccess.documents(context))) {
                mutable.value = mutable.value.copy(outcome = RomLibraryAccess.ScanOutcome.FAILED)
                return@withContext
            }
            mutable.value = mutable.value.copy(folderLimitReached = false)
        }
        refresh()
    }

    /** يضيف ملفّات مفردة. `null` تعني أن رابطًا لم يُمنح صلاحية مستمرّة ⇒ لا يُحفظ نصفها. */
    fun addDocuments(uris: List<Uri>) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val kept = uris.filter { RomLibraryAccess.keep(context, it) }.map(Uri::toString)
            if (kept.size != uris.size) {
                mutable.value = mutable.value.copy(outcome = RomLibraryAccess.ScanOutcome.NO_ACCESS)
                return@withContext
            }
            val next = RomLibraryAccess.documents(context) + kept
            if (!RomLibraryAccess.saveSources(context, RomLibraryAccess.folders(context), next)) {
                mutable.value = mutable.value.copy(outcome = RomLibraryAccess.ScanOutcome.FAILED)
            }
        }
        refresh()
    }

    /** إزالة مصدر: الرابط يُنسى ولا يُحذف ملفّه أبدًا. */
    fun forgetFolder(uri: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            RomLibraryAccess.saveSources(
                context,
                RomLibraryAccess.folders(context) - uri,
                RomLibraryAccess.documents(context),
            )
        }
        refresh()
    }

    private companion object {
        val RomIndexStoreLimit = RomIndexStore.MAX_FOLDERS
    }
}
