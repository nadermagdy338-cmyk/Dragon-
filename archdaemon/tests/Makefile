# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.
#
# Host parity suite for `sys.maxmanager-service`.
#
# This builds the *real* daemon translation units for the host (see host/shim/
# for the bionic property stand-in) and runs assertions on their pure decision
# logic. It is deliberately NOT the device build: no NDK, no ARM target, no
# sysfs. What it proves is that the daemon's decisions still hold; what it
# cannot prove is that they work against a real phone (that needs a device).
#
# Usage:
#   make -C archdaemon/tests run     # build if needed, then run
#   make -C archdaemon/tests clean

# `CC ?=` would not win against make's built-in default (`cc`), which lacks -std=c23.
# `=` is still overridable from the command line (e.g. `make CC=gcc-14`).
CC = clang

# clang >= 16 spells the standard `c23`; older toolchains only know `c2x`. Probe once
# so the harness runs on any CI runner instead of dying on a flag spelling.
CSTD := $(shell $(CC) -std=c23 -fsyntax-only -x c /dev/null >/dev/null 2>&1 && echo c23 || echo c2x)

ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST)))/..)
SHIM := $(CURDIR)/host/shim

# The production units under audit (unmodified daemon sources).
#
# StatusMonitor.c and CLIUtility.c joined in the contract round: they carry the two channels
# `docs/ai/ARCHITECTURE-AUDIT.md` §12 declares (the app_status state file and the --profile
# control path), and both were reachable only on a rooted device until now.
#
# PerAppKernel.c joined after a field defect: its `enforce_per_app_governors()` — the only
# writer of the per-app CPU/GPU governor in the daemon — had **no caller at all**, and while an
# app with an override is in front every profile path publishes
# `sys.maxmanager.perapp.governor_isolation=1`, which makes the profiles binary stand down too.
# So both halves stood down and the governor never changed when the user selected one. Compiling
# this unit for the host is what makes the guard's behaviour (allowlist vs. kernel authority)
# measurable without a phone; whether System.c *calls* it stays a code-reading claim.
# ModuleIntegrity.c joined after a field defect that killed the daemon on **every** clean install:
# `is_kanged()` ran `grep -q '^name=Max Manager$'` while the shipped `module.prop` says
# `name=MaxManager` (a later, deliberate rename) — so the guard fired on the module's own file,
# logged `MODULE_INTEGRITY_FAILED reason=modified_by_third_party`, cleared
# `persist.sys.maxmanager.service` and `exit`ed. Everything the daemon owns went silent with it
# (the profile file, the per-app governor, `--checkbypasschg`), which is the user-visible
# "MaxManager daemon is not running". Compiling this unit for the host makes both the pure
# identity predicate and the read-and-decide gate measurable without a phone.
UNITS := \
    $(ROOT)/jni/src/MaxManagerUtility/DaemonUtility.c \
    $(ROOT)/jni/src/MaxManagerUtility/ModuleIntegrity.c \
    $(ROOT)/jni/src/SystemProfile/ProfileUtility.c \
    $(ROOT)/jni/src/SystemProfile/PerAppKernel.c \
    $(ROOT)/jni/src/StartupInit/PropValidator.c \
    $(ROOT)/jni/src/BypassCharge/ChargingNodes.c \
    $(ROOT)/jni/src/ConfigHandler/ResolutionChanger.c \
    $(ROOT)/jni/src/ConfigHandler/RenderingHandler.c \
    $(ROOT)/jni/src/AppLoader/StatusMonitor.c \
    $(ROOT)/jni/src/BinaryCLI/CLIUtility.c

# Harness: the tests plus the shim implementation and link-time stubs.
HARNESS := parity_test.c file_protocols.c cli_profile.c broadcast.c host/props.c host/stubs.c host/fopen_router.c

