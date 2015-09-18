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
import org.apache.bcel.generic.*;

import java.util.Set;
import java.util.List;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A class to sort class initializers according to the order in which
 * their class initializers should be called.
 */
public class ClassInitOrder {

    /** A map between class names and classes. */
    private Map<String, ClassInfo> classInfoMap;

    /** A helper structure for traversing the object graph. */
    private Set<Method> visited = new LinkedHashSet<Method>();

    /**
     * Constructor.
     * @param classInfoMap A map between class names and classes for
     * all classes in the application.
     */
    public ClassInitOrder(Map<String, ClassInfo> classInfoMap) {
        this.classInfoMap = classInfoMap;
    }

    /**
     * Find a valid order for the class initializers.
     * @return A list of classes that is sorted according to the order
     * in which their class initializers should be called.
     */
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
                    updateOrder(order, depGraph, e.getKey());
                    break;
                }
            }
        }
        if (!depGraph.isEmpty()) {
            Logger.getGlobal().severe("Cyclic class initializer dependency!");
            for (Map.Entry<ClassInfo, Set<ClassInfo>> e : depGraph.entrySet()) {
                String msg = e.getKey().getName()+" depends on";
                for (ClassInfo d : e.getValue()) {
                    msg += " "+d.getName();
                }
                Logger.getGlobal().severe(msg);
                order.add(e.getKey());
            }
        }
        return order;
    }

    /**
     * A helper function to append a class to the order and remove it
     * from the dependency graph.
     * @param order The order of classes so far
     * @param depGraph The dependency graph
     * @param clazz The class to be appended to the order
     */
    private void updateOrder(List<ClassInfo> order, Map<ClassInfo, Set<ClassInfo>> depGraph, ClassInfo clazz) {
        order.add(clazz);
        for (Set<ClassInfo> d : depGraph.values()) {
            d.remove(clazz);
        }
        depGraph.remove(clazz);
    }

    /**
     * Find the dependencies of a class initializer.
     * @param clazz The class containing the class initializer to be analyzed
     * @return The set of classes that this class initializer depends on
     */
    public Set<ClassInfo> findDeps(ClassInfo clazz) {
        Set<ClassInfo> deps = new LinkedHashSet<ClassInfo>();
        Method clinit = clazz.findClinit();
        if (clinit != null) {
            deps.addAll(findDeps(clazz, clinit));
        }
        return deps;
    }

    /**
     * Find the dependencies of a class, starting at a particular method.
     * @param clazz The class to be analyzed
     * @param m The method to be analyzed
     * @return The set of classes that the current class depends on
     */
    private Set<ClassInfo> findDeps(ClassInfo clazz, Method m) {
        Set<ClassInfo> deps = new LinkedHashSet<ClassInfo>();
        visited.add(m);

        Code code = m.getCode();
        InstructionList il = new InstructionList(code.getCode());
        ConstantPoolGen constPool = clazz.getConstPool();
        for (Instruction i : il.getInstructions()) {
            if (i instanceof CPInstruction) {
                Type type = ((CPInstruction)i).getType(constPool);
                ClassInfo ci = ClassInfo.getClassInfo(type.toString());
                addDep(deps, clazz, ci);
            }
            if (i instanceof FieldOrMethod) {
                Type type = ((FieldOrMethod)i).getReferenceType(constPool);
                ClassInfo ci = ClassInfo.getClassInfo(type.toString());
                addDep(deps, clazz, ci);
                if (ci != null && i instanceof InvokeInstruction) {
                    InvokeInstruction ii = (InvokeInstruction)i;
                    String name = ii.getMethodName(constPool);
                    String signature = ii.getSignature(constPool);
                    deps.addAll(findInvokeDeps(clazz, ci, name, signature));
                }
            }
        }
        return deps;
    }

    /**
     * Find the dependencies incurred by a method invocation.
     * @param clazz The class to be analyzed
     * @param calledClass The class that is being called
     * @param name The name of the invoked method
     * @param name The signature of the invoked method     
     * @return The set of classes that the current class depends on
     */
    private Set<ClassInfo> findInvokeDeps(ClassInfo clazz, ClassInfo calledClazz, String name, String signature) {
        Set<ClassInfo> deps = new LinkedHashSet<ClassInfo>();
        for (Method m : calledClazz.getMethods()) {
            if (m.getName().equals(name) &&
                m.getSignature().equals(signature) &&
                !visited.contains(m) &&
                m.getCode() != null) {

                Set<ClassInfo> subDeps = findDeps(calledClazz, m);
                subDeps.remove(clazz);
                deps.addAll(subDeps);
            }
        }
        return deps;
    }

    /**
     * Add a dependency, skipping classes that are identical or do not
     * have a class initializer.
     * @param deps The set of dependencies
     * @param clazz The current class
     * @param depClazz The class the current class depends on
     */
    private void addDep(Set<ClassInfo> deps, ClassInfo clazz, ClassInfo depClazz) {
        if (depClazz != null && depClazz != clazz && depClazz.findClinit() != null) {
            deps.add(depClazz);
        }
    }
}