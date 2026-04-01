inline int *get_int_ptr(float *fp) {
  return fp;
}

#ifdef FATAL
void fatal(int);
void fatal(float);
#endif

// RUN: c-index-test -write-pch %t.pch  %s
// RUN: c-index-test -write-pch %t.pch -DFATAL  %s