# العُقَد المشتركة مع Kotlin (`fixtures/contracts/`). تُقرأ ولا تُنسخ؛ فملف واحد يُقاس من الطرفين.
REPO := $(abspath $(ROOT)/..)
FIXTURES := $(REPO)/fixtures/contracts

TARGET := build/parity_test

CFLAGS ?= -std=$(CSTD) -D_DEFAULT_SOURCE -Wall -Wextra -Wno-unused-parameter \
          -I$(ROOT)/jni/include -I$(SHIM) \
          -include $(SHIM)/host_prelude.h \
          -DFIXTURE_DIR='"$(FIXTURES)"' \
          -DREPO_DIR='"$(REPO)"' -DBUILD_DIR='"$(CURDIR)/build"'

# ── wrapped symbols: the harness observes without editing the daemon ──
#
# `--wrap=fopen` lets the UNMODIFIED parser read a fixture instead of /data/adb/...
# (host/fopen_router.c); with no route registered it is a pass-through.
# `--wrap=notify` only: notifications would try to write files and broadcast on the host, so
# they are recorded instead. `run_profiler` is deliberately **not** wrapped — the real function
# runs (its `write2file`/`is_kanged`/`uidof` are link-time stubs) and its single real side effect
# is the captured `sys.maxmanager-profilesettings <n>` command, so the CLI assertion measures the
# real path end to end instead of a recorder standing in for it.
# `-Wl` keeps them linker options, not compile ones.
LDFLAGS ?= -lpthread -Wl,--wrap=fopen -Wl,--wrap=notify

# ── هل مضيف البناء يُعلن `policyN` حقيقية؟ ──
#
# **نفس شرط** `host_exposes_real_cpu_policies()` في `parity_test.c`، وهو الذي تُسقط به المجموعة
# دعاوى مسار الكتابة في مجموعة الحاكم لئلا تكتب في معالج المضيف الحيّ. وطفرتا الحاكم أدناه
# تُقاسان بتلك الدعاوى، فلا معنى لهما على مضيف كذلك: **تُعلَن غير مُتحقَّقة ولا تُسقط البناء**.
#
# والسبب مقيس لا مُخمَّن: التشغيل `36501257551` أعطى `261/261` على runner بلا `policyN` ونجح،
# والتشغيل `36504291660` (‏`23b217d`) أعطى `234/234` على runner يُعلنها **ورسب** بالرسالة
# «the mutant passed — the suite does not catch a guard that rejects valid governors» — أي أن
# السبب مضيف CI لا تغييرٌ في الكود.
#
# ويُقاس الفرعان: `make self-check` على مضيف عادي، و`make HOST_HAS_CPU_POLICIES=yes self-check`.
HOST_HAS_CPU_POLICIES ?= $(shell ls -d /sys/devices/system/cpu/cpufreq/policy[0-9]* >/dev/null 2>&1 && echo yes)

.PHONY: all run self-check clean

all: $(TARGET)

$(TARGET): $(UNITS) $(HARNESS) host/host.h host/fopen_router.c $(SHIM)/sys/system_properties.h \
          $(FIXTURES)/app_status.valid.txt $(FIXTURES)/cli_profile.tsv
	@mkdir -p build
	$(CC) $(CFLAGS) $(UNITS) $(HARNESS) -o $@ $(LDFLAGS)

run: $(TARGET)
	@$(TARGET)

