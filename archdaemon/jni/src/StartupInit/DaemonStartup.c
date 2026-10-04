/*
 * Copyright (C) 2024-2025 Zexshia
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

#include "MaxManager.h"

/**
 * @brief Validates crucial system files and module integrity before startup.
 */
void verify_system_integrity(void) {
    if (check_running_state() != 0) {
        fprintf(stderr, "\033[31mERROR:\033[0m Daemon is already running!\n");
        exit(EXIT_FAILURE);
    }
    systemv("touch %s", PROFILE_MODE_APP);
    systemv("touch %s", GAME_INFO_APP);

    // Rotate any oversized logs left over from a previous run before this
    // session starts appending to them. The per-write check in
    // SystemLogger.c keeps things bounded during a long-running session,
    // but this covers the case the plan called out explicitly: don't rely
    // solely on the manual "Clear Logs" button in the app.
    rotate_log_if_needed(LOG_FILE);
    rotate_log_if_needed(LOG_VFILE);
    rotate_log_if_needed(LOG_FILE_PRELOAD);

    if (is_file_empty("/system/bin/dumpsys") == 1) {
        fprintf(stderr, "\033[31mFATAL ERROR:\033[0m /system/bin/dumpsys was tampered by kill logger module.\n");
        log_zenith(LOG_FATAL, "EVENT=INTEGRITY_CHECK_FAILED reason=dumpsys_tampered path=/system/bin/dumpsys");
        notify("Daemon Error", "Please remove your stupid kill logger module.", false, 0);
        exit(EXIT_FAILURE);
    }
    if (access(GAMELIST, F_OK) != 0) {
        fprintf(stderr, "\033[31mFATAL ERROR:\033[0m Unable to access Gamelist, either has been removed or moved.\n");
        log_zenith(LOG_FATAL, "EVENT=INTEGRITY_CHECK_FAILED reason=gamelist_missing path=%s", GAMELIST);
        exit(EXIT_FAILURE);
    }
    is_kanged();
    check_module_version();
}

/**
 * @brief مراقبة وجود الرفيق الـJava بنفسه — لا بقفله.
 *
 * **والفرق بين القياسين هو العطب كله (مقيس من جهاز حقيقي، 2026-10-01):** القفل يُقاس بـ`flock`،
 * وهو **أثرٌ** لحياة الرفيق لا حياته. فحين مات الرفيق عند `17:36:33.595` كان القفل غير مأخوذ
 * أصلًا، فلم يكن في متناول الدالة أي شيء يفرّق بين «رفيق يتهيّأ الآن» و«رفيق مات» — فانتظرت
 * الـ120 ثانية كاملة ثم أعلنت «crashed or failed to start» **بلا سبب**، بعد دقيقتين من الحدث.
 *
 * والقياس هنا **مباشر**: اسم الفئة في `/proc/<pid>/cmdline` (‏`nd.max.AppMonitor`) — فالاسم الذي
 * يضعه `app_process` موجود بنصّه ولا يُقتطع بحجم `TASK_COMM_LEN` مثل `/proc/<pid>/stat`. وقراءة
 * ملفّات صغيرة، وكل خمس ثوانٍ لا كل ثانية (كلفة الصدفة لا تُدفع حيث لا قرار لها).
 *
 * @param needle النصّ الذي يُبحث عنه في سطر أمر العملية.
 * @return true إن وُجدت عملية حيّة تحمله.
 */
static bool is_process_cmdline_alive(const char* needle) {
    DIR* proc = opendir("/proc");
    if (proc == NULL) {
        // بلا قراءة لا حكم: لا يُقال «مات» عن عملية لا يستطيع هذا الكود رؤيتها.
        return true;
    }

    bool found = false;
    struct dirent* entry = NULL;
    while (!found && (entry = readdir(proc)) != NULL) {
        // أسماء `/proc` الرقمية فقط: ما عداها مجلّدات نظام لا تخصّنا.
        if (entry->d_name[0] < '0' || entry->d_name[0] > '9') continue;

        char path[PATH_MAX];
        snprintf(path, sizeof(path), "/proc/%s/cmdline", entry->d_name);
        int fd = open(path, O_RDONLY);
        if (fd < 0) continue;

        char buffer[512];
        ssize_t read_bytes = read(fd, buffer, sizeof(buffer) - 1);
        close(fd);
        if (read_bytes <= 0) continue;
        buffer[read_bytes] = '\0';

        // وسائط `cmdline` مفصولة بـ'\0'؛ الاستبدال يسمح ببحث نصّي واحد.
        for (ssize_t index = 0; index < read_bytes; index++) {
            if (buffer[index] == '\0') buffer[index] = ' ';
        }
        if (strstr(buffer, needle) != NULL) found = true;
    }

    closedir(proc);
    return found;
}

