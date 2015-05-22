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

public class Float {

    public static final float NaN = Float.intBitsToFloat(0x7fc00000);
    public static final float POSITIVE_INFINITY = Float.intBitsToFloat(0x7f800000);
    public static final float NEGATIVE_INFINITY = Float.intBitsToFloat(0xff800000);
    public static final float MAX_VALUE = Float.intBitsToFloat(0x7f7fffff);
    public static final float MIN_VALUE = Float.intBitsToFloat(0x1);

    private float value;

    public Float(float value) {
        this.value = value;
    }

    public static String toString(float f) {
        // check for NaN
        if (f != f) {
            return "NaN";
        }

        // convert to integer representation
        int bits = Float.floatToIntBits(f);
        boolean negative = bits < 0;

        // extract exponent
        int exp  = ((bits >>> 23) & 0xff) - 127;
        if (exp == 128) {
            return negative ? "-Infinity" : "Infinity";
        }
        // extract mantissa
        int mant = (bits & ((1 << 23) - 1)) | (exp != -127 ? (1 << 23) : 0);
        if (mant == 0) {
            return negative ? "-0.0" : "0.0";
        }
        // fix exponent for denormalized numbers
        if (exp == -127) { exp++;
            while ((mant >>> 23) == 0) {
                mant <<= 1; exp--;                
            }
        }

        // create buffer and add sign
        StringBuffer buffer = new StringBuffer();
        if (negative) buffer.append('-');

        // compute order of number to be computed
        int order = (int)(exp/3.321928095f)+1;

        // keep track of the significant digits
        int signif = 1;

        // normalize for scientific notation
        if (order < -3) {
            int o = order;
            boolean round = false, sticky = false;
            while (o < 0) {
                round = false;
                mant *= 10; o++;
                signif *= 10;
                if (o <= -43) { signif /= 10; }
                while ((mant >>> 26) > 1) {
                    round = (mant & 1) != 0;
                    sticky |= round;
                    mant = mant >>> 1; exp++;
                    signif >>>= 1;
                }
            }
            if (round && sticky) { mant += 1; }
            if (signif == 0) { signif = 1; }
        }

        if (order >= 7) {
            int o = order;
            while (o > 0) {
                while ((mant >>> 30) < 1) {
                    mant <<= 1; exp--;
                }
                mant = (mant + 5) / 10; o--;
            }
        }

        // keep track of the point and the significant digits
        int point = 23-exp;

        // a hack to get things working within 32 bits
        while (point > 27) {
            mant = (mant + 4) >>> 3;
            point -= 3;
        }

        // fix imprecise order computation
        while ((order < -3 || order > 7) && ((mant + signif/2) >>> point) == 0) {
            mant *= 10; order--;
        }
        while ((order < -4 || order >= 7) && ((mant + signif/2) >>> point) >= 10) {
            mant = (mant + 5) / 10; order++;
        }

        int digits = 0;
        do {
            // calculate current digit(s)
            int intval = (mant + signif/2) >>> point;                
            buffer.append(intval);
            if (digits == 0) { buffer.append('.'); }
            digits++;
            // remove printed digits from mantissa
            mant = (mant - (intval << point)) * 10;
            signif *= 10;
        } while (digits < 2 || mant >= signif/2);

        // add exponent for scientific notation
        if (order < -3 || order >= 7) {        
            buffer.append('E');
            buffer.append(order);
        }

        return buffer.toString();
    }

    public static native int floatToIntBits(float value);
    public static native float intBitsToFloat(int bits);
}