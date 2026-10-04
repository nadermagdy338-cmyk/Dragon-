# Copyright (C) 2024-2025 Rem01Gaming x Zexshia
#
# Derived from Encore Tweaks (https://github.com/Rem01Gaming/encore),
# modified for MaxManager (modifications: Copyright (C) 2026 Nader Magdy).
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

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