/**
 * @brief Waits for the Java companion daemon to acquire its lock file.
 *
 * **وثلاث حالات لا واحدة:**
 *
 * 1. **قفل مأخوذ** ⇒ مضيّ كما كان.
 * 2. **عملية الرفيق غائبة** عن `/proc` بعد `COMPANION_ABSENT_CHECKS` ثانية ⇒ خرج **مُسمًّى**
 *    (`EVENT=JAVA_COMPANION_ABSENT`) وإشعار يقول ما قِيس (غيابُ العملية) ويُحيل إلى
 *    `sysmon.log` حيث يكتب الرفيق سببه بنفسه أوّل شيء (`AppMonitorLogger.persist`) — بدلًا من
 *    إشعار يُخمّن سببًا بعد دقيقتين.
 * 3. **العملية تُرى وليس القفل** ⇒ الانتظار الكامل كما كان: تطبيق يتهيّأ ببطء ليس عطبًا.
 *
 * @param ctx Pointer to the DaemonContext structure.
 */
void wait_for_java_companion(DaemonContext* ctx) {
#define MAX_JAVA_RETRIES 120
    /** عدد الفحوص قبل اعتبار غياب العملية موتًا لا تهيّؤًا بطيئًا (ثانية لكل فحص). */
#define COMPANION_ABSENT_CHECKS 20
/** كل كم فحصٍ يُسأل `/proc` — الكلفة معلنة ولا تُدفع حيث لا قرار لها. */
#define COMPANION_PROBE_EVERY 5

    log_zenith(LOG_INFO, "EVENT=JAVA_COMPANION_WAIT_START max_checks=%d", MAX_JAVA_RETRIES);
    int java_check_retries = 0;
    bool companion_seen = false;

    while (!is_java_lock_held(ctx->java_lock_path)) {
        if (++java_check_retries > MAX_JAVA_RETRIES) {
            log_zenith(LOG_FATAL, "EVENT=JAVA_COMPANION_TIMEOUT checks=%d action=exit", MAX_JAVA_RETRIES);
            notify("Daemon Error", "Java companion daemon crashed or failed to start.", false, 0);
            __system_property_set("persist.sys.maxmanager.service", "");
            __system_property_set("persist.sys.maxmanager.state", "stopped");
            exit(EXIT_FAILURE);
        }
        if (java_check_retries <= 1) {
            log_zenith(LOG_WARN, "EVENT=JAVA_COMPANION_WAIT_RETRY reason=lock_not_held");
        }

        if ((java_check_retries % COMPANION_PROBE_EVERY) == 0 &&
            is_process_cmdline_alive("nd.max.AppMonitor")) {
            companion_seen = true;
        }
        if (!companion_seen && java_check_retries >= COMPANION_ABSENT_CHECKS) {
            log_zenith(LOG_FATAL,
                       "EVENT=JAVA_COMPANION_ABSENT checks=%d action=exit",
                       java_check_retries);
            notify("Daemon Error",
                   "Java companion is not running; MaxManager stopped. Reason is in sysmon.log.",
                   false, 0);
            __system_property_set("persist.sys.maxmanager.service", "");
            __system_property_set("persist.sys.maxmanager.state", "stopped");
            exit(EXIT_FAILURE);
        }
        sleep(1);
    }
#undef COMPANION_PROBE_EVERY
#undef COMPANION_ABSENT_CHECKS
#undef MAX_JAVA_RETRIES
    log_zenith(LOG_INFO, "EVENT=JAVA_COMPANION_DETECTED action=proceeding");
}

/**
 * @brief Background thread to monitor Java daemon liveness via fcntl blocking.
 * @param arg Pointer to lock path string.
 * @return NULL
 */
void* java_lock_watcher_thread(void* arg) {
    const char* lock_path = (const char*)arg;
    int fd = open(lock_path, O_RDWR | O_CREAT, 0600);

    if (fd >= 0) {
        struct flock fl;
        memset(&fl, 0, sizeof(fl));
        fl.l_type = F_WRLCK;
        fl.l_whence = SEEK_SET;
        if (fcntl(fd, F_SETLKW, &fl) != -1) {
            fl.l_type = F_UNLCK;
            fcntl(fd, F_SETLK, &fl);
        }
        close(fd);
    }

    char signal_byte = '1';
    write(java_lock_pipe[1], &signal_byte, 1);
    return NULL;
}
