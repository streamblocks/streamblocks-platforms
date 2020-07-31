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

import javax.xml.bind.JAXBException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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


                    String platformName = context.getPlatform().name();

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
                    List<String> instancesNames = Configurations.getInstances(configuration, platformName);

                    List<Instance> newInstances = new ArrayList<>();
                    for (Instance instance : task.getNetwork().getInstances()) {

                        ImmutableList.Builder attributes = ImmutableList.builder();
                        attributes.addAll(instance.getAttributes().map(ToolAttribute::deepClone));

                        if (instancesNames.contains(instance.getInstanceName())) {
                            ToolValueAttribute partitionAttribute = new ToolValueAttribute("partition",
                                    new ExprLiteral(ExprLiteral.Kind.String,"\""+ Configurations.codeGeneratorName(configuration, platformName) +"\""));
                            attributes.add(partitionAttribute);
                            Instance pInstance = instance.withAttributes(attributes.build());
                            newInstances.add(pInstance);
                        }else{
                            ToolValueAttribute partitionAttribute = new ToolValueAttribute("partition",
                                    new ExprLiteral(ExprLiteral.Kind.String,"\""+ "undefined" +"\""));
                            attributes.add(partitionAttribute);
                            Instance pInstance = instance.withAttributes(attributes.build());
                            newInstances.add(pInstance);
                        }
                    }
                    return task.withNetwork(task.getNetwork().withInstances(newInstances));
                } catch (JAXBException e) {
                    e.printStackTrace();
                }
            }
        }

        return task;
    }

    @Override
    public Set<Class<? extends Phase>> dependencies() {
        return Collections.singleton(ElaborateNetworkPhase.class);
    }
}
