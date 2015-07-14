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

#ifndef _JVM_H
#define _JVM_H

#include <stdint.h>
#include <pthread.h>
#include "defs.h"

extern pthread_key_t currentThread;

extern pthread_mutex_t globalLock;

extern int32_t *allocPtr;

extern _java_lang_NullPointerException_obj_t npExc;
extern _java_lang_ArrayIndexOutOfBoundsException_obj_t abExc;
extern _java_lang_ClassCastException_obj_t ccExc;
extern _java_lang_ArithmeticException_obj_t aeExc;
extern _java_lang_InterruptedException_obj_t intrExc;
extern _java_lang_OutOfMemoryError_obj_t omErr;
extern _java_lang_VirtualMachineError_obj_t vmErr;

extern _java_lang_String_obj_t stringPool[];

void jvm_clinit(int32_t *exc);
void jvm_init(int32_t *exc);
int32_t jvm_args(int argc, char **argv, int32_t *exc);

int32_t jvm_encode(char *inbuf, int32_t inbytes, uint16_t *outbuf, int32_t outbytes);
int32_t jvm_decode(uint16_t *inbuf, int32_t inbytes, char *outbuf, int32_t outbytes);
void jvm_catch(int32_t exc);

int jvm_lock(_java_lang_Object_obj_t *obj);
int jvm_unlock(_java_lang_Object_obj_t *obj);

int jvm_wait(_java_lang_Object_obj_t *obj);
int jvm_notify(_java_lang_Object_obj_t *obj);
int jvm_notify_all(_java_lang_Object_obj_t *obj);

int32_t jvm_instanceof(const _java_lang_Object_class_t *ref,
                       const _java_lang_Object_class_t *type);

#define jvm_getfield(TYPE, REF, IDX, NAME)      \
  ((const TYPE *)REF)->_ ## IDX ## _ ## NAME
#define jvm_getfield_ref(TYPE, REF, IDX, NAME)  \
  jvm_getfield(TYPE, REF, IDX, NAME)
#define jvm_getfield_long(TYPE, REF, IDX, NAME) \
  ((const TYPE *)REF)->_ ## IDX ## _ ## NAME

#define jvm_putfield(TYPE, REF, IDX, NAME, VAL) \
  ((TYPE *)REF)->_ ## IDX ## _ ## NAME = VAL
#define jvm_putfield_ref(TYPE, REF, IDX, NAME, VAL) \
  jvm_putfield(TYPE, REF, IDX, NAME, VAL)
#define jvm_putfield_long(TYPE, REF, IDX, NAME, VAL)    \
  ((TYPE *)REF)->_ ## IDX ## _ ## NAME = VAL

#define jvm_arrlength(TYPE, REF)                \
  ((const TYPE *)REF)->_0_length
#define jvm_setarrlength(TYPE, REF, VAL)        \
  ((TYPE *)REF)->_0_length = VAL

#define jvm_arrload(TYPE, REF, IDX)             \
  (&((const TYPE *)REF)->_1_data[0])[IDX]
#define jvm_arrload_ref(TYPE, REF, IDX)         \
  jvm_arrload(TYPE, REF, IDX)
#define jvm_arrload_long(TYPE, REF, IDX)        \
  (&((const TYPE *)REF)->_1_data[0])[IDX]

#define jvm_arrstore(TYPE, REF, IDX, VAL)       \
  (&((TYPE *)REF)->_1_data[0])[IDX] = VAL
#define jvm_arrstore_ref(TYPE, REF, IDX, VAL)   \
  jvm_arrstore(TYPE, REF, IDX, VAL)
#define jvm_arrstore_long(TYPE, REF, IDX, VAL)  \
  (&((TYPE *)REF)->_1_data[0])[IDX] = VAL

#define jvm_getstatic(NAME)                     \
  NAME
#define jvm_getstatic_ref(NAME)                 \
  jvm_getstatic(NAME)
#define jvm_getstatic_long(NAME)                \
  NAME

#define jvm_putstatic(NAME, VAL)                \
  NAME = VAL
#define jvm_putstatic_ref(NAME, VAL)            \
  jvm_putstatic(NAME, VAL)
#define jvm_putstatic_long(NAME, VAL)           \
  NAME = VAL

#if defined(__gcc__)
#define ALLOC_ATTRIBS __attribute__((returns_nonnull,malloc))
#elif defined(__clang__)
#define ALLOC_ATTRIBS __attribute__((malloc))
#else
#define ALLOC_ATTRIBS
#endif

int32_t *jvm_alloc(void *type, int32_t size, int32_t *exc) ALLOC_ATTRIBS;

#if defined(__gcc__) || defined(__clang__)
#define unlikely(cond) __builtin_expect(cond, 0)
#else
#define unlikely(cond) cond
#endif

#endif /* _JVM_H */
