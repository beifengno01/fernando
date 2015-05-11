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

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.CPInstruction;
import org.apache.bcel.generic.FieldOrMethod;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.Type;

import java.util.Set;
import java.util.List;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class ClassInitOrder {

    private Map<String, ClassInfo> classInfoMap;
    public ClassInitOrder(Map<String, ClassInfo> classInfoMap) {
        this.classInfoMap = classInfoMap;
    }

    public List<ClassInfo> findOrder() {
        List<ClassInfo> order = new LinkedList<ClassInfo>();
        Map<ClassInfo, Set<ClassInfo>> depGraph = new LinkedHashMap<ClassInfo, Set<ClassInfo>>();
        for (ClassInfo ci : classInfoMap.values()) {
            if (ci.findClinit() != null) {
                depGraph.put(ci, findDeps(ci));
            }
        }
        for (int i = depGraph.size(); i > 0; --i) {
            for (Map.Entry<ClassInfo, Set<ClassInfo>> e : depGraph.entrySet()) {
                Set<ClassInfo> val = e.getValue();
                if (val.isEmpty()) {
                    ClassInfo key = e.getKey();
                    order.add(key);
                    for (Set<ClassInfo> d : depGraph.values()) {
                        d.remove(key);
                    }
                    depGraph.remove(key);
                    break;
                }
            }
        }
        if (!depGraph.isEmpty()) {
            System.err.println("Cyclic class initializer dependency!");
            for (Map.Entry<ClassInfo, Set<ClassInfo>> e : depGraph.entrySet()) {
                System.err.print(e.getKey().getName()+" depends on");
                for (ClassInfo d : e.getValue()) {
                    System.err.print(" "+d.getName());
                }
                System.err.println();
                order.add(e.getKey());
            }
        }
        return order;
    }

    public Set<ClassInfo> findDeps(ClassInfo clazz) {
        Set<ClassInfo> deps = new LinkedHashSet<ClassInfo>();
        Method clinit = clazz.findClinit();
        if (clinit != null) {
            deps.addAll(findDeps(clazz, clinit));
        }
        return deps;
    }

    private Set<Method> visited = new LinkedHashSet<Method>();

    public Set<ClassInfo> findDeps(ClassInfo clazz, Method m) {
        Set<ClassInfo> deps = new LinkedHashSet<ClassInfo>();
        visited.add(m);

        Code code = m.getCode();
        InstructionList il = new InstructionList(code.getCode());
        ConstantPoolGen constPool = clazz.getConstPool();
        for (Instruction i : il.getInstructions()) {
            if (i instanceof CPInstruction) {
                Type type = ((CPInstruction)i).getType(constPool);
                ClassInfo ci = ClassInfo.getClassInfo(type.toString());
                if (ci != null && ci != clazz && ci.findClinit() != null) {
                    deps.add(ci);
                }
            }
            if (i instanceof FieldOrMethod) {
                FieldOrMethod fom = (FieldOrMethod)i;
                Type type = fom.getReferenceType(constPool);
                ClassInfo ci = ClassInfo.getClassInfo(type.toString());
                if (ci != null && ci != clazz && ci.findClinit() != null) {
                    deps.add(ci);
                }
                if (ci != null && i instanceof InvokeInstruction) {
                    InvokeInstruction ii = (InvokeInstruction)i;
                    for (Method im : ci.getMethods()) {
                        if (im.getName().equals(ii.getMethodName(constPool)) &&
                            im.getSignature().equals(ii.getSignature(constPool)) &&
                            !visited.contains(im) &&
                            im.getCode() != null) {
                            Set<ClassInfo> subDeps = findDeps(ci, im);
                            for (ClassInfo d : subDeps) {
                                if (d != clazz) {
                                    deps.add(d);
                                }
                            }
                        }
                    }
                }
            }
        }
        return deps;
    }

}