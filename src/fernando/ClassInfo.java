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
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantDouble;
import org.apache.bcel.classfile.ConstantFloat;
import org.apache.bcel.classfile.ConstantInteger;
import org.apache.bcel.classfile.ConstantLong;
import org.apache.bcel.classfile.ConstantString;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.ConstantValue;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.ConstantPushInstruction;
import org.apache.bcel.generic.GotoInstruction;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.Select;
import org.apache.bcel.generic.StoreInstruction;
import org.apache.bcel.generic.Type;
import org.apache.bcel.generic.UnconditionalBranch;
import org.apache.bcel.generic.ANEWARRAY;
import org.apache.bcel.generic.ATHROW;
import org.apache.bcel.generic.CHECKCAST;
import org.apache.bcel.generic.GETFIELD;
import org.apache.bcel.generic.GETSTATIC;
import org.apache.bcel.generic.IINC;
import org.apache.bcel.generic.INSTANCEOF;
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.LDC2_W;
import org.apache.bcel.generic.MULTIANEWARRAY;
import org.apache.bcel.generic.NEW;
import org.apache.bcel.generic.NEWARRAY;
import org.apache.bcel.generic.PUTFIELD;
import org.apache.bcel.generic.PUTSTATIC;
import org.apache.bcel.util.ByteSequence;

