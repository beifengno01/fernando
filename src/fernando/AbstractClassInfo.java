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

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * {@link AbstractClassInfo} holds information about a class and the
 * application and implements functions to generate C code. The code
 * implemented in this class is independent from the details of the
 * code generation.
 */
public abstract class AbstractClassInfo {

    /** The application this class belongs to */
    final AppInfo app;

    /** The name of the class. */
    final String name;
    /** The BCEL representation of  the class. */
    final JavaClass clazz;
    /** The constant pool of the class. */
    final ConstantPoolGen constPool;

    /** A helper structure to memoize the instance methods and the
     * classes that implement them. */
    private List<Map.Entry<Method, AbstractClassInfo>> instanceMethods;

    /**
     * Constructor, only to be used internally.
     */
    AbstractClassInfo(AppInfo app, String name, JavaClass clazz) {
        this.app = app;
        this.name = name;
        this.clazz = clazz;
        this.constPool = new ConstantPoolGen(clazz.getConstantPool());
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
     * Get the super class of this class.
     * @return The super class of the class
     */
    public AbstractClassInfo getSuperClass() {
        AbstractClassInfo superClass = app.getClassInfo(clazz.getSuperclassName());
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
    public Set<AbstractClassInfo> getInterfaces() {
        Set<AbstractClassInfo> interfaces = new LinkedHashSet<AbstractClassInfo>();
        Deque<AbstractClassInfo> queue = new LinkedList<AbstractClassInfo>();
        queue.add(this);
        while (!queue.isEmpty()) {
            AbstractClassInfo ci = queue.remove();
            if (ci.clazz.isInterface()) {
                interfaces.add(ci);
            } else {
                AbstractClassInfo superClass = ci.getSuperClass();
                if (superClass != null) {
                    queue.add(superClass);
                }
            }
            for (String i : ci.clazz.getInterfaceNames()) {
                queue.add(app.getClassInfo(i));
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

        AbstractClassInfo superClass = getSuperClass();
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
    public AbstractClassInfo findFieldDeclarator(AbstractClassInfo base, String name) {
        AbstractClassInfo b = base;
        while (b != null && !b.declaresField(name)) {
            for (AbstractClassInfo c : b.getInterfaces()) {
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
    public AbstractClassInfo findMethodDeclarator(AbstractClassInfo base, String name, String signature) {
        AbstractClassInfo b = base;
        while (b != null && !b.declaresMethod(name, signature)) {
            for (AbstractClassInfo c : b.getInterfaces()) {
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

    private void addMethodToList(List<Map.Entry<Method, AbstractClassInfo>> list, Method m, AbstractClassInfo ci, boolean replace) {
        boolean override = false;
        Map.Entry<Method, AbstractClassInfo> entry = new AbstractMap.SimpleImmutableEntry<Method, AbstractClassInfo>(m, ci);

        for (int i = 0; i < list.size() && !override; i++) {
            Map.Entry<Method, AbstractClassInfo> oldEntry = list.get(i);
            Method o = oldEntry.getKey();
            if (methodsEqual(m, o)) {
                if (replace) {
                    list.set(i, entry);
                }
                override = true;

                app.addVirtualMethod(oldEntry.getValue(), ci, m);
            }
        }
        if (!override) {
            list.add(entry);
        }
    }

    /**
     * Compute the instance methods of a class and store them in {@link instanceMethods}.
     */
    void computeInstanceMethods() {
        instanceMethods = new LinkedList<Map.Entry<Method, AbstractClassInfo>>();
        AbstractClassInfo superClass = getSuperClass();
        if (superClass != null) {
            List<Map.Entry<Method, AbstractClassInfo>> supers = superClass.getInstanceMethods();
            for (Map.Entry<Method, AbstractClassInfo> e : supers) {
                addMethodToList(instanceMethods, e.getKey(), e.getValue(), true);
            }
        }
        for (Method m : Filter.instances(getMethods())) {
            if (!m.isPrivate()) {
                addMethodToList(instanceMethods, m, this, true);
            }
        }

        for (AbstractClassInfo i : getInterfaces()) {
            for (Method m : i.getMethods()) {
                addMethodToList(instanceMethods, m, i, false);
            }
        }
    }

    /**
     * Get the instance methods of this class.
     * @return The list of methods and the classes that implement them
     */
    public List<Map.Entry<Method, AbstractClassInfo>> getInstanceMethods() {
        if (instanceMethods == null) {
            computeInstanceMethods();
        }
        return instanceMethods;
    }

    /**
     * Compare whether two methods are equal according to their name and signature.
     * @return true if the methods are equals, false otherwise
     */
    public static boolean methodsEqual(Method a, Method b) {
        return a.getName().equals(b.getName()) &&
            a.getSignature().equals(b.getSignature());
    }

    private boolean implementsMethodsOf(AbstractClassInfo ci) {
        for (Method m : ci.getMethods()) {
            for (Map.Entry<Method, AbstractClassInfo> e : getInstanceMethods()) {
                if (methodsEqual(m, e.getKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Compute whether the class requires an interface table.
     * @return true if the current class requires an interface table, false otherwise
     */
    public boolean needsInterfaceTable() throws ClassNotFoundException {
        if (clazz.isInterface() || clazz.isAbstract() || getInterfaces().isEmpty()) {
            return false;
        }
        for (AbstractClassInfo i : app.getInterfaceList()) {
            if (clazz.implementationOf(i.clazz) && implementsMethodsOf(i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generate the list of arguments for a method.
     * @param out The file to write to
     * @param m The method the code is generated for
     */
    public abstract void dumpArgList(PrintWriter out, Method m);
 
    /**
     * Generate the declarations for this class.
     * @param out The file to write to
     */
    public void dumpDefs(PrintWriter out, Set<AbstractClassInfo> dumped) {
        AbstractClassInfo superClass = getSuperClass();
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
    public abstract void dumpClassDef(PrintWriter out);

    /**
     * Generate the C declarations for the static fields.
     * @param out The file to write to
     */
    public abstract void dumpStaticFieldDefs(PrintWriter out);

    /**
     * Generate the C declarations for the methods (prototypes).
     * @param out The file to write to
     */
    public abstract void dumpMethodDefs(PrintWriter out);
        
    /**
     * Generate the C declaration for the object structure.
     * @param out The file to write to
     */
    public abstract void dumpObjectDef(PrintWriter out);

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
     * Generate the C definitions of the static fields.
     * @param out The file to write to
     */
    public abstract void dumpStaticFields(PrintWriter out, Map<String, Integer> stringPool);
 
    /**
     * Generate the C implementations of the methods.
     * @param out The file to write to
     */
    public abstract void dumpMethodBodies(PrintWriter out, Map<String, Integer> stringPool);

    /**
     * Generate the C definition of the interface method table for this class.
     * @param out The file to write to
     */
    public abstract void dumpIfaceMethTab(PrintWriter out) throws ClassNotFoundException;

    /**
     * Generate the C definition of the class structure.
     * @param out The file to write to
     */
    public abstract void dumpClassBody(PrintWriter out, Map<String, Integer> stringPool) throws ClassNotFoundException;
}
