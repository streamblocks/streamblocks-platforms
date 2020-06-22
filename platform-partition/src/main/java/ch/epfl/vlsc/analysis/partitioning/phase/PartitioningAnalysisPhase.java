package ch.epfl.vlsc.analysis.partitioning.phase;

import ch.epfl.vlsc.analysis.partitioning.util.PartitionSettings;

import gurobi.*;
import org.w3c.dom.*;

import se.lth.cs.tycho.attribute.Types;
import se.lth.cs.tycho.ir.entity.Entity;
import se.lth.cs.tycho.attribute.GlobalNames;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;

import se.lth.cs.tycho.ir.entity.PortDecl;
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
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartitioningAnalysisPhase implements Phase {

    private Map<Instance, Long> executionCost;
    private Map<Connection, Long> commCost;
    private Map<Connection, Long> connectionSize;

    public PartitioningAnalysisPhase() {
        this.executionCost = new HashMap<Instance, Long>();
        this.commCost = new HashMap<Connection, Long>();
        this.connectionSize = new HashMap<Connection, Long>();
    }
    @Override
    public String getDescription() {
        return "finds a partition based on profiling information";
    }

    @Override
    public List<Setting<?>> getPhaseSettings() {
        return ImmutableList.of(
                PartitionSettings.profilePath,
                PartitionSettings.configPath,
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


        parseNetworkProfile(network, context);
        Map<Integer, List<Instance>> partitions = findPartitions(task, context);
        createConfig(partitions, context);

        return task;
    }

    private void createConfig(Map<Integer, List<Instance>> partitions, Context context) {

        Path configPath =
            context.getConfiguration().isDefined(PartitionSettings.configPath) ?
                context.getConfiguration().get(PartitionSettings.configPath) :
                    context.getConfiguration().get(Compiler.targetPath).resolve("bin/config.xml");

        try {
            // the config xml doc
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element configRoot = doc.createElement("Configuration");
            doc.appendChild(configRoot);
            Element partitioningRoot = doc.createElement("Partitioning");
            configRoot.appendChild(partitioningRoot);

            partitions.forEach( (partition, instances) -> {
                Element partitionRoot = doc.createElement("Partition");
                partitionRoot.setAttribute("id", partition.toString());
                instances.forEach( instance -> {
                    Element instanceRoot = doc.createElement("Instance");
                    instanceRoot.setAttribute("actor-id", instance.getInstanceName() + "/0");
                    partitionRoot.appendChild(instanceRoot);
                });
                partitioningRoot.appendChild(partitionRoot);
            });

            StreamResult configStream = new StreamResult(new File(configPath.toUri()));
            DOMSource configDom = new DOMSource(doc);
            Transformer configTransformer = TransformerFactory.newInstance().newTransformer();
            configTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
            configTransformer.transform(configDom, configStream);

            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Config file saved to " + configPath.toString()));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private Map<Integer, List<Instance>> findPartitions(CompilationTask task, Context context) {

        Network network = task.getNetwork();
        Map<Integer, List<Instance>> partitions = new HashMap<>();
        try {

            GRBEnv env = new GRBEnv(true);
            Path logPath = context.getConfiguration().get(Compiler.targetPath);
            Path logfile = logPath.resolve("partitions.log");

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
            for (Instance instance : network.getInstances()) {
                GRBLinExpr constraintExpr = new GRBLinExpr();
                List<GRBVar> vars = new ArrayList<>();
                for (int part = 0; part < numPartitions; part++) {
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

            GRBVar[] execTicks = new GRBVar[numPartitions];
            // Partition execution tick
            for (int part = 0; part < numPartitions; part++) {
                GRBVar partitionTicks =
                        model.addVar(0.0, ticksUpperBound(), 0.0, GRB.INTEGER, "ticks_" + part);
                execTicks[part] = partitionTicks;
                GRBLinExpr ticksExpr = new GRBLinExpr();
                for (Instance instance : network.getInstances()) {
                    GRBVar partitionSelector = partitionVars.get(instance).get(part);
                    Long instanceTicks = executionCost.get(instance);
                    ticksExpr.addTerm(instanceTicks, partitionSelector);
                }
                // partition ticks is the sum of ticks
                model.addConstr(partitionTicks, GRB.EQUAL, ticksExpr,
                        String.format("ticks constraint %d", part));
            }
            GRBVar totalExecTicks = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.INTEGER, "total_execution_ticks");

            // Total ticks is the max of parallel ticks
            model.addGenConstrMax(totalExecTicks, execTicks, 0.0, "parallel tasks time constraint");

            Map<Connection, GRBVar> commTicks = new HashMap<>();
            // communication cost
            for (Connection connection : network.getConnections()) {
                Connection.End sourceEnd = connection.getSource();

                Connection.End targetEnd = connection.getTarget();

                Instance sourceInstance = findSourceInstance(sourceEnd, network);
                Instance targetInstance = findTargetInstance(targetEnd, network);

                List<GRBVar> sourceVars = partitionVars.get(sourceInstance);
                List<GRBVar> targetVars = partitionVars.get(targetInstance);

                Long intraTicks = Long.valueOf(256);
                Long interTicks = Long.valueOf(5617);

                GRBQuadExpr sumOfProducts = new GRBQuadExpr();
                for (int sourcePart = 0; sourcePart < numPartitions; sourcePart++) {

                    for (int targetPart = 0; targetPart < numPartitions; targetPart++) {
                        GRBVar sourcePartVar = sourceVars.get(sourcePart);
                        GRBVar targetPartVar = targetVars.get(targetPart);
                        Long tokensTransferred = commCost.get(connection);
                        Long tokenSize = connectionSize.get(connection);
                        Double commTime = Double.valueOf(tokensTransferred * tokenSize) / 4096;
                        if (sourcePart == targetPart)
                            commTime = commTime * intraTicks;
                        else
                            commTime = commTime * interTicks;
                        sumOfProducts.addTerm(commTime, sourcePartVar, targetPartVar);

                    }
                }
                String connectionName = String.format("%s.%s->%s.%s",
                        sourceEnd.getInstance().get(), sourceEnd.getPort(),
                        targetEnd.getInstance().get(), targetEnd.getPort());
                GRBVar connectionTicks = model.addVar(
                        0.0,
                        GRB.INFINITY,
                        0.0,
                        GRB.INTEGER,
                        connectionName + "_ticks");
                commTicks.put(connection, connectionTicks);
                model.addQConstr(connectionTicks, GRB.EQUAL, sumOfProducts, "constraint " + connectionName);
            }

            GRBVar totalCommTicks = model.addVar(
                    0.0,
                    GRB.INFINITY,
                    0.0,
                    GRB.INTEGER,
                    "total communication ticks");
            GRBLinExpr sumCommTicks = new GRBLinExpr();
            commTicks.values().forEach(v -> sumCommTicks.addTerm(1.0, v));
            model.addConstr(totalCommTicks, GRB.EQUAL, sumCommTicks, "sum of communication ticks");

            // Set the objective to minimize total ticks
            GRBLinExpr objectiveExpr = new GRBLinExpr();
            objectiveExpr.addTerm(1.0, totalExecTicks);
            objectiveExpr.addTerm(1.0, totalCommTicks);
            model.setObjective(objectiveExpr, GRB.MINIMIZE);

            Path modelFile = logPath.resolve("model.lp");
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.INFO, "Writing model into " + modelFile.toString()));
            model.write(modelFile.toString());

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
                if (partitions.containsKey(maxIndex)) {
                    partitions.get(maxIndex).add(instance);
                } else {
                    List<Instance> ls = new ArrayList<Instance>();
                    ls.add(instance);
                    partitions.put(maxIndex, ls);
                }
                context.getReporter().report(
                        new Diagnostic(Diagnostic.Kind.INFO,
                                String.format("Instance %s -> partition %d", instance.getInstanceName(), maxIndex)));
            }
//            Path modelIlp = logPath.resolve("model.ilp");
//            Path solutionFile = logPath.resolve("model.sol");

//            model.write(modelIlp.toString());
//            model.write(solutionFile.toString());
            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }
        return partitions;
    }
    private String stripAffinity(String orig) {
        return orig.substring(0, orig.indexOf("/"));
    }


    /**TODO: handle potential errors*/
    private Instance findSourceInstance(Connection.End source, Network network) {

        return network.getInstances().stream()
                .filter(
                    instance ->
                        instance.getInstanceName().equals(source.getInstance().get())
                    ).findFirst().get();

    }
    /**TODO: handle potential errors*/
    private Instance findTargetInstance(Connection.End target, Network network) {
        return network.getInstances().stream()
                .filter(
                    instance ->
                        instance.getInstanceName().equals(target.getInstance().get())

                        ).findFirst().get();
    }


    private void parseConnection(Element connection, Network network, Context context) {

        String source = stripAffinity(connection.getAttribute("src"));
        String sourcePort = connection.getAttribute("src-port");
        String target = stripAffinity(connection.getAttribute("dst"));
        String targetPort = connection.getAttribute("dst-port");
        Long bandwidth = Long.valueOf(connection.getAttribute("bandwidth"));
        Long tokenSize = Long.valueOf(connection.getAttribute("token-size"));
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
                        connectionSize.put(con, tokenSize);
                    }
            }
        });

    }

    private void parseNetworkProfile(Network network, Context context) {

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
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.ERROR, "Error parsing profile data "
                            + profilePath.toString()));
            e.printStackTrace();
        }
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
