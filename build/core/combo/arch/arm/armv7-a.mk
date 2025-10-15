# Configuration for Linux on ARM.
# Generating binaries for the ARMv7-a architecture and higher
#
ifneq ($(strip $(TARGET_ARCH_VARIANT_FPU)),none)
else
endif

ifeq ($(strip $(TARGET_ARCH_VARIANT_FPU)),)
TARGET_ARCH_VARIANT_FPU         := vfpv3-d16
endif

ifeq ($(strip $(TARGET_ARCH_VARIANT_FPU)),neon)
endif

ifeq ($(strip $(TARGET_ARCH_VARIANT_CPU)),)
TARGET_ARCH_VARIANT_CPU         := cortex-a8
endif

ifeq ($(strip $(TARGET_CPU_SMP)),true)
endif

arch_variant_cflags := \
    -march=armv7-a \
    -mtune=$(TARGET_ARCH_VARIANT_CPU)

ifeq ($(strip $(ARCH_ARM_HAVE_VFP)),true)
arch_variant_cflags += -mfloat-abi=softfp
arch_variant_cflags += -mfpu=$(TARGET_ARCH_VARIANT_FPU)
else
arch_variant_cflags += -mfloat-abi=soft
endif

ifeq ($(strip $(TARGET_ARCH_VARIANT_CPU)),cortex-a8)
arch_variant_ldflags := \
	-Wl,--fix-cortex-a8
else
arch_variant_ldflags :=
endif
