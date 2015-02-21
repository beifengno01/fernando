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

public class String {
    private final char[] value;

    public String() {
        value = new char[0];
    }
    public String(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }
    public String(byte[] bytes, int offset, int count) {
        value = new char[count];
        for (int i = 0; i < count; i++) {
            value[i] = (char)bytes[offset+i];
        }
    }
    public String(char[] data) {
        this(data, 0, data.length);
    }
    public String(char[] data, int offset, int count) {
        value = new char[count];
        for (int i = 0; i < count; i++) {
            value[i] = data[offset+i];
        }
    }


    public char charAt(int index) {
        return value[index];
    }

    public int length() {
        return value.length;
    }

    public String toString() {
        return this;
    }


    public static String valueOf(boolean b) {
        return b ? "true" : "false";
    }

    public static String valueOf(char c) {
        char arr[] = new char[1];
        arr[0] = c;
        return new String(arr);
    }

    public static String valueOf(char data []) {
        return new String(data);
    }

    public static String valueOf(char data [], int offset, int count) {
        return new String(data, offset, count);
    }

    public static String valueOf(int i) {
        if (i == 0) {
            return "0";
        }
        char buf [] = new char[11];
        int pos = 0;
        if (i < 0) {
            buf[pos++] = '-';
            i = -i;
        } 
        boolean cont = false;
        for (int k = 1000000000; k > 0; k /= 10) {
            int m = (i/k)%10;
            if (m != 0 || cont) {
                buf[pos++] = (char)('0'+m);
                cont = true;
            }
        }
        return new String(buf, 0, pos);
    }

    public static String valueOf(long l) {
        if (l == 0) {
            return "0";
        }
        char buf [] = new char[20];
        int pos = 0;
        if (l < 0) {
            buf[pos++] = '-';
            l = -l;
        } 
        boolean cont = false;
        for (long k = 1000000000000000000L; k > 0; k /= 10) {
            int m = (int)((l/k)%10);
            if (m != 0 || cont) {
                buf[pos++] = (char)('0'+m);
                cont = true;
            }
        }
        return new String(buf, 0, pos);
    }

    public static String valueOf(float f) {
        return valueOf((double)f);
    }

    private static native int fillDoubleValue(byte [] buf, double d);
    public static String valueOf(double d) {
        byte [] buf = new byte[32];
        int len = fillDoubleValue(buf, d);
        return new String(buf, 0, len);
    }

    public static String valueOf(Object obj) {
        return obj.toString();
    }
}