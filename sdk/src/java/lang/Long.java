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

public class Long {
    private long value;

    public Long(long value) {
        this.value = value;
    }

    public static long parseLong(String s) throws NumberFormatException {
        int sign = 1;
        long val = 0;
        int i = 0;
        if (s.charAt(i) == '+') {
            i++;
        } else if (s.charAt(i) == '-') {
            i++;
            sign = -1;
        }
        for ( ; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                val = val * 10 + (c-'0');
            } else {
                throw new NumberFormatException();
            }
        }
        return sign*val;
    }

    public static String toString(long l) {
        if (l == 0) {
            return "0";
        }
        char buf [] = new char[20];
        int pos = 0;
        if (l < 0) {
            buf[pos++] = '-';
        } else {
            l = -l;
        }
        boolean cont = false;
        for (long k = 1000000000000000000L; k > 0; k /= 10) {
            int m = -(int)((l/k)%10);
            if (m != 0 || cont) {
                buf[pos++] = (char)('0'+m);
                cont = true;
            }
        }
        return new String(buf, 0, pos);
    }

    public static String toHexString(long l) {
        if (l == 0) {
            return "0";
        }
        char buf [] = new char[16];
        int pos = buf.length;
        while (l != 0) {
            int c = (int)(l & 0xf);
            buf[--pos] = c < 10 ? (char)('0'+c) : (char)('a'+c-10);
            l >>>= 4;
        }
        return new String(buf, pos, buf.length-pos);
    }
}