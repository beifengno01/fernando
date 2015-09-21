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

/**
 * This class implements low-level details of the target language,
 * such as name mangling and arithmetic operators.
 */
public class Lang {
    // hide default constructor
    private Lang() {
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
     * Get the name of a class to be used in the C code.
     * @param clazz The class whose name is to be converted
     * @return The name of the class to be used in the C code
     */
    public static String getName(AbstractClassInfo clazz) {
        return escapeName("_"+clazz.getName());
    }

    /**
     * Get the name of the C type that represents a class.
     * @param clazz The class whose name is to be converted
     * @return The name of the C type that represents the class
     */
    public static String getClassType(AbstractClassInfo clazz) {
        return getName(clazz) + "_class_t";
    }

    /**
     * Get the name of the C type that represents instances of a class.
     * @param clazz The class whose name is to be converted
     * @return The name of the C type that represents instances of the class
     */
    public static String getObjType(AbstractClassInfo clazz) {
        return getName(clazz) + "_obj_t";
    }

    /**
     * Get the name (and signature) of a method in a form that can be used in C.
     * @param name The name of the method to be converted
     * @param signature The signature of the method to be converted
     * @return The escaped name (and signature)
     */
    public static String getMethod(String name, String signature) {
        return escapeName(name+signature);
    }

    /**
     * Get the name (and signature) of a method in a form that can be used in C.
     * @param m The method whose name is to be converted
     * @return The escaped name (and signature)
     */
    public static String getMethod(Method m) {
        return getMethod(m.getName(), m.getSignature());
    }

    /**
     * Get the name of a field in a form that can be used in C.
     * @param name The field name
     * @return The escaped name
     */
    public static String getField(String name) {
        return name.replaceAll("[\\$]", "_");
    }

    /**
     * Get the name of the C type that represents a Java type.
     * @param type The Java type
     * @return The C type
     */
    public static String getType(Type type) {
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
     * Get the C operator for an arithmetic bytecode.
     * @param i The bytecode
     * @return The C operator
     */
    public static String getArithOp(Instruction i) {
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
    public static String getCondition(Instruction i) {
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
     * Get the C type to be used for an array bytecode.
     * @param app The application (for resolving the array class)
     * @param i The bytecode
     * @return The C type
     */
    public static String getArrayType(AppInfo app, Instruction i) {
        String type = null;
        switch(i.getOpcode()) {
        case Constants.AALOAD: case Constants.AASTORE:
            // this should return "_java_lang_Object___obj_t"
            type = getObjType(app.getClassInfo("int[]"));
            break;
        case Constants.IALOAD: case Constants.IASTORE:
            type = getObjType(app.getClassInfo("int[]"));
            break;
        case Constants.FALOAD: case Constants.FASTORE:
            type = getObjType(app.getClassInfo("float[]"));
            break;
        case Constants.CALOAD: case Constants.CASTORE:
            type = getObjType(app.getClassInfo("char[]"));
            break;
        case Constants.SALOAD: case Constants.SASTORE:
            type = getObjType(app.getClassInfo("short[]"));
            break;
        case Constants.BALOAD: case Constants.BASTORE:
            type = getObjType(app.getClassInfo("byte[]"));
            break;
        case Constants.LALOAD: case Constants.LASTORE:
            type = getObjType(app.getClassInfo("long[]"));
            break;
        case Constants.DALOAD: case Constants.DASTORE:
            type = getObjType(app.getClassInfo("double[]"));
            break;
        default:
            throw new IllegalArgumentException("Invalid array instruction: "+i);
        }
        return type;
    }
}