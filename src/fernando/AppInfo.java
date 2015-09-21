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

import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;

import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class AppInfo {

    /** The name of the class that contains the main method. */
    private String entryClass;
    
    /** A map between class names and classes. */
    private Map<String, AbstractClassInfo> classInfoMap = new LinkedHashMap<String, AbstractClassInfo>();

    /** The list of interfaces in the application. */
    private List<AbstractClassInfo> interfaceList = new LinkedList<AbstractClassInfo>();

    /** A helper structure to compute which methods are truly virtual. */
    private Set<String> virtualMethods;

    /**
     * Constructor
     */
    public AppInfo(String entry) throws ClassNotFoundException {
        loadHull(entry);
    }

    /**
     * Find class info for a name.
     * @param name The name of the class to find
     * @return The class info for the name
     */
    public AbstractClassInfo getClassInfo(String name) {
        return classInfoMap.get(name);
    }

    /**
     * Get the list of interfaces of this application.
     * @return The list of interfaces of this application
     */
    public List<AbstractClassInfo> getInterfaceList() {
        return interfaceList;
    }

    /**
     * Add a method to the set of truly virtual methods.
     * @param oldClass The class of the original definition
     * @param newClass The class of the new definition
     * @param m The method to be added
     */
    public void addVirtualMethod(AbstractClassInfo oldClass, AbstractClassInfo newClass, Method m) {
        if (virtualMethods != null) {
            String methName = m.getName()+m.getSignature();                
            virtualMethods.add(oldClass.getName()+"."+methName);
            virtualMethods.add(newClass.getName()+"."+methName);
        }
    }

    /**
     * Get the truly virtual methods of this application.
     * @return The set of truly virtual methods of the application
     */
    public Set<String> getVirtualMethods() {
        if (virtualMethods == null) {
            virtualMethods = new LinkedHashSet<String>();
            for (AbstractClassInfo ci : classInfoMap.values()) {
                ci.computeInstanceMethods();
            }
        }
        return virtualMethods;
    }

    /**
     * Load the transitive hull of the application, including classes
     * that are used by the JVM such as {@link java.lang.NullPointerException}.
     * @param entry The name of the entry class
     * @throws ClassNotFoundException if some referenced class cannot be found
     */
    private void loadHull(String entry) throws ClassNotFoundException {
        Hull hull = new Hull();
        hull.add("[Z");
        hull.add("[B");
        hull.add("[C");
        hull.add("[S");
        hull.add("[I");
        hull.add("[J");
        hull.add("[F");
        hull.add("[D");
        hull.add("[Ljava.lang.String;");
        hull.add("java.lang.Class");
        hull.add("java.lang.Thread");
        hull.add("java.lang.NullPointerException");
        hull.add("java.lang.ArrayIndexOutOfBoundsException");
        hull.add("java.lang.ClassCastException");
        hull.add("java.lang.ArithmeticException");
        hull.add("java.lang.OutOfMemoryError");
        hull.add("java.lang.InterruptedException");
        hull.add("java.lang.VirtualMachineError");

        hull.add(entry);
        Map<String, JavaClass> classes = hull.resolve();
        for (Map.Entry<String, JavaClass> e : classes.entrySet()) {
            AbstractClassInfo ci = new ClassInfo(this, e.getKey(), e.getValue());
            classInfoMap.put(e.getKey(), ci);
            if (e.getValue().isInterface()) {
                interfaceList.add(ci);
            }
        }
        entryClass = entry.replace('/', '.');
    }

    /**
     * Create a writable file.
     * @param dir The directory in which to create the file
     * @param name The file name
     * @return The created file
     */
    private PrintWriter getFile(String dir, String name) throws IOException {
        FileWriter f = new FileWriter(dir+File.separator+name);
        BufferedWriter b = new BufferedWriter(f);
        return new PrintWriter(b);
    }

    /**
     * Generate C code for the entire application.
     * @param outDir The directory in which to create generated files
     */
    public void dumpAll(String outDir) throws IOException {
        Map<String, Integer> stringPool = new LinkedHashMap<String, Integer>();
     
        PrintWriter defsOut = getFile(outDir, "defs.h");
        dumpStartDefs(defsOut);
        Set<AbstractClassInfo> dumpedDef = new LinkedHashSet<AbstractClassInfo>();
        for (AbstractClassInfo c : classInfoMap.values()) {
            c.dumpDefs(defsOut, dumpedDef);
        }
        dumpEndDefs(defsOut);
        defsOut.flush();

        try {
            for (AbstractClassInfo c : classInfoMap.values()) {
                PrintWriter out = getFile(outDir+File.separator+"classes", Lang.getName(c)+".c");
                out.println("#include \"jvm.h\"");
                c.dumpBodies(out, stringPool);
                out.flush();
            }
        } catch (ClassNotFoundException exc) {
            Logger.getGlobal().severe("Class that should have been loaded not found: "+exc);
        }

        PrintWriter mainOut = getFile(outDir, "main.c");
        mainOut.println("#include \"jvm.h\"");
        dumpStringPool(mainOut, stringPool);
        dumpMain(mainOut);
        mainOut.flush();
    }

    /**
     * Generate the start of the C declarations.
     * @param out The file to write to
     */
    public void dumpStartDefs(PrintWriter out) {
        out.println("#ifndef _DEFS_H_");
        out.println("#define _DEFS_H_");
        out.println();
        out.println("#include <stdio.h>");
        out.println("#include <stdint.h>");
        out.println("#include <string.h>");
        out.println("#include <math.h>");
        out.println("#include <pthread.h>");
        out.println();
        out.println("typedef pthread_mutex_t lock_t;");
        out.println("typedef pthread_cond_t wait_t;");
        out.println();

        dumpIfaceMethTabDef(out);
    }

   /**
     * Generate the C declaration of the interface method table.
     * @param out The file to write to
     */
    public void dumpIfaceMethTabDef(PrintWriter out) {
        if (!interfaceList.isEmpty()) {
            out.println("typedef struct {");
            for (AbstractClassInfo i : interfaceList) {
                out.println("\t/* interface "+i.getName()+" */");
                for (Method m : i.getMethods()) {
                    out.print("\t"+Lang.getType(m.getReturnType())+
                              " (* const "+Lang.getName(i)+"_"+Lang.getMethod(m)+")");
                    i.dumpArgList(out, m);
                    out.println(";");
                }
            }
            out.println("} imtab_t;");
            out.println();
        }
    }

    /**
     * Generate the end of the C declarations.
     * @param out The file to write to
     */
    public void dumpEndDefs(PrintWriter out) {
        out.println("#endif /* _DEFS_H_ */");
    }

    /**
     * Generate the C code for the string pool.
     * @param out The file to write to
     * @param stringPool The string pool
     */
    public void dumpStringPool(PrintWriter out, Map<String, Integer> stringPool) {
        AbstractClassInfo arrci = getClassInfo("char[]");
        for (Map.Entry<String, Integer> e : stringPool.entrySet()) {
            String str = e.getKey();
            int index = e.getValue();

            out.println("const struct { ");
            out.println("\t"+Lang.getClassType(arrci)+" *type;");
            out.println("\tlock_t *lock;");
            out.println("\twait_t *wait;");
            out.println("\t"+Lang.getType(Type.INT)+" _0_length;");
            if (str.length() > 0) {
                out.println("\t"+Lang.getType(Type.CHAR)+" _1_data["+str.length()+"];");
            }
            out.println("} string_"+index+"_val = {");
            out.println("\t&"+Lang.getName(arrci)+", /* type */");
            out.println("\t0, /* lock */");
            out.println("\t0, /* wait */");
            out.println("\t"+str.length()+", /* length */");
            if (str.length() > 0) {
                String safeStr = str.replace("*/", "*\\/").replace("/*", "/\\*")
                    .replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
                out.println("\t{ /* data: \""+safeStr+"\" */");
                out.print("\t\t");
                for (int i = 0; i < str.length(); i++) {
                    out.print((int)str.charAt(i)+(i < str.length()-1 ? ", " : ""));
                }
                out.println();
                out.println("\t}");
            }
            out.println("};");
        }

        AbstractClassInfo strci = getClassInfo("java.lang.String");
        out.println(Lang.getObjType(strci)+" stringPool["+stringPool.size()+"] = {");
        for (int i = 0; i < stringPool.size(); i++) {
            out.println("{\t&"+Lang.getName(strci)+", /* type */");
            out.println("\t0, /* lock */");
            out.println("\t0, /* wait */");
            out.println("\t(int32_t)&string_"+i+"_val /* value */");
            out.println("},");
        }
        out.println("};");
    }

    /**
     * Generate the code to check for an exception and call jvm_catch().
     * @param out The file to write to
     */
    private void dumpCatch(PrintWriter out) {
        out.println("\tif (exc != 0) { jvm_catch(exc); }");
    }

    /**
     * Generate the code for the C main method.
     * @param out The file to write to
     */
    public void dumpMain(PrintWriter out) {
        AbstractClassInfo entry = getClassInfo(entryClass);
        out.println("int main(int argc, char **argv) {");

        out.println("\tint32_t exc = 0;");
        out.println("\tjvm_clinit(&exc);");
        dumpCatch(out);

        ClassInitOrder cio = new ClassInitOrder(classInfoMap);
        for (AbstractClassInfo ci : cio.findOrder()) {
            String className = Lang.getName(ci);
            String methName = Lang.getMethod(ci.findClinit());
            out.println("\t"+className+"_"+methName+"(&exc);");
            dumpCatch(out);
        }

        out.println("\tjvm_init(&exc);");
        dumpCatch(out);

        out.println("\tint32_t args = jvm_args(argc, argv, &exc);");
        dumpCatch(out);

        out.println("\t"+Lang.getName(entry)+"_main__Ljava_lang_String__V(args, &exc);");
        dumpCatch(out);

        out.println("\tpthread_exit(NULL);");
        out.println("}");
    }
}