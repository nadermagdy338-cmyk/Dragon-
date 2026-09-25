# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := sys.maxmanager-service

ALL_ABS_FILES := $(wildcard $(LOCAL_PATH)/src/*/*.c)

LOCAL_SRC_FILES := Main.c $(ALL_ABS_FILES:$(LOCAL_PATH)/%=%)

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include

LOCAL_CFLAGS := -DNDEBUG -Wall -Wextra -Werror \
                -pedantic-errors -Wpedantic \
                -O2 -std=c23 -fPIC -flto

LOCAL_LDFLAGS := -flto
LOCAL_LDLIBS  += -llog  

include $(BUILD_EXECUTABLE)
