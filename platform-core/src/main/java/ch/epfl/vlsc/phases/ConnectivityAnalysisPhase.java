package ch.epfl.vlsc.phases;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.MultiJ;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.attribute.NlNetworks;
import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;

import se.lth.cs.tycho.compiler.SourceUnit;
import se.lth.cs.tycho.ir.IRNode;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.decl.GlobalEntityDecl;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.entity.nl.*;

import se.lth.cs.tycho.ir.network.Instance;

import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.CreateNetworkPhase;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.phase.ResolveGlobalEntityNamesPhase;
import se.lth.cs.tycho.phase.TreeShadow;
import se.lth.cs.tycho.reporting.CompilationException;
import org.multij.Module;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.type.Type;


import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Visit all the connections in the NlNetwork and checks
 */
public class ConnectivityAnalysisPhase implements Phase {
    @Override
    public String getDescription() {
        return "checks type and source and target instances of a connection";
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        ConnectionChecker checker = MultiJ.from(ConnectionChecker.class)
                .bind("tree").to(task.getModule(TreeShadow.key))
                .bind("globalNames").to(task.getModule(GlobalNames.key))
                .bind("types").to(task.getModule(Types.key))
                .bind("networks").to(task.getModule(NlNetworks.key))
                .bind("context").to(context)
                .instance();
        checker.visit(task);
        return task;
    }

    @Override
    public Set<Class <? extends Phase>> dependencies() {
        return Stream.of(
                CreateNetworkPhase.class,
                ResolveGlobalEntityNamesPhase.class).collect(Collectors.toSet());
    }

    @Module
    interface ConnectionChecker {

        @Binding(BindingKind.INJECTED)
        TreeShadow tree();

        @Binding(BindingKind.INJECTED)
        GlobalNames globalNames();

        @Binding(BindingKind.INJECTED)
        Types types();

        @Binding(BindingKind.INJECTED)
        Context context();

        @Binding(BindingKind.INJECTED)
        NlNetworks networks();

        void visit(IRNode node);

        default void visit(CompilationTask task) {

            for (Instance instance: task.getNetwork().getInstances()) {
                GlobalEntityDecl entityDecl =  globalNames().entityDecl(instance.getEntityName(), true);
                if (entityDecl.getEntity() instanceof NlNetwork)
                    visit((NlNetwork) entityDecl.getEntity());
            }
        }
        default void visit(NlNetwork network) {

            network.getStructure().forEach(stmt -> {
                if (!(stmt instanceof StructureConnectionStmt)) {
                    String msg = String.format("Invalid structure statement %s", stmt.toString());
                    context().getReporter().report(new Diagnostic(Diagnostic.Kind.ERROR, msg, sourceUnit(stmt), stmt));
                }
            });
            network.getStructure().forEach(stmt -> visit(stmt));
            network.getEntities().stream()
                    .map(this::getEntityDecl).map(GlobalEntityDecl::getEntity)
                    .filter(entity -> entity instanceof NlNetwork)
                    .forEach(entity -> visit( (NlNetwork) entity ));

        }
        default void visit(StructureConnectionStmt stmt) {

            NlNetwork network = networks().enclosingNetwork(stmt);
            Optional<InstanceDecl> sourceInstance = findInstance(stmt.getSrc().getEntityName(), network.getEntities());
            String sourcePort = stmt.getSrc().getPortName();
            Optional<InstanceDecl> targetInstance = findInstance(stmt.getDst().getEntityName(), network.getEntities());
            String targetPort = stmt.getDst().getPortName();

            checkSourceInstanceExistence(stmt);
            checkTargetInstanceExistence(stmt);

            checkInstancePortExistence(sourceInstance, sourcePort, stmt, false);
            checkInstancePortExistence(targetInstance, targetPort, stmt, true);

            checkNetworkPortExistence(stmt);

            checkPortTypes(stmt);
        }

        default void visit(StructureForeachStmt foreachStmt){
        }

        default void checkSourceInstanceExistence(StructureConnectionStmt stmt) {

            if (stmt.getSrc().getEntityName() != null) {
                Optional<InstanceDecl> instanceDecl =  findSourceInstanceDecl(stmt);
                if (!instanceDecl.isPresent()) {
                    String msg = String.format("Could not find instance %s in connection %s",
                            stmt.getSrc().getEntityName(), connectionName(stmt));
                    context().getReporter().report(
                            new Diagnostic(Diagnostic.Kind.ERROR, msg, sourceUnit(stmt), stmt));
                }
            }

        }

