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

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;

import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * {@link ClassInfo} holds information about a class and the
 * application and implements functions to generate C code.
 */
public class ClassInfo {

    /** The name of the class. */
    private final String name;
    /** The BCEL representation of  the class. */
    private final JavaClass clazz;
    /** The constant pool of the class. */
    private final ConstantPoolGen constPool;

    /** A helper structure to memoize the instance methods and the
     * classes that implement them. */
    private List<Map.Entry<Method, ClassInfo>> instanceMethods;
    /** A helper structure to compute which methods are truly virtual. */
    private static Set<String> virtualMethods;

    /** The name of the class that contains the main method. */
    private static String entryClass;
    
    /** A map between class names and classes. */
    private static Map<String, ClassInfo> classInfoMap = new LinkedHashMap<String, ClassInfo>();

    /** The list of interfaces in the application. */
    private static List<ClassInfo> interfaceList = new LinkedList<ClassInfo>();

    /**
     * Constructor, only to be used internally.
     */
    private ClassInfo(String name, JavaClass clazz) {
        this.name = name;
        this.clazz = clazz;
        this.constPool = new ConstantPoolGen(clazz.getConstantPool());
    }

    /**
     * Find class info for a name.
     * @param name The name of the class to find
     * @return The class info for the name
     */
    public static ClassInfo getClassInfo(String name) {
        return classInfoMap.get(name);
    }

