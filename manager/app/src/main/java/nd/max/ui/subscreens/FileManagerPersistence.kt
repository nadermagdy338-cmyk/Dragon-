/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * `MT-FM-04` — **ربط الحفظ بالشاشة**: المفضّلة والسجل يُقرآن عند الفتح ويُكتبان عند كل
 * تغيير.
 *
 * ولماذا هنا لا داخل `FileManagerScreen`: الشاشة قربت سقف الأسطر، وهذه سياسة تخزين لا
 * تخطيط؛ وفصلها يجعل الحفظ **قابلًا للقراءة وحده**: ملفان في مجلد التطبيق الخاصّ، وكتابة
 * على IO فلا تتجمّد الشاشة لأجل ملف صغير.
 *
 * والكتابة الأولى تُتخطّى عمدًا: ما قُرأ للتوّ لا يُعاد كتابته، فلو تعذّرت القراءة لما
 * مُحي الملف بقيمة افتراضية.
 */
package nd.max.ui.subscreens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.ui.util.FileBookmark
import nd.max.ui.util.FileStore
import nd.max.ui.util.HistoryEntry
import java.io.File

/** مخزنا الشاشة: ملف للمفضّلة وملف للسجل — في مجلد التطبيق الخاصّ، بلا إذن إضافي. */
internal class FileManagerStores(filesDir: File) {
    val bookmarks: FileStore = FileStore(File(filesDir, "max_files_bookmarks.txt"))
    val history: FileStore = FileStore(File(filesDir, "max_files_history.txt"))
}

@Composable
internal fun rememberFileManagerStores(): FileManagerStores {
    val context = LocalContext.current
    return remember(context) { FileManagerStores(context.applicationContext.filesDir) }
}

/**
 * حالة المفضّلة المحفوظة: تُقرأ مرة عند الفتح، وتُكتب عند كل تغيير بعد ذلك — لا عند
 * الإغلاق (فالإغلاق قد لا يأتي).
 */
@Composable
internal fun rememberStoredBookmarks(store: FileStore): MutableState<List<FileBookmark>> {
    val state = remember(store) { mutableStateOf(store.loadBookmarks()) }
    var primed by remember(store) { mutableStateOf(false) }
    LaunchedEffect(store, state.value) {
        if (primed) withContext(Dispatchers.IO) { store.saveBookmarks(state.value) } else primed = true
    }
    return state
}

/** حالة السجل المحفوظ — نفس العقد، وبسقف النموذج `FileHistory.LIMIT`. */
@Composable
internal fun rememberStoredHistory(store: FileStore): MutableState<List<HistoryEntry>> {
    val state = remember(store) { mutableStateOf(store.loadHistory()) }
    var primed by remember(store) { mutableStateOf(false) }
    LaunchedEffect(store, state.value) {
        if (primed) withContext(Dispatchers.IO) { store.saveHistory(state.value) } else primed = true
    }
    return state
}
