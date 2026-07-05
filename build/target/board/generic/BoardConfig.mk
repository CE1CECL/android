# config.mk
#
# Product-specific compile-time definitions.
#

# The generic product target doesn't have any hardware-specific pieces.
TARGET_NO_BOOTLOADER := true
TARGET_NO_KERNEL := true
TARGET_ARCH := arm
TARGET_PRELINK_MODULE := false

# Note: we build the platform images for ARMv7-A _without_ NEON.
#
# Technically, the emulator supports ARMv7-A _and_ NEON instructions, but
# emulated NEON code paths typically ends up 2x slower than the normal C code
# it is supposed to replace (unlike on real devices where it is 2x to 3x
# faster).
#
# What this means is that the platform image will not use NEON code paths
# that are slower to emulate. On the other hand, it is possible to emulate
# application code generated with the NDK that uses NEON in the emulator.
#
TARGET_ARCH_VARIANT := armv5te
TARGET_CPU_VARIANT := generic
TARGET_CPU_ABI := armeabi
TARGET_CPU_ABI2 := armeabi-v7a
ARCH_ARM_HAVE_TLS_REGISTER := true

BOARD_WPA_SUPPLICANT_DRIVER := WEXT
HAVE_HTC_AUDIO_DRIVER := true
BOARD_USES_GENERIC_AUDIO := false
BOARD_USES_ALSA_AUDIO := true
BOARD_USES_LEGACY_ALSA_AUDIO := false

# no hardware camera
USE_CAMERA_STUB := false

# Enable dex-preoptimization to speed up the first boot sequence
# of an SDK AVD. Note that this operation only works on Linux for now
ifeq ($(HOST_OS),linux)
  ifeq ($(WITH_DEXPREOPT),)
    WITH_DEXPREOPT := true
  endif
endif

# Build OpenGLES emulation guest and host libraries
BUILD_EMULATOR_OPENGL := false

# Build and enable the OpenGL ES View renderer. When running on the emulator,
# the GLES renderer disables itself if host GL acceleration isn't available.
USE_OPENGL_RENDERER := false
