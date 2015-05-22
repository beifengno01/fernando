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

public class Double {

    public static final double NaN = Double.longBitsToDouble(0x7ff8000000000000L);
    public static final double POSITIVE_INFINITY = Double.longBitsToDouble(0x7ff0000000000000L);
    public static final double NEGATIVE_INFINITY = Double.longBitsToDouble(0xfff0000000000000L);
    public static final double MAX_VALUE = Double.longBitsToDouble(0x7fefffffffffffffL);
    public static final double MIN_VALUE = Double.longBitsToDouble(0x1L);

    private double value;

    public Double(double value) {
        this.value = value;
    }

    public static String toString(double d) {
        // check for NaN
        if (d != d) {
            return "NaN";
        }

        // convert to integer representation
        long bits = Double.doubleToLongBits(d);
        boolean negative = bits < 0;

        // extract exponent
        int exp  = (int)(((bits >>> 52) & 0x7ff) - 1023);
        if (exp == 1024) {
            return negative ? "-Infinity" : "Infinity";
        }
        // extract mantissa
        long mant = (bits & ((1L << 52) - 1)) | (exp != -1023 ? (1L << 52) : 0);
        if (mant == 0) {
            return negative ? "-0.0" : "0.0";
        }
        // fix exponent for denormalized numbers
        if (exp == -1023) {
            exp++;
            while ((mant >>> 52) == 0) {
                mant <<= 1; exp--;                
            }
        }
 
        // create buffer and add sign
        StringBuffer buffer = new StringBuffer();
        if (negative) buffer.append('-');

        // compute order of number to be computed
        int order = (int)(exp/3.321928095)+1;

        // keep track of the significant digits
        long signif = 1;

        // normalize for scientific notation
        if (order < -3) {
            int o = order;
            boolean round = false, sticky = false;
            while (o < 0) {
                round = false;
                mant *= 10; o++;
                signif *= 10;
                if (o <= -322) { signif /= 10; }
                while ((mant >>> 58) > 1) {
                    round = (mant & 1) != 0;
                    sticky |= round;
                    mant >>>= 1; exp++;
                    signif >>>= 1;
                }
            }
            if (round && sticky) { mant += 1; }
            if (signif == 0) { signif = 1; }
        }

        if (order >= 7) {
            int o = order;
            while (o > 0) {
                while ((mant >>> 62) < 1) {
                    mant <<= 1; exp--;
                }
                mant = (mant + 5) / 10; o--;
            }
        }

        // the decimal point
        int point = 52-exp;

        // a hack to get things working within 64 bits
        while (point > 59) {
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
            long intval = (mant + signif/2) >>> point;
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

    public static native long doubleToLongBits(double value);
    public static native double longBitsToDouble(long bits);
}