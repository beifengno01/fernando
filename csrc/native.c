/*
   Copyright 2015 Technical University of Denmark, DTU Compute.
   All rights reserved.

   This file is part of the ahead-of-time bytecode compiler Fernando.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are met:

      1. Redistributions of source code must retain the above copyright notice,
         this list of conditions and the following disclaimer.

      2. Redistributions in binary form must reproduce the above copyright
         notice, this list of conditions and the following disclaimer in the
         documentation and/or other materials provided with the distribution.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER ``AS IS'' AND ANY EXPRESS
   OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
   OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
   NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY
   DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
   (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
   ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
   (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
   THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

   The views and conclusions contained in the software and documentation are
   those of the authors and should not be interpreted as representing official
   policies, either expressed or implied, of the copyright holder.
*/

#define _POSIX_C_SOURCE 200809L

#include "defs.h"
#include "jvm.h"
#include <stdlib.h>
#include <sys/time.h>
#include <time.h>
#include <pthread.h>
#include <sched.h>
#include <errno.h>

int32_t _java_lang_Object_getClass__Ljava_lang_Class_(int32_t ref, int32_t *exc) {
  return (int32_t)((_java_lang_Object_obj_t *)ref)->type;
}
int32_t _java_lang_Object_hashCode__I(int32_t ref, int32_t *exc) {
  return ref;
}

void _java_lang_Object_wait__V(int32_t ref, int32_t *exc) {
  _java_lang_Object_obj_t *obj = (_java_lang_Object_obj_t *)ref;
  if (jvm_wait(obj)) {
    *exc = (int32_t)&vmErr;
  }
}
void _java_lang_Object_notify__V(int32_t ref, int32_t *exc) {
  _java_lang_Object_obj_t *obj = (_java_lang_Object_obj_t *)ref;
  if (jvm_notify(obj)) {
    *exc = (int32_t)&vmErr;
  }
}
void _java_lang_Object_notifyAll__V(int32_t ref, int32_t *exc) {
  _java_lang_Object_obj_t *obj = (_java_lang_Object_obj_t *)ref;
  if (jvm_notify_all(obj)) {
    *exc = (int32_t)&vmErr;
  }
}

int32_t _java_lang_Class_getName__Ljava_lang_String_(int32_t ref, int32_t *exc) {
  return (int32_t)((_java_lang_Object_class_t *)ref)->name;
}

int64_t _java_lang_System_currentTimeMillis__J(int32_t *exc) {
  struct timeval time;
  gettimeofday(&time, NULL);
  return time.tv_sec*1000 + time.tv_usec/1000;
}

void _java_lang_System_exit_I_V(int32_t status, int32_t *exc) {
  exit(status);
}

struct thread_args_t {
  int32_t ref;
  int32_t *exc;
};
static void *thread_wrapper(void *arg_ptr) {
  struct thread_args_t *args = (struct thread_args_t *)arg_ptr;
  _java_lang_Thread_obj_t *thread = (_java_lang_Thread_obj_t *)args->ref;
  pthread_setspecific(currentThread, thread);
  thread->type->run__V(args->ref, args->exc);
  free(args);
  return NULL;
}

void _java_lang_Thread_start__V(int32_t ref, int32_t *exc) {
  pthread_t *pthread = malloc(sizeof(pthread_t));
  if (!pthread) {
    *exc = (int32_t)&vmErr;
    return;
  }

  jvm_putfield(_java_lang_Thread_obj_t, ref, 0, _pthread, (int32_t)pthread);

  struct thread_args_t *args = malloc(sizeof(struct thread_args_t));
  if (!args) {
    *exc = (int32_t)&vmErr;
    return;
  }

  args->ref = ref;
  args->exc = exc;

  if (pthread_create(pthread, NULL, thread_wrapper, args)) {
    *exc = (int32_t)&vmErr;
    return;
  }
}

void _java_lang_Thread_join__V(int32_t ref, int32_t *exc) {
  pthread_t *thread =
    (pthread_t *)jvm_getfield(_java_lang_Thread_obj_t, ref, 0, _pthread);
  if (pthread_join(*thread, NULL)) {
    *exc = (int32_t)&vmErr;
  }
}

void _java_lang_Thread_yield__V(int32_t *exc) {
  sched_yield();
}

void _java_lang_Thread_sleep_J_V(int32_t lo, int32_t hi, int32_t *exc) {
  int64_t v = ((int64_t)hi << 32) | (uint32_t)lo;
  const struct timespec time = { v/1000, (v % 1000)*1000000 };
  int retval = nanosleep(&time, NULL);
  if (retval && errno == EINTR) {
    *exc = (int32_t)&intrExc;
    return;
  }
}

int32_t _java_lang_Thread_currentThread__Ljava_lang_Thread_(int32_t *exc) {
  return (int32_t)pthread_getspecific(currentThread);
}

