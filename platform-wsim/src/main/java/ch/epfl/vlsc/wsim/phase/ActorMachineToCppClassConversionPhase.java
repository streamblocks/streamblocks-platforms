package ch.epfl.vlsc.wsim.phase;


import ch.epfl.vlsc.wsim.ir.cpp.CppNominalTypeExpr;
import ch.epfl.vlsc.wsim.ir.cpp.decl.*;
import ch.epfl.vlsc.wsim.ir.cpp.expression.ExprCppMethod;
import ch.epfl.vlsc.wsim.ir.cpp.expression.ExprCppMethodCall;
import ch.epfl.vlsc.wsim.ir.cpp.statement.*;
import ch.epfl.vlsc.wsim.ir.cpp.types.*;
import ch.epfl.vlsc.wsim.phase.attributes.InstructionTranslator;
import ch.epfl.vlsc.wsim.phase.attributes.SourceUnitFinder;
import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.Ports;

import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.*;

import se.lth.cs.tycho.ir.decl.*;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.am.*;

import se.lth.cs.tycho.ir.entity.am.ctrl.InstructionKind;
import se.lth.cs.tycho.ir.entity.am.ctrl.State;
import se.lth.cs.tycho.ir.expr.*;
import se.lth.cs.tycho.ir.stmt.*;

import se.lth.cs.tycho.ir.type.TypeExpr;
import se.lth.cs.tycho.ir.util.ImmutableList;