# ── قياس الأداة نفسها (نفس درس `--self-test` في بوابات المستودع) ──
#
# مجموعة دعاوى لا يمكن أن تفشل لا تُثبت شيئًا. هذه الطفرة تُزيل ملكية النطاق
# `persist.sys.maxmanagerconf.` — وهي بالضبط الانحدار الموصوف في تعليق المصدر —
# ويجب أن **تُسقط** الدعوى. فإن مرّت الطفرة فالبوابة عمياء.
self-check:
	@mkdir -p build
	@echo "── mutation self-check: removing the maxmanagerconf. prefix must fail the suite ──"
	@sed 's|"persist.sys.maxmanagerconf.",||' \
	   $(ROOT)/jni/src/StartupInit/PropValidator.c > build/PropValidator_mutant.c
	@if $(CC) $(CFLAGS) $(filter-out $(ROOT)/jni/src/StartupInit/PropValidator.c,$(UNITS)) \
	        build/PropValidator_mutant.c $(HARNESS) -o build/mutant $(LDFLAGS) >/dev/null 2>&1; then \
	   if ./build/mutant >/dev/null 2>&1; then \
	     echo "❌ the mutant passed — the suite does not catch the regression"; exit 1; \
	   else echo "✅ the mutant failed the suite, as it must"; fi; \
	 else echo "❌ the mutant did not build"; exit 1; fi
	@echo "── mutation self-check: renaming an app_status key must fail the protocol suite ──"
	@sed 's|"battery_level ", 14|"battery_levl ", 14|' \
	   $(ROOT)/jni/src/AppLoader/StatusMonitor.c > build/StatusMonitor_mutant.c
	@if $(CC) $(CFLAGS) $(filter-out $(ROOT)/jni/src/AppLoader/StatusMonitor.c,$(UNITS)) \
	        build/StatusMonitor_mutant.c $(HARNESS) -o build/mutant_file $(LDFLAGS) >/dev/null 2>&1; then \
	   if ./build/mutant_file >/dev/null 2>&1; then \
	     echo "❌ the mutant passed — the fixtures do not pin the wire format"; exit 1; \
	   else echo "✅ the mutant failed the suite, as it must"; fi; \
	 else echo "❌ the mutant did not build"; exit 1; fi
	@echo "── mutation self-check: dropping the gpu_studio prefix must fail the property-surface checks ──"
	@sed 's|"persist.sys.maxmanager.gpu_studio.",||' \
	   $(ROOT)/jni/src/StartupInit/PropValidator.c > build/PropValidator_prefix_mutant.c
	@if $(CC) $(CFLAGS) $(filter-out $(ROOT)/jni/src/StartupInit/PropValidator.c,$(UNITS)) \
	        build/PropValidator_prefix_mutant.c $(HARNESS) -o build/mutant_props $(LDFLAGS) >/dev/null 2>&1; then \
	   if ./build/mutant_props >/dev/null 2>&1; then \
	     echo "❌ the mutant passed — nothing asserts that the app's surface is covered"; exit 1; \
	   else echo "✅ the mutant failed the suite, as it must"; fi; \
	 else echo "❌ the mutant did not build"; exit 1; fi
	@echo "── mutation self-check: renaming a broadcast extra must fail the broadcast contract ──"
	@sed 's|--es notifytitle|--es notify_title|' \
	   $(ROOT)/jni/src/MaxManagerUtility/DaemonUtility.c > build/DaemonUtility_mutant.c
	@if $(CC) $(CFLAGS) $(filter-out $(ROOT)/jni/src/MaxManagerUtility/DaemonUtility.c,$(UNITS)) \
	        build/DaemonUtility_mutant.c $(HARNESS) -o build/mutant_broadcast $(LDFLAGS) >/dev/null 2>&1; then \
	   if ./build/mutant_broadcast >/dev/null 2>&1; then \
	     echo "❌ the mutant passed — nothing ties the emitted extra names to the receiver"; exit 1; \
	   else echo "✅ the mutant failed the suite, as it must"; fi; \
	 else echo "❌ the mutant did not build"; exit 1; fi
ifeq ($(HOST_HAS_CPU_POLICIES),yes)
	@echo "── mutation self-check: the two governor mutations are NOT VERIFIED on this host ──"
	@echo "   this host exposes real policyN nodes, so parity_test.c skips that suite's write-path"
	@echo "   assertions against the live CPU. The mutants are neither passed nor failed here - the"
	@echo "   suite above says SKIPPED, and so does this line."