void _ferdl_io_NativeOutputStream_write_I_V(int32_t ref, int32_t b, int32_t *exc) {
  int32_t i;
  uint16_t inbuf[1] = { (uint16_t)b };
  char outbuf[6];
  int32_t len = jvm_decode(inbuf, sizeof(inbuf), outbuf, sizeof(outbuf));

  for (i = 0; i < len; i++) {
    fputc(outbuf[i], stdout);
  }
}

int32_t _ferdl_io_NativeInputStream_read__I(int32_t ref, int32_t *exc) {
  return getchar();
}

int32_t _java_lang_Float_floatToIntBits_F_I(int32_t val, int32_t *exc) {
  return val;
}
int32_t _java_lang_Float_intBitsToFloat_I_F(int32_t val, int32_t *exc) {
  return val;
}

int64_t _java_lang_Double_doubleToLongBits_D_J(int32_t lo, int32_t hi, int32_t *exc) {
  return ((int64_t) hi << 32) | (uint32_t)lo;
}
int64_t _java_lang_Double_longBitsToDouble_J_D(int32_t lo, int32_t hi, int32_t *exc) {
  return ((int64_t) hi << 32) | (uint32_t)lo;
}

#define _DMATHFUN1(JFUN, CFUN, ARGTY, RETTY)                             \
  int64_t _java_lang_Math_##JFUN##_##ARGTY##_##RETTY(int32_t lo, int32_t hi, int32_t *exc) { \
    int64_t v = ((int64_t) hi << 32) | (uint32_t)lo;                    \
    double *d = (double *)&v;                                           \
    *d = CFUN(*d);                                                      \
    return v;                                                           \
  }

#define DMATHFUN1(FUN) _DMATHFUN1(FUN,FUN,D,D)
DMATHFUN1(asin)
DMATHFUN1(acos)
DMATHFUN1(atan)
DMATHFUN1(sin)
DMATHFUN1(cos)
DMATHFUN1(tan)
DMATHFUN1(sinh)
DMATHFUN1(cosh)
DMATHFUN1(tanh)
DMATHFUN1(sqrt)
DMATHFUN1(cbrt)
DMATHFUN1(exp)
DMATHFUN1(expm1)
DMATHFUN1(log)
DMATHFUN1(log10)
DMATHFUN1(log1p)
DMATHFUN1(ceil)
DMATHFUN1(floor)

_DMATHFUN1(round, llround, D, J)

#define DMATHFUN2(FUN)                                                   \
  int64_t _java_lang_Math_##FUN##_DD_D(int32_t lo1, int32_t hi1, int32_t lo2, int32_t hi2, int32_t *exc) { \
    int64_t v1 = ((int64_t) hi1 << 32) | (uint32_t)lo1;                 \
    double *d1 = (double *)&v1;                                         \
    int64_t v2 = ((int64_t) hi2 << 32) | (uint32_t)lo2;                 \
    double *d2 = (double *)&v2;                                         \
    *d1 = FUN(*d1, *d2);                                               \
    return v1;                                                          \
  }

DMATHFUN2(atan2)
DMATHFUN2(pow)
DMATHFUN2(hypot)

#define _FMATHFUN1(JFUN, CFUN, ARGTY, RETTY)                            \
  int32_t _java_lang_Math_##JFUN##_##ARGTY##_##RETTY(int32_t v, int32_t *exc) { \
    float *f = (float *)&v;                                             \
    *f = CFUN(*f);                                                      \
    return v;                                                           \
  }

#define FMATHFUN1(FUN) _FMATHFUN1(FUN,FUN##f,F,F)
FMATHFUN1(asin)
FMATHFUN1(acos)
FMATHFUN1(atan)
FMATHFUN1(sin)
FMATHFUN1(cos)
FMATHFUN1(tan)
FMATHFUN1(sinh)
FMATHFUN1(cosh)
FMATHFUN1(tanh)
FMATHFUN1(sqrt)
FMATHFUN1(cbrt)
FMATHFUN1(exp)
FMATHFUN1(expm1)
FMATHFUN1(log)
FMATHFUN1(log10)
FMATHFUN1(log1p)
FMATHFUN1(ceil)
FMATHFUN1(floor)

_FMATHFUN1(round, lroundf, F, I)

#define FMATHFUN2(FUN)                                                  \
  int32_t _java_lang_Math_##FUN##_FF_F(int32_t v1, int32_t v2, int32_t *exc) { \
    float *d1 = (float *)&v1;                                           \
    float *d2 = (float *)&v2;                                           \
    *d1 = FUN##f(*d1, *d2);                                             \
    return v1;                                                          \
  }

FMATHFUN2(atan2)
FMATHFUN2(pow)
FMATHFUN2(hypot)
