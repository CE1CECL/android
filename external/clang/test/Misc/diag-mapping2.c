// This should warn by default.
// RUN: %clang_cc1 %s 2>&1 | grep "warning:"

// This should not emit anything.
// RUN: %clang_cc1 %s -w 2>&1 | not grep diagnostic
// RUN: %clang_cc1 %s -Wno-#warnings 2>&1 | not grep diagnostic

//  can map all warnings to error.
// RUN: not %clang_cc1 %s  2>&1 | grep "error:"

//  can map this one warning to error.
// RUN: not %clang_cc1 %s -Wno-#warnings 2>&1 | grep "error:"

// -Wno-error= overrides .  rdar://3158301
// RUN: %clang_cc1 %s  -Wno-error=#warnings 2>&1 | grep "warning:"

// -Wno-error overrides .  PR4715
// RUN: %clang_cc1 %s  -Wno-error 2>&1 | grep "warning:"

#warning foo


