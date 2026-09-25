# Copyright (C) 2026 Nader Magdy. All rights reserved.
# Proprietary and confidential — not licensed for use, copying, or distribution
# without prior written permission from the copyright holder.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := sys.maxmanager-preloadbin
LOCAL_SRC_FILES := \
    main.c \

LOCAL_CFLAGS := -DNDEBUG \
                -O2 -std=c23 -fPIC -flto

LOCAL_LDFLAGS := -flto

include $(BUILD_EXECUTABLE)
