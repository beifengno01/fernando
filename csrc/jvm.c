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

#include <stdlib.h>
#include <stdio.h>
#include <locale.h>
#include <iconv.h>
#include <pthread.h>
#include "jvm.h"

int32_t *allocPtr;
int32_t *allocEnd;

_java_lang_NullPointerException_obj_t npExc = { &_java_lang_NullPointerException, 0, };
_java_lang_ArrayIndexOutOfBoundsException_obj_t abExc = { &_java_lang_ArrayIndexOutOfBoundsException, 0, };
_java_lang_ClassCastException_obj_t ccExc = { &_java_lang_ClassCastException, 0, };
_java_lang_ArithmeticException_obj_t aeExc = { &_java_lang_ArithmeticException, 0, };
_java_lang_OutOfMemoryError_obj_t omErr = { &_java_lang_OutOfMemoryError, 0, };

void jvm_clinit(int32_t *exc) {
  const int heapSize = 256*1024;
  allocPtr = malloc(heapSize);
  allocEnd = allocPtr+(heapSize >> 2);
  setlocale(LC_ALL, "");
}

void jvm_init(int32_t *retexc) {
  int32_t exc = 0;
  _java_lang_NullPointerException__init___V((int32_t)&npExc, &exc);
  if (exc != 0) { *retexc = exc; return; }
  _java_lang_ArrayIndexOutOfBoundsException__init___V((int32_t)&abExc, &exc);
  if (exc != 0) { *retexc = exc; return; }
  _java_lang_ClassCastException__init___V((int32_t)&ccExc, &exc);
  if (exc != 0) { *retexc = exc; return; }
  _java_lang_ArithmeticException__init___V((int32_t)&aeExc, &exc);
  if (exc != 0) { *retexc = exc; return; }
  _java_lang_OutOfMemoryError__init___V((int32_t)&omErr, &exc);
  if (exc != 0) { *retexc = exc; return; }
}

static pthread_mutex_t global_lock = PTHREAD_MUTEX_INITIALIZER;

void jvm_lock(_java_lang_Object_obj_t *obj) {
  if (obj->lock == NULL) {
    pthread_mutex_lock(&global_lock);
    if (obj->lock == NULL) {
      obj->lock = malloc(sizeof(pthread_mutex_t));
      pthread_mutex_init(obj->lock, NULL);
    }
    pthread_mutex_unlock(&global_lock);
  }
  
  pthread_mutex_lock(obj->lock);
}

void jvm_unlock(_java_lang_Object_obj_t *obj) {
  pthread_mutex_unlock(obj->lock);
}

int32_t jvm_instanceof(const _java_lang_Object_class_t *ref,
                       const _java_lang_Object_class_t *type) {
  if (ref == 0) {
    return 0;
  }
  if (ref == type) {
    return 1;
  }
  if (ref->elemtype != 0 && type->elemtype != 0) {
    return jvm_instanceof(ref->elemtype, type->elemtype);
  }
  return jvm_instanceof(ref->super, type);
}

int32_t jvm_decode(uint16_t *inbuf, int32_t inbytes, char *outbuf, int32_t outbytes) {
#ifdef __patmos__ /* no implementation of iconv on Patmos */
  int32_t i;
  for (i = 0; i < inbytes/sizeof(inbuf[0]); i++) {
    if (inbuf[i] < 0x7f) {
      outbuf[i] = inbuf[i];
    } else {
      outbuf[i] = '?';
    }
  }
  return inbytes/sizeof(inbuf[0]);
#else
  iconv_t conv = iconv_open("//TRANSLIT", "UTF-16//");
  int32_t maxbytes = outbytes;
  int32_t len = iconv(conv, (char **)&inbuf, (size_t *)&inbytes,
                      &outbuf, (size_t *)&outbytes);
  iconv_close(conv);
  if (len < 0) {
    return len;
  } else {
    return maxbytes-outbytes;
  }
#endif
}

void jvm_catch(int32_t exc) {
  int i;

  fflush(0);
  fprintf(stderr, "Uncaught exception: ");

  _java_lang_Throwable_obj_t *thr = (_java_lang_Throwable_obj_t *)exc;
  _java_lang_String_obj_t *name = (_java_lang_String_obj_t*)thr->type->name;
  _char___obj_t *chars = (_char___obj_t *)name->_0_value;
  int32_t chars_bytes = sizeof(chars->_1_data[0])*chars->_0_length;
  char print_buf[chars_bytes]; // this size is just a guess

  int len = jvm_decode(chars->_1_data, chars_bytes, print_buf, sizeof(print_buf));

  for (i = 0; i < len; i++) {
    fputc(print_buf[i], stderr);
  }

  fputc('\n', stderr);
  exit(EXIT_FAILURE);
}

int32_t *jvm_alloc(void *type, int32_t size, int32_t *exc) {

  pthread_mutex_lock(&global_lock);
  int32_t *ptr = allocPtr;
  allocPtr += (size + 3) >> 2;
  pthread_mutex_unlock(&global_lock);

  if (allocPtr > allocEnd) {
    *exc = (int32_t)&omErr;
    return ptr;
  }
  memset(ptr, 0, (size + 3) & ~0x3);
  ((_java_lang_Object_obj_t*)ptr)->type = type;
  return ptr;
}
