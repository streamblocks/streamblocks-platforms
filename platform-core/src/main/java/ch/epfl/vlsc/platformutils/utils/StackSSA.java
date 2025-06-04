package ch.epfl.vlsc.platformutils.utils;

import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

/**
 * A utility class for managing SSA (Static Single Assignment) variable naming during MLIR code generation
 * for the CAL actor language.
 * <p>
 * In SSA form, each variable is assigned exactly once, and every variable is defined before it is used.
 * While full SSA conversion (as used in imperative languages) often requires control flow analysis like
 * the LT algorithm with φ-functions, the CAL language is quite simple generally free of complex branching. As a
 * result, SSA variable generation can be managed with a scoped stack rather than global control-flow-sensitive analysis.
 * <p>
 * This class tracks variable lifetimes across nested scopes using a stack of maps. Each map on the stack
 * corresponds to a scope (e.g., an actor or control construct like `if`), mapping variable names to the
 * current SSA index. Each time a variable is assigned, the index is incremented to reflect a new SSA version.
 * <p>
 * Special attention is given to variable access and update behavior in nested constructs:
 * <ul>
 *     <li><b>Aliasing:</b> MLIR does not permit direct SSA assignments like {@code %a = %b}. To support this,
 *     aliasing is used: the variable `%a` is "aliased" to `%b`, and future accesses to `%a` resolve to `%b`.</li>
 *     <li><b>Control Flow Constructs:</b> Methods like {@link #getVarToBeAssignedBeforeBlockOpen(String)} and
 *     {@link #getVarToBeAssignedBeforeBlockClose(String)} handle special cases where a new SSA version should
 *     not interfere with reads within the same control structure (e.g., inside an `if` or loop).</li>
 *     <li><b>Scoped Stack:</b> Each call to {@link #newBlock()} pushes a new scope, and {@link #blockDone()}
 *     pops it, maintaining correct visibility and isolation of SSA indices per scope.</li>
 *     <li><b>State Variables:</b> Actor state variables differ from local variables in that they persist across
 *     actor firings and are stored in memory. Therefore, they are not assigned new SSA names. Instead, they are
 *     recorded in a separate map ({@code stateVar}) with their types and must be explicitly loaded/stored from
 *     memory when accessed or updated.</li>
 * </ul>
 * This class is actor-scoped: a new actor context should reset the stack via {@link #newActorContext()}.
 * <p>
 * This is not the neatest approach for this. I developed it before I had properly learnt about SSA form. However, so
 * far it works, but it is definitely rough around the edges.
 *
 * @author Gareth Callanan
 */
public class StackSSA {

    LinkedList<Map<String, Integer>> stack;
    Map<String, String> aliases;
    Map<String, String> stateVar; // Map of a state var and its type

    int tempVarIndex;
    int depth;

    public StackSSA() {
        init();
    }

    private void init() {
        stack = new LinkedList<>();
        aliases = new TreeMap<>();
        stateVar = new TreeMap<>();
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
        Map<String, Integer> removedItems = stack.removeLast();
        for (Map.Entry<String, Integer> entry : removedItems.entrySet()) {
            String nameToRemove = entry.getKey() + "_d" + depth + "_" + entry.getValue();
            aliases.remove(nameToRemove);
        }
        depth--;
    }

    /**
     * Alias one SSA to another
     * <p>
     * So instead of: %currentSSA = %alias, everytime we need to get %currentSSA from the stack, %alias is returned
     * instead
     *
     * @param alias
     * @param currentSSA
     */
    public void aliasSSA(String alias, String currentSSA) {
        aliases.put(currentSSA, alias);
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
            if (fromStack != null && fromStack != -1) { // The -1 can occur when you are not supposed to get that
                // variable as it is the result of a yield
                String ssaName = varName + "_d" + currentDepth + "_" + fromStack;
                String aliasSSA = aliases.get(ssaName);
                if (aliasSSA != null) {
                    return aliasSSA;
                } else {
                    return ssaName;
                }
            }
            currentDepth--;
        }

        throw new RuntimeException("Variable " + varName + " does not exist in SSA stack.\n" + this.stack);
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

    /**
     * Resets the SSA tracking context for a new actor.
     * <p>
     * This method should be called when entering a new CAL actor to clear all previous SSA state,
     * including variable stacks, aliasing maps, temporary counters, and actor state variable metadata.
     * It effectively reinitializes the SSA generation system to ensure that no cross-actor interference occurs.
     */
    public void newActorContext() {
        init();
    }

    /**
     * Registers an actor state variable along with its type.
     * <p>
     * State variables in CAL are persistent across actor firings and are stored in memory rather than
     * assigned new SSA values. This method stores the state variable name and its type in a dedicated
     * map so that later accesses can generate appropriate load/store operations instead of SSA renaming.
     *
     * @param variableName The name of the state variable (e.g., "counter").
     * @param type         The string representation of the variable's type (e.g., "!memref<i32>").
     */
    public void setStateVar(String variableName, String type) {
        stateVar.put(variableName, type);
    }

    /**
     * Checks whether a variable is a registered actor state variable.
     * <p>
     * State variables are stored in memory and not tracked through SSA renaming. This method determines
     * if a given variable is one of those persistent state variables.
     *
     * @param variableName The name of the variable to check.
     * @return {@code true} if the variable is a state variable stored in memory; {@code false} otherwise.
     */
    public boolean hasStateVar(String variableName) {
        return stateVar.containsKey(variableName);
    }
}
