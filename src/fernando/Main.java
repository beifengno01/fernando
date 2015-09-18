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

package fernando;

import org.apache.bcel.Repository;
import org.apache.bcel.util.SyntheticRepository;

import java.io.InputStream;
import java.io.IOException;

/**
 * A patched version of BCEL's ClassPath that ignores the class loader.
 */
class ClassPath extends org.apache.bcel.util.ClassPath {
    /**
     * Create a new ClassPath
     * @param path The path to the classes
     */
    public ClassPath(String path) {
        super(path);
    }
    /**
     * Get input stream for class file with name and suffix
     * @param name The name of the class file
     * @param suffix The suffix of the class file
     */
    public InputStream getInputStream(String name, String suffix) throws IOException {
        return getClassFile(name, suffix).getInputStream();
    }
}

/**
 * The main class of Fernando.
 */
public class Main {
    // hide default constructor
    private Main() {
    }

    /**
     * The usual main method.
     * @param args The command line arguments. The first argument is
     * the class path, the second argument the name of the main class,
     * and the third argument the directory where to generate files.
     */
    public static void main(String [] args) throws ClassNotFoundException, IOException {
        if (args.length != 3) {
            System.err.println("Usage: java fernando.Main <classpath> <mainclass> <directory>");
            System.exit(-1);
        }

        ClassPath cp = new ClassPath(args[0]);
        Repository.setRepository(SyntheticRepository.getInstance(cp));
        ClassInfo.loadHull(args[1]);
        ClassInfo.dumpAll(args[2]);
    }
}