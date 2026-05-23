# Copyright (C) 2011-2013 The Android-x86 Open Source Project

LOCAL_PATH := $(call my-dir)

# HAL module implemenation, not prelinked and stored in
# hw/<OVERLAY_HARDWARE_MODULE_ID>.<ro.product.board>.so
include $(CLEAR_VARS)
LOCAL_PRELINK_MODULE := false
LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
LOCAL_SHARED_LIBRARIES := liblog libcutils
LOCAL_MODULE := sensors.default
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := kbdsensor.cpp
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PRELINK_MODULE := false
LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
LOCAL_SHARED_LIBRARIES := liblog libcutils
LOCAL_MODULE := sensors.hdaps
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := hdaps.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
LOCAL_SHARED_LIBRARIES := liblog libcutils
LOCAL_MODULE := sensors.iio
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := iio-sensors.cpp
include external/stlport/libstlport.mk
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PRELINK_MODULE := false
LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
LOCAL_SHARED_LIBRARIES := liblog libcutils
LOCAL_MODULE := sensors.kbd
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := kbdsensor.cpp
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PRELINK_MODULE := false
LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
LOCAL_SHARED_LIBRARIES := liblog libcutils
LOCAL_MODULE := sensors.s103t
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := s103t_sensor.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PRELINK_MODULE := false
LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
LOCAL_SHARED_LIBRARIES := liblog libcutils
LOCAL_MODULE := sensors.w500
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := w500_sensor.c
include $(BUILD_SHARED_LIBRARY)