else
	@echo "── mutation self-check: rejecting every governor must fail the governor suite ──"
	@sed 's|if (strlen(governor) >= 32)|if (strlen(governor) >= 0)|' \
	   $(ROOT)/jni/src/SystemProfile/PerAppKernel.c > build/PerAppKernel_overstrict_mutant.c
	@if $(CC) $(CFLAGS) $(filter-out $(ROOT)/jni/src/SystemProfile/PerAppKernel.c,$(UNITS)) \
	        build/PerAppKernel_overstrict_mutant.c $(HARNESS) -o build/mutant_gov_strict $(LDFLAGS) >/dev/null 2>&1; then \
	   if ./build/mutant_gov_strict >/dev/null 2>&1; then \
	     echo "❌ the mutant passed — the suite does not catch a guard that rejects valid governors"; exit 1; \
	   else echo "✅ the mutant failed the suite, as it must"; fi; \
	 else echo "❌ the mutant did not build"; exit 1; fi
	@echo "── mutation self-check: removing the shape guard must fail the governor suite ──"
	@sed 's|if (!accepted)|if (false)|' \
	   $(ROOT)/jni/src/SystemProfile/PerAppKernel.c > build/PerAppKernel_noguard_mutant.c
	@if $(CC) $(CFLAGS) $(filter-out $(ROOT)/jni/src/SystemProfile/PerAppKernel.c,$(UNITS)) \
	        build/PerAppKernel_noguard_mutant.c $(HARNESS) -o build/mutant_gov_none $(LDFLAGS) >/dev/null 2>&1; then \
	   if ./build/mutant_gov_none >/dev/null 2>&1; then \
	     echo "❌ the mutant passed — nothing asserts that a malformed token cannot reach a kernel node"; exit 1; \
	   else echo "✅ the mutant failed the suite, as it must"; fi; \
	 else echo "❌ the mutant did not build"; exit 1; fi
endif
	@echo "── mutation self-check: removing the AI brake must fail the CLI table ──"
	@sed 's|if (!from_ai && !strcmp(ai_state, "1")) {|if (0) {|' \
	   $(ROOT)/jni/src/BinaryCLI/CLIUtility.c > build/CLIUtility_mutant.c
	@if $(CC) $(CFLAGS) $(filter-out $(ROOT)/jni/src/BinaryCLI/CLIUtility.c,$(UNITS)) \
	        build/CLIUtility_mutant.c $(HARNESS) -o build/mutant_cli $(LDFLAGS) >/dev/null 2>&1; then \
	   if ./build/mutant_cli >/dev/null 2>&1; then \
	     echo "❌ the mutant passed — the table does not catch a removed brake"; exit 1; \
	   else echo "✅ the mutant failed the suite, as it must"; fi; \
	 else echo "❌ the mutant did not build"; exit 1; fi

	@echo "── mutation self-check: a stale identity string must fail the module-integrity suite ──"
	@sed 's|#define MODULE_IDENTITY_NAME   "MaxManager"|#define MODULE_IDENTITY_NAME   "Max Manager"|' \
	   $(ROOT)/jni/src/MaxManagerUtility/ModuleIntegrity.c > build/ModuleIntegrity_mutant.c
	@if $(CC) $(CFLAGS) $(filter-out $(ROOT)/jni/src/MaxManagerUtility/ModuleIntegrity.c,$(UNITS)) \
	        build/ModuleIntegrity_mutant.c $(HARNESS) -o build/mutant_identity $(LDFLAGS) >/dev/null 2>&1; then \
	   if ./build/mutant_identity >/dev/null 2>&1; then \
	     echo "❌ the mutant passed — nothing pins the guard to the shipped module.prop"; exit 1; \
	   else echo "✅ the mutant failed the suite, as it must"; fi; \
	 else echo "❌ the mutant did not build"; exit 1; fi

clean:
	rm -rf build
