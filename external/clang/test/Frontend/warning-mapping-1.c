// Check that -w has higher priority than .
// RUN: %clang_cc1 -verify -Wsign-compare  -w %s

int f0(int x, unsigned y) {
  return x < y;
}