        default void checkTargetInstanceExistence(StructureConnectionStmt stmt) {

            if (stmt.getDst().getEntityName() != null) {
                Optional<InstanceDecl> instanceDecl =  findTargetInstanceDecl(stmt);
                if (!instanceDecl.isPresent()) {
                    String msg = String.format("Could not find instance %s in connection %s",
                            stmt.getDst().getEntityName(), connectionName(stmt));
                    context().getReporter().report(
                            new Diagnostic(Diagnostic.Kind.ERROR, msg, sourceUnit(stmt), stmt));
                }
            }

        }



        default void checkInstancePortExistence(Optional<InstanceDecl> instance, String port, StructureConnectionStmt stmt,
                                        boolean isTargetPort) {

            if (instance.isPresent()) {
                try {


                    GlobalEntityDecl entityDecl = getEntityDecl(instance.get());
                    Entity entity = entityDecl.getEntity();
                    String entityName = entityDecl.getName();
                    if (isTargetPort &&
                            !findInputPort(entity, port).isPresent()) {
                        String msg = String.format("could not find input port %s in entity %s used in connection %s",
                                port, entityName.toString(), connectionName(stmt));
                        context().getReporter().report(
                                new Diagnostic(Diagnostic.Kind.ERROR, msg, sourceUnit(stmt), stmt));


                    } else if (!isTargetPort &&
                            !findOutputPort(entity, port).isPresent()) {
                        String msg = String.format("could not find output port %s in entity %s used in connection %s",
                                port, entityName.toString(), connectionName(stmt));
                        context().getReporter().report(
                                new Diagnostic(Diagnostic.Kind.ERROR, msg, sourceUnit(stmt), stmt));

                    }

                } catch (ClassCastException e) {
                    context().getReporter()
                            .report(new Diagnostic(Diagnostic.Kind.ERROR, "Only Instance Entity expression are " +
                                    "supported, compile with the " +
                                    "following setting: --set experimental-network-elaboration=on"));
                }
            }

        }

        default void checkNetworkPortExistence(StructureConnectionStmt stmt) {
            NlNetwork ownerNetwork = networks().enclosingNetwork(stmt);
            GlobalEntityDecl networkDecl = getEntityDecl(ownerNetwork);

            if ((stmt.getSrc().getEntityName() == null) &&
                    !ownerNetwork.getInputPorts().stream()
                            .filter(p -> p.getName().equals(stmt.getSrc().getPortName())).findFirst().isPresent()) {
                String msg = String.format("Could not find network input port %s in network %s used in connection %s",
                        stmt.getSrc().getPortName(), networkDecl.getName(), connectionName(stmt));
                context().getReporter()
                        .report(new Diagnostic(Diagnostic.Kind.ERROR, msg, sourceUnit(stmt), stmt));

            } else if ((stmt.getDst().getEntityName() == null) &&
                    !ownerNetwork.getOutputPorts().stream()
                            .filter(p -> p.getName().equals(stmt.getDst().getPortName())).findFirst().isPresent()) {
                String msg = String.format("Could not find network output port %s in network %s used in connection %s",
                        stmt.getDst().getPortName(), networkDecl.getName(), connectionName(stmt));
                context().getReporter()
                        .report(new Diagnostic(Diagnostic.Kind.ERROR, msg, sourceUnit(stmt), stmt));
            }
        }
        default void checkPortTypes(StructureConnectionStmt stmt) {

            Optional<PortDecl> sourcePort = getSourcePortDecl(stmt);
            Optional<PortDecl> targetPort = getTargetPortDecl(stmt);

            if (sourcePort.isPresent() && targetPort.isPresent()) {

                Type sourceType = types().declaredPortType(sourcePort.get());
                Type targetType = types().declaredPortType(targetPort.get());

                if (!sourceType.equals(targetType)) {
                    String msg = String.format("Connection %s has source port type %s and target port type %s",
                            connectionName(stmt), sourceType.toString(), targetType.toString());
                    context().getReporter().report(
                            new Diagnostic(Diagnostic.Kind.WARNING, msg, sourceUnit(stmt), stmt));

                }
            }
        }