import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.phase.Phase;
import org.multij.Module;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.*;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class ActorMachineToCppClassConversionPhase implements Phase {

    @Override
    public String getDescription() { return "Transform actor machines into C++ classes"; }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        CreateActorClass transformation = MultiJ.from(CreateActorClass.class)
                .bind("ports").to(task.getModule(Ports.key))
                .bind("sources").to(task.getModule(SourceUnitFinder.key))
                .bind("translator").to(task.getModule(InstructionTranslator.key))
                .instance();
        ImmutableList<CppWarpActorTypeDecl> decl = task.getSourceUnits()
                .map(SourceUnit::getTree).stream()
                .flatMap(ns -> ns.getEntityDecls().stream())
                .filter(e -> e.getEntity() instanceof ActorMachine)
                .map(transformation::create)
                .collect(ImmutableList.collector());


        return task;
    }



    @Module
    interface CreateActorClass {

        @Binding(BindingKind.INJECTED)
        Ports ports();

        @Binding(BindingKind.INJECTED)
        SourceUnitFinder sources();

        @Binding(BindingKind.INJECTED)
        InstructionTranslator translator();

        default SourceUnit findSourceUnit(IRNode node) {
            return sources().find(node);
        }

        default CppNominalTypeExpr cast(TypeExpr typeExpr) {
            if (typeExpr instanceof CppNominalTypeExpr)
                return (CppNominalTypeExpr) typeExpr;
            else
                throw new CompilationException(
                        new Diagnostic(Diagnostic.Kind.ERROR,
                                "Failed to cast to C++ nominal types, perhaps there is " +
                                "lambda or procedure defined inside a function?",
                                findSourceUnit(typeExpr), typeExpr)
                );
        }

        default String runtimeStateVar() {
            return "m_state";
        }
        default String latencyFunctionVarName() {
            return "m_latency";
        }
        default ExprLiteral maxLoops() {
            return new ExprLiteral(ExprLiteral.Kind.Integer, "100");
        }

        default CppWarpActorTypeDecl create(GlobalEntityDecl entityDecl) {

            ActorMachine actorMachine = (ActorMachine) entityDecl.getEntity();
            // class member variable declarations,
            // m_state, includes the program counter and local virtual time
            // m_inputs_... and m_output_... for every port as shared_ptr<T> types, where T is the
            // declared port type
            ImmutableList.Builder<CppMemberVarDecl> memberVariables = ImmutableList.<CppMemberVarDecl>builder()
                    .add(
                            CppMemberVarDecl.privateMemberVarDecl(
                                    new CppNominalTypeExpr(new WarpActorStateCppType()), runtimeStateVar())
                    ).add(
                            CppMemberVarDecl.privateMemberVarDecl(
                                    new CppNominalTypeExpr(
                                            new ArrayCppType(
                                                    NativeTypeCpp.VirtualTime(),
                                                    actorMachine.getTransitions().size())),
                                    latencyFunctionVarName()
                            )
                    )
                    .addAll( // variable defined by the actor scopes
                            actorMachine.getScopes().stream()
                                    .flatMap(scope -> scope.getDeclarations().stream())
                                    .filter(
                                        vdecl -> !(vdecl.getValue() instanceof ExprLambda ||
                                                vdecl.getValue() instanceof ExprProc))
                                    .map(vdecl -> {
                                        if (vdecl.getType() == null) {
                                            throw new CompilationException(
                                                    new Diagnostic(Diagnostic.Kind.ERROR,
                                                            "Null type expr", findSourceUnit(vdecl), vdecl)
                                            );
                                        }
                                        return CppMemberVarDecl.privateMemberVarDecl(
                                                new CppNominalTypeExpr(
                                                        cast(vdecl.getType()).getType()
                                                ), vdecl.getName());
                                    }).collect(ImmutableList.collector())
                    );

            ImmutableList<CppMemberVarDecl> inputPorts = actorMachine.getInputPorts().map(port ->
                    portMemberDecl(port, PortCppType::InputPort, this::moduleInputPortName));
            ImmutableList<CppMemberVarDecl> outputPorts = actorMachine.getOutputPorts().map(port ->
                    portMemberDecl(port, PortCppType::OutputPort, this::moduleOutputPortName));

            // class member functions, i.e., the lambdas and procedures
            ImmutableList<CppMemberFunctionDecl> memberFuncs = actorMachine.getScopes().stream()
                    .flatMap(scope -> scope.getDeclarations().stream())
                    .filter(decl -> decl.getValue() instanceof ExprLambda || decl.getValue() instanceof ExprProc)
                    .map(decl -> getFuncDecl(decl.getName(), decl.getValue()))
                    .collect(ImmutableList.collector());

            ImmutableList<CppMemberFunctionDecl> conditions = transformConditions(actorMachine);
            ImmutableList<CppMemberFunctionDecl> actions = transformActions(actorMachine);

            CppMemberFunctionDecl scheduler = createScheduler(
                    actorMachine, conditions, actions
            );


            return CppWarpActorTypeDecl.create(
                    actorMachine,
                    entityDecl.getName(),
                    memberVariables.build(),
                    scheduler,
                    memberFuncs,
                    ImmutableList.empty(),
                    conditions,
                    actions,
                    inputPorts, outputPorts
            );

        }


        /**
         * Transform the actor machine conditions into function declaration
         * @param actorMachine
         * @return A list of function declaration representing conditions
         */
        default ImmutableList<CppMemberFunctionDecl> transformConditions(ActorMachine actorMachine) {

            int conditionIndex = 0;



            ImmutableList.Builder<CppMemberFunctionDecl> builder = ImmutableList.builder();

            for (Condition condition : actorMachine.getConditions()) {
                builder.add(conditionDecl(condition, conditionIndex));
                conditionIndex ++;
            }
            return builder.build();

        }

        default CppMemberFunctionDecl conditionDecl(Condition condition, int conditionIndex) {
            String conditionName = "condition_" + conditionIndex;
            if (condition.kind() == Condition.ConditionKind.input ||
                    condition.kind() == Condition.ConditionKind.output) {
                String name_prefix = condition.kind() == Condition.ConditionKind.input ? "input_" : "output_";

                PortCondition portCond = (PortCondition) condition;
                PortDecl port = ports().declaration(portCond.getPortName());
                String portVarName = condition.kind() ==
                        Condition.ConditionKind.input ? moduleInputPortName(port) : moduleOutputPortName(port);
                Expression portAccess =
                        new ExprVariable(
                                Variable.variable(portVarName));
                Expression innerFuncCall =
                        new ExprVariable(
                                Variable.variable("evaluateCondition")
                        );
                ImmutableList<Expression> innerFuncArgs =
                        ImmutableList.of(
                                new ExprLiteral(
                                        ExprLiteral.Kind.Integer,
                                        String.valueOf(portCond.N()))
                        );
                ImmutableList<Statement> funcBody =
                        ImmutableList.of(
                                new StmtReturn(
                                        new ExprCppMethodCall(
                                                portAccess, ExprCppMethodCall.ArrowAccess,
                                                innerFuncCall, innerFuncArgs)));

                CppNominalTypeExpr returnType = new CppNominalTypeExpr(
                        new PairCppType(
                                NativeTypeCpp.Bool(),
                                NativeTypeCpp.Int64(false)
                        ));
                CppMemberFunctionDecl decl = new CppMemberFunctionDecl(
                        name_prefix + conditionName, new ExprCppMethod(ImmutableList.empty(),
                        funcBody, returnType),
                        true, true
                );
                return decl;
            } else {
                // guard condition
                PredicateCondition guardCond = (PredicateCondition) condition;
                CppNominalTypeExpr returnType = new CppNominalTypeExpr(NativeTypeCpp.Bool());
                ImmutableList<Statement> body = ImmutableList.of(
                        new StmtReturn(guardCond.getExpression())
                );
                CppMemberFunctionDecl decl =  new CppMemberFunctionDecl(
                        "guard_" + conditionName, new ExprCppMethod(ImmutableList.empty(),
                        body, returnType),
                        true, true
                );
                return decl;
            }
        }

        default String moduleInputPortName(PortDecl port) {
            return "m_inputs_" + port.getName();
        }
        default String moduleOutputPortName(PortDecl port) {
            return  "m_outputs_" + port.getName();
        }

        /**
         * Create a public port member variable declaration
         * TODO: make the port member declaration private with accessor methods
         * @param port original port declaration
         * @param nameFunction a function for naming the new declaration
         * @return a CppMemberVarDecl for the port
         */
        default CppMemberVarDecl portMemberDecl(PortDecl port, Function<CppType, PortCppType> builder,
                                                Function<PortDecl, String> nameFunction) {
            String portName = nameFunction.apply(port);
            CppNominalTypeExpr portType =
                    new CppNominalTypeExpr(
                            builder.apply(
                            new SharedPointerCppType(
                                    ((CppNominalTypeExpr)port.getType()).getType()))
                    );
            return CppMemberVarDecl.publicMemberVarDecl(
                    portType, portName
            );
        }

        default ImmutableList<CppMemberFunctionDecl> transformActions(ActorMachine am) {

            int transitionIndex = 0;
            ImmutableList.Builder<CppMemberFunctionDecl> builder = ImmutableList.builder();
            for (Transition transition : am.getTransitions()) {
                builder.add(actionDecl(transition, transitionIndex++));
            }
            return builder.build();
        }
        default CppMemberFunctionDecl actionDecl(Transition transition, int transitionIndex) {
            Optional<Annotation> annotation = Annotation.getAnnotationWithName("ActionId",
                    transition.getAnnotations());
            String actionTag = annotation.map(a ->
                    ((ExprLiteral) a.getParameters().get(0).getExpression()).getText()).orElse("");
            String funcName = "action_" + transitionIndex + "_" + actionTag;
            CppNominalTypeExpr returnType = new CppNominalTypeExpr(NativeTypeCpp.Void());
            ExprCppMethod funcDef =  new ExprCppMethod(ImmutableList.empty(),
                    transition.getBody(), returnType);
            return new CppMemberFunctionDecl(funcName, funcDef, false, true);
        }


        CppMemberFunctionDecl getFuncDecl(String funcName, Expression funcBody);

        default CppMemberFunctionDecl getFuncDecl(String funcName, ExprLambda expr) {

            CppNominalTypeExpr returnType =
                    new CppNominalTypeExpr(cast(expr.getReturnType()), cast(expr.getReturnType()).getType());
            ImmutableList<CppParameterVarDecl> args = expr.getValueParameters().map(this::toCppParameterVarDecl);
            ImmutableList<Statement> body = ImmutableList.of(
                    new StmtReturn(expr.getBody())
            );
            ExprCppMethod funcDef = new ExprCppMethod(
                    args, body, returnType
            );
            return new CppMemberFunctionDecl(
                    funcName, funcDef, false, true
            );

        }

        default CppMemberFunctionDecl getFuncDecl(String funcName, ExprProc proc) {
            CppNominalTypeExpr returnType = new CppNominalTypeExpr(NativeTypeCpp.Void());
            ImmutableList<CppParameterVarDecl> args = proc.getValueParameters().map(this::toCppParameterVarDecl);
            ExprCppMethod funcDef =
                    new ExprCppMethod(args, proc.getBody(), returnType);
            return new CppMemberFunctionDecl(
                    funcName, funcDef, false, true
            );
        }

        default CppParameterVarDecl toCppParameterVarDecl(ParameterVarDecl v) {
            return new CppParameterVarDecl(v,
                            cast(v.getType()),
                            v.getName(),
                            v.getDefaultValue(),
                            v.isConstant(),
                            v.isExternal());
        }


        default CppMemberFunctionDecl createScheduler(ActorMachine actorMachine,
                                                      ImmutableList<CppMemberFunctionDecl> conditions,
                                                      ImmutableList<CppMemberFunctionDecl> actions) {


            List<State> stateList = actorMachine.controller().getStateList();
            Map<State, Integer> stateMap =
                    IntStream.range(0, actorMachine.controller().getStateList().size()).boxed()
                    .collect(Collectors
                            .toMap(
                                    stateList::get, Function.identity()
                            ));
            Set<State> waitTargets = new HashSet<>();
            stateList.stream()
                    .map(s -> s.getInstructions().get(0)) // get the first instructions (assumes SIAM)
                    .filter(i -> i.getKind() == InstructionKind.WAIT) // get wait instructions
                    .forEachOrdered(i->i.forEachTarget(waitTargets::add)); // collect the targets of waits instructions




            ImmutableList<StmtSwitchCase.StmtLiteralCase> cases = waitTargets.stream().map(s -> {
                Integer stateNumber = stateMap.get(s);
                StmtSwitchCase.CaseLiteral caseLabel = getStateCase(stateNumber);
                StmtSwitchCase.StmtLiteralCase gotoCase =
                        new StmtSwitchCase.StmtLiteralCase(
                                caseLabel,
                                ImmutableList.of(
                                        new StmtGoTo(caseLabel.getValue())
                                )
                        );
                return  gotoCase;
            }).collect(ImmutableList.collector());

            StmtSwitchCase.StmtLiteralCase defaultCase =
                    new StmtSwitchCase.StmtLiteralCase(
                            StmtSwitchCase.CaseLiteral.Default.INSTANCE,
                            ImmutableList.of(
                                    new StmtWarpActorException("Invalid actor state reached!")
                            )
                    );
            /**
             * build the following statement
             * switch(m_state.pc) {
             *  case n:
             *      goto Sn;
             *  [...]
             *  default:
             *      throw ActorException(...);
             * }
             *
             */
            StmtSwitchCase enterStatesSwitch =
                    new StmtSwitchCase(
                        new ExprField(
                                new ExprVariable(Variable.variable(runtimeStateVar())),
                                new Field(WarpActorStateCppType.getPcFieldName())),
                            ImmutableList.<StmtSwitchCase.StmtLiteralCase>builder()
                                    .addAll(cases).add(defaultCase).build()
                    );
            Variable currentLvt = Variable.variable("current_lvt");
            Variable scheduleQuery = Variable.variable("query");
            Variable latencyFunction = Variable.variable(latencyFunctionVarName());
            // translate all the instruction to statements
            ImmutableList<Statement> actionSelectionBody = stateList.stream().flatMap(s ->
                translator().translate(actorMachine, conditions, actions, stateMap, s,
                        currentLvt, scheduleQuery, latencyFunction).stream()
            ).collect(ImmutableList.collector());


            StmtBlock schedulerBlock = new StmtBlock(
                    ImmutableList.empty(), // no annotations
                    ImmutableList.empty(), // no type declarations
                    ImmutableList.of(
                            new LocalVarDecl(
                                    ImmutableList.empty(),
                                    new CppNominalTypeExpr(
                                            NativeTypeCpp.VirtualTime()
                                    ),
                                    currentLvt.getName(),
                                    WarpActorBaseCppType.makeGetVirtualTimeStmtCall(),
                                    false
                            )
                    ),
                    ImmutableList.<Statement>builder()
                            .add(
                                enterStatesSwitch
                            )
                            .addAll(
                                actionSelectionBody // action selection body with goto statements
                            )
                            .add(
                                new StmtLabel(translator().exitLabel()) // exit point, does nothing
                            ).build()
                    );

            return WarpActorBaseCppType.schedulerDecl(
                    ImmutableList.of(schedulerBlock), scheduleQuery
            );

        }



        default StmtSwitchCase.CaseLiteral getStateCase(Integer stateNumber) {
            return new StmtSwitchCase.CaseLiteral.Label(translator().getStateLabel(stateNumber));
        }


        /**
         * Translate {@link StmtConsume}, {@link StmtWrite}, and {@link ExprInput}
         */
//        @Module
//        interface TranslatePortStatements extends IRNode.Transformation {
//
//            @Binding(BindingKind.INJECTED)
//            Function<Port, String> inputNames();
//            @Binding(BindingKind.INJECTED)
//            Function<Port, String> outputNames();
//            @Binding(BindingKind.INJECTED)
//            Variable currentLvt();
//
//            default IRNode apply(IRNode node) {
//                return node.transformChildren(this);
//            }
//
//            default StmtMethodCall apply(StmtConsume consume) {
//                String portName = inputNames().apply(consume.getPort());
//                return PortCppType.portConsume(
//                        new ExprVariable(Variable.variable(portName)),
//                        StmtMethodCall.ArrowAccess, consume.getNumberOfTokens(),
//                        currentLvt()
//                );
//            }
//
//            default Statement apply(StmtWrite writeOperation) {
//                String portName = outputNames().apply(writeOperation.getPort());
//                if (writeOperation.getRepeatExpression() == null) {
//                    // one by one write to port, not optimized
//                    return new StmtBlock(
//                            ImmutableList.empty(), ImmutableList.empty(),
//                            ImmutableList.empty(),
//                            writeOperation.getValues().map(v ->
//                                PortCppType.portWrite(
//                                        new ExprVariable(Variable.variable(portName)),
//                                        StmtMethodCall.ArrowAccess, v, currentLvt()))
//                    );
//                } else {
//
//                }
//            }
//
//
//        }
    }




}
