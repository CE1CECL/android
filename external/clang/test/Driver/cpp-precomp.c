// RUN: %clang -target x86_64-apple-darwin10 \
// RUN:    -cpp-precomp -fsyntax-only %s

// RUN: %clang -target x86_64-apple-darwin10 \
// RUN:    -no-cpp-precomp -fsyntax-only %s
