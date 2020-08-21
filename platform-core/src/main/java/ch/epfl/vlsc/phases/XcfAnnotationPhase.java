package ch.epfl.vlsc.phases;

import ch.epfl.vlsc.configuration.Configuration;
import ch.epfl.vlsc.configuration.ConfigurationManager;
import ch.epfl.vlsc.configuration.Configurations;
import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Compiler;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.ToolAttribute;
import se.lth.cs.tycho.ir.ToolValueAttribute;
import se.lth.cs.tycho.ir.expr.ExprLiteral;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.ElaborateNetworkPhase;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import javax.tools.Tool;
import javax.xml.bind.JAXBException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class XcfAnnotationPhase implements Phase {


    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {
        QID taskQID = task.getIdentifier();
        Path xcfPath = context.getConfiguration().get(Compiler.xcfPath);

        if (xcfPath != null) {
            if (Files.isReadable(xcfPath)) {
                File xcfFile = xcfPath.toFile();
                try {
                    ConfigurationManager manager = new ConfigurationManager(xcfFile);
                    Configuration configuration = manager.getConfiguration();

                    QID xcfQID = Configurations.getQID(configuration);

                    // -- Check if we have the same QID
                    if (!taskQID.equals(xcfQID)) {
                        context.getReporter().report(
                                new Diagnostic(
                                        Diagnostic.Kind.WARNING, "The XCF configuration file Network ID is different from " +
                                        "the one given for compilation, ignoring the configuration !")
                        );

                        return task;
                    }

                    // -- Check if all instances of out network are the same as in the XCF
                    if (!Configurations.hasAllInstances(configuration, task.getNetwork())) {
                        return task;
                    }

                    // -- Get Instances for this platform

                    Map<String, List<String>> codeGenInstanceMap = Configurations.getCodeGenMap(configuration);
                    ImmutableList.Builder<Instance> instanceBuilder = ImmutableList.builder();

                    for (String codeGen: codeGenInstanceMap.keySet()) {

                        List<String> currentInstanceName = codeGenInstanceMap.get(codeGen);
                        for (Instance instance : task.getNetwork().getInstances()) {

                            if (currentInstanceName.contains(instance.getInstanceName())) {
                                ImmutableList.Builder<ToolAttribute> attributeBuilder = ImmutableList.builder();

                                // -- get all the currently defined attributes for each instance except for existing partition attributes
                                attributeBuilder.addAll(
                                        instance.getAttributes().stream().filter(
                                                attr -> !attr.getName().equals("partition")
                                        ).map(ToolAttribute::deepClone).collect(ImmutableList.collector()));

                                // See if partition has already been defined for the instance
                                Optional<ToolValueAttribute> partitionAttr = instance.getAttributes().stream().filter(
                                        attr -> attr.getName().equals("partition"))
                                        .map(attr -> (ToolValueAttribute) attr).findAny();

                                if (partitionAttr.isPresent()) {
                                    try {

                                        ExprLiteral partExpr = (ExprLiteral) partitionAttr.get().getValue();
                                        boolean partitionsMatch =
                                                partExpr.getText()
                                                        .equals("\"" +
                                                                Configurations.
                                                                        codeGeneratorName(configuration, codeGen) + "\"");
                                        if (!partitionsMatch) {
                                            context.getReporter().report(new Diagnostic(

                                                    Diagnostic.Kind.WARNING, "XCF configurations overwrites " +
                                                    "partition attribute " + partExpr.getText() + " to " + codeGen +
                                                    " for instance " + instance.getInstanceName()));

                                        }
                                    } catch (ClassCastException e) {
                                        throw new CompilationException(
                                                new Diagnostic(
                                                        Diagnostic.Kind.ERROR,
                                                        "Could invalid partition attribute " +
                                                                "expression type for instance " +
                                                                instance.getInstanceName()
                                                )
                                        );

                                    }
                                }
                                ToolValueAttribute partitionAttribute = new ToolValueAttribute("partition",
                                        new ExprLiteral(ExprLiteral.Kind.String,"\"" + codeGen + "\""));
                                attributeBuilder.add(partitionAttribute);

                                instanceBuilder.add(instance.withAttributes(attributeBuilder.build()));
                            }

                        }
                    }

                    CompilationTask transformedTask =
                            task.withNetwork(task.getNetwork().withInstances(instanceBuilder.build()));

                    // -- make sure all instances are kept
                    allInstancesKept(task, transformedTask);

                    // -- make sure all instances have partitions
                    if (!instancesHaveDefinedPartitions(transformedTask, context)) {
                        throw new CompilationException(
                            new Diagnostic(Diagnostic.Kind.ERROR,
                                    "some instances do not have defined partitions in the XCF file")
                        );
                    }
                    return transformedTask;
                } catch (JAXBException e) {
                    e.printStackTrace();
                }
            } else {
                context.getReporter().report(
                        new Diagnostic(Diagnostic.Kind.ERROR, "Could not read xcf config " + xcfPath.toString())
                );
            }
        }

        return task;
    }

    @Override
    public Set<Class<? extends Phase>> dependencies() {
        return Collections.singleton(ElaborateNetworkPhase.class);
    }

    public boolean instancesHaveDefinedPartitions(CompilationTask task, Context context) {

        return task.getNetwork().getInstances().map(
                inst -> {
                    boolean hasPartition = inst.getAttributes().stream()
                            .anyMatch(attr -> attr.getName().equals("partition"));

                    if (!hasPartition) {
                        context.getReporter().report(new Diagnostic(
                                Diagnostic.Kind.ERROR,
                                "instance " + inst.getInstanceName() + " has no partition! Make sure the XCF" +
                                        " configuration defines partitions for all instances"
                        ));
                    }
                    return hasPartition;
                }).stream().reduce((a, b) -> a && b).orElse(false);


    }

    public void allInstancesKept(CompilationTask originalTask, CompilationTask newTask) {

        if (originalTask.getNetwork().getInstances().size() != newTask.getNetwork().getInstances().size()) {
            throw new CompilationException(
                    new Diagnostic(
                            Diagnostic.Kind.ERROR, "Internal error in " + this.getName() + " phase"
                    )
            );
        }
    }
}
