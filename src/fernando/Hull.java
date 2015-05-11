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
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantCP;
import org.apache.bcel.classfile.ConstantInterfaceMethodref;
import org.apache.bcel.classfile.ConstantFieldref;
import org.apache.bcel.classfile.ConstantMethodref;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.DescendingVisitor;
import org.apache.bcel.classfile.EmptyVisitor;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.ANEWARRAY;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;
import org.apache.bcel.util.InstructionFinder;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

// A class to compute the transitive hull of all classes
public class Hull extends EmptyVisitor {
    private LinkedList<String> queue = new LinkedList<String>();
    private Set<String> seen = new HashSet<String>();
    private Map<String, JavaClass> map = new LinkedHashMap<String, JavaClass>();

    private JavaClass currentClass;

    public void add(String className) {
        className = className.replace('/', '.');
        String baseName = baseName(className);
        if (!queue.contains(className) &&
            !seen.contains(className)) {
            // if (currentClass != null) {
            //     System.err.println(currentClass.getClassName()+" adds "+className);
            // } else {
            //     System.err.println("<null> adds "+className);
            // }
            queue.add(className);
        }
    }

    private static int getDim(String name) {
        return name.lastIndexOf('[')+1;
    }

    public static String baseName(String name) {
        if (!name.startsWith("[")) {
            return name;
        }
        int dim = getDim(name);
        if (name.charAt(dim) == 'L') {
            return name.substring(dim+1, name.length()-1);
        } else {
            return getPrimitiveName(name.substring(dim, dim+1));
        }
    }

    public static boolean isPrimitiveName(String name) {
        switch (name) {
        case "boolean":
        case "byte":
        case "char":
        case "short":
        case "int":
        case "long":
        case "float":
        case "double":
            return true;
        default:
            return false;
        }
    }

    public static String getPrimitiveName(String tag) {
        switch (tag) {
        case "Z": return "boolean";
        case "B": return "byte";
        case "C": return "char";
        case "S": return "short";
        case "I": return "int";
        case "J": return "long";
        case "F": return "float";
        case "D": return "double";
        default: return null;
        }
    }

    private JavaClass genPrimitiveClass(String name) {
        ClassGen cg = new ClassGen(name, "java.lang.Object", "",
                                   Constants.ACC_SUPER | 
                                   Constants.ACC_FINAL |
                                   Constants.ACC_SYNTHETIC, null);
        ConstantPoolGen cpg = cg.getConstantPool();
        return cg.getJavaClass();
    }

    public static String arrayName(String name) {
        int dim = getDim(name);
        name = baseName(name);
        for (int i = 0; i < dim; i++) {
            name += "[]";
        }
        return name;
    }

    private static Type arrayType(String name) {
        if (!name.startsWith("[")) {
            return Type.VOID;
        }
        int dim = name.lastIndexOf('[');
        if (dim == 0) {
            switch (name.charAt(1)) {
            case 'L': return Type.OBJECT;
            case 'Z': return Type.BOOLEAN;
            case 'B': return Type.BYTE;
            case 'C': return Type.CHAR;
            case 'S': return Type.SHORT;
            case 'I': return Type.INT;
            case 'J': return Type.LONG;
            case 'F': return Type.FLOAT;
            case 'D': return Type.DOUBLE;
            default:
                System.err.println("Unknown array tag "+name.charAt(dim));
                System.exit(-1);
            }
        }
        return Type.OBJECT;
    }
    
    private void addArrayInterfaces(ClassGen cg, String baseName, int dim) {
        JavaClass base = load(baseName);
        cg.isInterface(base.isInterface());
        try {
            for (JavaClass i : base.getAllInterfaces()) {
                if (!i.getClassName().equals(baseName)) {
                    String ifTagName = "";
                    for (int k = 0; k < dim; k++) {
                        ifTagName += "[";
                    }
                    ifTagName += "L"+i.getClassName()+";";
                    add(ifTagName);
                    
                    String ifName = i.getClassName();
                    for (int k = 0; k < dim; k++) {
                        ifName += "[]";
                    }
                    cg.addInterface(ifName);
                }
            }
        } catch (ClassNotFoundException exc) {
            System.err.println(exc);
            System.exit(-1);
        }
    }

