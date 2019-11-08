package ch.epfl.vlsc.hls.backend;

import ch.epfl.vlsc.hls.backend.host.DeviceHandle;
import ch.epfl.vlsc.hls.backend.kernel.*;
// import ch.epfl.vlsc.hls.backend.kernel.KernelsXml;
// import ch.epfl.vlsc.hls.backend.kernel.Kernel;
import ch.epfl.vlsc.hls.backend.scripts.VivadoTCL;
import ch.epfl.vlsc.hls.backend.simulators.WcfgWaveform;
import ch.epfl.vlsc.hls.backend.verilog.VerilogNetwork;
import ch.epfl.vlsc.hls.backend.verilog.VerilogTestbench;
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
    default Controllers controllers() {
        return MultiJ.from(Controllers.class).bind("backend").to(this).instance();
    }

    // -- Verilog Network generator
    @Binding(LAZY)
    default VerilogNetwork vnetwork() {
        return MultiJ.from(VerilogNetwork.class).bind("backend").to(this).instance();
    }

    // -- Globals
    @Binding(LAZY)
    default Globals globals() {
        return MultiJ.from(Globals.class).bind("backend").to(this).instance();
    }

    // -- DeviceHandle
    @Binding(LAZY)
    default DeviceHandle devicehandle() {
        return MultiJ.from(DeviceHandle.class).bind("backend").to(this).instance();
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

    // -- Verilog Testbenches
    @Binding(LAZY)
    default WcfgWaveform wcfg() {
        return MultiJ.from(WcfgWaveform.class).bind("backend").to(this).instance();
    }

    // -- Vivado TCL
    @Binding(LAZY)
    default VivadoTCL vivadotcl() {
        return MultiJ.from(VivadoTCL.class).bind("backend").to(this).instance();
    }

    // -----------------------------------
    // -- Kernels top
    @Binding(LAZY)
    default Kernel kernel() {
        return MultiJ.from(Kernel.class).bind("backend").to(this).instance();
    }
    
    // -----------------------------------
    // -- Core kernel
    @Binding(LAZY) 
    default CoreKernelWrapper corekernelwrapper() {
        return MultiJ.from(CoreKernelWrapper.class).bind("backend").to(this).instance();
    }
    // ------------------------------------
    // -- IO kernel
    @Binding(LAZY) 
    default IOKernelWrapper iokernelwrapper() {
        return MultiJ.from(IOKernelWrapper.class).bind("backend").to(this).instance();
    }

    // -- AXI Lite Control
    @Binding(LAZY)
    default AxiLiteControlKernels axilitecontrolkernels() {
        return MultiJ.from(AxiLiteControlKernels.class).bind("backend").to(this).instance();
    }


    // -- Input Stage Pass
    @Binding(LAZY)
    default StagePass stagepass() {
        return MultiJ.from(StagePass.class).bind("backend").to(this).instance();
    }

    // -- Input Stage Mem
    @Binding(LAZY)
    default InputStageMem inputstagemem() {
        return MultiJ.from(InputStageMem.class).bind("backend").to(this).instance();
    }

    // -- Input Stage
    @Binding(LAZY)
    default InputStage inputstage() {
        return MultiJ.from(InputStage.class).bind("backend").to(this).instance();
    }

    // -- Output Stage Mem
    @Binding(LAZY)
    default OutputStageMem outputstagemem() {
        return MultiJ.from(OutputStageMem.class).bind("backend").to(this).instance();
    }

    // -- Output Stage
    @Binding(LAZY)
    default OutputStage outputstage() {
        return MultiJ.from(OutputStage.class).bind("backend").to(this).instance();
    }

    @Binding(LAZY) 
    default KernelsXml kernelsxml() {
        return MultiJ.from(KernelsXml.class).bind("backend").to(this).instance();
    }


    // -- TCL scripts for kernel packaging
    @Binding(LAZY) 
    default PackageKernels packagekernels() {
        return MultiJ.from(PackageKernels.class).bind("backend").to(this).instance();
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
