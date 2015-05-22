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
    public String(String original) {
        this(original.value);
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

    public String substring(int beginIndex) {
        return substring(beginIndex, length());
    }

    public String substring(int beginIndex, int endIndex) {
        return new String(value, beginIndex, endIndex-beginIndex);
    }

    public boolean startsWith(String prefix) {
        return startsWith(prefix, 0);
    }
    public boolean startsWith(String prefix, int toffset) {
        return regionMatches(toffset, prefix, 0, prefix.length());
    }

    public boolean endsWith(String suffix) {
        return regionMatches(length()-suffix.length(), suffix, 0, suffix.length());
    }

    public int indexOf(int ch) {
        return indexOf(ch, 0);
    }
    public int indexOf(int ch, int fromIndex) {
        for (int i = fromIndex; i < length(); i++) {
            if (ch == charAt(i)) {
                return i;
            }
        }
        return -1;
    }

    public int indexOf(String str) {
        return indexOf(str, 0);
    }
    public int indexOf(String str, int fromIndex) {
        for (int i = fromIndex; i <= length()-str.length(); i++) {
            if (this.startsWith(str, i)) {
                return i;
            }
        }
        return -1;
    }

    public boolean contains(String str) {
        return indexOf(str) >= 0;
    }

    public boolean regionMatches(int toffset, String other, int ooffset, int len) {
        for (int i = 0; i < len; i++) {
            if (charAt(toffset+i) != other.charAt(ooffset+1)) {
                return false;
            }
        }
        return true;
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
        return Integer.toString(i);
    }

    public static String valueOf(long l) {
        return Long.toString(l);
    }

    public static String valueOf(float f) {
        return Float.toString(f);
    }

    public static String valueOf(double d) {
        return Double.toString(d);
    }

    public static String valueOf(Object obj) {
        return obj.toString();
    }
}