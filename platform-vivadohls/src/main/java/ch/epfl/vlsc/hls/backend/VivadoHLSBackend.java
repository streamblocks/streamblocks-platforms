package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.attributes.Memories;
import ch.epfl.vlsc.hls.backend.controllers.*;
import ch.epfl.vlsc.hls.backend.kernel.AxiLiteControl;

import ch.epfl.vlsc.hls.backend.kernel.InputStageMem;
import ch.epfl.vlsc.hls.backend.kernel.KernelWrapper;
import ch.epfl.vlsc.hls.backend.kernel.KernelXml;

import ch.epfl.vlsc.hls.backend.kernel.OutputStageMem;
import ch.epfl.vlsc.hls.backend.kernel.PackageKernel;
import ch.epfl.vlsc.hls.backend.kernel.TopKernel;
import ch.epfl.vlsc.hls.backend.scripts.IdealWeight;
import ch.epfl.vlsc.hls.backend.scripts.VivadoTCL;
import ch.epfl.vlsc.hls.backend.simulators.WcfgWaveform;
import ch.epfl.vlsc.hls.backend.systemc.Simulator;
import ch.epfl.vlsc.hls.backend.systemc.SystemCNetwork;
import ch.epfl.vlsc.hls.backend.systemc.SystemCTestBench;
import ch.epfl.vlsc.hls.backend.testbench.TestbenchHLS;
import ch.epfl.vlsc.hls.backend.verilog.VerilogNetwork;
import ch.epfl.vlsc.hls.backend.verilog.VerilogTestbench;
import ch.epfl.vlsc.platformutils.DefaultValues;
import ch.epfl.vlsc.platformutils.Emitter;
import ch.epfl.vlsc.platformutils.utils.Box;
import org.multij.Binding;
import org.multij.Module;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.*;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.compiler.UniqueNumbers;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.meta.interp.Interpreter;
import se.lth.cs.tycho.meta.interp.op.Binary;
import se.lth.cs.tycho.meta.interp.op.Unary;
import se.lth.cs.tycho.meta.interp.value.util.Convert;
import se.lth.cs.tycho.phase.TreeShadow;

import static org.multij.BindingKind.INJECTED;
import static org.multij.BindingKind.LAZY;

@Module
public interface VivadoHLSBackend {

    @Binding(INJECTED)
    CompilationTask task();

    // -- Compilation Context
    @Binding(INJECTED)
    Context context();

    // -- Instance Box
    @Binding(LAZY)
    default Box<Instance> instancebox() {
        return Box.empty();
    }

    // -- Entity Box
    @Binding(LAZY)
    default Box<Entity> entitybox() {
        return Box.empty();
    }

    // -- Use trigger box
    @Binding(LAZY)
    default Box<Boolean> triggerBox() {
        return Box.empty();
    }

    @Binding(LAZY)
    default Box<Boolean> complexReadBox() {
        return Box.empty();
    }

    // -- Globals names
    @Binding(LAZY)
    default GlobalNames globalnames() {
        return task().getModule(GlobalNames.key);
    }

    // -- Types
    @Binding(LAZY)
    default Types types() {
        return task().getModule(Types.key);
    }

    // -- Unique Numbers
    @Binding(LAZY)
    default UniqueNumbers uniqueNumbers() {
        return context().getUniqueNumbers();
    }

    // -- Constant Evaluator
    @Binding(LAZY)
    default ConstantEvaluator constants() {
        return task().getModule(ConstantEvaluator.key);
    }

    // -- Variable Declarations
    @Binding(LAZY)
    default VariableDeclarations varDecls() {
        return task().getModule(VariableDeclarations.key);
    }

    // -- Variable Declarations
    @Binding(LAZY)
    default VariableScopes varScopes() {
        return task().getModule(VariableScopes.key);
    }

    // -- IR Tree Shadow
    @Binding(LAZY)
    default TreeShadow tree() {
        return task().getModule(TreeShadow.key);
    }

    // -- Actor Machine scopes
    @Binding(LAZY)
    default ActorMachineScopes scopes() {
        return task().getModule(ActorMachineScopes.key);
    }

    // -- Closures
    @Binding(LAZY)
    default Closures closures() {
        return task().getModule(Closures.key);
    }

    // -- Free variables
    @Binding(LAZY)
    default FreeVariables freeVariables() {
        return task().getModule(FreeVariables.key);
    }

