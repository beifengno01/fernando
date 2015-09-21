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

import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * {@link ClassInfo} holds information about a class and the
 * application and implements functions to generate C code.
 */
public class ClassInfo extends AbstractClassInfo {

    /**
     * Constructor, only to be used internally.
     */
    ClassInfo(AppInfo app, String name, JavaClass clazz) {
        super(app, name, clazz);
    }

    /**
     * Get the number of arguments for a C function.
     * @param types The types of the arguments
     * @param isStatic Whether the corresponding methid is static
     * @return The number of arguments for a C function
     */
    private int getArgCount(Type [] types, boolean isStatic) {
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
     * Get the C comparison for a comparison bytecode.
     * @param i The bytecode
     * @param depth The current stack depth
     * @return The C comparison
     */
    private String getComparison(Instruction i, int depth) {
        String comp = null;
        switch (i.getOpcode()) {
        case Constants.IFNONNULL: case Constants.IFNE:
        case Constants.IFNULL: case Constants.IFEQ:
        case Constants.IFGE: case Constants.IFLT:
        case Constants.IFGT: case Constants.IFLE:
            comp = s(depth)+" "+Lang.getCondition(i)+" 0";
            break;
        case Constants.IF_ACMPNE: case Constants.IF_ICMPNE:
        case Constants.IF_ACMPEQ: case Constants.IF_ICMPEQ:        
        case Constants.IF_ICMPGE: case Constants.IF_ICMPLT:
        case Constants.IF_ICMPGT: case Constants.IF_ICMPLE:
            comp = s(depth-1)+" "+Lang.getCondition(i)+" "+s(depth);
            break;
        default:
            throw new IllegalArgumentException("Invalid comparison instruction: "+i);
        }
        return comp;
    }

    /**
     * Generate the list of arguments for a method.
     * @param out The file to write to
     * @param m The method the code is generated for
     */
    public void dumpArgList(PrintWriter out, Method m) {
        int argCount = getArgCount(m.getArgumentTypes(), m.isStatic());
        out.print("(");
        for (int i = 0; i < argCount; i++) {
            out.print("int32_t "+v(i)+", ");
        }
        out.print("int32_t *restrict retexc)");
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
        if (!app.getInterfaceList().isEmpty()) {
            out.println("\tconst int32_t itab ["+((app.getInterfaceList().size()+31)/32)+"];");
            out.println("\tconst imtab_t * const imtab;");
        }

        dumpMethodPointerDefs(out);
 
        out.println("} "+Lang.getClassType(this)+";");
        out.println();

        out.println("/* forward declaration of class object */");
        out.println("extern "+Lang.getClassType(this)+" "+Lang.getName(this)+";");
        out.println();
    }

    /**
     * Generate the C declarations for the method pointers.
     * @param out The file to write to
     */
    public void dumpMethodPointerDefs(PrintWriter out) {
        out.println("\t/* method pointers */");
        for (Map.Entry<Method, AbstractClassInfo> e : getInstanceMethods()) {
            Method m = e.getKey();
            String fqName = e.getValue().getName()+"."+m.getName()+m.getSignature();
            if (app.getVirtualMethods().contains(fqName)
                && !("<init>".equals(m.getName())
                     && "()V".equals(m.getSignature()))) {
                out.print("\t"+Lang.getType(m.getReturnType())+
                          " (* const "+Lang.getMethod(m)+")");
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
                out.println("extern "+Lang.getType(f.getType())+
                            " "+Lang.getName(this)+"_"+Lang.getField(f.getName())+";");
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
            out.print(Lang.getType(m.getReturnType())+
                      " "+Lang.getName(this)+"_"+Lang.getMethod(m));
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
        out.println("\tconst "+Lang.getClassType(this)+" *type;");
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
            out.println(Lang.getType(f.getType())+" _"+fieldIdx+"_"+Lang.getField(f.getName())+";");
            fieldIdx += f.getType().getSize();
        }
        out.println("} "+Lang.getObjType(this)+";");
        out.println();
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
                out.print(Lang.getType(m.getReturnType())+
                          " "+Lang.getName(this)+"_"+Lang.getMethod(m));
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
        out.println("imtab_t "+Lang.getName(this)+"_imtab = {");
        for (AbstractClassInfo i : app.getInterfaceList()) {
            out.println("\t/* interface "+i.getName()+" */");
            for (Method m : i.getMethods()) {
                boolean found = false;
                if (clazz.implementationOf(i.clazz)) {
                    for (Map.Entry<Method, AbstractClassInfo> e : getInstanceMethods()) {
                        if (methodsEqual(m, e.getKey())) {
                            out.println("\t"+Lang.getName(e.getValue())+"_"+Lang.getMethod(m)+", ");
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
        String classClassPtr = "&"+Lang.getName(app.getClassInfo("java.lang.Class"));
        AbstractClassInfo superClass = getSuperClass();
        String superClassPtr = superClass != null ? "&"+Lang.getName(superClass) : "0";
        String elemType = "0";
        if (getName().endsWith("[]")) {
            String typeName = getName().substring(0, getName().length()-2);
            AbstractClassInfo ci = app.getClassInfo(typeName);
            if (ci == null) {
                Logger.getGlobal().severe("Class not found: "+typeName+" in "+getName());
            } else {
                elemType = "&"+Lang.getName(ci);
            }
        }
        if (!stringPool.containsKey(getName())) {
            stringPool.put(getName(), stringPool.size());
        }
        String namePtr = "&stringPool["+stringPool.get(getName())+"]";

        out.println(Lang.getClassType(this)+" "+Lang.getName(this)+" = {");
        out.println("\t/* header */");
        out.println("\t"+classClassPtr+", /* type */");
        out.println("\t0, /* lock */");
        out.println("\t0, /* wait */");
        out.println("\t"+superClassPtr+", /* super */");
        out.println("\t"+elemType+", /* elemtype */");
        out.println("\t"+namePtr+", /* name */");
        if (!app.getInterfaceList().isEmpty()) {
            out.println("\t/* interface table */");
            out.print("\t{ ");
            int buffer = 0;
            int bufferCount = 0;
            for (AbstractClassInfo i : app.getInterfaceList()) {
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
                out.println("\t&"+Lang.getName(this)+"_imtab,");
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
        for (Map.Entry<Method, AbstractClassInfo> e : getInstanceMethods()) {
            Method m = e.getKey();
            String fqName = e.getValue().getName()+"."+m.getName()+m.getSignature();
            if (app.getVirtualMethods().contains(fqName)
                && !("<init>".equals(m.getName())
                     && "()V".equals(m.getSignature()))) {
                String className = Lang.getName(e.getValue());
                String methName = Lang.getMethod(m);
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

            out.print(Lang.getType(f.getType())+
                      " "+Lang.getName(this)+"_"+Lang.getField(f.getName())+" = ");
            ConstantValue cv = f.getConstantValue();
            out.print("("+Lang.getType(f.getType())+")");
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
            out.print("\t"+s(depth-1)+" "+Lang.getArithOp(i)+"= "+s(depth)+";");
            break;
        case Constants.IDIV: case Constants.IREM:
            out.print("\tif (unlikely("+s(depth)+" == 0)) { "+s(0)+" = (int32_t)&aeExc;");
            dumpThrow(out, method, code, pos);
            out.print(" }"+
                      " "+s(depth-1)+" = (int64_t)"+s(depth-1)+" "+Lang.getArithOp(i)+" (int64_t)"+s(depth)+";");
            break;
        case Constants.ISHL: case Constants.ISHR:
            out.print("\t"+s(depth-1)+" "+Lang.getArithOp(i)+"= "+s(depth)+" & 0x1f;");
            break;
        case Constants.IUSHR:
            out.print("\t"+s(depth-1)+" = (uint32_t)"+s(depth-1)+" "+Lang.getArithOp(i)+" ("+s(depth)+" & 0x1f);");
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
                      " a "+Lang.getArithOp(i)+"= b;"+
                      " "+s(depth-3)+" = (int32_t)a;"+
                      " "+s(depth-2)+" = (int32_t)(a >> 32); }");
            break;

        case Constants.LDIV: case Constants.LREM:
            out.println("\t{ int64_t a = ((int64_t)"+s(depth-2)+" << 32) | (uint32_t)"+s(depth-3)+";"+
                        " int64_t b = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";");
            out.print("\tif (unlikely(b == 0)) { "+s(0)+" = (int32_t)&aeExc;");
            dumpThrow(out, method, code, pos);
            out.print(" }"+
                      " a "+Lang.getArithOp(i)+"= b;"+
                      " "+s(depth-3)+" = (int32_t)a;"+
                      " "+s(depth-2)+" = (int32_t)(a >> 32); }");
            break;
        case Constants.LSHL: case Constants.LSHR:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth-1)+" << 32) | (uint32_t)"+s(depth-2)+";"+
                      " a "+Lang.getArithOp(i)+"= "+s(depth)+" & 0x3f;"+
                      " "+s(depth-2)+" = (int32_t)a;"+
                      " "+s(depth-1)+" = (int32_t)(a >> 32); }");
            break;
        case Constants.LUSHR:
            out.print("\t{ int64_t a = ((int64_t)"+s(depth-1)+" << 32) | (uint32_t)"+s(depth-2)+";"+
                      " a = (uint64_t)a "+Lang.getArithOp(i)+" ("+s(depth)+" & 0x3f);"+
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
                      " *a "+Lang.getArithOp(i)+"= *b; }");
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
                      " *c "+Lang.getArithOp(i)+"= *d;"+
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
            dumpABE(out, method, code, pos, depth-1, depth, Lang.getArrayType(app, i));
            
            out.print("\t"+s(depth-1)+" = ");
            if (i.getOpcode() == Constants.AALOAD) {
                out.print("jvm_arrload_ref(");
            } else {
                out.print("jvm_arrload(");
            }
            out.print(Lang.getArrayType(app, i)+", "+s(depth-1)+", "+s(depth)+");");
            break;

        case Constants.LALOAD: case Constants.DALOAD:
            dumpNPE(out, method, code, pos, depth-1);
            dumpABE(out, method, code, pos, depth-1, depth, Lang.getArrayType(app, i));
            out.print("\t{ int64_t a = jvm_arrload_long("+Lang.getArrayType(app, i)+", "+s(depth-1)+", "+s(depth)+");"+
                      " "+s(depth-1)+" = (int32_t)a;"+
                      " "+s(depth)+" = (int32_t)(a >> 32); }");
            break;

        case Constants.AASTORE: case Constants.IASTORE: case Constants.FASTORE:
        case Constants.CASTORE: case Constants.SASTORE: case Constants.BASTORE:
            dumpNPE(out, method, code, pos, depth-2);
            dumpABE(out, method, code, pos, depth-2, depth-1, Lang.getArrayType(app, i));
            if (i.getOpcode() == Constants.AASTORE) {
                out.print("\tjvm_arrstore_ref(");
            } else {
                out.print("\tjvm_arrstore(");
            }
            out.print(Lang.getArrayType(app, i)+", "+s(depth-2)+", "+s(depth-1)+", "+s(depth)+");");
            break;

        case Constants.LASTORE: case Constants.DASTORE:
            dumpNPE(out, method, code, pos, depth-3);
            dumpABE(out, method, code, pos, depth-3, depth-2, Lang.getArrayType(app, i));
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " jvm_arrstore_long("+Lang.getArrayType(app, i)+", "+s(depth-3)+", "+s(depth-2)+", a); }");
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
            AbstractClassInfo ci = app.getClassInfo(Hull.arrayName(objVal.getClassName()));
            if (ci == null) {
                dumpNotFound(out, "Constant", objVal.toString());
            } else {
                out.print("\t"+s(depth+1)+" = (int32_t)&"+Lang.getName(ci)+";");
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
        AbstractClassInfo ci = app.getClassInfo(className);
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
            out.print(Lang.getObjType(ci)+", "+s(depth)+", "+fieldIdx+", "+Lang.getField(fieldName)+");");
        } else {
            out.print("\t{ int64_t a = jvm_getfield_long("+Lang.getObjType(ci)+", "+s(depth)+", "+fieldIdx+", "+Lang.getField(fieldName)+");"+
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
        AbstractClassInfo ci = app.getClassInfo(className);
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
            out.print(Lang.getObjType(ci)+", "+s(depth-1)+", "+fieldIdx+", "+Lang.getField(fieldName)+", "+s(depth)+");");
        } else {
            dumpNPE(out, method, code, pos, depth-2);
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " jvm_putfield_long("+Lang.getObjType(ci)+", "+s(depth-2)+", "+fieldIdx+", "+Lang.getField(fieldName)+", a); }");
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
        AbstractClassInfo ci = app.getClassInfo(className);
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
            out.print(Lang.getName(ci)+"_"+Lang.getField(fieldName)+");");
        } else {
            out.print("\t{ int64_t a = jvm_getstatic_long("+Lang.getName(ci)+"_"+Lang.getField(fieldName)+");"+
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
        AbstractClassInfo ci = app.getClassInfo(className);
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
            out.print(Lang.getName(ci)+"_"+Lang.getField(fieldName)+", "+s(depth)+");");
        } else {
            out.print("\t{ int64_t a = ((int64_t)"+s(depth)+" << 32) | (uint32_t)"+s(depth-1)+";"+
                      " jvm_putstatic_long("+Lang.getName(ci)+"_"+Lang.getField(fieldName)+", a); }");
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
        AbstractClassInfo ci = app.getClassInfo(className);
        AbstractClassInfo objci = app.getClassInfo("java.lang.Object");
        if (ci == null) {
            dumpNotFound(out, "Class", className);
            return;
        }
        if (ci.clazz.isInterface()) {
            int ifaceIdx = app.getInterfaceList().indexOf(ci);
            out.print("\t"+s(depth)+" = "+s(depth)+" == 0 ? 0 : ((("+Lang.getObjType(objci)+"*)"+s(depth)+")->type->itab["+(ifaceIdx / 32)+"] & "+(1 << (ifaceIdx % 32))+"UL) != 0;");
        } else {
            out.print("\t"+s(depth)+" = "+s(depth)+" == 0 ? 0 : jvm_instanceof((("+Lang.getObjType(objci)+"*)"+s(depth)+")->type, ("+Lang.getClassType(objci)+"*)&"+Lang.getName(ci)+");");
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
        AbstractClassInfo ci = app.getClassInfo(className);
        AbstractClassInfo objci = app.getClassInfo("java.lang.Object");
        if (ci == null) {
            dumpNotFound(out, "Class", className);
            return;
        }
        out.print("\tif (unlikely("+s(depth)+" != 0 &&");
        if (ci.clazz.isInterface()) {
            int ifaceIdx = app.getInterfaceList().indexOf(ci);
            out.print(" ((("+Lang.getObjType(objci)+"*)"+s(depth)+")->type->itab["+(ifaceIdx / 32)+"] & "+(1 << (ifaceIdx % 32))+"UL) == 0)");
        } else {
            out.print(" !jvm_instanceof((("+Lang.getObjType(objci)+"*)"+s(depth)+")->type, ("+Lang.getClassType(objci)+"*)&"+Lang.getName(ci)+")))");
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
        AbstractClassInfo ci = app.getClassInfo(type.toString());
        if (ci == null) {
            dumpNotFound(out, "Class", type.toString());
            return;
        }
        out.println("\t"+s(depth+1)+" = (int32_t)jvm_alloc(&"+Lang.getName(ci)+", sizeof("+Lang.getObjType(ci)+"), &exc);");
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

        AbstractClassInfo ci = app.getClassInfo(type.toString());
        if (ci == null) {
            dumpNotFound(out, "Class", type.toString());
            return;
        }
        String objType = Lang.getObjType(ci);

        out.println("\t"+dstVal+" = (int32_t)jvm_alloc(&"+Lang.getName(ci)+", sizeof("+objType+")+"+sizeVal+"*"+size+", &exc);");
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
        String escName = Lang.getMethod(methName, signature);        

        String className = ii.getReferenceType(constPool).toString();
        AbstractClassInfo ci = app.getClassInfo(className);
        String fqName = ci.getName()+"."+methName+signature;
        String typeName;
        if (opcode == Constants.INVOKEVIRTUAL
            && app.getVirtualMethods().contains(fqName)) {
            if (ci == null) {
                dumpNotFound(out, "Method", className+"."+methName+signature);
                return;
            }
            typeName = Lang.getObjType(ci);
        } else {
            ci = findMethodDeclarator(ci, methName, signature);
            if (ci == null) {
                dumpNotFound(out, "Method", className+"."+methName+signature);
                return;
            }
            typeName = Lang.getName(ci);
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
                && !app.getVirtualMethods().contains(fqName))) {
            out.print(typeName+"_"+escName+"(");
        } else if (opcode == Constants.INVOKEINTERFACE) {
            out.print("(("+Lang.getObjType(ci)+"*)"+s(depth-argCount+1)+")->type->imtab->"+typeName+"_"+escName+"("); 
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
                        AbstractClassInfo ci = app.getClassInfo(className);
                        AbstractClassInfo objci = app.getClassInfo("java.lang.Object");
                        if (ci == null) {
                            dumpNotFound(out, "Class", className);
                        } else {
                            out.println();
                            out.print("\tif (jvm_instanceof((("+Lang.getObjType(objci)+"*)"+s(0)+")->type, ("+Lang.getClassType(objci)+"*)&"+Lang.getName(ci)+"))"+
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
        AbstractClassInfo ci = app.getClassInfo("java.lang.Object");
        out.print("\t{ int r = jvm_lock(("+Lang.getObjType(ci)+" *)"+s(depth)+");");
        out.println(" if (unlikely(r)) jvm_catch((int32_t)&vmErr); }");
    }

    /**
     * Generate the C code for the MONITOREXIT bytecode
     * @param out The file to write to
     * @param depth The current stack depth
     */
    public void dumpMonitorExit(PrintWriter out, int depth) {
        AbstractClassInfo ci = app.getClassInfo("java.lang.Object");
        out.print("\t{ int r = jvm_unlock(("+Lang.getObjType(ci)+" *)"+s(depth)+");");
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
                out.println("\t"+s(0)+" = (int32_t)&"+Lang.getName(this)+";");
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
                out.println("\t"+s(0)+" = (int32_t)&"+Lang.getName(this)+";");
            } else {
                out.println("\t"+s(0)+" = "+v(0)+";");
            }
            dumpMonitorExit(out, 0);
        }
    }
}
