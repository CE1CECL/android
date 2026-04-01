// Check that #pragma diagnostic warning overrides . This matches GCC's
// original documentation, but not its earlier implementations.
// 
// RUN: %clang_cc1 -verify  %s

#pragma clang diagnostic warning "-Wsign-compare"
int f0(int x, unsigned y) {
  return x < y; // expected-warning {{comparison of integers}}
}
