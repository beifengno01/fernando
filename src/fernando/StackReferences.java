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
import org.apache.bcel.generic.*;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class StackReferences {

    private final Map<Integer, Deque<Boolean>> refMap = new HashMap<Integer, Deque<Boolean>>();

    public Deque<Boolean> get(int pos) {
        return refMap.get(pos);
    }

    private int findUndefinedMapPos(InstructionList il) {
        for (int pos : il.getInstructionPositions()) {
            if (!refMap.containsKey(pos)) {
                return pos;
            }
        }
        return -1;
    }

    public StackReferences(InstructionList il, ConstantPoolGen constPool) {
        boolean initial = true;
        Queue<Integer> queue = new LinkedList<Integer>();
        int pos = findUndefinedMapPos(il);

        while (pos >= 0) {
            refMap.put(pos, new LinkedList<Boolean>());
            if (!initial) {
                refMap.get(pos).push(true);
            } else {
                initial = false;
            }
            queue.add(pos);

            while (!queue.isEmpty()) {
                pos = queue.remove();
                Instruction i = il.findHandle(pos).getInstruction();

                Deque<Boolean> stack = new LinkedList<Boolean>(refMap.get(pos));

                // System.out.println(pos+": "+i+" "+stack);

                if (i instanceof ALOAD ||
                    i instanceof ACONST_NULL ||
                    i instanceof CHECKCAST ||
                    i instanceof NEW || i instanceof NEWARRAY ||
                    i instanceof ANEWARRAY || i instanceof MULTIANEWARRAY) {
                    for (int k = 0; k < i.consumeStack(constPool); k++) { 
                        stack.pop();
                    }
                    stack.push(true);
                } else if (i instanceof LDC || i instanceof LDC_W) {
                    CPInstruction cpi = (CPInstruction)i;
                    stack.push(cpi.getType(constPool) instanceof ReferenceType);
                } else if (i instanceof GETFIELD ||
                           i instanceof GETSTATIC) {
                    FieldInstruction fi = (FieldInstruction)i;
                    if (i instanceof GETFIELD) { 
                        stack.pop();
                    }
                    stack.push(fi.getFieldType(constPool) instanceof ReferenceType);
                } else if (i instanceof AALOAD) {
                    stack.pop();
                    stack.pop();
                    stack.push(true);
                } else if (i instanceof InvokeInstruction) {
                    InvokeInstruction ii = (InvokeInstruction)i;
                    for (int k = 0; k < i.consumeStack(constPool); k++) { 
                        stack.pop();
                    }
                    if (ii.getReturnType(constPool) instanceof ReferenceType) {
                        stack.push(true);
                    } else {
                        for (int k = 0; k < i.produceStack(constPool); k++) {
                            stack.push(false);
                        }
                    }
                } else if (i instanceof DUP) {
                    Boolean b = stack.pop();
                    stack.push(b);
                    stack.push(b);
                } else if (i instanceof DUP_X1) {
                    Boolean b1 = stack.pop();
                    Boolean b2 = stack.pop();
                    stack.push(b1);
                    stack.push(b2);
                    stack.push(b1);
                } else if (i instanceof DUP_X2) {
                    Boolean b1 = stack.pop();
                    Boolean b2 = stack.pop();
                    Boolean b3 = stack.pop();
                    stack.push(b1);
                    stack.push(b3);
                    stack.push(b2);
                    stack.push(b1);
                } else if (i instanceof DUP2) {
                    Boolean b1 = stack.pop();
                    Boolean b2 = stack.pop();
                    stack.push(b2);
                    stack.push(b1);
                    stack.push(b2);
                    stack.push(b1);
                } else if (i instanceof DUP2_X1) {
                    Boolean b1 = stack.pop();
                    Boolean b2 = stack.pop();
                    Boolean b3 = stack.pop();
                    stack.push(b2);
                    stack.push(b1);
                    stack.push(b3);
                    stack.push(b2);
                    stack.push(b1);
                } else if (i instanceof DUP2_X2) {
                    Boolean b1 = stack.pop();
                    Boolean b2 = stack.pop();
                    Boolean b3 = stack.pop();
                    Boolean b4 = stack.pop();
                    stack.push(b2);
                    stack.push(b1);
                    stack.push(b4);
                    stack.push(b3);
                    stack.push(b2);
                    stack.push(b1);
                } else if (i instanceof SWAP) {
                    Boolean b1 = stack.pop();
                    Boolean b2 = stack.pop();
                    stack.push(b1);
                    stack.push(b2);
                } else if (i instanceof ASTORE) {
                    // a minimal sanity check
                    boolean b = stack.pop();
                    if (!b) { System.err.println("ERROR"); }
                } else {
                    for (int k = 0; k < i.consumeStack(constPool); k++) {
                        stack.pop();
                    }
                    for (int k = 0; k < i.produceStack(constPool); k++) {
                        stack.push(false);
                    }
                }

                // System.out.println("=>"+stack);

                if (i instanceof Select) {
                    Select s = (Select)i;
                    for (int idx : s.getIndices()) {
                        Integer target = pos + idx;
                        if (!refMap.containsKey(target)) {
                            refMap.put(target, stack);
                            queue.add(target);
                        }
                    }
                } 
                if (i instanceof BranchInstruction) {
                    BranchInstruction bi = (BranchInstruction)i;
                    Integer target = pos + bi.getIndex();
                    if (!refMap.containsKey(target)) {
                        refMap.put(target, stack);
                        queue.add(target);
                    }
                }
                if (!(i instanceof ReturnInstruction ||
                      i instanceof UnconditionalBranch)) {
                    Integer next = pos + i.getLength();
                    if (!refMap.containsKey(next)) {
                        refMap.put(next, stack);
                        queue.add(next);
                    }
                }
            }

            pos = findUndefinedMapPos(il);
        }
    }
}