    // -- Scope dependencies
    @Binding(LAZY)
    default ScopeDependencies scopeDependencies() {
        return task().getModule(ScopeDependencies.key);
    }

    // -- Ports
    @Binding(LAZY)
    default Ports ports() {
        return task().getModule(Ports.key);
    }

    // -- Emitter
    @Binding(LAZY)
    default Emitter emitter() {
        return new Emitter();
    }

    // -- Types evaluator
    @Binding(LAZY)
    default TypesEvaluator typeseval() {
        return MultiJ.from(TypesEvaluator.class).bind("backend").to(this).instance();
    }

    // -- Declarations
    @Binding(LAZY)
    default Declarations declarations() {
        return MultiJ.from(Declarations.class).bind("backend").to(this).instance();
    }

    // -- Variables
    @Binding(LAZY)
    default Variables variables() {
        return MultiJ.from(Variables.class).bind("backend").to(this).instance();
    }

    // -- ChannelUtils
    @Binding(LAZY)
    default LValues lvalues() {
        return MultiJ.from(LValues.class).bind("backend").to(this).instance();
    }

    // -- Expression Evaluator
    @Binding(LAZY)
    default ExpressionEvaluator expressioneval() {
        return MultiJ.from(ExpressionEvaluator.class).bind("backend").to(this).instance();
    }

    // -- Statements
    @Binding(LAZY)
    default Statements statements() {
        return MultiJ.from(Statements.class).bind("backend").to(this).instance();
    }

    // -- Callables
    @Binding(LAZY)
    default CallablesInActors callables() {
        return MultiJ.from(CallablesInActors.class).bind("backend").to(this).instance();
    }

    // -- Algebraic Types
    @Binding(LAZY)
    default AlgebraicTypes algebraicTypes() {
        return MultiJ.from(AlgebraicTypes.class).bind("backend").to(this).instance();
    }

    // -- Pattern Matching
    @Binding(LAZY)
    default PatternMatching patternMatching() {
        return MultiJ.from(PatternMatching.class).bind("backend").to(this).instance();
    }

    // -- Default Values
    @Binding(LAZY)
    default DefaultValues defaultValues() {
        return MultiJ.from(DefaultValues.class).instance();
    }

    // -- ChannelUtils
    @Binding(LAZY)
    default ChannelsUtils channelsutils() {
        return MultiJ.from(ChannelsUtils.class).bind("backend").to(this).instance();
    }

    // -- Instance generator
    @Binding(LAZY)
    default Instances instance() {
        return MultiJ.from(Instances.class).bind("backend").to(this).instance();
    }

