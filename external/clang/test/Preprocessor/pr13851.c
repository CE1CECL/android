// Check that -E -M -MF does not cause an "argument unused" error, by adding
//  to the clang invocation.  Also check the dependency output, if any.
// RUN: %clang  -E -M -MF %t-M.d %s
// RUN: FileCheck --input-file=%t-M.d %s
// CHECK: pr13851.o:
// CHECK: pr13851.c

// Check that -E -MM -MF does not cause an "argument unused" error, by adding
//  to the clang invocation.  Also check the dependency output, if any.
// RUN: %clang  -E -MM -MF %t-MM.d %s
// RUN: FileCheck --input-file=%t-MM.d %s
