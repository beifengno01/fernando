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
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * A class to compute the transitive hull of all classes, including
 * pseudo-classes for arrays and primitive types.
 */
public class Hull extends org.apache.bcel.classfile.EmptyVisitor {
    /** The queue of classes to be visited */
    private Queue<String> queue = new LinkedList<String>();
    /** The set of classes that has been visited */
    private Set<String> seen = new HashSet<String>();

    /** The class being currently visited */
    private JavaClass currentClass;

    /** The map between class names and classes */
    private Map<String, JavaClass> map = new LinkedHashMap<String, JavaClass>();

    /**
     * Add a class to the transitive hull.
     * @param className The name of the class to be added
     */
    public void add(String className) {
        String classDotName = className.replace('/', '.');
        if (!queue.contains(classDotName) &&
            !seen.contains(classDotName)) {
            queue.add(classDotName);
        }
    }

    /**
     * Get the dimension of a raw array name.
     * @param name The raw array name
     */
    private static int getDim(String name) {
        return name.lastIndexOf('[')+1;
    }

    /**
     * Get the name of the base type from a raw array name; dimensions
     * are stripped, and tags for primitive types are converted to
     * names.
     * @param name The raw array name
     * @return The name of the base class
     */
    private static String baseName(String name) {
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

    /**
     * Check whether the class name is the name of a primitive type.
     * @param name The name of the class
     * @return true if the name is a primitive type, false otherwise
     */
    private static boolean isPrimitiveName(String name) {
        boolean isPrimitive = false;
        switch (name) {
        case "boolean":
        case "byte":
        case "char":
        case "short":
        case "int":
        case "long":
        case "float":
        case "double":
            isPrimitive = true;
            break;
        default:
            isPrimitive = false;
        }
        return isPrimitive;
    }

    /**
     * Convert a tag to the name of a primitive type
     * @param tag The tag
     * @return The name of the primitive type
     */
    private static String getPrimitiveName(String tag) {
        String name = null;
        switch (tag) {
        case "Z":
            name = "boolean";
            break;
        case "B":
            name = "byte";
            break;
        case "C":
            name = "char";
            break;
        case "S":
            name = "short";
            break;
        case "I":
            name = "int";
            break;
        case "J":
            name = "long";
            break;
        case "F":
            name = "float";
            break;
        case "D":
            name = "double";
            break;
        default:
            throw new IllegalArgumentException("Unknown primitive tag "+tag);
        }
        return name;
    }
    
    /**
     * Generate a pseudo-class for a primitive type.
     * @param name The name of the primitive type
     * @return The generated pseudo-class
     */
    private JavaClass genPrimitiveClass(String name) {
        ClassGen cg = new ClassGen(name, "java.lang.Object", "",
                                   Constants.ACC_SUPER | 
                                   Constants.ACC_FINAL |
                                   Constants.ACC_SYNTHETIC, null);
        return cg.getJavaClass();
    }

    /**
     * Convert a raw array name to a more usable form.
     * @param name The raw array name
     * @return The converted array name
     */
    static String arrayName(String name) {
        int dim = getDim(name);
        String arrName = baseName(name);
        for (int i = 0; i < dim; i++) {
            arrName += "[]";
        }
        return arrName;
    }

    /**
     * Get the type of an array from a raw array name.
     * @param name The raw array name
     * @return The type of the array
     */
    private static Type arrayType(String name) {
        if (!name.startsWith("[")) {
            return Type.VOID;
        }
        int dim = name.lastIndexOf('[');
        Type type = Type.OBJECT;
        if (dim == 0) {
            switch (name.charAt(1)) {
            case 'L':
                type = Type.OBJECT;
                break;
            case 'Z':
                type = Type.BOOLEAN;
                break;
            case 'B':
                type = Type.BYTE;
                break;
            case 'C':
                type = Type.CHAR;
                break;
            case 'S':
                type = Type.SHORT;
                break;
            case 'I':
                type = Type.INT;
                break;
            case 'J':
                type = Type.LONG;
                break;
            case 'F':
                type = Type.FLOAT;
                break;
            case 'D':
                type = Type.DOUBLE;
                break;
            default:
                throw new IllegalArgumentException("Unknown array tag "+name.charAt(dim));
            }
        }
        return type;
    }
    
    /**
     * Add pseudo-classes for arrays of interfaces.
     * @param cg The array class
     * @param baseName The name of the base type
     * @param dim The dimension of the array
     * @throws ClassNotFoundException if a needed class cannot be found
     */
    private void addArrayInterfaces(ClassGen cg, String baseName, int dim) throws ClassNotFoundException {
        JavaClass base = load(baseName);
        cg.isInterface(base.isInterface());
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
    }

    /**
     * Generate a pseudo-class for an array type.
     * @param name The name of the array type
     * @param type The type of the array data
     * @param baseName The name of the array data type
     * @param dim The dimension of the array
     * @return The pseudo-class for the array type
     * @throws ClassNotFoundException if a needed class cannot be found
     */
    private JavaClass genArrayClass(String name, Type type, String baseName, int dim) throws ClassNotFoundException {
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

    /**
     * Add pseudo-classes for all array dimensions.
     * @param name The raw array name
     * @return The base name of the array; dimensions are stripped,
     * and tags for primitive types are converted to names.
     * @throws ClassNotFoundException if a needed class cannot be found
     */
    private String resolveArray(String name) throws ClassNotFoundException {
        String n = name;
        while (n.startsWith("[")) {
            String arrayName = arrayName(n);
            Type arrayType = arrayType(n);
            String baseName = baseName(n);
            int dim = getDim(n);
            JavaClass clazz = genArrayClass(arrayName, arrayType, baseName, dim);
            map.put(arrayName, clazz);
            n = n.substring(1);
        }
        if (n.startsWith("L")) {
            n = n.substring(1, n.length()-1);
        } else {
            n = getPrimitiveName(n);
        }
        return n;
     }

    /**
     * Resolve the transitive hull, by recursively adding classes and
     * generating pseudo-classes for primitive types and arrays.
     * @return A map between class names and classes
     * @throws ClassNotFoundException if a referenced class cannot be found
     */
    public Map<String, JavaClass> resolve() throws ClassNotFoundException {
        while(!queue.isEmpty()) {
            String name = queue.remove();
            if (!seen.contains(name)) {
                seen.add(name);
                if (name.startsWith("[")) {
                    name = resolveArray(name);
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

    /**
     * Load a class from the repository
     * @param className The name of the class to be loaded
     * @return The loaded class
     * @throws ClassNotFoundException if the class cannot be found
     */
    private static JavaClass load(String className) throws ClassNotFoundException{
        JavaClass clazz = Repository.lookupClass(className);
        if (clazz == null) {
            throw new ClassNotFoundException("Error while loading class: " + className);
        }
        return clazz;
    }

    /**
     * Visit a constant class reference.
     * @param cc The constant class
     */
    public void visitConstantClass(ConstantClass cc) {
        String className = (String)cc.getConstantValue(currentClass.getConstantPool());
        add(className);
    }

    /**
     * Visit a code sequence.
     * @param code The code sequence
     */
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

    /**
     * Visit a constant field reference.
     * @param ref The field reference
     */
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

    /**
     * Visit a method reference.
     * @param ref The method reference
     */
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

    /**
     * Visit a (regular) method reference.
     * @param ref The method reference
     */
    public void visitConstantMethodref(ConstantMethodref ref) {
        visitMethod(ref);
    }
    /**
     * Visit an interface method reference.
     * @param ref The method reference
     */
    public void visitConstantInterfaceMethodref(ConstantInterfaceMethodref ref) {
        visitMethod(ref);
    }
}
