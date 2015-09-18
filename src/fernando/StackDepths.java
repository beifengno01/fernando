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

import org.apache.bcel.generic.*;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

/**
 * A simple data flow analysis to determine the depth of the operand stack.
 */
public class StackDepths {

    /** A map between code positions and computed stack depths. */
    private final Map<Integer, Integer> depthMap = new HashMap<Integer, Integer>();

    /**
     * Create and run the analysis.
     * @param il The list of instructions to be analyzed
     * @param constPool The constant pool for the instructions
     */
    public StackDepths(InstructionList il, ConstantPoolGen constPool) {

        Queue<Integer> queue = new LinkedList<Integer>();
        int depth = -1;
        int pos = findUndefinedDepthPos(il);
        
        while (pos >= 0) {
            depthMap.put(pos, depth);
            queue.add(pos);

            while (!queue.isEmpty()) {
                pos = queue.remove();
                Instruction i = il.findHandle(pos).getInstruction();
                
                depth = depthMap.get(pos)
                    - i.consumeStack(constPool)
                    + i.produceStack(constPool);
                
                updateQueue(queue, pos, i, depth);
            }

            depth = 0;
            pos = findUndefinedDepthPos(il);
        }
    }

    /**
     * Store current analysis information and update the queue of positions to be analyzed.
     * @param queue The queue of positions to be analyzed
     * @param pos The current position in the code
     * @param i The current instruction
     * @param depth The computed stack depth
     */
    private void updateQueue(Queue<Integer> queue, int pos, Instruction i, int depth) {

        if (i instanceof Select) {
            Select s = (Select)i;
            for (int idx : s.getIndices()) {
                Integer target = pos + idx;
                if (!depthMap.containsKey(target)) {
                    depthMap.put(target, depth);
                    queue.add(target);
                }
            }
        } 
        if (i instanceof BranchInstruction) {
            BranchInstruction bi = (BranchInstruction)i;
            Integer target = pos + bi.getIndex();
            if (!depthMap.containsKey(target)) {
                depthMap.put(target, depth);
                queue.add(target);
            }
        }
        if (!(i instanceof ReturnInstruction ||
              i instanceof UnconditionalBranch)) {
            Integer next = pos + i.getLength();
            depthMap.put(next, depth);
            queue.add(next);
        }
    }

    /**
     * Find a position that has not been analyzed yet.
     * @return A position that has not yet been analyzed, -1 if all positions have been analyzed.
     */
    private int findUndefinedDepthPos(InstructionList il) {
        for (int pos : il.getInstructionPositions()) {
            if (!depthMap.containsKey(pos)) {
                return pos;
            }
        }
        return -1;
    }

    /**
     * Get the depth of the stack at a particular position in the code.
     * @param pos The position in the code
     * @return The depth of the stack
     */
    public int get(int pos) {
        return depthMap.get(pos);
    }
}