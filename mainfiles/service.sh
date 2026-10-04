#!/system/bin/sh

#
# Copyright (C) 2026-2027 Zexshia
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

readonly MODDIR="${0%/*}"
readonly MODULE_CONFIG="/data/adb/.config/MaxManager"
readonly BIN_SVC="$MODDIR/system/bin/sys.maxmanager-service"
readonly APK_COMP="$MODDIR/system/product/priv-app/MaxManager/MaxManager.apk"
# The app mounts as a priv-app from the module system tree; MODDIR works
# for Magisk, but KSU/APatch soft-reboots can re-point script locations,
# so resolve the APK through the live /product mount instead.
APK_MOUNTED="/product/priv-app/MaxManager/MaxManager.apk"
[ -f "$APK_MOUNTED" ] || APK_MOUNTED="$APK_COMP"

# shellcheck source=props.sh
. "$MODDIR/props.sh"

# Wait boot to complete
until [ "$(getprop sys.boot_completed)" = "1" ]; do 
    sleep 1
done

# Remove Single Instance since we dont need it anymore
rm -f /dev/.maxmanagerSingleInstance

# Reset anti bootloop
echo "BOOTCOUNT=0" > "$MODDIR/count.sh"

# Clear Old Logs
"$BIN_SVC" --clearlogs

readonly LAUNCHER_STATE="$MODULE_CONFIG/launcher_visibility"
readonly LEGACY_LAUNCHER_MARKER="$MODULE_CONFIG/.launcher_enabled"
readonly RECOVERY_LOG="$MODULE_CONFIG/package-recovery.log"

log_recovery() {
    printf '%s %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$*" >> "$RECOVERY_LOG"
}

package_installed() {
    pm path nd.max >/dev/null 2>&1
}

package_known() {
    pm list packages -u 2>/dev/null | grep -qx 'package:nd.max'
}

# PackageManager may finish scanning product apps after boot_completed. Keep
# mount state separate from package state so a mounted but rejected APK does
# not suppress recovery.
PACKAGE_READY=0
attempt=0
while [ "$attempt" -lt 30 ]; do
    if package_installed; then
        PACKAGE_READY=1
        break
    fi
    attempt=$((attempt + 1))
    sleep 2
done

if [ "$PACKAGE_READY" -eq 0 ]; then
    if [ -f /product/priv-app/MaxManager/MaxManager.apk ]; then
        log_recovery "product APK mounted but package unavailable after scan wait"
    else
        log_recovery "product APK not mounted after scan wait"
    fi

    if package_known; then
        log_recovery "package known; attempting install-existing for user 0"
        cmd package install-existing --user 0 nd.max >> "$RECOVERY_LOG" 2>&1
        package_installed && PACKAGE_READY=1
    fi
fi

if [ "$PACKAGE_READY" -eq 0 ] && [ -f "$APK_COMP" ]; then
    log_recovery "installing degraded user-app fallback"
    cp "$APK_COMP" /data/local/tmp/MaxManager.apk
    chmod 644 /data/local/tmp/MaxManager.apk
    pm install -r -d --user 0 /data/local/tmp/MaxManager.apk >> "$RECOVERY_LOG" 2>&1
    rm -f /data/local/tmp/MaxManager.apk
    package_installed && PACKAGE_READY=1
fi