    private JavaClass genArrayClass(String name, Type type, String baseName, int dim) {
        ClassGen cg = new ClassGen(name, "java.lang.Object", "",
                                   Constants.ACC_SUPER | 
                                   Constants.ACC_FINAL |
                                   Constants.ACC_SYNTHETIC, null);
        ConstantPoolGen cpg = cg.getConstantPool();

        FieldGen length = new FieldGen(Constants.ACC_PUBLIC |
                                       Constants.ACC_SYNTHETIC,
                                       Type.INT, "length", cpg);
        cg.addField(length.getField());

        FieldGen data = new FieldGen(Constants.ACC_PUBLIC |
                                     Constants.ACC_SYNTHETIC,
                                     type, "data[]", cpg);
        cg.addField(data.getField());

        if (!isPrimitiveName(baseName)) {
            addArrayInterfaces(cg, baseName, dim);
        }

        JavaClass clazz = cg.getJavaClass();
        Repository.getRepository().storeClass(clazz);
        return clazz;
    }

    public Map<String, JavaClass> resolve() {
        while(!queue.isEmpty()) {
            String name = queue.remove();
            if (!seen.contains(name)) {
                seen.add(name);
                if (name.startsWith("[")) {                    
                    while (name.startsWith("[")) {
                        String arrayName = arrayName(name);
                        Type arrayType = arrayType(name);
                        String baseName = baseName(name);
                        int dim = getDim(name);
                        JavaClass clazz = genArrayClass(arrayName, arrayType, baseName, dim);
                        map.put(arrayName, clazz);
                        name = name.substring(1);
                    }
                    if (name.startsWith("L")) {
                        name = name.substring(1, name.length()-1);
                    } else {
                        name = getPrimitiveName(name);
                    }
                }
                if (isPrimitiveName(name)) {
                    JavaClass clazz = genPrimitiveClass(name);
                    map.put(name, clazz);
                } else {
                    JavaClass clazz = load(name);
                    map.put(name, clazz);
                    currentClass = clazz;
                    new DescendingVisitor(clazz, this).visit();
                    currentClass = null;
                }
            }
        }
        return map;
    }

    private static JavaClass load(String className) {
        JavaClass clazz = null;
        try {
            clazz = Repository.lookupClass(className);
        } catch (ClassNotFoundException exc) {
            System.err.println(exc);
            System.exit(-1);
        }
        if (clazz == null) {
            System.err.println("Error while loading class: " + className);
            System.exit(-1);
        }
        return clazz;
    }

    public void visitConstantClass(ConstantClass cc) {
        String className = (String)cc.getConstantValue(currentClass.getConstantPool());
        add(className);
    }

    public void visitCode(Code code) {
        InstructionList il = new InstructionList(code.getCode());
        InstructionFinder finder = new InstructionFinder(il);
        Iterator<InstructionHandle[]> iter = finder.search("ANEWARRAY");
        while (iter.hasNext()) {
            InstructionHandle[] match = iter.next();
            ANEWARRAY an = (ANEWARRAY)match[0].getInstruction();
            ConstantPoolGen cpg = new ConstantPoolGen(currentClass.getConstantPool());
            add("["+an.getType(cpg).getSignature());
        }
    }

    public void visitConstantFieldref(ConstantFieldref ref) {
        ConstantPool cp = currentClass.getConstantPool();

        String className = ref.getClass(cp);
        add(className);

        ConstantNameAndType cnat =
            (ConstantNameAndType)(cp.getConstant(ref.getNameAndTypeIndex(),
                                                 Constants.CONSTANT_NameAndType));
        String signature = cnat.getSignature(cp);
            
        Type type = Type.getType(signature);
        if (type instanceof ObjectType) {
            add(((ObjectType)type).getClassName());
        }
    }

    private void visitMethod(ConstantCP ref) {
        ConstantPool cp = currentClass.getConstantPool();

        String className = ref.getClass(cp);
        add(className);

        ConstantNameAndType cnat =
            (ConstantNameAndType)(cp.getConstant(ref.getNameAndTypeIndex(),
                                                 Constants.CONSTANT_NameAndType));
        String signature = cnat.getSignature(cp);

        Type type = Type.getReturnType(signature);
        if (type instanceof ObjectType) {
            add(((ObjectType)type).getClassName());
        }
        Type [] types = Type.getArgumentTypes(signature);
        for (Type t : types) {
            if (t instanceof ObjectType) {
                add(((ObjectType)t).getClassName());
            }
        }
    }

    public void visitConstantMethodref(ConstantMethodref ref) {
        visitMethod(ref);
    }
    public void visitConstantInterfaceMethodref(ConstantInterfaceMethodref ref) {
        visitMethod(ref);
    }
}