        default Optional<PortDecl> getTargetPortDecl(StructureConnectionStmt stmt) {


            Optional<PortDecl> port = Optional.empty();
            if (stmt.getDst().getEntityName() != null) {

                // instance should be present
                Optional<InstanceDecl> instanceDecl = findTargetInstanceDecl(stmt);
                if (instanceDecl.isPresent()) {
                    GlobalEntityDecl entityDecl = getEntityDecl(instanceDecl.get());
                    Optional<PortDecl> entityPort = findInputPort(entityDecl.getEntity(), stmt.getDst().getPortName());
                    if (entityPort.isPresent()) {
                        port = entityPort;

                    }
                }
            } else {
                // instance is not present, portdecl comes from network output port

                port = findNetworkOutputPort(stmt);
            }

            return port;
        }

        default Optional<PortDecl> getSourcePortDecl(StructureConnectionStmt stmt) {


            Optional<PortDecl> port = Optional.empty();

            if (stmt.getSrc().getEntityName() != null) {

                Optional<InstanceDecl> instanceDecl = findSourceInstanceDecl(stmt);
                if (instanceDecl.isPresent()) {
                    GlobalEntityDecl entityDecl = getEntityDecl(instanceDecl.get());
                    Optional<PortDecl> entityPort = findOutputPort(entityDecl.getEntity(), stmt.getSrc().getPortName());
                    if (entityPort.isPresent()) {
                        port = entityPort;

                    }
                }
            } else {
                port = findNetworkInputPort(stmt);
            }
            return port;
        }

        default Optional<PortDecl> findInputPort(Entity entity, String port) {
            return entity.getInputPorts().stream().filter(p -> p.getName().equals(port)).findFirst();
        }
        default Optional<PortDecl> findOutputPort(Entity entity, String port) {
            return entity.getOutputPorts().stream().filter(p -> p.getName().equals(port)).findFirst();
        }

        default Optional<PortDecl> findNetworkInputPort(StructureConnectionStmt stmt) {

            NlNetwork ownerNetwork = networks().enclosingNetwork(stmt);
            return ownerNetwork.getInputPorts().stream()
                    .filter(p -> p.getName().equals(stmt.getSrc().getPortName())).findFirst();
        }

        default Optional<PortDecl> findNetworkOutputPort(StructureConnectionStmt stmt) {

            NlNetwork ownerNetwork = networks().enclosingNetwork(stmt);
            return ownerNetwork.getOutputPorts().stream()
                    .filter(p -> p.getName().equals(stmt.getDst().getPortName())).findFirst();
        }

        default GlobalEntityDecl getEntityDecl(InstanceDecl instance) {

            assert instance.getEntityExpr() instanceof EntityInstanceExpr;
            EntityInstanceExpr expr = (EntityInstanceExpr) instance.getEntityExpr();
            assert expr.getEntityName() instanceof EntityReferenceGlobal;
            QID entityName = ((EntityReferenceGlobal) expr.getEntityName()).getGlobalName();
            return globalNames().entityDecl(entityName, true);

        }
        default Optional<InstanceDecl> findInstance(String name, ImmutableList<InstanceDecl> instances) {
            ImmutableList<InstanceDecl> potentialInstances =
                    instances.stream().filter(i -> i.getInstanceName().equals(name)).collect(ImmutableList.collector());
            if (potentialInstances.size() == 1) {
                return Optional.of(potentialInstances.get(0));
            } else {
                return Optional.empty();
            }
        }

        default String connectionName(StructureConnectionStmt stmt) {

            String sourceInstance = stmt.getSrc().getEntityName();
            String sourcePort = stmt.getSrc().getPortName();
            String targetInstance = stmt.getDst().getEntityName();
            String targetPort = stmt.getDst().getPortName();
            return String.format("%s%s-->%s%s",
                    sourceInstance == null ? "" : sourceInstance + ".",
                    sourcePort,
                    targetInstance == null ? "" : targetInstance + ".",
                    targetPort);
        }

        default SourceUnit sourceUnit(IRNode node) {
            return sourceUnit(tree().parent(node));
        }
        default SourceUnit sourceUnit(SourceUnit unit) {
            return unit;
        }

        default GlobalEntityDecl getEntityDecl(NlNetwork network) {

            IRNode node = tree().parent(network);
            while(!(node instanceof GlobalEntityDecl)) {
                node = tree().parent(node);
            }

            return (GlobalEntityDecl)node;
        }


        default Optional<InstanceDecl> findSourceInstanceDecl(StructureConnectionStmt stmt) {
            return findInstance(stmt.getSrc().getEntityName(), networks().enclosingNetwork(stmt).getEntities());
        }

        default Optional<InstanceDecl> findTargetInstanceDecl(StructureConnectionStmt stmt) {
            return findInstance(stmt.getDst().getEntityName(), networks().enclosingNetwork(stmt).getEntities());
        }
    }
}
