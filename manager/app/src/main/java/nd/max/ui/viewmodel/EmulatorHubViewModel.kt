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
    )

    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()

    init {
        // الرفّ يُرسم فورًا من آخر فهرس، والمسح الطازج يستبدله — فلا شاشة فارغة عند كل فتح.
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { RomLibraryAccess.index(context) }
            if (cached.isNotEmpty()) {
                mutable.value = mutable.value.copy(entries = groupDiscSets(cached), loading = false)
            }
            refresh()
        }
    }

    fun refresh() = viewModelScope.launch {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                // «تحميل» لا تُعرض إلا حين لا شيء يُرسم: الفهرس المحفوظ يبقى ظاهرًا حتى يصل الطازج.
                mutable.value = mutable.value.copy(loading = mutable.value.entries.isEmpty())
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

    /**
     * يضيف مجلدًا ممنوحًا ويحتفظ بصلاحيته.
     *
     * **والرفض يُعلَن ولا يُمسح:** كان `refresh()` يُنادى بعد كل عودة، و`refresh()` يبني `State`
     * جديدًا كاملًا ⇒ يمحو النتيجة المضبوطة فورًا، فيصير الرفض صامتًا. لذلك لا يُعاد المسح إلّا
     * بعد نجاح الحفظ.
     */
    fun addFolder(uri: Uri) = viewModelScope.launch {
        val added = withContext(Dispatchers.IO) {
            val folders = RomLibraryAccess.folders(context)
            if (!RomLibraryAccess.keep(context, uri)) {
                mutable.value = mutable.value.copy(outcome = RomLibraryAccess.ScanOutcome.NO_ACCESS)
                return@withContext false
            }
            if (!RomLibraryAccess.saveSources(context, folders + uri.toString(), RomLibraryAccess.documents(context))) {
                mutable.value = mutable.value.copy(outcome = RomLibraryAccess.ScanOutcome.FAILED)
                return@withContext false
            }
            true
        }
        if (added) refresh()
    }

    /** يضيف ملفّات مفردة. ونصف الحفظ مرفوض: رابط بلا صلاحية مستمرّة لا يُخزَّن. */
    fun addDocuments(uris: List<Uri>) = viewModelScope.launch {
        val added = withContext(Dispatchers.IO) {
            val kept = uris.filter { RomLibraryAccess.keep(context, it) }.map(Uri::toString)
            if (kept.size != uris.size) {
                mutable.value = mutable.value.copy(outcome = RomLibraryAccess.ScanOutcome.NO_ACCESS)
                return@withContext false
            }
            if (!RomLibraryAccess.saveSources(context, RomLibraryAccess.folders(context), RomLibraryAccess.documents(context) + kept)) {
                mutable.value = mutable.value.copy(outcome = RomLibraryAccess.ScanOutcome.FAILED)
                return@withContext false
            }
            true
        }
        if (added) refresh()
    }
}
