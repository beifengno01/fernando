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

package java.lang;

public class Math {
    public static final double E = 2.718281828459045;
    public static final double PI = 3.141592653589793;

    public static int abs(int x) {
        return x < 0 ? -x : x;
    }
    public static long abs(long x) {
        return x < 0 ? -x : x;
    }
    public static float abs(float x) {
        return x < 0 ? -x : x;
    }
    public static double abs(double x) {
        return x < 0 ? -x : x;
    }

    public static int min(int x, int y) {
        return x < y ? x : y;
    }
    public static long min(long x, long y) {
        return x < y ? x : y;
    }
    public static float min(float x, float y) {
        return x < y ? x : y;
    }
    public static double min(double x, double y) {
        return x < y ? x : y;
    }

    public static int max(int x, int y) {
        return x > y ? x : y;
    }
    public static long max(long x, long y) {
        return x > y ? x : y;
    }
    public static float max(float x, float y) {
        return x > y ? x : y;
    }
    public static double max(double x, double y) {
        return x > y ? x : y;
    }


    public static native double asin(double x);
    public static native double acos(double x);
    public static native double atan(double x);
    public static native double sin(double x);
    public static native double cos(double x);
    public static native double tan(double x);
    public static native double sinh(double x);
    public static native double cosh(double x);
    public static native double tanh(double x);
    public static native double sqrt(double x);
    public static native double cbrt(double x);
    public static native double exp(double x);
    public static native double expm1(double x);
    public static native double log(double x);
    public static native double log10(double x);
    public static native double log1p(double x);

    public static native double ceil(double x);
    public static native double floor(double x);
    public static native long round(double x);

    public static native double atan2(double x, double y);
    public static native double pow(double x, double y);
    public static native double hypot(double x, double y);


    public static native float asin(float x);
    public static native float acos(float x);
    public static native float atan(float x);
    public static native float sin(float x);
    public static native float cos(float x);
    public static native float tan(float x);
    public static native float sinh(float x);
    public static native float cosh(float x);
    public static native float tanh(float x);
    public static native float sqrt(float x);
    public static native float cbrt(float x);
    public static native float exp(float x);
    public static native float expm1(float x);
    public static native float log(float x);
    public static native float log10(float x);
    public static native float log1p(float x);

    public static native float ceil(float x);
    public static native float floor(float x);
    public static native int round(float x);

    public static native float atan2(float x, float y);
    public static native float pow(float x, float y);
    public static native float hypot(float x, float y);
}