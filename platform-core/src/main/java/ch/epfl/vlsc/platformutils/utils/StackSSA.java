package ch.epfl.vlsc.platformutils.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * @author Gareth Callanan
 * <p>
 * Create a stack keeping track of the SSA variable names used during MLIR generation. This allows for unique
 * operand names to be generated each time a variable is updated.
 * <p>
 * Each element on the stack contains a map \<variable names, integers\>. The map key represents the variable name
 * and the integer represents the current assignment to this variable. The integer needs to increment every time the
 * variable is assigned to in order to respect the SSA rules.
 * <p>
 * NOTE: I am not sure if this is the best implementation but I am rolling with this now.
 */
public class StackSSA {
    Stack<Map<String, Integer>> stack;
    int tempVarIndex;

    public StackSSA() {
        stack = new Stack<>();
        tempVarIndex = 0;
    }

    /**
     * Call this function when we enter a new block and want a new element to be added to the stack. Call @ref blockDone
     * when exiting the block.
     */
    public void newBlock() {
        stack.push(new HashMap<>());
    }

    /**
     * Call this function when we exit a new block and want to remove the top element of the stack. Call @ref newBlock
     * when exiting the block.
     */
    public void blockDone() {
        stack.pop();
    }

    /**
     * Get the variable name in an instance when something is being assigned to this variable. This causes a new
     * operand name to be generated, eg: If getVarToBeAssignedTo("var1") is called then var1_0 is returned on the
     * first call, var1_1 on the second and so forth. This prevents multiple assignments to a single operator which
     * is not allowed in SSA.
     *
     * @param varName The name of the variable to be converted to an SSA operand: eg "varName"
     * @return The name of the variable with a new postfix attached to it: eg: "varName_1"
     */
    public String getVarToBeAssignedTo(String varName) {
        Map<String, Integer> topOfStack = stack.peek();
        topOfStack.putIfAbsent(varName, -1);
        int nextIncr = topOfStack.get(varName) + 1;
        topOfStack.put(varName, nextIncr);
        return varName + "_" + nextIncr;
    }

    /**
     * Get the var name of the last used instance of a given variable to be given as an argument into an MLIR
     * operation. The count does not increment in this call.
     *
     * @param varName
     * @return
     */
    public String getVarName(String varName) {
        if (stack.peek().get(varName) == null) {
            throw new Error("Variable does not exist in SSA stack.");
        }
        return varName + "_" + stack.peek().get(varName);
    }

    public String getNewTempVar() {
        return "tmp_" + tempVarIndex++;
    }

    @Override
    public String toString() {
        return stack.toString();
    }
}