if [ "$PACKAGE_READY" -eq 1 ]; then
    log_recovery "package available path=$(pm path nd.max 2>/dev/null | tr '\n' ' ')"

    # The privileged allowlist is framework-owned. These runtime permissions
    # and appops are applied only after package recovery succeeds.
    pm grant nd.max android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
    pm grant nd.max android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1
    pm grant nd.max android.permission.READ_MEDIA_IMAGES >/dev/null 2>&1
    appops set nd.max SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
    appops set nd.max WRITE_SETTINGS allow >/dev/null 2>&1

    # الأيقونة تبقى ظاهرة دائمًا — بلا حالة محفوظة تُقلَب.
    #
    # وهذا ليس تفضيلًا: التطبيق هو الطريق الوحيد إلى مدير الروت بعد التفليش (منح الإذن يتمّ من
    # داخل التطبيق أو من قائمة مدير الروت)، وإخفاؤه من المشغّل كان يقطع الطريق على من أراد منحه
    # الإذن ثم يقول «تطبيقي لا يظهر». وأي إخفاء سابق محفوظ في `PackageManager` يُلغى هنا عند كل
    # إقلاع، لأن حالة المكوّن المحفوظة تسبق قيمة البيان الافتراضية.
    if pm enable --user 0 nd.max/.Launcher >/dev/null 2>&1; then
        printf '%s\n' shown > "$LAUNCHER_STATE"
        chmod 600 "$LAUNCHER_STATE" 2>/dev/null
        rm -f "$LEGACY_LAUNCHER_MARKER"
    else
        log_recovery "launcher enable failed (component state may still be hidden)"
    fi

    # نسخة تطبيق-مستخدم مطابقة لنسخة الـpriv-app.
    #
    # **ومقيس من جهاز حقيقي (٢٠٢٦-١٠-٠١):** `PackageManager` سجّل `Package nd.max at
    # /product/priv-app/MaxManager ignored: updated version 68 better than this 68` — أي أن
    # النسختين بالـ`versionCode` نفسه، والقسم المُحدَّث هو الذي يُخدَم. ولا يُغيَّر التغليف هنا
    # (قرار إصدار لا قرار سكربت)، لكن السطر التالي يُبقي الحقيقة في السجلّ: أيّ مسار يُخدَم الآن.
    #
    # ولماذا: ما تُثبّته الوحدة هو **تطبيق نظام** (`/product/priv-app`)، ومديرو الروت (KernelSU Next
    # وAPatch وMagisk) يعرضون في قوائمهم تطبيقات المستخدم — فيغيب تطبيق الوحدة عن القائمة التي
    # يُمنح منها الإذن. وكتابة النسخة نفسها في قسم البيانات تجعل الحزمة **تطبيق نظام مُحدَّث**:
    # يبقى أصلها الـpriv-app فتظل صلاحياتها، وتظهر مع التطبيقات العادية في كل قائمة.
    # والشرط مقيَّد بالغياب فقط، فلا يُعاد تثبيت شيء في كل إقلاع.
    #
    # والشرط هو وجود **مسار في قسم البيانات** لا «ظهور في قائمة -3»: معنى الأول دقيق ومقيس
    # (`pm path` يطبع كل مسارات الحزمة)، ومعنى الثاني يختلف بين إصدارات وأندرويد ومديري الروت
    # — ولو بنينا الشرط على الثاني لكان كل إقلاع يعيد التثبيت في أي ROM لا يُدرج تطبيق النظام
    # المُحدَّث في -3. النتيجة واحدة (لا تكرار)، والسبب مقروء من المخرَج نفسه.
    if ! pm path nd.max 2>/dev/null | grep -q '/data/app/'; then
        if [ -f "$APK_COMP" ]; then
            log_recovery "installing user-app copy so root managers list nd.max"
            cp "$APK_COMP" /data/local/tmp/MaxManager.apk
            chmod 644 /data/local/tmp/MaxManager.apk
            pm install -r -d --user 0 /data/local/tmp/MaxManager.apk >> "$RECOVERY_LOG" 2>&1
            rm -f /data/local/tmp/MaxManager.apk
            log_recovery "user-app copy path=$(pm path nd.max 2>/dev/null | tr '\n' ' ')"
        fi
    fi

    # حالة الحزمة كما يراها `PackageManager` — سطر واحد يُجيب «هل التطبيق تطبيق مستخدم أم نظام فقط؟»
    # من ملف السجل وحده، فلا نحتاج سؤالًا ولا صندوق أوامر لمعرفة سبب غيابه من قائمة مدير الروت.
    #
    # و«تطبيق مستخدم» يُقاس بما يراه مديرو الروت فعلًا: وجود نسخة في قسم البيانات (`/data/app`).
    # أمّا «ظهوره في قائمة النظام» فيُقاس بالقائمة نفسها — فالسطر يقول الاثنين، وكل واحد يُقاس
    # بمصدره لا بتخمين العلاقة بينهما.
    data_copy=no
    pm path nd.max 2>/dev/null | grep -q '/data/app/' && data_copy=yes
    listed=no
    pm list packages -3 2>/dev/null | grep -qx 'package:nd.max' && listed=yes
    if [ "$data_copy" = "yes" ]; then
        log_recovery "package state: user_app=yes listed_as_third_party=$listed privileged_base=yes"
    else
        log_recovery "package state: user_app=no listed_as_third_party=$listed privileged_base=yes (system-only; root-manager lists may omit it)"
    fi
else
    log_recovery "package recovery failed; app companion not started"
fi

# Remove reboot flag
if [ -f "$MODDIR/reboot" ]; then
    rm -f "$MODDIR/reboot"
