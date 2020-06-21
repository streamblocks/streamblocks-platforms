package ch.epfl.vlsc.analysis.partitioning.phase;

import ch.epfl.vlsc.analysis.partitioning.util.PartitionSettings;

import gurobi.*;
import org.w3c.dom.Document;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;
import se.lth.cs.tycho.settings.Setting;
import se.lth.cs.tycho.compiler.Compiler;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartitioningAnalysisPhase implements Phase {

    private Map<Instance, Long> executionCost;
    private Map<Connection, Long> commCost;

    public PartitioningAnalysisPhase() {
        this.executionCost = new HashMap<Instance, Long>();
        this.commCost = new HashMap<Connection, Long>();
    }
    @Override
    public String getDescription() {
        return "finds a partition based on profiling information";
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(
                PartitionSettings.profilePath,
                PartitionSettings.cpuCoreCount);
    }
    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        Network network = task.getNetwork();
        boolean hasProfilePath = context.getConfiguration().isDefined(PartitionSettings.profilePath) &&
                context.getConfiguration().get(PartitionSettings.profilePath) != null;
        if (!hasProfilePath) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Profile path is not specified! use " +
                            "--set profile-path= to set the profiling path"));

        }

        Path profilePath = context.getConfiguration().get(PartitionSettings.profilePath);
        try {

            File profileXml = new File(profilePath.toUri());
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(profileXml);
            // normalize the doc
            doc.getDocumentElement().normalize();


            NodeList instanceList = doc.getElementsByTagName("Instance");
            for (int instanceId = 0; instanceId < instanceList.getLength(); instanceId ++) {

                Node instNode = instanceList.item(instanceId);
                if (instNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element instElem = (Element) instNode;
                    parseInstance(instElem, network, context);


                }

            }

            NodeList connectionList = doc.getElementsByTagName("Connection");

            for (int connectionId = 0; connectionId < connectionList.getLength(); connectionId ++) {
                Node conNode = connectionList.item(connectionId);
                if (conNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element conElem = (Element) conNode;
                    parseConnection(conElem, network, context);
                }
            }


        } catch (Exception e) {
            throw new CompilationException(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Error opening profile data "
                            + profilePath.toString()));
        }

        try {

            GRBEnv env = new GRBEnv(true);
            Path logfile = context.getConfiguration().get(Compiler.targetPath).resolve("partitions.log");
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Logging into " + logfile.toString()));

            env.set("LogFile", logfile.toString());
            env.start();

            GRBModel model = new GRBModel(env);

            int numPartitions =
                    context.getConfiguration().isDefined(PartitionSettings.cpuCoreCount) ?
                            context.getConfiguration().get(PartitionSettings.cpuCoreCount) :
                            PartitionSettings.cpuCoreCount.defaultValue(context.getConfiguration());
            Map<Instance, List<GRBVar>> partitionVars = new HashMap<>();

            // Partition variables
            for (Instance instance: network.getInstances()) {
                GRBLinExpr constraintExpr = new GRBLinExpr();
                List<GRBVar> vars = new ArrayList<>();
                for (int part = 0; part < numPartitions; part ++) {
                    GRBVar partitionSelector =
                            model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                                    String.format("p_%d_%s", part, instance.getInstanceName()));
                    vars.add(partitionSelector);
                    constraintExpr.addTerm(1.0, partitionSelector);
                }
                partitionVars.put(instance, vars);
                // unique partition constraint
                model.addConstr(constraintExpr, GRB.EQUAL,
                        1.0, String.format("unique partition %s", instance.getInstanceName()));
            }

            GRBVar[] ticks = new GRBVar[numPartitions];
            // Partition execution tick
            for (int part = 0; part < numPartitions; part ++) {
                GRBVar partitionTicks =
                        model.addVar(0.0, ticksUpperBound(), 0.0, GRB.INTEGER, "ticks_" + part);
                ticks[part] = partitionTicks;
                GRBLinExpr ticksExpr = new GRBLinExpr();
                for (Instance instance: network.getInstances()) {
                    GRBVar partitionSelector = partitionVars.get(instance).get(part);
                    Long instanceTicks = executionCost.get(instance);
                    ticksExpr.addTerm(instanceTicks, partitionSelector);
                }
                // partition ticks is the sum of ticks
                model.addConstr(partitionTicks, GRB.EQUAL, ticksExpr,
                        String.format("ticks constraint %d", part));
            }
            GRBVar totalTicks = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "total_execution_ticks");

            // Total ticks is the max of parallel ticks
            model.addGenConstrMax(totalTicks, ticks, 0.0, "parallel tasks time constraint");


            // Set the objective to minimize total ticks
            GRBLinExpr objectiveExpr = new GRBLinExpr();
            objectiveExpr.addTerm(1.0, totalTicks);
            model.setObjective(objectiveExpr, GRB.MINIMIZE);

            // Find a solution
            model.optimize();

            context.getReporter().report(
                    new Diagnostic(
                            Diagnostic.Kind.INFO, String.format("Partitions 0 to %d found: ", numPartitions - 1)));

            for (Instance instance: network.getInstances()) {
                int maxIndex = 0;
                double maxVal = 0;
                for (GRBVar part: partitionVars.get(instance)) {
                    if (part.get(GRB.DoubleAttr.X) > maxVal) {
                        maxVal = part.get(GRB.DoubleAttr.X);
                        maxIndex = partitionVars.get(instance).indexOf(part);
                    }
                }
                context.getReporter().report(
                        new Diagnostic(Diagnostic.Kind.INFO,
                                String.format("Instance %s -> partition %d", instance.getInstanceName(), maxIndex)));
            }
            model.dispose();
            env.dispose();


        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }

        return null;
    }

    private String stripAffinity(String orig) {
        return orig.substring(0, orig.indexOf("/"));
    }

    private void parseConnection(Element connection, Network network, Context context) {

        String source = stripAffinity(connection.getAttribute("src"));
        String sourcePort = connection.getAttribute("src-port");
        String target = stripAffinity(connection.getAttribute("dst"));
        String targetPort = connection.getAttribute("dst-port");
        Long bandwidth = Long.valueOf(connection.getAttribute("bandwidth"));

        network.getConnections().forEach(con -> {
            if(con.getSource().getInstance().isPresent() && con.getTarget().getInstance().isPresent()) {
                if (con.getSource().getInstance().get().equals(source) && con.getSource().getPort().equals(sourcePort) &&
                con.getTarget().getInstance().get().equals(target) && con.getTarget().getPort().equals(targetPort))
                    if(commCost.containsKey(con))
                        throw new CompilationException(
                                new Diagnostic(
                                        Diagnostic.Kind.ERROR,
                                        String.format("Duplicated bandwidth " +
                                                "for connection %s.%s --> %s.%s",
                                                source, sourcePort, target, targetPort)));

                    else {

                        context.getReporter().report(new Diagnostic(Diagnostic.Kind.INFO,
                                String.format("Connection: %s.%s --> %s.%s  %d tokens",
                                        source, sourcePort, target, targetPort, bandwidth)));
                        commCost.put(con, bandwidth);
                    }
            }
        });

    }

    private void parseInstance(Element instance, Network network, Context context) {
        String strippedName = stripAffinity(instance.getAttribute("actor-id"));
        Long complexity = Long.valueOf(instance.getAttribute("complexity"));

        network.getInstances().stream().forEach(inst -> {

            if (inst.getInstanceName().equals(strippedName)) {
                context.getReporter().report(
                        new Diagnostic(Diagnostic.Kind.INFO,
                                "Instance: " + strippedName + "  " +
                                        complexity.toString() + " ticks "));
                executionCost.put(inst, complexity);
            }
        });
    }

    private Long ticksUpperBound() {
        return executionCost.values().stream().reduce((a, b) -> a + b).get();
    }



}