import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public class ClassInfo {

    private static String entryClass;
    private static Map<String, ClassInfo> classInfoMap = new LinkedHashMap<String, ClassInfo>();
    private static List<ClassInfo> interfaceList = new LinkedList<ClassInfo>();

    public static ClassInfo getClassInfo(String name) {
        return classInfoMap.get(name);
    }

    public static void loadHull(String entry) {
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

    private final String name;
    private final JavaClass clazz;
    private final ConstantPoolGen constPool;

    private ClassInfo(String name, JavaClass clazz) {
        this.name = name;
        this.clazz = clazz;
        this.constPool = new ConstantPoolGen(clazz.getConstantPool());
    }

    public String getName() {
        return name;
    }
    public ConstantPoolGen getConstPool() {
        return constPool;
    }

    private static String escapeName(String name) {
        return name.replaceAll("[./<>();\\[\\]\\$]", "_");
    }
    public String getCName() {
        return escapeName("_"+getName());
    }
    public String getCClassTypeName() {
        return getCName() + "_class_t";
    }
    public String getCObjTypeName() {
        return getCName() + "_obj_t";
    }
    public static String getCMethName(Method m) {
        return escapeName(m.getName()+m.getSignature());
    }
    private static String getCFieldName(String name) {
        return name.replaceAll("[\\$]", "_");
    }

    public static String getCType(Type type) {
        switch (type.getType()) {
        case Constants.T_VOID:
            return "void";
        case Constants.T_BOOLEAN:
        case Constants.T_BYTE:
            return "int8_t";
        case Constants.T_CHAR:
            return "uint16_t";
        case Constants.T_SHORT:
            return "int16_t";
        case Constants.T_LONG:
        case Constants.T_DOUBLE:
            return "int64_t";
        case Constants.T_INT:
        case Constants.T_FLOAT:
        case Constants.T_OBJECT:
        default:
            return "int32_t";
        }
    }

    public ClassInfo getSuperClass() {
        ClassInfo superClass = getClassInfo(clazz.getSuperclassName());
        if (superClass == this) {
            return null;
        } else {
            return superClass;
        }
    }

    public boolean instanceOf(ClassInfo ci) {
        boolean retval = false;
        try {
            retval = clazz.instanceOf(ci.clazz);
        } catch (ClassNotFoundException exc) {
            System.err.println(exc);
            System.exit(-1);
        }
        return retval;
    }

    public boolean implementationOf(ClassInfo ci) {
        boolean retval = false;
        try {
            retval = clazz.implementationOf(ci.clazz);
        } catch (ClassNotFoundException exc) {
            System.err.println(exc);
            System.exit(-1);
        }
        return retval;
    }

    public Set<ClassInfo> getInterfaces() {
        Set<ClassInfo> interfaces = new LinkedHashSet<ClassInfo>();
        LinkedList<ClassInfo> queue = new LinkedList<ClassInfo>();
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

    public List<Field> getFields() {
        return Arrays.asList(clazz.getFields());
    }

    public List<Field> getInstanceFields() {
        List<Field> fields = new LinkedList<Field>();

        ClassInfo superClass = getSuperClass();
        if (superClass != null) {
            fields.addAll(superClass.getInstanceFields());
        }

        fields.addAll(Filter.instances(getFields()));

        return fields;
    }

    public boolean declaresField(String name) {
        List<Field> fields = getFields();
        for (Field f : fields) {
            if (f.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public ClassInfo findFieldDeclarator(ClassInfo base, String name) {
        while (base != null && !base.declaresField(name)) {
            for (ClassInfo c : base.getInterfaces()) {
                if (c.declaresField(name)) {
                    return c;
                }
            }
            base = base.getSuperClass();
        }
        return base;
    }

    public int getFieldIndex(String name) {
        List<Field> fields = getInstanceFields();
        for (int i = fields.size()-1; i >= 0; i--) {
            if (fields.get(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    public List<Method> getMethods() {
        return Arrays.asList(clazz.getMethods());
    }

    public boolean declaresMethod(String name, String signature) {
        for (Method m : getMethods()) {
            if (m.getName().equals(name)
                && m.getSignature().equals(signature)) {
                return true;
            }
        }
        return false;
    }

    public ClassInfo findMethodDeclarator(ClassInfo base, String name, String signature) {
        while (base != null && !base.declaresMethod(name, signature)) {
            for (ClassInfo c : base.getInterfaces()) {
                if (c.declaresMethod(name, signature)) {
                    return c;
                }
            }
            base = base.getSuperClass();
        }
        return base;
    }

    public Method findClinit() {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals("<clinit>")
                && m.getSignature().equals("()V")) {
                return m;
            }
        }
        return null;
    }

    private List<Map.Entry<Method, ClassInfo>> instanceMethods;
    private static Set<String> virtualMethods;

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

    public List<Map.Entry<Method, ClassInfo>> getInstanceMethods() {
        if (instanceMethods == null) {
            computeInstanceMethods();
        }
        return instanceMethods;
    }

    public Set<String> getVirtualMethods() {
        if (virtualMethods == null) {
            virtualMethods = new LinkedHashSet<String>();
            for (ClassInfo ci : classInfoMap.values()) {
                ci.computeInstanceMethods();
            }
        }
        return virtualMethods;
    }

    public static boolean methodsEqual(Method a, Method b) {
        return a.getName().equals(b.getName()) &&
            a.getSignature().equals(b.getSignature());
    }

    public boolean needsInterfaceTable() {
        if (clazz.isInterface() || clazz.isAbstract() || getInterfaces().isEmpty()) {
            return false;
        }
        for (ClassInfo i : interfaceList) {
            if (implementationOf(i)) {
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

    private static String getArithOp(Instruction i) {
        switch (i.getOpcode()) {
        case Constants.IADD: case Constants.LADD: 
        case Constants.FADD: case Constants.DADD: return "+";
        case Constants.ISUB: case Constants.LSUB:
        case Constants.FSUB: case Constants.DSUB: return "-";
        case Constants.IMUL: case Constants.LMUL:
        case Constants.FMUL: case Constants.DMUL: return "*";
        case Constants.IDIV: case Constants.LDIV:
        case Constants.FDIV: case Constants.DDIV: return "/";
        case Constants.IREM: case Constants.LREM: return "%";
        case Constants.IOR:  case Constants.LOR:  return "|";
        case Constants.IAND: case Constants.LAND: return "&";
        case Constants.IXOR: case Constants.LXOR: return "^";
        case Constants.ISHL: case Constants.LSHL: return "<<";
        case Constants.ISHR: case Constants.LSHR:
        case Constants.IUSHR: case Constants.LUSHR: return ">>";
        default:
            System.err.println("Invalid arithmetic instruction: "+i);
            System.exit(-1);
            return null;
        }
    }
    private static String getCondition(Instruction i) {
        switch (i.getOpcode()) {
        case Constants.IFNONNULL: case Constants.IFNE:
        case Constants.IF_ACMPNE: case Constants.IF_ICMPNE: return "!=";
        case Constants.IFNULL: case Constants.IFEQ:
        case Constants.IF_ACMPEQ: case Constants.IF_ICMPEQ: return "==";
        case Constants.IFGE: case Constants.IF_ICMPGE: return ">=";
        case Constants.IFLT: case Constants.IF_ICMPLT: return "<";
        case Constants.IFGT: case Constants.IF_ICMPGT: return ">";
        case Constants.IFLE: case Constants.IF_ICMPLE: return "<=";
        default:
            System.err.println("Invalid comparison instruction: "+i);
            System.exit(-1);
            return null;
        }
    }
    private static String getComparison(Instruction i, int depth) {
        switch (i.getOpcode()) {
        case Constants.IFNONNULL: case Constants.IFNE:
        case Constants.IFNULL: case Constants.IFEQ:
        case Constants.IFGE: case Constants.IFLT:
        case Constants.IFGT: case Constants.IFLE:
            return s(depth)+" "+getCondition(i)+" 0";
        case Constants.IF_ACMPNE: case Constants.IF_ICMPNE:
        case Constants.IF_ACMPEQ: case Constants.IF_ICMPEQ:        
        case Constants.IF_ICMPGE: case Constants.IF_ICMPLT:
        case Constants.IF_ICMPGT: case Constants.IF_ICMPLE:
            return s(depth-1)+" "+getCondition(i)+" "+s(depth);
        default:
            System.err.println("Invalid comparison instruction: "+i);
            System.exit(-1);
            return null;
        }        
    }

    private static String getArrayType(Instruction i) {
        switch(i.getOpcode()) {
        case Constants.AALOAD: case Constants.AASTORE: return "_int___obj_t"; //return "_java_lang_Object___obj_t";
        case Constants.IALOAD: case Constants.IASTORE: return "_int___obj_t";
        case Constants.FALOAD: case Constants.FASTORE: return "_float___obj_t";
        case Constants.CALOAD: case Constants.CASTORE: return "_char___obj_t";
        case Constants.SALOAD: case Constants.SASTORE: return "_short___obj_t";
        case Constants.BALOAD: case Constants.BASTORE: return "_byte___obj_t";
        case Constants.LALOAD: case Constants.LASTORE: return "_long___obj_t";
        case Constants.DALOAD: case Constants.DASTORE: return "_double___obj_t";
        default:
            System.err.println("Invalid array instruction: "+i);
            System.exit(-1);
            return null;
        }
    }

    private static PrintWriter getFile(String dir, String name) {
        PrintWriter file = null;
        try {
            FileWriter f = new FileWriter(dir+File.separator+name);
            BufferedWriter b = new BufferedWriter(f);
            file = new PrintWriter(b);
        } catch (IOException exc) {
            System.err.println(exc);
            System.exit(-1);
        }
        return file;
    }

    public static void dumpAll(String outDir) {
        Map<String, Integer> stringPool = new LinkedHashMap<String, Integer>();
     
        PrintWriter defsOut = getFile(outDir, "defs.h");
        dumpStartDefs(defsOut);
        Set<ClassInfo> dumpedDef = new LinkedHashSet<ClassInfo>();
        for (ClassInfo c : classInfoMap.values()) {
            c.dumpDefs(defsOut, dumpedDef);
        }
        dumpEndDefs(defsOut);
        defsOut.flush();

        for (ClassInfo c : classInfoMap.values()) {
            PrintWriter out = getFile(outDir+File.separator+"classes", c.getCName()+".c");
            out.println("#include \"jvm.h\"");
            c.dumpBodies(out, stringPool);
            out.flush();
        }

        PrintWriter mainOut = getFile(outDir, "main.c");
        mainOut.println("#include \"jvm.h\"");
        dumpStringPool(mainOut, stringPool);
        dumpMain(mainOut);
        mainOut.flush();
    }

    public static void dumpArgList(PrintWriter out, Method m) {
        int argCount = getArgCount(m.getArgumentTypes(), m.isStatic());
        out.print("(");
        for (int i = 0; i < argCount; i++) {
            out.print("int32_t "+v(i)+", ");
        }
        out.print("int32_t *restrict retexc)");
    }

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

        out.println("/* forward definition of class object */");
        out.println("extern "+getCClassTypeName()+" "+getCName()+";");
        out.println();
    }

    public void dumpMethodPointerDefs(PrintWriter out) {
        out.println("\t/* method pointers */");
        for (Map.Entry<Method, ClassInfo> e : getInstanceMethods()) {
            Method m = e.getKey();
            String fqName = e.getValue().getName()+"."+m.getName()+m.getSignature();
            if (getVirtualMethods().contains(fqName)
                && !(m.getName().equals("<init>")
                     && m.getSignature().equals("()V"))) {
                out.print("\t"+getCType(m.getReturnType())+
                          " (* const "+getCMethName(m)+")");
                dumpArgList(out, m);
                out.println(";");
            }
        }
    }

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
            fieldIdx++;
        }
        out.println("} "+getCObjTypeName()+";");
        out.println();
    }

    public static void dumpEndDefs(PrintWriter out) {
        out.println("#endif /* _DEFS_H_ */");
    }

    public void dumpBodies(PrintWriter out, Map<String, Integer> stringPool) {
        dumpStaticFields(out, stringPool);
        dumpMethodBodies(out, stringPool);

        if (needsInterfaceTable()) {
            dumpIfaceMethTab(out);
        }

        dumpClassBody(out, stringPool);
    }

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

    public void dumpIfaceMethTab(PrintWriter out) {
        out.println("imtab_t "+getCName()+"_imtab = {");
        for (ClassInfo i : interfaceList) {
            out.println("\t/* interface "+i.getName()+" */");
            for (Method m : i.getMethods()) {
                boolean found = false;
                if (implementationOf(i)) {
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

    public void dumpClassBody(PrintWriter out, Map<String, Integer> stringPool) {
        String classClassPtr = "&"+getClassInfo("java.lang.Class").getCName();
        ClassInfo superClass = getSuperClass();
        String superClassPtr = superClass != null ? "&"+superClass.getCName() : "0";
        String elemType = "0";
        if (getName().endsWith("[]")) {
            String typeName = getName().substring(0, getName().length()-2);
            ClassInfo ci = getClassInfo(typeName);
            if (ci == null) {
                System.err.println("Class not found: "+typeName+" in "+getName());
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
                buffer |= (instanceOf(i) ? 1 : 0) << bufferCount;
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

    public void dumpMethodPointers(PrintWriter out) {
        out.println("\t/* method pointers */");
        for (Map.Entry<Method, ClassInfo> e : getInstanceMethods()) {
            Method m = e.getKey();
            String fqName = e.getValue().getName()+"."+m.getName()+m.getSignature();
            if (getVirtualMethods().contains(fqName)
                && !(m.getName().equals("<init>")
                     && m.getSignature().equals("()V"))) {
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
                    System.err.println("Invalid tag for ConstantValue: "+cv.getTag());
                    System.exit(-1);
                }
            } else {
                out.println("0;");
            }
        }
        out.println();
    }

    private static String s(int depth) {
        return "s_"+depth;
    }
    private static String v(int depth) {
        return "v_"+depth;
    }

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

        dumpSyncEnter(out, method, code, 0);

        for (InstructionHandle ih : il.getInstructionHandles()) {
            Instruction i = ih.getInstruction();
            int pos = ih.getPosition();
            int depth = depthMap.get(pos);            

            if (ih.hasTargeters() || excHandlers.contains(pos)) {
                out.print("L"+pos+":");
            }

            dumpInstruction(out, stringPool, method, code, pos, i, depth);
        }
    }

    public void dumpInstruction(PrintWriter out, Map<String, Integer> stringPool, Method method, Code code, int pos, Instruction i, int depth) {
        switch (i.getOpcode()) {
        case Constants.ACONST_NULL:
            out.print("\t"+s(depth+1)+" = 0;");                
            break;

        case Constants.ICONST_M1: case Constants.ICONST_0: case Constants.ICONST_1:
        case Constants.ICONST_2: case Constants.ICONST_3: case Constants.ICONST_4:
        case Constants.ICONST_5: case Constants.BIPUSH: case Constants.SIPUSH: {
            ConstantPushInstruction cpi = (ConstantPushInstruction)i;
            out.print("\t"+s(depth+1)+" = "+cpi.getValue().intValue()+";");
            break;
        }
        case Constants.FCONST_0: case Constants.FCONST_1: case Constants.FCONST_2: {
            ConstantPushInstruction cpi = (ConstantPushInstruction)i;
            out.print("\t"+s(depth+1)+" = "+Float.floatToIntBits(cpi.getValue().floatValue())+";");
            break;
        }
        case Constants.LCONST_0: case Constants.LCONST_1: {
            ConstantPushInstruction cpi = (ConstantPushInstruction)i;
            out.print("\t"+s(depth+1)+" = "+(int)cpi.getValue().longValue()+";"+
                      " "+s(depth+2)+" = "+(int)(cpi.getValue().longValue() >> 32)+";");
            break;
        }
        case Constants.DCONST_0: case Constants.DCONST_1: {
            ConstantPushInstruction cpi = (ConstantPushInstruction)i;
            out.print("\t"+s(depth+1)+" = "+(int)Double.doubleToLongBits(cpi.getValue().doubleValue())+";"+
                      " "+s(depth+2)+" = "+(int)(Double.doubleToLongBits(cpi.getValue().doubleValue()) >> 32)+";");
            break;
        }

        case Constants.LDC: case Constants.LDC_W: {
            LDC ldc = (LDC)i;
            dumpLdc(out, stringPool, ldc.getValue(constPool), depth);
            break;
        }

        case Constants.LDC2_W: {
            LDC2_W ldc = (LDC2_W)i;
            dumpLdc2(out, ldc.getValue(constPool), depth);
            break;
        }

        case Constants.ALOAD_0: case Constants.ALOAD_1: case Constants.ALOAD_2:
        case Constants.ALOAD_3: case Constants.ALOAD:
        case Constants.ILOAD_0: case Constants.ILOAD_1: case Constants.ILOAD_2:
        case Constants.ILOAD_3: case Constants.ILOAD:
        case Constants.FLOAD_0: case Constants.FLOAD_1: case Constants.FLOAD_2:
        case Constants.FLOAD_3: case Constants.FLOAD: {
            LoadInstruction li = (LoadInstruction)i;
            out.print("\t"+s(depth+1)+" = "+v(li.getIndex())+";");
            break;
        }

        case Constants.LLOAD_0: case Constants.LLOAD_1: case Constants.LLOAD_2:
        case Constants.LLOAD_3: case Constants.LLOAD:
        case Constants.DLOAD_0: case Constants.DLOAD_1: case Constants.DLOAD_2:
        case Constants.DLOAD_3: case Constants.DLOAD: {
            LoadInstruction li = (LoadInstruction)i;
            out.print("\t"+s(depth+1)+" = "+v(li.getIndex())+";"+
                      " "+s(depth+2)+" = "+v(li.getIndex()+1)+";");
            break;
        }

        case Constants.ASTORE_0: case Constants.ASTORE_1: case Constants.ASTORE_2:
        case Constants.ASTORE_3: case Constants.ASTORE:
        case Constants.ISTORE_0: case Constants.ISTORE_1: case Constants.ISTORE_2:
        case Constants.ISTORE_3: case Constants.ISTORE:
        case Constants.FSTORE_0: case Constants.FSTORE_1: case Constants.FSTORE_2:
        case Constants.FSTORE_3: case Constants.FSTORE: {
            StoreInstruction si = (StoreInstruction)i;
            out.print("\t"+v(si.getIndex())+" = "+s(depth)+";");
            break;
        }

        case Constants.LSTORE_0: case Constants.LSTORE_1: case Constants.LSTORE_2:
        case Constants.LSTORE_3: case Constants.LSTORE:
        case Constants.DSTORE_0: case Constants.DSTORE_1: case Constants.DSTORE_2:
        case Constants.DSTORE_3: case Constants.DSTORE: {
            StoreInstruction si = (StoreInstruction)i;
            out.print("\t"+v(si.getIndex())+" = "+s(depth-1)+";"+
                      " "+v(si.getIndex()+1)+" = "+s(depth)+";");
            break;
        }

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

        case Constants.IINC: {
            IINC ii = (IINC)i;
            out.print("\t"+v(ii.getIndex())+" += "+ii.getIncrement()+";");
            break;
        }

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

        case Constants.GETFIELD: {
            GETFIELD gf = (GETFIELD)i;
            dumpGetField(out, method, code, pos, gf, depth);
            break;
        }

        case Constants.PUTFIELD: {
            PUTFIELD pf = (PUTFIELD)i;
            dumpPutField(out, method, code, pos, pf, depth);
            break;
        }

        case Constants.GETSTATIC: {
            GETSTATIC gs = (GETSTATIC)i;
            dumpGetStatic(out, method, code, pos, gs, depth);
            break;
        }

        case Constants.PUTSTATIC: {
            PUTSTATIC ps = (PUTSTATIC)i;
            dumpPutStatic(out, method, code, pos, ps, depth);
            break;
        }

        case Constants.AALOAD: case Constants.IALOAD: case Constants.FALOAD:
        case Constants.CALOAD: case Constants.SALOAD: case Constants.BALOAD:
            dumpNPE(out, method, code, pos, depth-1);
            dumpABE(out, method, code, pos, depth-1, depth, getArrayType(i));
            out.print("\t"+s(depth-1)+" = jvm_arrload("+getArrayType(i)+", "+s(depth-1)+", "+s(depth)+");");
            break;

        case Constants.LALOAD: case Constants.DALOAD:
            dumpNPE(out, method, code, pos, depth-1);
            dumpABE(out, method, code, pos, depth-1, depth, getArrayType(i));
            out.print("\t{ int64_t a = jvm_arrload2("+getArrayType(i)+", "+s(depth-1)+", "+s(depth)+");"+
                      " "+s(depth-1)+" = (int32_t)a;"+
                      " "+s(depth)+" = (int32_t)(a >> 32); }");
            break;

        case Constants.AASTORE: case Constants.IASTORE: case Constants.FASTORE:
        case Constants.CASTORE: case Constants.SASTORE: case Constants.BASTORE:
            dumpNPE(out, method, code, pos, depth-2);
            dumpABE(out, method, code, pos, depth-2, depth-1, getArrayType(i));
            out.print("\tjvm_arrstore("+getArrayType(i)+", "+s(depth-2)+", "+s(depth-1)+", "+s(depth)+");");
            break;

        case Constants.LASTORE: case Constants.DASTORE:
            dumpNPE(out, method, code, pos, depth-3);
            dumpABE(out, method, code, pos, depth-3, depth-2, getArrayType(i));
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " jvm_arrstore2("+getArrayType(i)+", "+s(depth-3)+", "+s(depth-2)+", a); }");
            break;

        case Constants.ARRAYLENGTH:
            dumpNPE(out, method, code, pos, depth);
            out.print("\t"+s(depth)+" = jvm_arrlength(_int___obj_t, "+s(depth)+");");
            break;

        case Constants.INSTANCEOF: {
            INSTANCEOF io = (INSTANCEOF)i;
            dumpInstanceOf(out, io, depth);
            break;
        }
        case Constants.CHECKCAST: {
            CHECKCAST cc = (CHECKCAST)i;
            dumpCheckCast(out, method, code, pos, cc, depth);
            break;
        }

        case Constants.IFNONNULL: case Constants.IFNE:
        case Constants.IF_ACMPNE: case Constants.IF_ICMPNE:
        case Constants.IFNULL: case Constants.IFEQ:
        case Constants.IF_ACMPEQ: case Constants.IF_ICMPEQ:
        case Constants.IFGE: case Constants.IF_ICMPGE:
        case Constants.IFLT: case Constants.IF_ICMPLT:
        case Constants.IFGT: case Constants.IF_ICMPGT:
        case Constants.IFLE: case Constants.IF_ICMPLE: {
            BranchInstruction bi = (BranchInstruction)i;
            out.print("\tif ("+getComparison(i, depth)+") "+
                      "goto L"+(pos + bi.getIndex())+";");
            break;
        }

        case Constants.GOTO:
        case Constants.GOTO_W: {
            GotoInstruction gi = (GotoInstruction)i;
            out.print("\tgoto L"+(pos + gi.getIndex())+";");
            break;
        }

        case Constants.TABLESWITCH: case Constants.LOOKUPSWITCH: {
            Select sel = (Select)i;
            dumpSwitch(out, pos, sel, depth);
            break;
        }

        case Constants.RETURN:
            dumpSyncReturn(out, method, code, pos);
            out.print("\treturn;");
            break;
        case Constants.ARETURN: case Constants.IRETURN: case Constants.FRETURN:
            out.println("\t{ int32_t a = "+s(depth)+";");
            dumpSyncReturn(out, method, code, pos);
            out.print("\treturn "+s(depth)+"; }");
            break;
        case Constants.LRETURN: case Constants.DRETURN:
            out.println("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";");
            dumpSyncReturn(out, method, code, pos);
            out.print("\treturn a; }");
            break;

        case Constants.NEW: {
            NEW n = (NEW)i;
            dumpNew(out, method, code, pos, n.getType(constPool), s(depth+1));
            break;                
        }
        case Constants.ANEWARRAY: case Constants.NEWARRAY: {
            Type type;
            if (i.getOpcode() == Constants.NEWARRAY) {
                type = ((NEWARRAY)i).getType();
            } else {
                ANEWARRAY an = (ANEWARRAY)i;
                type = Type.getType("["+an.getType(constPool).getSignature());
            }
            out.println("\t{ int32_t z_0 = "+s(depth)+";");
            dumpNewArray(out, method, code, pos, type, "z_0", s(depth));
            out.print(" }");
            break;                
        }
        case Constants.MULTIANEWARRAY: {
            MULTIANEWARRAY mn = (MULTIANEWARRAY)i;
            int dim = mn.getDimensions();
            String sig = mn.getType(constPool).getSignature();
            out.println("\t{ int32_t z_0 = "+s(depth-dim+1)+";");
            dumpNewArray(out, method, code, pos, Type.getType(sig), "z_0", s(depth-dim+1));
            for (int k = 1; k < dim; k++) {
                out.println();
                sig = sig.substring(1);
                out.println("\tint32_t z_"+k+" = "+s(depth-dim+k+1)+";"+
                            " int32_t k_"+k+";"+
                            " for (k_"+k+" = 0; k_"+k+" < z_"+(k-1)+"; k_"+k+"++) {");
                dumpNewArray(out, method, code, pos, Type.getType(sig), "z_"+k, s(depth-dim+k+1));
                out.print(" jvm_arrstore(_int___obj_t, "+s(depth-dim+k)+", k_"+k+", "+s(depth-dim+k+1)+");");
            }
            for (int k = 0; k < dim; k++) {
                out.print(" }");
            }
            break;
        }

        case Constants.INVOKESTATIC: case Constants.INVOKEVIRTUAL:
        case Constants.INVOKESPECIAL: case Constants.INVOKEINTERFACE: {
            InvokeInstruction ii = (InvokeInstruction)i;
            dumpInvoke(out, method, code, pos, ii, depth);
            break;
        }

        case Constants.ATHROW: {
            ATHROW at = (ATHROW)i;
            if (depth != 0) {
                out.print("\t"+s(0)+" = "+s(depth)+";");
            } else {
                out.print("\t");
            }
            dumpThrow(out, method, code, pos);
            break;
        }

        case Constants.MONITORENTER: {
            dumpNPE(out, method, code, pos, depth);
            dumpMonitorEnter(out, method, code, pos, depth);
            break;
        }
        case Constants.MONITOREXIT: {
            dumpNPE(out, method, code, pos, depth);
            dumpMonitorExit(out, method, code, pos, depth);
            break;
        }

        default:
            out.print("\tfprintf(stderr, \""+i.getName()+" not implemented\\n\");");
        }

        out.println("\t/* "+i.getName()+" */");
    }

    public void dumpNotFound(PrintWriter out, String type, String item) {
        String msg = type+" not found: "+item+" in "+getName();
        System.err.println(msg);
        out.print("\tfprintf(stderr, \""+msg+"\\n\");");
    }

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

    public void dumpGetField(PrintWriter out, Method method, Code code, int pos, GETFIELD gf, int depth) {
        String className = gf.getReferenceType(constPool).toString();
        ClassInfo ci = getClassInfo(className);
        if (ci == null) {
            dumpNotFound(out, "Class", className);
            return;
        }
        String fieldName = gf.getFieldName(constPool);
        int fieldIdx = ci.getFieldIndex(fieldName);
        if (gf.getFieldType(constPool).getSize() == 1) {
            dumpNPE(out, method, code, pos, depth);
            out.print("\t"+s(depth)+" = jvm_getfield("+ci.getCObjTypeName()+", "+s(depth)+", "+fieldIdx+", "+getCFieldName(fieldName)+");");
        } else {
            dumpNPE(out, method, code, pos, depth);
            out.print("\t{ int64_t a = jvm_getfield2("+ci.getCObjTypeName()+", "+s(depth)+", "+fieldIdx+", "+getCFieldName(fieldName)+");"+
                      " "+s(depth)+" = (int32_t)a;"+
                      " "+s(depth+1)+" = (int32_t)(a >> 32); }");
        }
    }

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
            out.print("\tjvm_putfield("+ci.getCObjTypeName()+", "+s(depth-1)+", "+fieldIdx+", "+getCFieldName(fieldName)+", "+s(depth)+");");
        } else {
            dumpNPE(out, method, code, pos, depth-2);
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " jvm_putfield2("+ci.getCObjTypeName()+", "+s(depth-2)+", "+fieldIdx+", "+getCFieldName(fieldName)+", a); }");
        }
    }

    public void dumpGetStatic(PrintWriter out, Method method, Code code, int pos, GETSTATIC gs, int depth) {
        String className = gs.getReferenceType(constPool).toString();
        ClassInfo ci = getClassInfo(className);
        String fieldName = gs.getFieldName(constPool);
        ci = findFieldDeclarator(ci, fieldName);
        if (ci == null) {
            dumpNotFound(out, "Static field", className+"."+fieldName);
            return;
        }
        if (gs.getFieldType(constPool).getSize() == 1) {
            out.print("\t"+s(depth+1)+" = "+ci.getCName()+"_"+getCFieldName(fieldName)+";");
        } else {
            out.print("\t{ int64_t a = "+ci.getCName()+"_"+getCFieldName(fieldName)+";"+
                      " "+s(depth+1)+" = (int32_t)a;"+
                      " "+s(depth+2)+" = (int32_t)(a >> 32); }");
        }
    }

    public void dumpPutStatic(PrintWriter out, Method method, Code code, int pos, PUTSTATIC ps, int depth) {
        String className = ps.getReferenceType(constPool).toString();
        ClassInfo ci = getClassInfo(className);
        String fieldName = ps.getFieldName(constPool);
        ci = findFieldDeclarator(ci, fieldName);
        if (ci == null) {
            dumpNotFound(out, "Static field", className+"."+fieldName);
            return;
        }
        if (ps.getFieldType(constPool).getSize() == 1) {
            out.print("\t"+ci.getCName()+"_"+getCFieldName(fieldName)+" = "+s(depth)+";");
        } else {
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " "+ci.getCName()+"_"+getCFieldName(fieldName)+" = a; }");
        }
    }

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

    public void dumpSwitch(PrintWriter out, int pos, Select sel, int depth) {
        int [] matchs = sel.getMatchs();
        int [] indices = sel.getIndices();
        out.println("\tswitch("+s(depth)+") {");
        for (int k = 0; k < matchs.length; k++) {
            out.println("\tcase "+matchs[k]+"UL: goto L"+(pos+indices[k])+";");
        }
        out.print("\tdefault: goto L"+(pos+sel.getIndex())+"; }");
    }

    public void dumpNew(PrintWriter out, Method method, Code code, int pos, Type type, String dstVal) {
        ClassInfo ci = getClassInfo(type.toString());
        if (ci == null) {
            dumpNotFound(out, "Class", type.toString());
            return;
        }
        out.println("\t"+dstVal+" = (int32_t)jvm_alloc(&"+ci.getCName()+", sizeof("+ci.getCObjTypeName()+"), &exc);");
        out.print("\tif (unlikely(exc != 0)) { "+s(0)+" = exc; exc = 0;");
        dumpThrow(out, method, code, pos);
        out.print(" }");
    }

    public void dumpNewArray(PrintWriter out, Method method, Code code, int pos, Type type, String sizeVal, String dstVal) {
        int size;
        switch (type.getSignature()) {
        case "[Z": case "[B": size = 1; break;
        case "[C": case "[S": size = 2; break;
        case "[J": case "[D": size = 8; break;
        default: size = 4;
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

    public void dumpInvoke(PrintWriter out, Method method, Code code, int pos, InvokeInstruction ii, int depth) {
        int opcode = ii.getOpcode();

        String name = ii.getMethodName(constPool);
        String signature = ii.getSignature(constPool);
        String methName = escapeName(name+signature);        

        String className = ii.getReferenceType(constPool).toString();
        ClassInfo ci = getClassInfo(className);
        String fqName = ci.getName()+"."+name+signature;
        String typeName;
        if (opcode == Constants.INVOKESTATIC
            || opcode == Constants.INVOKESPECIAL
            || opcode == Constants.INVOKEINTERFACE
            || (opcode == Constants.INVOKEVIRTUAL
                && !getVirtualMethods().contains(fqName))) {
            ci = findMethodDeclarator(ci, name, signature);
            if (ci == null) {
                dumpNotFound(out, "Method", className+"."+name+signature);
                return;
            }
            typeName = ci.getCName();
        } else {
            if (ci == null) {
                dumpNotFound(out, "Method", className+"."+name+signature);
                return;
            }
            typeName = ci.getCObjTypeName();
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
        if (retSize > 0) {
            if (retSize == 2) {
                out.print("{ int64_t a = ");
            } else {
                out.print(s(depth-argCount+1)+" = ");
            }
        }
        if (opcode == Constants.INVOKESTATIC
            || opcode == Constants.INVOKESPECIAL
            || (opcode == Constants.INVOKEVIRTUAL
                && !getVirtualMethods().contains(fqName))) {
            out.print(typeName+"_"+methName+"(");
        } else if (opcode == Constants.INVOKEINTERFACE) {
            out.print("(("+ci.getCObjTypeName()+"*)"+s(depth-argCount+1)+")->type->imtab->"+typeName+"_"+methName+"("); 
        } else {
            out.print("(("+typeName+"*)"+s(depth-argCount+1)+")->type->"+methName+"(");
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

    public void dumpNPE(PrintWriter out, Method method, Code code, int pos, int depth) {
        out.print("\tif (unlikely("+s(depth)+" == 0)) { "+s(0)+" = (int32_t)&npExc;");
        dumpThrow(out, method, code, pos);
        out.println(" }");
    }

    public void dumpABE(PrintWriter out, Method method, Code code, int pos, int depth, int idxdepth, String type) {
        out.print("\tif (unlikely("+s(idxdepth)+" < 0 ||"+
                  " "+s(idxdepth)+" >= jvm_arrlength("+type+", "+s(depth)+")))"+
                  " { "+s(0)+" = (int32_t)&abExc;");
        dumpThrow(out, method, code, pos);
        out.println(" }");
    }

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
                            continue;
                        }
                        out.println();
                        out.print("\tif (jvm_instanceof((("+objci.getCObjTypeName()+"*)"+s(0)+")->type, ("+objci.getCClassTypeName()+"*)&"+ci.getCName()+"))"+
                                  " goto L"+handler+";");
                    }
                }
            }
        }
        if (!caught) {
            Type retType = method.getReturnType();
            out.println();
            out.println("\t*retexc = "+s(0)+";");
            dumpSyncReturn(out, method, code, pos);
            out.print("\treturn"+(retType != Type.VOID ? " 0" : "")+";");
        }
    }

    public void dumpMonitorEnter(PrintWriter out, Method method, Code code, int pos, int depth) {
        ClassInfo ci = getClassInfo("java.lang.Object");
        out.print("\t{ int r = jvm_lock(("+ci.getCObjTypeName()+" *)"+s(depth)+");");
        out.println(" if (unlikely(r)) jvm_catch((int32_t)&vmErr); }");
    }

    public void dumpMonitorExit(PrintWriter out, Method method, Code code, int pos, int depth) {
        ClassInfo ci = getClassInfo("java.lang.Object");
        out.print("\t{ int r = jvm_unlock(("+ci.getCObjTypeName()+" *)"+s(depth)+");");
        out.println(" if (unlikely(r)) jvm_catch((int32_t)&vmErr); }");
    }

    public void dumpSyncEnter(PrintWriter out, Method method, Code code, int pos) {
        if (method.isSynchronized()) {
            if (method.isStatic()) {
                out.println("\t"+s(0)+" = (int32_t)&"+getCName()+";");
            } else {
                out.println("\t"+s(0)+" = "+v(0)+";");
            }
            dumpMonitorEnter(out, method, code, pos, 0);
        }
    }
    public void dumpSyncReturn(PrintWriter out, Method method, Code code, int pos) {
        if (method.isSynchronized()) {            
            if (method.isStatic()) {
                out.println("\t"+s(0)+" = (int32_t)&"+getCName()+";");
            } else {
                out.println("\t"+s(0)+" = "+v(0)+";");
            }
            dumpMonitorExit(out, method, code, pos, 0);
        }
    }

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