    /** 
     * Load the transitive hull of the application, including classes
     * that are used by the JVM such as {@link java.lang.NullPointerException}.
     * @param entry The name of the entry class
     * @throws ClassNotFoundException if some referenced class cannot be found
     */
    public static void loadHull(String entry) throws ClassNotFoundException {
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
            ClassInfo ci = new ClassInfo(e.getKey(), e.getValue());
            classInfoMap.put(e.getKey(), ci);
            if (e.getValue().isInterface()) {
                interfaceList.add(ci);
            }
        }
        entryClass = entry.replace('/', '.');
    }

    /**
     * Get the name of this class.
     * @return The name of the class
     */
    public String getName() {
        return name;
    }
    /**
     * Get the constant pool of this class.
     * @return The constant pool of the class
     */
    public ConstantPoolGen getConstPool() {
        return constPool;
    }

    /**
     * Escape all special characters so it can be used as identifier in C.
     * @param name The original name
     * @return The escaped name
     */
    private static String escapeName(String name) {
        return name.replaceAll("[./<>();\\[\\]\\$]", "_");
    }

    /**
     * Get the name of the class to be used in the C code.
     * @return The name of the class to be used in the C code
     */
    public String getCName() {
        return escapeName("_"+getName());
    }

    /**
     * Get the name of the C type that represents this class.
     * @return The name of the C type that represents the class
     */
    public String getCClassTypeName() {
        return getCName() + "_class_t";
    }

    /**
     * Get the name of the C type that represents instances of this class.
     * @return The name of the C type that represents instances of the class
     */
    public String getCObjTypeName() {
        return getCName() + "_obj_t";
    }

    /**
     * Get the name (and signature) of a method in a form that can be used in C.
     * @param m The method
     * @return The escaped name (and signature)
     */
    private static String getCMethName(Method m) {
        return escapeName(m.getName()+m.getSignature());
    }

    /**
     * Get the name of a field in a form that can be used in C.
     * @param name The field name
     * @return The escaped name
     */
    private static String getCFieldName(String name) {
        return name.replaceAll("[\\$]", "_");
    }

    /**
     * Get the name of the C type that represents a Java type.
     * @param type The Java type
     * @return The C type
     */
    public static String getCType(Type type) {
        String cType = null;
        switch (type.getType()) {
        case Constants.T_VOID:
            cType = "void";
            break;
        case Constants.T_BOOLEAN:
        case Constants.T_BYTE:
            cType = "int8_t";
            break;
        case Constants.T_CHAR:
            cType = "uint16_t";
            break;
        case Constants.T_SHORT:
            cType = "int16_t";
            break;
        case Constants.T_LONG:
        case Constants.T_DOUBLE:
            cType = "int64_t";
            break;
        case Constants.T_INT:
        case Constants.T_FLOAT:
        case Constants.T_OBJECT:
        default:
            cType = "int32_t";
        }
        return cType;
    }

    /**
     * Get the super class of this class.
     * @return The super class of the class
     */
    public ClassInfo getSuperClass() {
        ClassInfo superClass = getClassInfo(clazz.getSuperclassName());
        if (superClass == this) {
            return null;
        } else {
            return superClass;
        }
    }

    /**
     * Get the interfaces implemented by this class.
     * @return The set of interfaces implemented by this class
     */
    public Set<ClassInfo> getInterfaces() {
        Set<ClassInfo> interfaces = new LinkedHashSet<ClassInfo>();
        Deque<ClassInfo> queue = new LinkedList<ClassInfo>();
        queue.add(this);
        while (!queue.isEmpty()) {
            ClassInfo ci = queue.remove();
            if (ci.clazz.isInterface()) {
                interfaces.add(ci);
            } else {
                ClassInfo superClass = ci.getSuperClass();
                if (superClass != null) {
                    queue.add(superClass);
                }
            }
            for (String i : ci.clazz.getInterfaceNames()) {
                queue.add(getClassInfo(i));
            }
        }
        return interfaces;
    }

    /**
     * Get the fields declared by this class.
     * @return The list of fields declared by this class
     */
    public List<Field> getFields() {
        return Arrays.asList(clazz.getFields());
    }

    /**
     * Get the instance fields of this class, including fields declared by super classes.
     * @return The list of instance fields of the class
     */
    public List<Field> getInstanceFields() {
        List<Field> fields = new LinkedList<Field>();

        ClassInfo superClass = getSuperClass();
        if (superClass != null) {
            fields.addAll(superClass.getInstanceFields());
        }

        fields.addAll(Filter.instances(getFields()));

        return fields;
    }

    /**
     * Check whether this class declares a field.
     * @param name The name of the field
     * @return true if the class declares the field, false otherwise
     */
    private boolean declaresField(String name) {
        List<Field> fields = getFields();
        for (Field f : fields) {
            if (f.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the class that declares a field.
     * @param base The class to start the search
     * @param name The field to look up
     * @return The class that declares the field
     */
    public ClassInfo findFieldDeclarator(ClassInfo base, String name) {
        ClassInfo b = base;
        while (b != null && !b.declaresField(name)) {
            for (ClassInfo c : b.getInterfaces()) {
                if (c.declaresField(name)) {
                    return c;
                }
            }
            b = b.getSuperClass();
        }
        return b;
    }

    /**
     * Get the index of a field.
     * @param name The name of the field
     * @return The offset of the field, relative to the first field
     */
    public int getFieldIndex(String name) {
        List<Field> fields = getInstanceFields();
        int i;
        for (i = fields.size()-1; i >= 0; i--) {            
            if (fields.get(i).getName().equals(name)) {
                break;
            }
        }
        if (i < 0) {
            return -1;
        }
        int idx = 0;
        for (int k = 0; k < i; k++) {
            idx += fields.get(k).getType().getSize();
        }
        return idx;
    }

    /**
     * Get the methods of this class.
     * @return The list of methods
     */
    public List<Method> getMethods() {
        return Arrays.asList(clazz.getMethods());
    }

    /**
     * Check whether the class declares a method.
     * @param name The name of the method
     * @param signature The signature of the method
     * @return true if the class declares the method, false otherwise
     */
    private boolean declaresMethod(String name, String signature) {
        for (Method m : getMethods()) {
            if (m.getName().equals(name)
                && m.getSignature().equals(signature)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the class that declares a method.
     * @param name The name of the method
     * @param signature The signature of the method
     * @return The class that declares the method
     */
    public ClassInfo findMethodDeclarator(ClassInfo base, String name, String signature) {
        ClassInfo b = base;
        while (b != null && !b.declaresMethod(name, signature)) {
            for (ClassInfo c : b.getInterfaces()) {
                if (c.declaresMethod(name, signature)) {
                    return c;
                }
            }
            b = b.getSuperClass();
        }
        return b;
    }

    /**
     * Find the class initializer method.
     * @return The method for the class initializer
     */
    public Method findClinit() {
        for (Method m : clazz.getMethods()) {
            if ("<clinit>".equals(m.getName())
                && "()V".equals(m.getSignature())) {
                return m;
            }
        }
        return null;
    }

    private void addMethodToList(List<Map.Entry<Method, ClassInfo>> list, Method m, ClassInfo ci, boolean replace) {
        boolean override = false;
        Map.Entry<Method, ClassInfo> entry = new AbstractMap.SimpleImmutableEntry<Method, ClassInfo>(m, ci);

        for (int i = 0; i < list.size() && !override; i++) {
            Map.Entry<Method, ClassInfo> oldEntry = list.get(i);
            Method o = oldEntry.getKey();
            if (methodsEqual(m, o)) {
                if (replace) {
                    list.set(i, entry);
                }
                override = true;

                if (virtualMethods != null) {
                    String methName = m.getName()+m.getSignature();                
                    virtualMethods.add(oldEntry.getValue().getName()+"."+methName);
                    virtualMethods.add(ci.getName()+"."+methName);
                }
            }
        }
        if (!override) {
            list.add(entry);
        }
    }

    /**
     * Compute the instance methods of a class and store them in {@link instanceMethods}.
     */
    private void computeInstanceMethods() {
        instanceMethods = new LinkedList<Map.Entry<Method, ClassInfo>>();
        ClassInfo superClass = getSuperClass();
        if (superClass != null) {
            List<Map.Entry<Method, ClassInfo>> supers = superClass.getInstanceMethods();
            for (Map.Entry<Method, ClassInfo> e : supers) {
                addMethodToList(instanceMethods, e.getKey(), e.getValue(), true);
            }
        }
        for (Method m : Filter.instances(getMethods())) {
            if (!m.isPrivate()) {
                addMethodToList(instanceMethods, m, this, true);
            }
        }

        for (ClassInfo i : getInterfaces()) {
            for (Method m : i.getMethods()) {
                addMethodToList(instanceMethods, m, i, false);
            }
        }
    }

    /**
     * Get the instance methods of this class.
     * @return The list of methods and the classes that implement them
     */
    public List<Map.Entry<Method, ClassInfo>> getInstanceMethods() {
        if (instanceMethods == null) {
            computeInstanceMethods();
        }
        return instanceMethods;
    }

    /**
     * Get the truly virtual methods of this class.
     * @return The set of truly virtual methods of this class
     */
    public Set<String> getVirtualMethods() {
        if (virtualMethods == null) {
            virtualMethods = new LinkedHashSet<String>();
            for (ClassInfo ci : classInfoMap.values()) {
                ci.computeInstanceMethods();
            }
        }
        return virtualMethods;
    }

    /**
     * Compare whether two methods are equal according to their name and signature.
     * @return true if the methods are equals, false otherwise
     */
    public static boolean methodsEqual(Method a, Method b) {
        return a.getName().equals(b.getName()) &&
            a.getSignature().equals(b.getSignature());
    }

    /**
     * Compute whether the class requires an interface table.
     * @return true if the current class requires an interface table, false otherwise
     */
    public boolean needsInterfaceTable() throws ClassNotFoundException {
        if (clazz.isInterface() || clazz.isAbstract() || getInterfaces().isEmpty()) {
            return false;
        }
        for (ClassInfo i : interfaceList) {
            if (clazz.implementationOf(i.clazz)) {
                for (Method m : i.getMethods()) {
                    for (Map.Entry<Method, ClassInfo> e : getInstanceMethods()) {
                        if (methodsEqual(m, e.getKey())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Get the number of arguments for a C function.
     * @param types The types of the arguments
     * @param isStatic Whether the corresponding methid is static
     * @return The number of arguments for a C function
     */
    private static int getArgCount(Type [] types, boolean isStatic) {
        int count = 0;
        for (Type t : types) {
            count += t.getSize();
        }
        if (!isStatic) {
            count++;
        }
        return count;
    }

    /**
     * Get the C operator for an arithmetic bytecode.
     * @param i The bytecode
     * @return The C operator
     */
    private static String getArithOp(Instruction i) {
        String op = null;
        switch (i.getOpcode()) {
        case Constants.IADD: case Constants.LADD: 
        case Constants.FADD: case Constants.DADD:
            op = "+";
            break;
        case Constants.ISUB: case Constants.LSUB:
        case Constants.FSUB: case Constants.DSUB:
            op = "-";
            break;
        case Constants.IMUL: case Constants.LMUL:
        case Constants.FMUL: case Constants.DMUL:
            op = "*";
            break;
        case Constants.IDIV: case Constants.LDIV:
        case Constants.FDIV: case Constants.DDIV:
            op = "/";
            break;
        case Constants.IREM: case Constants.LREM:
            op = "%";
            break;
        case Constants.IOR:  case Constants.LOR:
            op = "|";
            break;
        case Constants.IAND: case Constants.LAND:
            op = "&";
            break;
        case Constants.IXOR: case Constants.LXOR:
            op = "^";
            break;
        case Constants.ISHL: case Constants.LSHL:
            op = "<<";
            break;
        case Constants.ISHR: case Constants.LSHR:
        case Constants.IUSHR: case Constants.LUSHR:
            op = ">>";
            break;
        default:
            throw new IllegalArgumentException("Invalid arithmetic instruction: "+i);
        }
        return op;
    }

    /**
     * Get the C comparison operator for a comparison bytecode.
     * @param i The bytecode
     * @return The C operator
     */
    private static String getCondition(Instruction i) {
        String cond = null;
        switch (i.getOpcode()) {
        case Constants.IFNONNULL: case Constants.IFNE:
        case Constants.IF_ACMPNE: case Constants.IF_ICMPNE:
            cond = "!=";
            break;
        case Constants.IFNULL: case Constants.IFEQ:
        case Constants.IF_ACMPEQ: case Constants.IF_ICMPEQ:
            cond = "==";
            break;
        case Constants.IFGE: case Constants.IF_ICMPGE:
            cond = ">=";
            break;
        case Constants.IFLT: case Constants.IF_ICMPLT:
            cond = "<";
            break;
        case Constants.IFGT: case Constants.IF_ICMPGT:
            cond = ">";
            break;
        case Constants.IFLE: case Constants.IF_ICMPLE:
            cond = "<=";
            break;
        default:
            throw new IllegalArgumentException("Invalid comparison instruction: "+i);
        }
        return cond;
    }

    /**
     * Get the C comparison for a comparison bytecode.
     * @param i The bytecode
     * @param depth The current stack depth
     * @return The C comparison
     */
    private static String getComparison(Instruction i, int depth) {
        String comp = null;
        switch (i.getOpcode()) {
        case Constants.IFNONNULL: case Constants.IFNE:
        case Constants.IFNULL: case Constants.IFEQ:
        case Constants.IFGE: case Constants.IFLT:
        case Constants.IFGT: case Constants.IFLE:
            comp = s(depth)+" "+getCondition(i)+" 0";
            break;
        case Constants.IF_ACMPNE: case Constants.IF_ICMPNE:
        case Constants.IF_ACMPEQ: case Constants.IF_ICMPEQ:        
        case Constants.IF_ICMPGE: case Constants.IF_ICMPLT:
        case Constants.IF_ICMPGT: case Constants.IF_ICMPLE:
            comp = s(depth-1)+" "+getCondition(i)+" "+s(depth);
            break;
        default:
            throw new IllegalArgumentException("Invalid comparison instruction: "+i);
        }
        return comp;
    }

    /**
     * Get the C type to be used for an array bytecode.
     * @param The bytecode
     * @return The C type
     */
    private static String getArrayType(Instruction i) {
        String type = null;
        switch(i.getOpcode()) {
        case Constants.AALOAD: case Constants.AASTORE:
            // this should be "_java_lang_Object___obj_t"
            type = "_int___obj_t";
            break;
        case Constants.IALOAD: case Constants.IASTORE:
            type = "_int___obj_t";
            break;
        case Constants.FALOAD: case Constants.FASTORE:
            type = "_float___obj_t";
            break;
        case Constants.CALOAD: case Constants.CASTORE:
            type = "_char___obj_t";
            break;
        case Constants.SALOAD: case Constants.SASTORE:
            type = "_short___obj_t";
            break;
        case Constants.BALOAD: case Constants.BASTORE:
            type = "_byte___obj_t";
            break;
        case Constants.LALOAD: case Constants.LASTORE:
            type = "_long___obj_t";
            break;
        case Constants.DALOAD: case Constants.DASTORE:
            type = "_double___obj_t";
            break;
        default:
            throw new IllegalArgumentException("Invalid array instruction: "+i);
        }
        return type;
    }

    /**
     * Create a writable file.
     * @param dir The directory in which to create the file
     * @param name The file name
     * @return The created file
     */
    private static PrintWriter getFile(String dir, String name) throws IOException {
        FileWriter f = new FileWriter(dir+File.separator+name);
        BufferedWriter b = new BufferedWriter(f);
        return new PrintWriter(b);
    }

    /**
     * Generate C code for the entire application.
     * @param outDir The directory in which to create generated files
     */
    public static void dumpAll(String outDir) throws IOException {
        Map<String, Integer> stringPool = new LinkedHashMap<String, Integer>();
     
        PrintWriter defsOut = getFile(outDir, "defs.h");
        dumpStartDefs(defsOut);
        Set<ClassInfo> dumpedDef = new LinkedHashSet<ClassInfo>();
        for (ClassInfo c : classInfoMap.values()) {
            c.dumpDefs(defsOut, dumpedDef);
        }
        dumpEndDefs(defsOut);
        defsOut.flush();

        try {
            for (ClassInfo c : classInfoMap.values()) {
                PrintWriter out = getFile(outDir+File.separator+"classes", c.getCName()+".c");
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
     * Generate the list of arguments for a method.
     * @param out The file to write to
     * @param m The method the code is generated for
     */
    public static void dumpArgList(PrintWriter out, Method m) {
        int argCount = getArgCount(m.getArgumentTypes(), m.isStatic());
        out.print("(");
        for (int i = 0; i < argCount; i++) {
            out.print("int32_t "+v(i)+", ");
        }
        out.print("int32_t *restrict retexc)");
    }

    /**
     * Generate the start of the C declarations.
     * @param out The file to write to
     */
    public static void dumpStartDefs(PrintWriter out) {
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
    public static void dumpIfaceMethTabDef(PrintWriter out) {
        if (!interfaceList.isEmpty()) {
            out.println("typedef struct {");
            for (ClassInfo i : interfaceList) {
                out.println("\t/* interface "+i.getName()+" */");
                for (Method m : i.getMethods()) {
                    out.print("\t"+getCType(m.getReturnType())+
                              " (* const "+i.getCName()+"_"+getCMethName(m)+")");
                    dumpArgList(out, m);
                    out.println(";");
                }
            }
            out.println("} imtab_t;");
            out.println();
        }
    }

    /**
     * Generate the C declarations for this class.
     * @param out The file to write to
     */
    public void dumpDefs(PrintWriter out, Set<ClassInfo> dumped) {
        ClassInfo superClass = getSuperClass();
        if (superClass != null && !dumped.contains(superClass)) {
            superClass.dumpDefs(out, dumped);
        }
        if (dumped.contains(this)) {
            return;
        }
        dumped.add(this);

        dumpClassDef(out);
        dumpStaticFieldDefs(out);
        dumpMethodDefs(out);
        dumpObjectDef(out);
    }

    /**
     * Generate the C declaration for the class structure.
     * @param out The file to write to
     */
    public void dumpClassDef(PrintWriter out) {
        out.println("typedef struct {");
        out.println("\t/* header */");
        out.println("\tvoid * const type;");
        out.println("\tlock_t * lock;");
        out.println("\twait_t * wait;");
        out.println("\t/* class type fields */");
        out.println("\tvoid * const super;");
        out.println("\tvoid * const elemtype;");
        out.println("\tvoid * const name;");
        if (!interfaceList.isEmpty()) {
            out.println("\tconst int32_t itab ["+((interfaceList.size()+31)/32)+"];");
            out.println("\tconst imtab_t * const imtab;");
        }

        dumpMethodPointerDefs(out);
 
        out.println("} "+getCClassTypeName()+";");
        out.println();

        out.println("/* forward declaration of class object */");
        out.println("extern "+getCClassTypeName()+" "+getCName()+";");
        out.println();
    }

    /**
     * Generate the C declarations for the method pointers.
     * @param out The file to write to
     */
    public void dumpMethodPointerDefs(PrintWriter out) {
        out.println("\t/* method pointers */");
        for (Map.Entry<Method, ClassInfo> e : getInstanceMethods()) {
            Method m = e.getKey();
            String fqName = e.getValue().getName()+"."+m.getName()+m.getSignature();
            if (getVirtualMethods().contains(fqName)
                && !("<init>".equals(m.getName())
                     && "()V".equals(m.getSignature()))) {
                out.print("\t"+getCType(m.getReturnType())+
                          " (* const "+getCMethName(m)+")");
                dumpArgList(out, m);
                out.println(";");
            }
        }
    }

    /**
     * Generate the C declarations for the static fields.
     * @param out The file to write to
     */
    public void dumpStaticFieldDefs(PrintWriter out) {
        out.println("/* static fields */");
        for (Field f : Filter.statics(getFields())) {
            if (!f.isPrivate()
                && !(f.isFinal()
                     && f.getType() instanceof BasicType
                     && f.getConstantValue() != null)) {
                if (f.isVolatile()) {
                    out.print("volatile ");
                }
                out.println("extern "+getCType(f.getType())+
                            " "+getCName()+"_"+getCFieldName(f.getName())+";");
            }
        }
        out.println();
    }

    /**
     * Generate the C declarations for the methods (prototypes).
     * @param out The file to write to
     */
    public void dumpMethodDefs(PrintWriter out) {
        out.println("/* method prototypes */");
        for (Method m : getMethods()) {
            if (m.isPrivate() && !m.isNative()) {
                out.print("static ");
            }
            out.print(getCType(m.getReturnType())+
                      " "+getCName()+"_"+getCMethName(m));
            dumpArgList(out, m);
            out.println(";");
        }
        out.println();
    }

    /**
     * Generate the C declaration for the object structure.
     * @param out The file to write to
     */
    public void dumpObjectDef(PrintWriter out) {
        out.println("typedef struct {");
        out.println("\t/* header */");
        out.println("\tconst "+getCClassTypeName()+" *type;");
        out.println("\tlock_t *lock;");
        out.println("\twait_t *wait;");
        out.println("\t/* instance fields */");
        int fieldIdx = 0;
        for (Field f : getInstanceFields()) {
            if (f.isVolatile()) {
                out.print("\tvolatile ");
            } else {
                out.print("\t");
            }
            out.println(getCType(f.getType())+" _"+fieldIdx+"_"+getCFieldName(f.getName())+";");
            fieldIdx += f.getType().getSize();
        }
        out.println("} "+getCObjTypeName()+";");
        out.println();
    }

    /**
     * Generate the end of the C declarations.
     * @param out The file to write to
     */
    public static void dumpEndDefs(PrintWriter out) {
        out.println("#endif /* _DEFS_H_ */");
    }

    /**
     * Generate the C implementations for this class.
     * @param out The file to write to
     */
    public void dumpBodies(PrintWriter out, Map<String, Integer> stringPool) throws ClassNotFoundException {
        dumpStaticFields(out, stringPool);
        dumpMethodBodies(out, stringPool);

        if (needsInterfaceTable()) {
            dumpIfaceMethTab(out);
        }

        dumpClassBody(out, stringPool);
    }

    /**
     * Generate the C implementations of the methods.
     * @param out The file to write to
     */
    public void dumpMethodBodies(PrintWriter out, Map<String, Integer> stringPool) {
        for (Method m : getMethods()) {
            Code code = m.getCode();
            if (code != null || m.isNative()) {
                if (m.isPrivate() && !m.isNative()) {
                    out.print("static ");
                }
                out.print(getCType(m.getReturnType())+
                          " "+getCName()+"_"+getCMethName(m));
                dumpArgList(out, m);
                if (code != null) {
                    out.println(" {");
                    dumpCode(out, stringPool, m, code);
                    out.println("}");
                } else {
                    out.println(";");
                }
            }
        }
        out.println();
    }

    /**
     * Generate the C definition of the interface method table for this class.
     * @param out The file to write to
     */
    public void dumpIfaceMethTab(PrintWriter out) throws ClassNotFoundException {
        out.println("imtab_t "+getCName()+"_imtab = {");
        for (ClassInfo i : interfaceList) {
            out.println("\t/* interface "+i.getName()+" */");
            for (Method m : i.getMethods()) {
                boolean found = false;
                if (clazz.implementationOf(i.clazz)) {
                    for (Map.Entry<Method, ClassInfo> e : getInstanceMethods()) {
                        if (methodsEqual(m, e.getKey())) {
                            out.println("\t"+e.getValue().getCName()+"_"+getCMethName(m)+", ");
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    out.println("\t0, /*"+m.getName()+m.getSignature()+" */");
                }
            }
        }
        out.println("};");
    }

    /**
     * Generate the C definition of the class structure.
     * @param out The file to write to
     */
    public void dumpClassBody(PrintWriter out, Map<String, Integer> stringPool) throws ClassNotFoundException {
        String classClassPtr = "&"+getClassInfo("java.lang.Class").getCName();
        ClassInfo superClass = getSuperClass();
        String superClassPtr = superClass != null ? "&"+superClass.getCName() : "0";
        String elemType = "0";
        if (getName().endsWith("[]")) {
            String typeName = getName().substring(0, getName().length()-2);
            ClassInfo ci = getClassInfo(typeName);
            if (ci == null) {
                Logger.getGlobal().severe("Class not found: "+typeName+" in "+getName());
            } else {
                elemType = "&"+ci.getCName();
            }
        }
        if (!stringPool.containsKey(getName())) {
            stringPool.put(getName(), stringPool.size());
        }
        String namePtr = "&stringPool["+stringPool.get(getName())+"]";

        out.println(getCClassTypeName()+" "+getCName()+" = {");
        out.println("\t/* header */");
        out.println("\t"+classClassPtr+", /* type */");
        out.println("\t0, /* lock */");
        out.println("\t0, /* wait */");
        out.println("\t"+superClassPtr+", /* super */");
        out.println("\t"+elemType+", /* elemtype */");
        out.println("\t"+namePtr+", /* name */");
        if (!interfaceList.isEmpty()) {
            out.println("\t/* interface table */");
            out.print("\t{ ");
            int buffer = 0;
            int bufferCount = 0;
            for (ClassInfo i : interfaceList) {
                buffer |= (clazz.instanceOf(i.clazz) ? 1 : 0) << bufferCount;
                bufferCount++;
                if (bufferCount == 32) {
                    out.print(String.format("0x%08x", buffer)+", ");
                    buffer = 0;
                    bufferCount = 0;
                }
            }
            if (bufferCount != 0) {
                out.print(String.format("0x%08x", buffer)+", ");
            }
            out.println("},");

            out.println("\t/* interface method table */");
            if (needsInterfaceTable()) {
                out.println("\t&"+getCName()+"_imtab,");
            } else {
                out.println("\t0,");
            }
        }

        dumpMethodPointers(out);

        out.println("};");
        out.println();
    }

    /**
     * Generate the C definitions of the method pointers.
     * @param out The file to write to
     */
    public void dumpMethodPointers(PrintWriter out) {
        out.println("\t/* method pointers */");
        for (Map.Entry<Method, ClassInfo> e : getInstanceMethods()) {
            Method m = e.getKey();
            String fqName = e.getValue().getName()+"."+m.getName()+m.getSignature();
            if (getVirtualMethods().contains(fqName)
                && !("<init>".equals(m.getName())
                     && "()V".equals(m.getSignature()))) {
                String className = e.getValue().getCName();
                String methName = getCMethName(m);
                if (m.getCode() != null || m.isNative()) {
                    out.println("\t"+className+"_"+methName+",");
                } else {
                    out.println("\t0, /* "+className+"_"+methName+" */");
                }
            }
        }
    }

    /**
     * Generate the C definitions of the static fields.
     * @param out The file to write to
     */
    public void dumpStaticFields(PrintWriter out, Map<String, Integer> stringPool) {
        out.println("/* static fields */");
        for (Field f : Filter.statics(getFields())) {
            if (f.isFinal()
                && f.getType() instanceof BasicType
                && f.getConstantValue() != null) {
                continue;
            }

            if (f.isVolatile()) {
                out.print("volatile ");
            }

            if (f.isPrivate()) {
                out.print("static ");
            }

            out.print(getCType(f.getType())+
                      " "+getCName()+"_"+getCFieldName(f.getName())+" = ");
            ConstantValue cv = f.getConstantValue();
            out.print("("+getCType(f.getType())+")");
            if (cv != null) {
                Constant c = cv.getConstantPool().getConstant(cv.getConstantValueIndex());
                switch (c.getTag()) {
                case Constants.CONSTANT_Integer:
                    out.println(((ConstantInteger) c).getBytes()+"UL;");
                    break;
                case Constants.CONSTANT_Long:
                    out.println(((ConstantLong) c).getBytes()+"ULL;");
                    break;
                case Constants.CONSTANT_Float:
                    out.println(Float.floatToIntBits(((ConstantFloat) c).getBytes())+"UL;");
                    break;
                case Constants.CONSTANT_Double:
                    out.println(Double.doubleToLongBits(((ConstantDouble) c).getBytes())+"ULL;");
                    break;
                case Constants.CONSTANT_String:
                    int i = ((ConstantString)c).getStringIndex();
                    c = cv.getConstantPool().getConstant(i, Constants.CONSTANT_Utf8);
                    String strVal = ((ConstantUtf8)c).getBytes();
                    if (!stringPool.containsKey(strVal)) {
                        stringPool.put(strVal, stringPool.size());
                    }
                    out.println("(int32_t)&stringPool["+stringPool.get(strVal)+"];");
                    break;
                default:
                    throw new IllegalArgumentException("Invalid tag for ConstantValue: "+cv.getTag());
                }
            } else {
                out.println("0;");
            }
        }
        out.println();
    }

    /**
     * Create the name for a variable that represents a stack slot.
     * @param depth The depth of the stack slot
     */
    private static String s(int depth) {
        return "s_"+depth;
    }

    /**
     * Create the name for a variable that represents a local variable.
     * @param depth The index of the local variable
     */
    private static String v(int index) {
        return "v_"+index;
    }

    /**
     * Generate the C code for a method.
     * @param out The file to write to
     * @param stringPool The string pool
     * @param method The method the code is generated for
     * @param code The code of the method
     */
    public void dumpCode(PrintWriter out, Map<String, Integer> stringPool, Method method, Code code) {
        InstructionList il = new InstructionList(code.getCode());

        int inputVarCount = getArgCount(method.getArgumentTypes(), method.isStatic());
        for (int i = inputVarCount; i < code.getMaxLocals(); i++) {
            out.println("\tint32_t "+v(i)+";");
        }
        for (int i = 0; i < code.getMaxStack() || i < 1; i++) {
            out.println("\tint32_t "+s(i)+";");
        }
        out.println("\tint32_t exc = 0;");

        Set<Integer> excHandlers = new LinkedHashSet<Integer>();
        for (CodeException exc : code.getExceptionTable()) {
            excHandlers.add(exc.getHandlerPC());
        }

        StackDepths depthMap = new StackDepths(il, constPool);
        StackReferences refMap = new StackReferences(il, constPool);

        dumpSyncEnter(out, method);

        for (InstructionHandle ih : il.getInstructionHandles()) {
            Instruction i = ih.getInstruction();
            int pos = ih.getPosition();
            int depth = depthMap.get(pos);
            Deque<Boolean> refs = refMap.get(pos);

            if (ih.hasTargeters() || excHandlers.contains(pos)) {
                out.print("L"+pos+":");
            }

            dumpInstruction(out, stringPool, method, code, pos, i, depth, refs);
        }
    }

    /**
     * Generate the C code for a bytecode.
     * @param out The file to write to
     * @param stringPool The string pool
     * @param method The method the code is generated for
     * @param code The code of the method
     * @param pos The current position in the code
     * @param i The bytecode
     * @param depth The current stack depth
     * @param refs The current stack state (references/primitive values)
     */
    public void dumpInstruction(PrintWriter out, Map<String, Integer> stringPool, Method method, Code code, int pos, Instruction i, int depth, Deque<Boolean> refs) {

        // variables shared by different branches of the switch
        ConstantPushInstruction cpi;
        LoadInstruction li;
        StoreInstruction si;

        switch (i.getOpcode()) {

        case Constants.ACONST_NULL:
            out.print("\t"+s(depth+1)+" = 0;");                
            break;

        case Constants.ICONST_M1: case Constants.ICONST_0: case Constants.ICONST_1:
        case Constants.ICONST_2: case Constants.ICONST_3: case Constants.ICONST_4:
        case Constants.ICONST_5: case Constants.BIPUSH: case Constants.SIPUSH:
            cpi = (ConstantPushInstruction)i;
            out.print("\t"+s(depth+1)+" = "+cpi.getValue().intValue()+";");
            break;
        case Constants.FCONST_0: case Constants.FCONST_1: case Constants.FCONST_2:
            cpi = (ConstantPushInstruction)i;
            out.print("\t"+s(depth+1)+" = "+Float.floatToIntBits(cpi.getValue().floatValue())+";");
            break;
        case Constants.LCONST_0: case Constants.LCONST_1:
            cpi = (ConstantPushInstruction)i;
            out.print("\t"+s(depth+1)+" = "+(int)cpi.getValue().longValue()+";"+
                      " "+s(depth+2)+" = "+(int)(cpi.getValue().longValue() >> 32)+";");
            break;
        case Constants.DCONST_0: case Constants.DCONST_1:
            cpi = (ConstantPushInstruction)i;
            out.print("\t"+s(depth+1)+" = "+(int)Double.doubleToLongBits(cpi.getValue().doubleValue())+";"+
                      " "+s(depth+2)+" = "+(int)(Double.doubleToLongBits(cpi.getValue().doubleValue()) >> 32)+";");
            break;

        case Constants.LDC: case Constants.LDC_W:
            dumpLdc(out, stringPool, ((LDC)i).getValue(constPool), depth);
            break;

        case Constants.LDC2_W:
            dumpLdc2(out, ((LDC2_W)i).getValue(constPool), depth);
            break;

        case Constants.ALOAD_0: case Constants.ALOAD_1: case Constants.ALOAD_2:
        case Constants.ALOAD_3: case Constants.ALOAD:
        case Constants.ILOAD_0: case Constants.ILOAD_1: case Constants.ILOAD_2:
        case Constants.ILOAD_3: case Constants.ILOAD:
        case Constants.FLOAD_0: case Constants.FLOAD_1: case Constants.FLOAD_2:
        case Constants.FLOAD_3: case Constants.FLOAD:
            li = (LoadInstruction)i;
            out.print("\t"+s(depth+1)+" = "+v(li.getIndex())+";");
            break;
        case Constants.LLOAD_0: case Constants.LLOAD_1: case Constants.LLOAD_2:
        case Constants.LLOAD_3: case Constants.LLOAD:
        case Constants.DLOAD_0: case Constants.DLOAD_1: case Constants.DLOAD_2:
        case Constants.DLOAD_3: case Constants.DLOAD:
            li = (LoadInstruction)i;
            out.print("\t"+s(depth+1)+" = "+v(li.getIndex())+";"+
                      " "+s(depth+2)+" = "+v(li.getIndex()+1)+";");
            break;

        case Constants.ASTORE_0: case Constants.ASTORE_1: case Constants.ASTORE_2:
        case Constants.ASTORE_3: case Constants.ASTORE:
        case Constants.ISTORE_0: case Constants.ISTORE_1: case Constants.ISTORE_2:
        case Constants.ISTORE_3: case Constants.ISTORE:
        case Constants.FSTORE_0: case Constants.FSTORE_1: case Constants.FSTORE_2:
        case Constants.FSTORE_3: case Constants.FSTORE:
            si = (StoreInstruction)i;
            out.print("\t"+v(si.getIndex())+" = "+s(depth)+";");
            break;
        case Constants.LSTORE_0: case Constants.LSTORE_1: case Constants.LSTORE_2:
        case Constants.LSTORE_3: case Constants.LSTORE:
        case Constants.DSTORE_0: case Constants.DSTORE_1: case Constants.DSTORE_2:
        case Constants.DSTORE_3: case Constants.DSTORE:
            si = (StoreInstruction)i;
            out.print("\t"+v(si.getIndex())+" = "+s(depth-1)+";"+
                      " "+v(si.getIndex()+1)+" = "+s(depth)+";");
            break;

        case Constants.DUP:
            out.print("\t"+s(depth+1)+" = "+s(depth)+";");
            break;
        case Constants.DUP_X1:
            out.print("\t"+s(depth+1)+" = "+s(depth+0)+";"+
                      " "+s(depth+0)+" = "+s(depth-1)+";"+
                      " "+s(depth-1)+" = "+s(depth+1)+";");
            break;
        case Constants.DUP_X2:
            out.print("\t"+s(depth+1)+" = "+s(depth+0)+";"+
                      " "+s(depth+0)+" = "+s(depth-1)+";"+
                      " "+s(depth-1)+" = "+s(depth-2)+";"+
                      " "+s(depth-2)+" = "+s(depth+1)+";");
            break;
        case Constants.DUP2:
            out.print("\t"+s(depth+1)+" = "+s(depth-1)+";"+
                      " "+s(depth+2)+" = "+s(depth+0)+";");
            break;
        case Constants.DUP2_X1:
            out.print("\t"+s(depth+1)+" = "+s(depth-1)+";"+
                      " "+s(depth+2)+" = "+s(depth+0)+";"+
                      " "+s(depth+0)+" = "+s(depth-2)+";"+
                      " "+s(depth-2)+" = "+s(depth+1)+";"+
                      " "+s(depth-1)+" = "+s(depth+2)+";");
            break;
        case Constants.DUP2_X2:
            out.print("\t"+s(depth+1)+" = "+s(depth-1)+";"+
                      " "+s(depth+2)+" = "+s(depth+0)+";"+
                      " "+s(depth+0)+" = "+s(depth-2)+";"+
                      " "+s(depth-1)+" = "+s(depth-3)+";"+
                      " "+s(depth-3)+" = "+s(depth+1)+";"+
                      " "+s(depth-2)+" = "+s(depth+2)+";");
            break;
        case Constants.POP:
            out.print("\t/* pop */;");
            break;
        case Constants.POP2:
            out.print("\t/* pop2 */;");
            break;
        case Constants.SWAP:
            out.print("\t{ int32_t a = "+s(depth)+";"+
                      " "+s(depth)+" = "+s(depth-1)+";"+
                      " "+s(depth-1)+" = a; }");
            break;

        case Constants.IINC:
            IINC ii = (IINC)i;
            out.print("\t"+v(ii.getIndex())+" += "+ii.getIncrement()+";");
            break;

        case Constants.IADD: case Constants.ISUB: case Constants.IMUL:
        case Constants.IOR: case Constants.IAND: case Constants.IXOR:
            out.print("\t"+s(depth-1)+" "+getArithOp(i)+"= "+s(depth)+";");
            break;
        case Constants.IDIV: case Constants.IREM:
            out.print("\tif (unlikely("+s(depth)+" == 0)) { "+s(0)+" = (int32_t)&aeExc;");
            dumpThrow(out, method, code, pos);
            out.print(" }"+
                      " "+s(depth-1)+" = (int64_t)"+s(depth-1)+" "+getArithOp(i)+" (int64_t)"+s(depth)+";");
            break;
        case Constants.ISHL: case Constants.ISHR:
            out.print("\t"+s(depth-1)+" "+getArithOp(i)+"= "+s(depth)+" & 0x1f;");
            break;
        case Constants.IUSHR:
            out.print("\t"+s(depth-1)+" = (uint32_t)"+s(depth-1)+" "+getArithOp(i)+" ("+s(depth)+" & 0x1f);");
            break;
        case Constants.INEG:
            out.print("\t"+s(depth)+" = -"+s(depth)+";");
            break;

        case Constants.I2C:
            out.print("\t"+s(depth)+" = (uint16_t)"+s(depth)+";");
            break;
        case Constants.I2S:
            out.print("\t"+s(depth)+" = (int16_t)"+s(depth)+";");
            break;
        case Constants.I2B:
            out.print("\t"+s(depth)+" = (int8_t)"+s(depth)+";");
            break;
        case Constants.I2L:
            out.print("\t"+s(depth+1)+" = "+s(depth)+" >> 31;");
            break;
        case Constants.I2F:
            out.print("\t{ float *a = (float *)&"+s(depth)+"; *a = "+s(depth)+"; }");
            break;
        case Constants.I2D:
            out.print("\t{ int64_t a; double *b = (double *)&a; *b = "+s(depth)+";"+
                      " "+s(depth)+" = (int32_t)a;"+
                      " "+s(depth+1)+" = (int32_t)(a >> 32); }");
            break;

        case Constants.L2I:
            out.print("\t/* l2i */;");
            break;
        case Constants.L2F:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " float *b = (float *)&"+s(depth-1)+"; *b = a; }");
            break;
        case Constants.L2D:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " double *b = (double *)&a; *b = a;"+
                      " "+s(depth-1)+" = (int32_t)a;"+
                      " "+s(depth)+" = (int32_t)(a >> 32); }");
            break;

        case Constants.F2I:
            out.print("\t{ float *a = (float *)&"+s(depth)+";"+
                      " if (*a != *a) { "+s(depth)+" = 0; }"+
                      " else if (*a >= (int32_t)0x7fffffff) { "+s(depth)+" = 0x7fffffff; }"+
                      " else if (*a <= (int32_t)0x80000000) { "+s(depth)+" = 0x80000000; }"+
                      " else { "+s(depth)+" = *a; } }");
            break;
        case Constants.F2L:
            out.print("\t{ float *a = (float *)&"+s(depth)+"; int64_t b = *a;"+
                      " if (*a != *a) { b = 0; }"+
                      " else if (*a >= (int64_t)0x7fffffffffffffffLL) { b = 0x7fffffffffffffffLL; }"+
                      " else if (*a <= (int64_t)0x8000000000000000LL) { b = 0x8000000000000000LL; }"+
                      " "+s(depth)+" = (int32_t)b;"+
                      " "+s(depth+1)+" = (int32_t)(b >> 32); }");
            break;
        case Constants.F2D:
            out.print("\t{ float *a = (float *)&"+s(depth)+";"+
                      " int64_t b; double *c = (double *)&b; *c = *a;"+
                      " "+s(depth)+" = (int32_t)b;"+
                      " "+s(depth+1)+" = (int32_t)(b >> 32); }");
            break;

        case Constants.D2I:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " double *b = (double *)&a;"+
                      " if (*b != *b) { "+s(depth-1)+" = 0; }"+
                      " else if (*b >= (int32_t)0x7fffffff) { "+s(depth-1)+" = 0x7fffffff; }"+
                      " else if (*b <= (int32_t)0x80000000) { "+s(depth-1)+" = 0x80000000; }"+
                      " else { "+s(depth-1)+" = *b; } }");
            break;
        case Constants.D2L:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " double *b = (double *)&a;"+
                      " if (*b != *b) { a = 0; }"+
                      " else if (*b >= (int64_t)0x7fffffffffffffffLL) { a = 0x7fffffffffffffffLL; }"+
                      " else if (*b <= (int64_t)0x8000000000000000LL) { a = 0x8000000000000000LL; }"+
                      " else { a = *b; }"+
                      " "+s(depth-1)+" = (int32_t)a;"+
                      " "+s(depth)+" = (int32_t)(a >> 32); }");
            break;
        case Constants.D2F:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " double *b = (double *)&a; float *c = (float *)&"+s(depth-1)+"; *c = *b; }");
            break;

        case Constants.LADD: case Constants.LSUB: case Constants.LMUL:
        case Constants.LOR: case Constants.LAND: case Constants.LXOR:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth-2)+" << 32) | (uint32_t)"+s(depth-3)+";"+
                      " int64_t b = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " a "+getArithOp(i)+"= b;"+
                      " "+s(depth-3)+" = (int32_t)a;"+
                      " "+s(depth-2)+" = (int32_t)(a >> 32); }");
            break;

        case Constants.LDIV: case Constants.LREM:
            out.println("\t{ int64_t a = ((int64_t)"+s(depth-2)+" << 32) | (uint32_t)"+s(depth-3)+";"+
                        " int64_t b = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";");
            out.print("\tif (unlikely(b == 0)) { "+s(0)+" = (int32_t)&aeExc;");
            dumpThrow(out, method, code, pos);
            out.print(" }"+
                      " a "+getArithOp(i)+"= b;"+
                      " "+s(depth-3)+" = (int32_t)a;"+
                      " "+s(depth-2)+" = (int32_t)(a >> 32); }");
            break;
        case Constants.LSHL: case Constants.LSHR:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth-1)+" << 32) | (uint32_t)"+s(depth-2)+";"+
                      " a "+getArithOp(i)+"= "+s(depth)+" & 0x3f;"+
                      " "+s(depth-2)+" = (int32_t)a;"+
                      " "+s(depth-1)+" = (int32_t)(a >> 32); }");
            break;
        case Constants.LUSHR:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth-1)+" << 32) | (uint32_t)"+s(depth-2)+";"+
                      " a = (uint64_t)a "+getArithOp(i)+" ("+s(depth)+" & 0x3f);"+
                      " "+s(depth-2)+" = (int32_t)a;"+
                      " "+s(depth-1)+" = (int32_t)(a >> 32); }");
            break;
        case Constants.LNEG:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " a = -a;"+
                      " "+s(depth-1)+" = (int32_t)a;"+
                      " "+s(depth)+" = (int32_t)(a >> 32); }");
            break;

        case Constants.LCMP:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth-2)+" << 32) | (uint32_t)"+s(depth-3)+";"+
                      " int64_t b = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " "+s(depth-3)+" = a > b ? 1 : (a == b ? 0 : -1); }");
            break;

        case Constants.FADD: case Constants.FSUB:
        case Constants.FMUL: case Constants.FDIV:
            out.print("\t{ float *a = (float *)&"+s(depth-1)+"; float *b = (float *)&"+s(depth)+";"+
                      " *a "+getArithOp(i)+"= *b; }");
            break;
        case Constants.FREM:
            out.print("\t{ float *a = (float *)&"+s(depth-1)+"; float *b = (float *)&"+s(depth)+";"+
                      " *a = remainderf(*a, *b); }");
            break;
        case Constants.FNEG:
            out.print("\t{ float *a = (float *)&"+s(depth)+"; *a = -*a; }");
            break;
        case Constants.FCMPL:
            out.print("\t{ float *a = (float *)&"+s(depth-1)+"; float *b = (float *)&"+s(depth)+";"+
                      " if (*a != *a || *b != *b) { "+s(depth-1)+" = -1; }"+
                      " else { "+s(depth-1)+" = *a > *b ? 1 : (*a == *b ? 0 : -1); } }");
            break;
        case Constants.FCMPG:
            out.print("\t{ float *a = (float *)&"+s(depth-1)+"; float *b = (float *)&"+s(depth)+";"+
                      " if (*a != *a || *b != *b) { "+s(depth-1)+" = 1; }"+
                      " else { "+s(depth-1)+" = *a > *b ? 1 : (*a == *b ? 0 : -1); } }");
            break;

        case Constants.DADD: case Constants.DSUB:
        case Constants.DMUL: case Constants.DDIV:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth-2)+" << 32) | (uint32_t)"+s(depth-3)+";"+
                      " int64_t b = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " double *c = (double *)&a; double *d = (double *)&b;"+
                      " *c "+getArithOp(i)+"= *d;"+
                      " "+s(depth-3)+" = (int32_t)a;"+
                      " "+s(depth-2)+" = (int32_t)(a >> 32); }");
            break;
        case Constants.DREM:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth-2)+" << 32) | (uint32_t)"+s(depth-3)+";"+
                      " int64_t b = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " double *c = (double *)&a; double *d = (double *)&b;"+
                      " *c = remainder(*c, *d);"+
                      " "+s(depth-3)+" = (int32_t)a;"+
                      " "+s(depth-2)+" = (int32_t)(a >> 32); }");
            break;
        case Constants.DNEG:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " double *c = (double *)&a; *c = -*c;"+
                      " "+s(depth-1)+" = (int32_t)a;"+
                      " "+s(depth)+" = (int32_t)(a >> 32); }");
            break;
        case Constants.DCMPL:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth-2)+" << 32) | (uint32_t)"+s(depth-3)+";"+
                      " int64_t b = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " double *c = (double *)&a; double *d = (double *)&b;"+
                      " if (*c != *c || *d != *d) { "+s(depth-3)+" = -1; }"+
                      " else { "+s(depth-3)+" = *c > *d ? 1 : (*c == *d ? 0 : -1); } }");
            break;
        case Constants.DCMPG:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth-2)+" << 32) | (uint32_t)"+s(depth-3)+";"+
                      " int64_t b = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " double *c = (double *)&a; double *d = (double *)&b;"+
                      " if (*c != *c || *d != *d) { "+s(depth-3)+" = 1; }"+
                      " else { "+s(depth-3)+" = *c > *d ? 1 : (*c == *d ? 0 : -1); } }");
            break;

        case Constants.GETFIELD:
            dumpGetField(out, method, code, pos, (GETFIELD)i, depth);
            break;

        case Constants.PUTFIELD:
            dumpPutField(out, method, code, pos, (PUTFIELD)i, depth);
            break;

        case Constants.GETSTATIC:
            dumpGetStatic(out, (GETSTATIC)i, depth);
            break;

        case Constants.PUTSTATIC:
            dumpPutStatic(out, (PUTSTATIC)i, depth);
            break;

        case Constants.AALOAD: case Constants.IALOAD: case Constants.FALOAD:
        case Constants.CALOAD: case Constants.SALOAD: case Constants.BALOAD:
            dumpNPE(out, method, code, pos, depth-1);
            dumpABE(out, method, code, pos, depth-1, depth, getArrayType(i));
            
            out.print("\t"+s(depth-1)+" = ");
            if (i.getOpcode() == Constants.AALOAD) {
                out.print("jvm_arrload_ref(");
            } else {
                out.print("jvm_arrload(");
            }
            out.print(getArrayType(i)+", "+s(depth-1)+", "+s(depth)+");");
            break;

        case Constants.LALOAD: case Constants.DALOAD:
            dumpNPE(out, method, code, pos, depth-1);
            dumpABE(out, method, code, pos, depth-1, depth, getArrayType(i));
            out.print("\t{ int64_t a = jvm_arrload_long("+getArrayType(i)+", "+s(depth-1)+", "+s(depth)+");"+
                      " "+s(depth-1)+" = (int32_t)a;"+
                      " "+s(depth)+" = (int32_t)(a >> 32); }");
            break;

        case Constants.AASTORE: case Constants.IASTORE: case Constants.FASTORE:
        case Constants.CASTORE: case Constants.SASTORE: case Constants.BASTORE:
            dumpNPE(out, method, code, pos, depth-2);
            dumpABE(out, method, code, pos, depth-2, depth-1, getArrayType(i));
            if (i.getOpcode() == Constants.AASTORE) {
                out.print("\tjvm_arrstore_ref(");
            } else {
                out.print("\tjvm_arrstore(");
            }
            out.print(getArrayType(i)+", "+s(depth-2)+", "+s(depth-1)+", "+s(depth)+");");
            break;

        case Constants.LASTORE: case Constants.DASTORE:
            dumpNPE(out, method, code, pos, depth-3);
            dumpABE(out, method, code, pos, depth-3, depth-2, getArrayType(i));
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " jvm_arrstore_long("+getArrayType(i)+", "+s(depth-3)+", "+s(depth-2)+", a); }");
            break;

        case Constants.ARRAYLENGTH:
            dumpNPE(out, method, code, pos, depth);
            out.print("\t"+s(depth)+" = jvm_arrlength(_int___obj_t, "+s(depth)+");");
            break;

        case Constants.INSTANCEOF:
            dumpInstanceOf(out, (INSTANCEOF)i, depth);
            break;
        case Constants.CHECKCAST:
            dumpCheckCast(out, method, code, pos, (CHECKCAST)i, depth);
            break;

        case Constants.IFNONNULL: case Constants.IFNE:
        case Constants.IF_ACMPNE: case Constants.IF_ICMPNE:
        case Constants.IFNULL: case Constants.IFEQ:
        case Constants.IF_ACMPEQ: case Constants.IF_ICMPEQ:
        case Constants.IFGE: case Constants.IF_ICMPGE:
        case Constants.IFLT: case Constants.IF_ICMPLT:
        case Constants.IFGT: case Constants.IF_ICMPGT:
        case Constants.IFLE: case Constants.IF_ICMPLE:
            BranchInstruction bi = (BranchInstruction)i;
            out.print("\tif ("+getComparison(i, depth)+") "+
                      "goto L"+(pos + bi.getIndex())+";");
            break;

        case Constants.GOTO:
        case Constants.GOTO_W:
            GotoInstruction gi = (GotoInstruction)i;
            out.print("\tgoto L"+(pos + gi.getIndex())+";");
            break;

        case Constants.TABLESWITCH: case Constants.LOOKUPSWITCH:
            dumpSwitch(out, pos, (Select)i, depth);
            break;

        case Constants.RETURN:
            dumpSyncReturn(out, method);
            out.print("\treturn;");
            break;
        case Constants.ARETURN: case Constants.IRETURN: case Constants.FRETURN:
            out.println("\t{ int32_t a = "+s(depth)+";");
            dumpSyncReturn(out, method);
            out.print("\treturn "+s(depth)+"; }");
            break;
        case Constants.LRETURN: case Constants.DRETURN:
            out.println("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";");
            dumpSyncReturn(out, method);
            out.print("\treturn a; }");
            break;

        case Constants.NEW:
            NEW n = (NEW)i;
            dumpNew(out, method, code, pos, n.getType(constPool), depth);
            break;

        case Constants.ANEWARRAY: case Constants.NEWARRAY:
            dumpNewArray(out, method, code, pos, i, depth);
            break;                

        case Constants.MULTIANEWARRAY:
            MULTIANEWARRAY mn = (MULTIANEWARRAY)i;
            dumpMultiANewArray(out, method, code, pos, mn, depth);
            break;

        case Constants.INVOKESTATIC: case Constants.INVOKEVIRTUAL:
        case Constants.INVOKESPECIAL: case Constants.INVOKEINTERFACE:
            dumpInvoke(out, method, code, pos, (InvokeInstruction)i, depth);
            break;

        case Constants.ATHROW:
            if (depth != 0) {
                out.print("\t"+s(0)+" = "+s(depth)+";");
            } else {
                out.print("\t");
            }
            dumpThrow(out, method, code, pos);
            break;

        case Constants.MONITORENTER:
            dumpNPE(out, method, code, pos, depth);
            dumpMonitorEnter(out, depth);
            break;

        case Constants.MONITOREXIT:
            dumpNPE(out, method, code, pos, depth);
            dumpMonitorExit(out, depth);
            break;

        default:
            out.print("\tfprintf(stderr, \""+i.getName()+" not implemented\\n\");");
        }

        out.println("\t/* "+i.getName()+" */");
    }

    /**
     * Generate a message that some item could not be found.
     * @param out The file to write to
     * @param type The type of item that could not be found
     * @param item The name of the item that could not be found
     */
    public void dumpNotFound(PrintWriter out, String type, String item) {
        String msg = type+" not found: "+item+" in "+getName();
        Logger.getGlobal().severe(msg);
        out.print("\tfprintf(stderr, \""+msg+"\\n\");");
    }

    /**
     * Generate the C code for the LDC bytecode
     * @param out The file to write to
     * @param stringPool The string pool
     * @param value The constant to be loaded
     * @param depth The current stack depth
     */
    public void dumpLdc(PrintWriter out, Map<String, Integer> stringPool, Object value, int depth) {
        if (value instanceof String) { 
            String strVal = (String)value;
            if (!stringPool.containsKey(strVal)) {
                stringPool.put(strVal, stringPool.size());
            }
            out.print("\t"+s(depth+1)+" = (int32_t)&stringPool["+stringPool.get(strVal)+"];");
        } else if (value instanceof ObjectType) {
            ObjectType objVal = (ObjectType)value;
            ClassInfo objClass = getClassInfo(Hull.arrayName(objVal.getClassName()));
            if (objClass == null) {
                dumpNotFound(out, "Constant", objVal.toString());
            } else {
                out.print("\t"+s(depth+1)+" = (int32_t)&"+objClass.getCName()+";");
            }
        } else if (value instanceof Float) {
            Float floatVal = (Float)value;
            out.print("\t"+s(depth+1)+" = "+Float.floatToIntBits(floatVal)+"UL;");
        } else {
            out.print("\t"+s(depth+1)+" = "+value+"UL;");
        }
    }

    /**
     * Generate the C code for the LDC2 bytecode
     * @param out The file to write to
     * @param value The constant to be loaded
     * @param depth The current stack depth
     */
    public void dumpLdc2(PrintWriter out, Object value, int depth) {
        if (value instanceof Double) {
            Double doubleVal = (Double)value;
            out.print("\t"+s(depth+1)+" = "+(int)Double.doubleToLongBits(doubleVal)+"UL;"+
                      " "+s(depth+2)+" = "+(int)(Double.doubleToLongBits(doubleVal) >> 32)+"UL;");
        } else {
            Long longVal = (Long)value;
            out.print("\t"+s(depth+1)+" = "+(int)longVal.longValue()+"UL;"+
                      " "+s(depth+2)+" = "+(int)(longVal.longValue() >> 32)+"UL;");
        }
    }

    /**
     * Generate the C code for the GETFIELD bytecode.
     * @param out The file to write to
     * @param method The method the code is generated for
     * @param code The code of the method
     * @param pos The current position in the code
     * @param gf The bytecode
     * @param depth The current stack depth
     */
    public void dumpGetField(PrintWriter out, Method method, Code code, int pos, GETFIELD gf, int depth) {
        String className = gf.getReferenceType(constPool).toString();
        ClassInfo ci = getClassInfo(className);
        if (ci == null) {
            dumpNotFound(out, "Class", className);
            return;
        }
        String fieldName = gf.getFieldName(constPool);
        int fieldIdx = ci.getFieldIndex(fieldName);
        dumpNPE(out, method, code, pos, depth);
        if (gf.getFieldType(constPool).getSize() == 1) {
            out.print("\t"+s(depth)+" = ");
            if (gf.getFieldType(constPool) instanceof ReferenceType) {
                out.print("\tjvm_getfield_ref(");
            } else {
                out.print("\tjvm_getfield(");
            }
            out.print(ci.getCObjTypeName()+", "+s(depth)+", "+fieldIdx+", "+getCFieldName(fieldName)+");");
        } else {
            out.print("\t{ int64_t a = jvm_getfield_long("+ci.getCObjTypeName()+", "+s(depth)+", "+fieldIdx+", "+getCFieldName(fieldName)+");"+
                      " "+s(depth)+" = (int32_t)a;"+
                      " "+s(depth+1)+" = (int32_t)(a >> 32); }");
        }
    }

    /**
     * Generate the C code for the PUTFIELD bytecode.
     * @param out The file to write to
     * @param method The method the code is generated for
     * @param code The code of the method
     * @param pos The current position in the code
     * @param pf The bytecode
     * @param depth The current stack depth
     */
    public void dumpPutField(PrintWriter out, Method method, Code code, int pos, PUTFIELD pf, int depth) {
        String className = pf.getReferenceType(constPool).toString();
        ClassInfo ci = getClassInfo(className);
        if (ci == null) {
            dumpNotFound(out, "Class", className);
            return;
        }
        String fieldName = pf.getFieldName(constPool);
        int fieldIdx = ci.getFieldIndex(fieldName);
        if (pf.getFieldType(constPool).getSize() == 1) {
            dumpNPE(out, method, code, pos, depth-1);
            if (pf.getFieldType(constPool) instanceof ReferenceType) {
                out.print("\tjvm_putfield_ref(");
            } else {
                out.print("\tjvm_putfield(");
            }
            out.print(ci.getCObjTypeName()+", "+s(depth-1)+", "+fieldIdx+", "+getCFieldName(fieldName)+", "+s(depth)+");");
        } else {
            dumpNPE(out, method, code, pos, depth-2);
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " jvm_putfield_long("+ci.getCObjTypeName()+", "+s(depth-2)+", "+fieldIdx+", "+getCFieldName(fieldName)+", a); }");
        }
    }

    /**
     * Generate the C code for the GETSTATIC bytecode.
     * @param out The file to write to
     * @param gs The bytecode
     * @param depth The current stack depth
     */
    public void dumpGetStatic(PrintWriter out, GETSTATIC gs, int depth) {
        String className = gs.getReferenceType(constPool).toString();
        ClassInfo ci = getClassInfo(className);
        String fieldName = gs.getFieldName(constPool);
        ci = findFieldDeclarator(ci, fieldName);
        if (ci == null) {
            dumpNotFound(out, "Static field", className+"."+fieldName);
            return;
        }
        if (gs.getFieldType(constPool).getSize() == 1) {
            out.print("\t"+s(depth+1)+" = ");
            if (gs.getFieldType(constPool) instanceof ReferenceType) {
                out.print("jvm_getstatic_ref(");
            } else {
                out.print("jvm_getstatic(");
            }
            out.print(ci.getCName()+"_"+getCFieldName(fieldName)+");");
        } else {
            out.print("\t{ int64_t a = jvm_getstatic_long("+ci.getCName()+"_"+getCFieldName(fieldName)+");"+
                      " "+s(depth+1)+" = (int32_t)a;"+
                      " "+s(depth+2)+" = (int32_t)(a >> 32); }");
        }
    }

    /**
     * Generate the C code for the PUTSTATIC bytecode.
     * @param out The file to write to
     * @param ps The bytecode
     * @param depth The current stack depth
     */
    public void dumpPutStatic(PrintWriter out, PUTSTATIC ps, int depth) {
        String className = ps.getReferenceType(constPool).toString();
        ClassInfo ci = getClassInfo(className);
        String fieldName = ps.getFieldName(constPool);
        ci = findFieldDeclarator(ci, fieldName);
        if (ci == null) {
            dumpNotFound(out, "Static field", className+"."+fieldName);
            return;
        }
        if (ps.getFieldType(constPool).getSize() == 1) {
            if (ps.getFieldType(constPool) instanceof ReferenceType) {
                out.print("\tjvm_putstatic_ref(");
            } else {
                out.print("\tjvm_putstatic(");
            }
            out.print(ci.getCName()+"_"+getCFieldName(fieldName)+", "+s(depth)+");");
        } else {
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " jvm_putstatic_long("+ci.getCName()+"_"+getCFieldName(fieldName)+", a); }");
        }
    }

    /**
     * Generate the C code for the INSTANCEOF bytecode.
     * @param out The file to write to
     * @param io The bytecode
     * @param depth The current stack depth
     */
    public void dumpInstanceOf(PrintWriter out, INSTANCEOF io, int depth) {
        String className = io.getType(constPool).toString();
        ClassInfo ci = getClassInfo(className);
        ClassInfo objci = getClassInfo("java.lang.Object");
        if (ci == null) {
            dumpNotFound(out, "Class", className);
            return;
        }
        if (ci.clazz.isInterface()) {
            int ifaceIdx = interfaceList.indexOf(ci);
            out.print("\t"+s(depth)+" = "+s(depth)+" == 0 ? 0 : ((("+objci.getCObjTypeName()+"*)"+s(depth)+")->type->itab["+(ifaceIdx / 32)+"] & "+(1 << (ifaceIdx % 32))+"UL) != 0;");
        } else {
            out.print("\t"+s(depth)+" = "+s(depth)+" == 0 ? 0 : jvm_instanceof((("+objci.getCObjTypeName()+"*)"+s(depth)+")->type, ("+objci.getCClassTypeName()+"*)&"+ci.getCName()+");");
        }
    }

    /**
     * Generate the C code for the CHECKCAST bytecode.
     * @param out The file to write to
     * @param method The method the code is generated for
     * @param code The code of the method
     * @param pos The current position in the code
     * @param cc The bytecode
     * @param depth The current stack depth
     */
    public void dumpCheckCast(PrintWriter out, Method method, Code code, int pos, CHECKCAST cc, int depth) {
        String className = cc.getType(constPool).toString();
        ClassInfo ci = getClassInfo(className);
        ClassInfo objci = getClassInfo("java.lang.Object");
        if (ci == null) {
            dumpNotFound(out, "Class", className);
            return;
        }
        out.print("\tif (unlikely("+s(depth)+" != 0 &&");
        if (ci.clazz.isInterface()) {
            int ifaceIdx = interfaceList.indexOf(ci);
            out.print(" ((("+objci.getCObjTypeName()+"*)"+s(depth)+")->type->itab["+(ifaceIdx / 32)+"] & "+(1 << (ifaceIdx % 32))+"UL) == 0)");
        } else {
            out.print(" !jvm_instanceof((("+objci.getCObjTypeName()+"*)"+s(depth)+")->type, ("+objci.getCClassTypeName()+"*)&"+ci.getCName()+")))");
        }
        out.print(" { "+s(0)+" = (int32_t)&ccExc;");
        dumpThrow(out, method, code, pos);
        out.print(" }");
    }

    /**
     * Generate the C code for a switch bytecode.
     * @param out The file to write to
     * @param pos The current position in the code
     * @param sel The bytecode
     * @param depth The current stack depth
     */
    public void dumpSwitch(PrintWriter out, int pos, Select sel, int depth) {
        int [] matchs = sel.getMatchs();
        int [] indices = sel.getIndices();
        out.println("\tswitch("+s(depth)+") {");
        for (int k = 0; k < matchs.length; k++) {
            out.println("\tcase "+matchs[k]+"UL: goto L"+(pos+indices[k])+";");
        }
        out.print("\tdefault: goto L"+(pos+sel.getIndex())+"; }");
    }

    /**
     * Generate the C code for the NEW bytecode.
     * @param out The file to write to
     * @param method The method the code is generated for
     * @param code The code of the method
     * @param pos The current position in the code
     * @param type The type to be allocated
     * @param depth The current stack depth
     */
    public void dumpNew(PrintWriter out, Method method, Code code, int pos, Type type, int depth) {
        ClassInfo ci = getClassInfo(type.toString());
        if (ci == null) {
            dumpNotFound(out, "Class", type.toString());
            return;
        }
        out.println("\t"+s(depth+1)+" = (int32_t)jvm_alloc(&"+ci.getCName()+", sizeof("+ci.getCObjTypeName()+"), &exc);");
        out.print("\tif (unlikely(exc != 0)) { "+s(0)+" = exc; exc = 0;");
        dumpThrow(out, method, code, pos);
        out.print(" }");
    }

    /**
     * Generate the C code for the NEWARRAY/ANEWARRAY bytecodes.
     * @param out The file to write to
     * @param method The method the code is generated for
     * @param code The code of the method
     * @param pos The current position in the code
     * @param i The bytecode
     * @param depth The current stack depth
     */
    public void dumpNewArray(PrintWriter out, Method method, Code code, int pos, Instruction i, int depth) {
        Type type;
        if (i.getOpcode() == Constants.NEWARRAY) {
            type = ((NEWARRAY)i).getType();
        } else {
            ANEWARRAY an = (ANEWARRAY)i;
            type = Type.getType("["+an.getType(constPool).getSignature());
        }
        out.println("\t{ int32_t z_0 = "+s(depth)+";");
        dumpNewArrayRaw(out, method, code, pos, type, "z_0", s(depth));
        out.print(" }");
    }

    /**
     * Generate the C code for the MULTIANEWARRAY bytecode.
     * @param out The file to write to
     * @param method The method the code is generated for
     * @param code The code of the method
     * @param pos The current position in the code
     * @param mn The bytecode
     * @param depth The current stack depth
     */
    public void dumpMultiANewArray(PrintWriter out, Method method, Code code, int pos, MULTIANEWARRAY mn, int depth) {
        int dim = mn.getDimensions();
        String sig = mn.getType(constPool).getSignature();
        out.println("\t{ int32_t z_0 = "+s(depth-dim+1)+";");
        dumpNewArrayRaw(out, method, code, pos, Type.getType(sig), "z_0", s(depth-dim+1));
        for (int k = 1; k < dim; k++) {
            out.println();
            sig = sig.substring(1);
            out.println("\tint32_t z_"+k+" = "+s(depth-dim+k+1)+";"+
                        " int32_t k_"+k+";"+
                        " for (k_"+k+" = 0; k_"+k+" < z_"+(k-1)+"; k_"+k+"++) {");
            dumpNewArrayRaw(out, method, code, pos, Type.getType(sig), "z_"+k, s(depth-dim+k+1));
            out.print(" jvm_arrstore_ref(_int___obj_t, "+s(depth-dim+k)+", k_"+k+", "+s(depth-dim+k+1)+");");
        }
        for (int k = 0; k < dim; k++) {
            out.print(" }");
        }
    }

    /**
     * Generate the C code for a raw array allocation.
     * @param out The file to write to
     * @param method The method the code is generated for
     * @param code The code of the method
     * @param pos The current position in the code
     * @param type The type of array to be allocated
     * @param sizeVal The variable that holds the size
     * @param dstVal The variable to receive the reference to the allocated array
     */
    public void dumpNewArrayRaw(PrintWriter out, Method method, Code code, int pos, Type type, String sizeVal, String dstVal) {

        int size;
        switch (type.getSignature()) {
        case "[Z": case "[B":
            size = 1;
            break;
        case "[C": case "[S":
            size = 2;
            break;
        case "[J": case "[D":
            size = 8;
            break;
        default:
            size = 4;
        }

        ClassInfo ci = getClassInfo(type.toString());
        if (ci == null) {
            dumpNotFound(out, "Class", type.toString());
            return;
        }
        String objType = ci.getCObjTypeName();

        out.println("\t"+dstVal+" = (int32_t)jvm_alloc(&"+ci.getCName()+", sizeof("+objType+")+"+sizeVal+"*"+size+", &exc);");
        out.print("\tif (unlikely(exc != 0)) { "+s(0)+" = exc; exc = 0;");
        dumpThrow(out, method, code, pos);
        out.println(" }");
        out.print("\tjvm_setarrlength("+objType+", "+dstVal+", "+sizeVal+");");
    }

    /**
     * Generate the C code for a method invocation.
     * @param out The file to write to
     * @param method The method the code is generated for
     * @param code The code of the method
     * @param pos The current position in the code
     * @param ii The bytecode
     * @param depth The current stack depth
     */
    public void dumpInvoke(PrintWriter out, Method method, Code code, int pos, InvokeInstruction ii, int depth) {
        int opcode = ii.getOpcode();

        String methName = ii.getMethodName(constPool);
        String signature = ii.getSignature(constPool);
        String escName = escapeName(methName+signature);        

        String className = ii.getReferenceType(constPool).toString();
        ClassInfo ci = getClassInfo(className);
        String fqName = ci.getName()+"."+methName+signature;
        String typeName;
        if (opcode == Constants.INVOKEVIRTUAL
            && getVirtualMethods().contains(fqName)) {
            if (ci == null) {
                dumpNotFound(out, "Method", className+"."+methName+signature);
                return;
            }
            typeName = ci.getCObjTypeName();
        } else {
            ci = findMethodDeclarator(ci, methName, signature);
            if (ci == null) {
                dumpNotFound(out, "Method", className+"."+methName+signature);
                return;
            }
            typeName = ci.getCName();
        }
        
        Type retType = ii.getReturnType(constPool);
        int retSize = retType.getSize();
        int argCount = getArgCount(ii.getArgumentTypes(constPool),
                                   opcode == Constants.INVOKESTATIC);
        
        if (opcode == Constants.INVOKEVIRTUAL
            || opcode == Constants.INVOKEINTERFACE
            || opcode == Constants.INVOKESPECIAL) {
            dumpNPE(out, method, code, pos, depth-argCount+1);
        }
        
        out.print("\t");
        if (retSize == 2) {
            out.print("{ int64_t a = ");
        } else if (retSize > 0) {
            out.print(s(depth-argCount+1)+" = ");
        }
        if (opcode == Constants.INVOKESTATIC
            || opcode == Constants.INVOKESPECIAL
            || (opcode == Constants.INVOKEVIRTUAL
                && !getVirtualMethods().contains(fqName))) {
            out.print(typeName+"_"+escName+"(");
        } else if (opcode == Constants.INVOKEINTERFACE) {
            out.print("(("+ci.getCObjTypeName()+"*)"+s(depth-argCount+1)+")->type->imtab->"+typeName+"_"+escName+"("); 
        } else {
            out.print("(("+typeName+"*)"+s(depth-argCount+1)+")->type->"+escName+"(");
        }
        for (int k = argCount-1; k >= 0; --k) {
            out.print(s(depth-k)+", ");
        }
        out.print("&exc);");
        if (retSize == 2) {
            out.print(" "+s(depth-argCount+1)+" = (int32_t)a;"+
                      " "+s(depth-argCount+2)+" = (int32_t)(a >> 32); }");
        }
        
        out.println();
        out.print("\tif (unlikely(exc != 0)) { "+s(0)+" = exc; exc = 0;");
        dumpThrow(out, method, code, pos);
        out.print(" }");
    }

    /**
     * Generate the C code for a null pointer check.
     * @param out The file to write to
     * @param method The method the code is generated for
     * @param code The code of the method
     * @param pos The current position in the code
     * @param depth The current stack depth
     */
    public void dumpNPE(PrintWriter out, Method method, Code code, int pos, int depth) {
        out.print("\tif (unlikely("+s(depth)+" == 0)) { "+s(0)+" = (int32_t)&npExc;");
        dumpThrow(out, method, code, pos);
        out.println(" }");
    }

    /**
     * Generate the C code for an array bounds check.
     * @param out The file to write to
     * @param method The method the code is generated for
     * @param code The code of the method
     * @param pos The current position in the code
     * @param depth The current stack depth
     * @param idxdepth The stack slot of the array index
     * @param type The type of the array
     */
    public void dumpABE(PrintWriter out, Method method, Code code, int pos, int depth, int idxdepth, String type) {
        out.print("\tif (unlikely("+s(idxdepth)+" < 0 ||"+
                  " "+s(idxdepth)+" >= jvm_arrlength("+type+", "+s(depth)+")))"+
                  " { "+s(0)+" = (int32_t)&abExc;");
        dumpThrow(out, method, code, pos);
        out.println(" }");
    }

    /**
     * Generate the C code to throw an exception
     * @param out The file to write to
     * @param method The method the code is generated for
     * @param code The code of the method
     * @param pos The current position in the code
     */
    public void dumpThrow(PrintWriter out, Method method, Code code, int pos) {
        CodeException [] excTab = code.getExceptionTable();

        boolean caught = false;
        if (excTab != null) {
            for (CodeException exc : excTab) {
                int start = exc.getStartPC();
                int end = exc.getEndPC();
                int handler = exc.getHandlerPC();
                int type = exc.getCatchType();
                if (start <= pos && pos <= end) {
                    if (type == 0) {
                        out.println();
                        out.print("\tgoto L"+handler+";");
                        caught = true;
                        break;
                    } else {
                        ConstantClass cc = (ConstantClass)constPool.getConstant(exc.getCatchType());
                        String className = (String)cc.getConstantValue(constPool.getConstantPool());
                        className = className.replace('/', '.');
                        ClassInfo ci = getClassInfo(className);
                        ClassInfo objci = getClassInfo("java.lang.Object");
                        if (ci == null) {
                            dumpNotFound(out, "Class", className);
                        } else {
                            out.println();
                            out.print("\tif (jvm_instanceof((("+objci.getCObjTypeName()+"*)"+s(0)+")->type, ("+objci.getCClassTypeName()+"*)&"+ci.getCName()+"))"+
                                      " goto L"+handler+";");
                        }
                    }
                }
            }
        }
        if (!caught) {
            Type retType = method.getReturnType();
            out.println();
            out.println("\t*retexc = "+s(0)+";");
            dumpSyncReturn(out, method);
            out.print("\treturn"+(retType != Type.VOID ? " 0" : "")+";");
        }
    }

    /**
     * Generate the C code for the MONITORENTER bytecode
     * @param out The file to write to
     * @param depth The current stack depth
     */
    public void dumpMonitorEnter(PrintWriter out, int depth) {
        ClassInfo ci = getClassInfo("java.lang.Object");
        out.print("\t{ int r = jvm_lock(("+ci.getCObjTypeName()+" *)"+s(depth)+");");
        out.println(" if (unlikely(r)) jvm_catch((int32_t)&vmErr); }");
    }

    /**
     * Generate the C code for the MONITOREXIT bytecode
     * @param out The file to write to
     * @param depth The current stack depth
     */
    public void dumpMonitorExit(PrintWriter out, int depth) {
        ClassInfo ci = getClassInfo("java.lang.Object");
        out.print("\t{ int r = jvm_unlock(("+ci.getCObjTypeName()+" *)"+s(depth)+");");
        out.println(" if (unlikely(r)) jvm_catch((int32_t)&vmErr); }");
    }

    /**
     * Generate the C code to acquiring a lock upon entering a method.
     * @param out The file to write to
     * @param method The method the code is generated for
     */
    public void dumpSyncEnter(PrintWriter out, Method method) {
        if (method.isSynchronized()) {
            if (method.isStatic()) {
                out.println("\t"+s(0)+" = (int32_t)&"+getCName()+";");
            } else {
                out.println("\t"+s(0)+" = "+v(0)+";");
            }
            dumpMonitorEnter(out, 0);
        }
    }

    /**
     * Generate the C code to release a lock before returning.
     * @param out The file to write to
     * @param method The method the code is generated for
     */
    public void dumpSyncReturn(PrintWriter out, Method method) {
        if (method.isSynchronized()) {            
            if (method.isStatic()) {
                out.println("\t"+s(0)+" = (int32_t)&"+getCName()+";");
            } else {
                out.println("\t"+s(0)+" = "+v(0)+";");
            }
            dumpMonitorExit(out, 0);
        }
    }

    /**
     * Generate the C code for the string pool.
     * @param out The file to write to
     * @param stringPool The string pool
     */
    public static void dumpStringPool(PrintWriter out, Map<String, Integer> stringPool) {
        ClassInfo arrci = getClassInfo("char[]");
        for (Map.Entry<String, Integer> e : stringPool.entrySet()) {
            String str = e.getKey();
            int index = e.getValue();

            out.println("const struct { ");
            out.println("\t"+arrci.getCClassTypeName()+" *type;");
            out.println("\tlock_t *lock;");
            out.println("\twait_t *wait;");
            out.println("\t"+getCType(Type.INT)+" _0_length;");
            if (str.length() > 0) {
                out.println("\t"+getCType(Type.CHAR)+" _1_data["+str.length()+"];");
            }
            out.println("} string_"+index+"_val = {");
            out.println("\t&"+arrci.getCName()+", /* type */");
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

        ClassInfo strci = getClassInfo("java.lang.String");
        out.println(strci.getCObjTypeName()+" stringPool["+stringPool.size()+"] = {");
        for (int i = 0; i < stringPool.size(); i++) {
            out.println("{\t&"+strci.getCName()+", /* type */");
            out.println("\t0, /* lock */");
            out.println("\t0, /* wait */");
            out.println("\t(int32_t)&string_"+i+"_val /* value */");
            out.println("},");
        }
        out.println("};");
    }

    /**
     * Generate the code for the C main method.
     * @param out The file to write to
     */
    public static void dumpMain(PrintWriter out) {
        ClassInfo entry = getClassInfo(entryClass);
        out.println("int main(int argc, char **argv) {");
        out.println("\tint32_t exc = 0;");
        out.println("\tjvm_clinit(&exc);");
        out.println("\tif (exc != 0) { jvm_catch(exc); }");

        ClassInitOrder cio = new ClassInitOrder(classInfoMap);
        for (ClassInfo ci : cio.findOrder()) {
            String className = ci.getCName();
            String methName = getCMethName(ci.findClinit());
            out.println("\t"+className+"_"+methName+"(&exc);");
            out.println("\tif (exc != 0) { jvm_catch(exc); }");
        }

        out.println("\tjvm_init(&exc);");
        out.println("\tif (exc != 0) { jvm_catch(exc); }");

        out.println("\tint32_t args = jvm_args(argc, argv, &exc);");
        out.println("\tif (exc != 0) { jvm_catch(exc); }");

        out.println("\t"+entry.getCName()+"_main__Ljava_lang_String__V(args, &exc);");

        out.println("\tif (exc != 0) { jvm_catch(exc); }");
        out.println("\tpthread_exit(NULL);");
        out.println("}");
    }

}
