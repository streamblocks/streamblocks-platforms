package ch.epfl.vlsc.platformutils.utils;

import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

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
 * MLIR does not allow an operand to be assigned directly to a resul eg: %a = %b is not valid. To get around this,
 * This lass supports aliasing. That way instead of directly assigning, we alias %a to %b, and then whenever
 * a call to this class would return %a, we instead return %b. This gets around this issue.
 * <p>
 * NOTE: I am not sure if this is the best implementation but I am going with it for now.
 */
public class StackSSA {

    LinkedList<Map<String, Integer>> stack;
    Map<String, String> aliases;

    int tempVarIndex;
    int depth;

    public StackSSA() {
        stack = new LinkedList<>();
        aliases = new TreeMap<>();
        tempVarIndex = 0;
        depth = -1;
    }

    /**
     * Call this function when we enter a new block and want a new element to be added to the stack. Call @ref blockDone
     * when exiting the block.
     */
    public void newBlock() {
        stack.addLast(new TreeMap<>());
        depth++;
    }

    /**
     * Call this function when we exit a new block and want to remove the top element of the stack. Call @ref newBlock
     * when exiting the block.
     */
    public void blockDone() {
        // We need to remove the aliases so that they are not referenced in other contexts.
        Map<String, Integer> removedItems =  stack.removeLast();
        for (Map.Entry<String, Integer> entry: removedItems.entrySet()){
            String nameToRemove = entry.getKey() + "_d" + depth + "_" + entry.getValue();
            aliases.remove(nameToRemove);
        }
        depth--;
    }

    /**
     * Alias one SSA to another
     *
     * So instead of: %currentSSA = %alias, everytime we need to get %currentSSA from the stack, %alias is returned
     * instead
     *
     * @param alias
     * @param currentSSA
     */
    public void aliasSSA(String alias, String currentSSA){
        aliases.put(currentSSA,alias);
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
        Map<String, Integer> topOfStack = stack.getLast();
        topOfStack.putIfAbsent(varName, -1);
        int nextIncr = topOfStack.get(varName) + 1;
        topOfStack.put(varName, nextIncr);
        return varName + "_d" + depth + "_" + nextIncr;
    }

    /**
     * Get the var name of the last used instance of a given variable to be given as an argument into an MLIR
     * operation. The count does not increment in this call. If we do not find the assignment at the current depth,
     * we go back through the depth of the stack to ensure that we find the variable if it exists
     *
     * @param varName
     * @return
     */
    public String getVarName(String varName) {
        int currentDepth = depth;
        while (currentDepth >= 0) {
            Integer fromStack = this.stack.get(currentDepth).get(varName);
            //System.out.println(varName + " Depth " + currentDepth + " of " + depth + " retVal " + fromStack);
            if (fromStack != null && fromStack != -1) { // The -1 can occur when you are not supposed to get that variable as it is the result of a yield
                String ssaName = varName + "_d" + currentDepth + "_" + fromStack;
                String aliasSSA = aliases.get(ssaName);
                if(aliasSSA != null){
                    return aliasSSA;
                }else{
                    return ssaName;
                }
            }
            currentDepth--;
        }
        throw new Error("Variable " + varName + " does not exist in SSA stack.\n" + this.stack);
    }

    /**
     * When we assign to a variable on a return of a control flow construct use this instruction instead of @ref
     * getVarToBeAssignedTo to get its SSA name. This stops this particular SSA value being referenced within the
     * block, instead the SSA with the index before the one returned here is returned when @ref getVarName() is
     * called. On the exiting of the block within the stack call @ref getVarToBeAssignedBeforeBlockClose to ensure
     * that the count is then fixed.
     * <p>
     * Example of why this is important:
     * From this:
     * if t <= x then
     * .... x := x + t;
     * We generate this:
     * %x_d0_1 = scf.if %tmp_4 -> (i32) { (1)
     * .... %tmp_5 = arith.addi %x_d0_0, %t_d0_0 : i32 (2)
     * .... %x_d1_0 = arith.bitcast %tmp_5: i32 to i32
     * .... scf.yield %x_d1_0 : i32
     * }
     * We need to make sure that %x_d0_1 assigned in line (1) is not called again in line (2) on getVarName(),
     * this method ensures that this does not happen. Instead %x_d0_0 is returned
     *
     * @param varName The name of the variable to be converted to an SSA operand: eg "varName"
     * @return The name of the variable with a new postfix attached to it: eg: "varName_1"
     */
    public String getVarToBeAssignedBeforeBlockOpen(String varName) {
        Map<String, Integer> topOfStack = stack.getLast();
        topOfStack.putIfAbsent(varName, -1);
        int nextIncr = topOfStack.get(varName) + 1;
        //topOfStack.put(varName, nextIncr);
        return varName + "_d" + depth + "_" + nextIncr;
    }

    /**
     * Called at the exit of a control flow construct to ensure that the stack is maintained correctly. See @ref
     * getVarToBeAssignedBeforeBlockOpen for a more complete explanation
     *
     * @param varName The name of the variable that was converted to an operand on the opening of the control flow
     *                construct
     */
    public void getVarToBeAssignedBeforeBlockClose(String varName) {
        Map<String, Integer> topOfStack = stack.getLast();
        topOfStack.putIfAbsent(varName, -1);
        int nextIncr = topOfStack.get(varName) + 1;
        topOfStack.put(varName, nextIncr);
    }

    public String getNewTempVar() {
        return "tmp_" + tempVarIndex++;
    }

    @Override
    public String toString() {
        return stack.toString();
    }


}
