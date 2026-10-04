# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.
#
# Host measurement suite for `maxfx` (the native audio effect).
#
# This builds the REAL DSP core and the REAL effect wrapper for the host (the property
# channel is a test-controlled stand-in, exactly like archdaemon/tests' property shim)
# and asserts falsifiable claims: the shared contract, the neutral-is-identity rule,
# the declared compressor transfer curve, the limiter ceiling, and the audioserver
# command contract. It is deliberately NOT the device build: no NDK, no audioserver.
#
# Usage:
#   make -C maxfx/tests run          # build if needed, then run
#   make -C maxfx/tests self-check   # inject mutants; each MUST fail the suite
#   make -C maxfx/tests clean

# `CC ?=` would not win against make's built-in default (`cc`), which lacks -std=c23.
CC = clang

# clang >= 16 spells the standard `c23`; older toolchains only know `c2x`.
CSTD := $(shell $(CC) -std=c23 -fsyntax-only -x c /dev/null >/dev/null 2>&1 && echo c23 || echo c2x)

ROOT := $(abspath $(dir $(lastword $(MAKEFILE_LIST)))/..)
REPO := $(abspath $(ROOT)/..)
FIXTURES := $(REPO)/fixtures/contracts

# الوحدات تحت القياس: النواة النقّية + غلاف المؤثّر (بلا `maxfx_props_android.c` — المضيف
# يوفّر البديل `props_stub.c`).
UNITS := $(ROOT)/src/maxfx_dsp.c $(ROOT)/src/maxfx_effect.c
HARNESS := dsp_test.c props_stub.c

TARGET := build/dsp_test

CFLAGS ?= -std=$(CSTD) -D_DEFAULT_SOURCE -Wall -Wextra -Wno-unused-parameter \
          -I$(ROOT)/include -I$(ROOT)/src \
          -DFIXTURE_DIR='"$(FIXTURES)"'
LDFLAGS ?= -lm

.PHONY: all run self-check clean

all: $(TARGET)

$(TARGET): $(UNITS) $(HARNESS) $(ROOT)/src/maxfx_dsp.h $(ROOT)/src/maxfx_effect.h \
           $(ROOT)/include/maxfx_effect_abi.h $(ROOT)/src/maxfx_props.h \
           $(FIXTURES)/maxfx_params.tsv
	@mkdir -p build
	$(CC) $(CFLAGS) $(UNITS) $(HARNESS) -o $@ $(LDFLAGS)

run: $(TARGET)
	@$(TARGET)

# ── قياس الأداة نفسها: الطفرة **يجب أن تُسقط** المجموعة ──
#
# مجموعةٌ لا تفشل أبدًا لا تُثبت شيئًا (درس `archdaemon/tests` و`--self-test` في البوّابات).
# وكلّ طفرة هنا تكسر عقدًا مُعلنًا في تعليقات المصدر، والاختبار المسمّى هو ما يجب أن يمسكها.
self-check: $(UNITS) $(HARNESS)
	@mkdir -p build
	@echo "── mutation: neutralizing the compressor gain must fail the transfer-curve claim ──"
	@sed 's|float gr_db = over \* (1.0f / comp_ratio - 1.0f);|float gr_db = 0.0f;|' \
	   $(ROOT)/src/maxfx_dsp.c > build/maxfx_dsp_comp_mutant.c
	@if $(CC) $(CFLAGS) build/maxfx_dsp_comp_mutant.c $(ROOT)/src/maxfx_effect.c \
	        $(HARNESS) -o build/mutant_comp $(LDFLAGS) >/dev/null 2>&1; then \
	   if ./build/mutant_comp >/dev/null 2>&1; then \
	     echo "❌ the mutant passed — the suite does not pin the compressor curve"; exit 1; \
	   else echo "✅ the mutant failed the suite, as it must"; fi; \
	 else echo "❌ the mutant did not build"; exit 1; fi
	@echo "── mutation: removing the bypass gate must fail the untouched-buffers claim ──"
	@sed 's|if (!c->enable) return;|if (0) return;|' \
	   $(ROOT)/src/maxfx_dsp.c > build/maxfx_dsp_bypass_mutant.c
	@if $(CC) $(CFLAGS) build/maxfx_dsp_bypass_mutant.c $(ROOT)/src/maxfx_effect.c \
	        $(HARNESS) -o build/mutant_bypass $(LDFLAGS) >/dev/null 2>&1; then \
	   if ./build/mutant_bypass >/dev/null 2>&1; then \
	     echo "❌ the mutant passed — the suite does not pin the bypass contract"; exit 1; \
	   else echo "✅ the mutant failed the suite, as it must"; fi; \
	 else echo "❌ the mutant did not build"; exit 1; fi
	@echo "── mutation: NaN falling back to zero must fail the ADR-07 default claim ──"
	@sed 's|if (!isfinite(v)) return p->def;|if (!isfinite(v)) return 0.0f;|' \
	   $(ROOT)/src/maxfx_dsp.c > build/maxfx_dsp_nan_mutant.c
	@if $(CC) $(CFLAGS) build/maxfx_dsp_nan_mutant.c $(ROOT)/src/maxfx_effect.c \
	        $(HARNESS) -o build/mutant_nan $(LDFLAGS) >/dev/null 2>&1; then \
	   if ./build/mutant_nan >/dev/null 2>&1; then \
	     echo "❌ the mutant passed — the suite does not pin NaN ⇒ default"; exit 1; \
	   else echo "✅ the mutant failed the suite, as it must"; fi; \
	 else echo "❌ the mutant did not build"; exit 1; fi

clean:
	rm -rf build