fi

# Refresh MaxManager daemon state
STATE=$(getprop "$PROP_STATE")
{ [ -z "$STATE" ] || { [ "$STATE" = "running" ] && [ -z "$(/system/bin/toybox pidof sys.maxmanager-service)" ]; }; } && {
    setprop "$PROP_STATE" stopped
    setprop "$PROP_SERVICE" ""
}

readonly COMPANION_NAME="sys.maxmanager-appmonitoring"

launch_companion() {
    # والمخرَج **يُضاف** لا يُقتطع: كان `>` يمحو ما كتبه رفيق سابق في الإقلاع نفسه —
    # والحالة التي دفعت الثمن هي التي تُشفى: موت الرفيق في الإقلاع يُتبع بإعادة تشغيل
    # (من الحارس في التطبيق، أو من شاشة فحص الشحن) — فلو مُحي السابق لم يبق دليل يُرسل.
    nohup app_process -Djava.class.path="$APK_MOUNTED" / \
        --nice-name="$COMPANION_NAME" nd.max.AppMonitor \
        "$MODULE_CONFIG/app_status" \
        "$MODULE_CONFIG/background_apps" \
        "$MODULE_CONFIG/java.lock" >>"$MODULE_CONFIG/sysmon.log" 2>&1 &
}

# اسم الرفيق يضعه `--nice-name`، فيُقاس حضوره **بالاسم** لا بالتخمين.
companion_alive() {
    pids=$(/system/bin/toybox pidof "$COMPANION_NAME" 2>/dev/null) || return 1
    [ -n "$pids" ]
}

# Exec Java Companion Daemon only when PackageManager can resolve the app.
COMPANION_READY=0
if [ "$PACKAGE_READY" -eq 1 ]; then
    launch_companion
    # **ولا يُبدأ الخادم على ظنّ أنّ الرفيق حيّ:** الخادم ينتظر قفله ١٢٠ ثانية كاملة ثم يُغلق الوحدة
    # كلها برسالة «Java companion daemon crashed or failed to start» — وهذا يقع فعلًا حين يموت الرفيق
    # في أوّل ثانية من عمره (عطب مقيس: تكملة ٢٢٧، حيث مُنع تخزين المستخدم قبل فتح الشاشة). والمهلة
    # هنا قصيرة عن قصد: الرفيق الذي لا يستطيع التهيئة **يقولها في سجلّه ويخرج**، فلا معنى لانتظار طويل.
    if ! /system/bin/toybox pidof init >/dev/null 2>&1; then
        # وغياب أداة الفحص **لا يُسقط الوحدة**: يُعلن أنه لم يُقس، ويُعاد السلوك السابق (الخادم يفحص بنفسه).
        log_recovery "pidof unavailable; companion liveness not measured"
        COMPANION_READY=1
    else
        attempt=0
        while [ "$attempt" -lt 15 ]; do
            if companion_alive; then
                COMPANION_READY=1
                break
            fi
            attempt=$((attempt + 1))
            sleep 1
        done

        if [ "$COMPANION_READY" -eq 1 ]; then
            log_recovery "java companion running pid=$(/system/bin/toybox pidof "$COMPANION_NAME" 2>/dev/null | tr ' ' ',')"
        else
            # ومحاولة ثانية واحدة: أوّل ثانية من الإقلاع قد تسبق جاهزية `app_process`، والثانية تُنقذ الجلسة.
            log_recovery "java companion not alive after ${attempt}s; retrying once"
            launch_companion
            attempt=0
            while [ "$attempt" -lt 10 ]; do
                if companion_alive; then
                    COMPANION_READY=1
                    break
                fi
                attempt=$((attempt + 1))
                sleep 1
            done
            if [ "$COMPANION_READY" -eq 1 ]; then
                log_recovery "java companion running after retry"
            else
                log_recovery "java companion failed to start; see sysmon.log (service not started)"
            fi
        fi
    fi
fi

# Run MaxManager service — **فقط** إذا كان الرفيق حيًّا.
# وليس الشرط وجود الحزمة وحده: انظر تعليق الفحص أعلاه — الرسالة المُضلّلة («crashed or failed to start»)
# تُستبدل بسطر واحد صادق في `package-recovery.log` يسمّي ما جرى.
if [ "$COMPANION_READY" -eq 1 ]; then
    sleep 1 && exec "$BIN_SVC" --run
else
    log_recovery "service not started; java companion unavailable"
    exit 0
fi
