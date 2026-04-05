// RUN: %clang_cc1 -fsyntax-only -Wunused-variable  -verify %s

void f() {
  int i;  // expected-error{{unused}}
  int j;  // expected-error{{unused}}
}
