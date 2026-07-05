ARCH_X86_HAVE_SSE2 := false
ARCH_X86_HAVE_SSE3 := false
ARCH_X86_HAVE_SSSE3 := false
TARGET_GLOBAL_CFLAGS += -march=i386 -mtune=i386 -mfpmath=387 -mno-mmx -mno-sse
