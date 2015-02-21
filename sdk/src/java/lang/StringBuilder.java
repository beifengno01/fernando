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

public class StringBuilder {

    private static final int INITIAL_CAPACITY = 16;
    private char buffer[];
    private int length;

    public StringBuilder() {
        buffer = new char[INITIAL_CAPACITY];
        length = 0;
    }

    private void ensureCapacity(int capacity) {
        while (capacity >= buffer.length) {
            char newBuffer[] = new char[buffer.length*2];
            for (int i = 0; i < buffer.length; i++) {
                newBuffer[i] = buffer[i];
            }
            buffer = newBuffer;
        }
    }

    public StringBuilder append(char c) {
        ensureCapacity(length+1);
        buffer[length++] = c;
        return this;
    }

    public StringBuilder append(String str) {
        for (int i = 0; i < str.length(); i++) {
            append(str.charAt(i));
        }
        return this;
    }

    public StringBuilder append(Object obj) {
        append(obj.toString());
        return this;
    }

    public StringBuilder append(boolean b) {
        append(String.valueOf(b));
        return this;
    }

    public StringBuilder append(int i) {
        append(String.valueOf(i));
        return this;
    }

    public StringBuilder append(long l) {
        append(String.valueOf(l));
        return this;
    }

    public StringBuilder append(float f) {
        append(String.valueOf(f));
        return this;
    }

    public StringBuilder append(double d) {
        append(String.valueOf(d));
        return this;
    }

    public String toString() {
        return new String(buffer, 0, length);
    }
}