    // -- Instance generator
    @Binding(LAZY)
    default QuickJumpController quickJumpController() {
        return MultiJ.from(QuickJumpController.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default CalActorController calActorController() {
        return MultiJ.from(CalActorController.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default FsmController fsmController() {
        return MultiJ.from(FsmController.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY)
    default BranchingController branchingController() {
        return MultiJ.from(BranchingController.class).bind("backend").to(this).instance();
    }


    // Experimental strawman contoller
    // Author: Mahyar
    @Binding(LAZY)
    default StrawManController strawManController() {
        return MultiJ.from(StrawManController.class).bind("backend").to(this).instance();
    }


    @Binding(LAZY)
    default ExternalMemory externalMemory() {
        return MultiJ.from(ExternalMemory.class)
                .bind("types").to(task().getModule(Types.key))
                .bind("memories").to(task().getModule(Memories.key))
                .instance();
    }


    // -- Verilog Network generator
    @Binding(LAZY)
    default VerilogNetwork vnetwork() {
        return MultiJ.from(VerilogNetwork.class).bind("backend").to(this).instance();
    }

    // -- SystemC network generator
    @Binding(LAZY)
    default SystemCNetwork scnetwork() {
        return MultiJ.from(SystemCNetwork.class).bind("backend").to(this).instance();
    }


    // -- Globals
    @Binding(LAZY)
    default Globals globals() {
        return MultiJ.from(Globals.class).bind("backend").to(this).instance();
    }



    // -- CMakeLists
    @Binding(LAZY)
    default CMakeLists cmakelists() {
        return MultiJ.from(CMakeLists.class).bind("backend").to(this).instance();
    }

    // -- Verilog Testbenches
    @Binding(LAZY)
    default VerilogTestbench testbench() {
        return MultiJ.from(VerilogTestbench.class).bind("backend").to(this).instance();
    }

    // -- HLS Testbenches
    @Binding(LAZY)
    default TestbenchHLS testbenchHLS() {
        return MultiJ.from(TestbenchHLS.class).bind("backend").to(this).instance();
    }

    // -- SystemC Tester
    @Binding(LAZY)
    default SystemCTestBench sctester() {
        return MultiJ.from(SystemCTestBench.class).bind("backend").to(this).instance();
    }

    // -- SystemC network simulator
    @Binding(LAZY)
    default Simulator simulator() {
        return MultiJ.from(Simulator.class).bind("backend").to(this).instance();
    }

    // -- Verilog Waveform for ISIM
    @Binding(LAZY)
    default WcfgWaveform wcfg() {
        return MultiJ.from(WcfgWaveform.class).bind("backend").to(this).instance();
    }

    // -- Vivado TCL
    @Binding(LAZY)
    default VivadoTCL vivadotcl() {
        return MultiJ.from(VivadoTCL.class).bind("backend").to(this).instance();
    }


    @Binding(LAZY)
    default Interpreter interpreter() {
        return MultiJ.from(Interpreter.class)
                .bind("variables").to(task().getModule(VariableDeclarations.key))
                .bind("types").to(task().getModule(TypeScopes.key))
                .bind("unary").to(MultiJ.from(Unary.class).instance())
                .bind("binary").to(MultiJ.from(Binary.class).instance())
                .instance();
    }

    @Binding(LAZY)
    default Convert converter() {
        return MultiJ.from(Convert.class)
                .instance();
    }

    // -----------------------------------
    // -- Annotations
    @Binding(LAZY)
    default Annotations annotations() {
        return MultiJ.from(Annotations.class).bind("backend").to(this).instance();
    }

    // -----------------------------------
    // -- Kernel Wrapper
    // -- Top Kernel
    @Binding(LAZY)
    default TopKernel topkernel() {
        return MultiJ.from(TopKernel.class).bind("backend").to(this).instance();
    }

    // -- AXI Lite Control
    @Binding(LAZY)
    default AxiLiteControl axilitecontrol() {
        return MultiJ.from(AxiLiteControl.class).bind("backend").to(this).instance();
    }

    // -- Kernel Wrapper (input/output stages and network)
    @Binding(LAZY)
    default KernelWrapper kernelwrapper() {
        return MultiJ.from(KernelWrapper.class).bind("backend").to(this).instance();
    }

    // -- Input Stage Mem
    @Binding(LAZY)
    default InputStageMem inputstagemem() {
        return MultiJ.from(InputStageMem.class).bind("backend").to(this).instance();
    }


    // -- Output Stage Mem
    @Binding(LAZY)
    default OutputStageMem outputstagemem() {
        return MultiJ.from(OutputStageMem.class).bind("backend").to(this).instance();
    }



    // -- OpenCL Kernel XML
    @Binding(LAZY)
    default KernelXml kernelxml() {
        return MultiJ.from(KernelXml.class).bind("backend").to(this).instance();
    }

    // -- TCL script for packaging the OpenCL kernel
    @Binding(LAZY)
    default PackageKernel packagekernel() {
        return MultiJ.from(PackageKernel.class).bind("backend").to(this).instance();
    }

    // -- Network to DOT
    @Binding(LAZY)
    default NetworkToDot netoworkToDot() {
        return MultiJ.from(NetworkToDot.class).bind("backend").to(this).instance();
    }

    // -- Utils
    default QID taskIdentifier() {
        return task().getIdentifier().getButLast();
    }

    default IdealWeight idealWeight(){
        return MultiJ.from(IdealWeight.class).bind("backend").to(this).instance();
    }

    /**
     * Get the QID of the instance for the C11 platform
     *
     * @param instanceName
     * @param delimiter
     * @return
     */
    default String instaceQID(String instanceName, String delimiter) {
        return String.join(delimiter, taskIdentifier().parts()) + "_" + instanceName;
    }

    default void includeUser(String h) {
        emitter().emit("#include \"%s\"", h);
    }

    default void includeSystem(String h) {
        emitter().emit("#include <%s>", h);
    }

}
