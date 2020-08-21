package ch.epfl.vlsc.configuration;

import com.google.common.collect.Ordering;
import se.lth.cs.tycho.ir.QID;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.util.Lists;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Configurations {

    public static QID getQID(Configuration configuration) {
        String id = configuration.getNetwork().getId();
        return QID.parse(id);
    }

    public static boolean hasAllInstances(Configuration configuration, Network network) {
        List<String> instancesNames = network.getInstances()
                .stream()
                .map(instance -> instance.getInstanceName())
                .collect(Collectors.toList());


        List<String> partInstances = getAllInstances(configuration);
        return Lists.equals(Ordering.natural().sortedCopy(instancesNames), Ordering.natural().sortedCopy(partInstances));
    }

    private static List<String> getAllInstances(Configuration configuration) {
        List<String> instances = new ArrayList<>();
        for (Configuration.Partitioning.Partition p : configuration.getPartitioning().partition) {
            instances.addAll(p.getInstance().stream().map(i -> i.getId()).collect(Collectors.toList()));
        }
        return instances;
    }

    private static Map<Configuration.CodeGenerators.CodeGenerator, List<Configuration.Partitioning.Partition>> getCodeGenAndPartitions(Configuration configuration) {
        Map<Configuration.CodeGenerators.CodeGenerator, List<Configuration.Partitioning.Partition>> cgPartitions = new HashMap<>();

        for (Configuration.CodeGenerators.CodeGenerator cg : configuration.codeGenerators.codeGenerator) {
            for (Configuration.Partitioning.Partition p : configuration.getPartitioning().partition) {
                if (p.codeGenerator.equals(cg.getId())) {
                    List<Configuration.Partitioning.Partition> partitions;
                    if (cgPartitions.containsKey(cg)) {
                        partitions = cgPartitions.get(cg);
                    } else {
                        partitions = new ArrayList<>();
                    }
                    partitions.add(p);
                    cgPartitions.put(cg, partitions);
                }
            }
        }
        return cgPartitions;
    }

    public static List<String> getInstances(Configuration configuration, String platform) {
        Map<Configuration.CodeGenerators.CodeGenerator, List<Configuration.Partitioning.Partition>> cgPartitions = getCodeGenAndPartitions(configuration);
        List<String> instances = new ArrayList<>();

        for (Configuration.CodeGenerators.CodeGenerator cg : cgPartitions.keySet()) {
            if (cg.getPlatform().equals(platform)) {
                for (Configuration.Partitioning.Partition p : cgPartitions.get(cg)) {
                    for (Configuration.Partitioning.Partition.Instance i : p.getInstance()) {
                        instances.add(i.getId());
                    }
                }
            }
        }

        return instances;
    }

    public static String codeGeneratorName(Configuration configuration, String platform) {
        Map<String, List<String>> platformCodeGenName = new HashMap<>();
        for (Configuration.CodeGenerators.CodeGenerator cg : configuration.codeGenerators.codeGenerator) {
            List<String> codegens;
            if (platformCodeGenName.containsKey(cg.getPlatform())) {
                codegens = platformCodeGenName.get(cg);
            } else {
                codegens = new ArrayList<>();
            }
            codegens.add(cg.id);
            platformCodeGenName.put(cg.getPlatform(), codegens);
        }

        if (platformCodeGenName.containsKey(platform)) {
            return platformCodeGenName.get(platform).get(0);
        } else {
            return "not-defined";
        }


    }

    public static Map<String, List<String>> getCodeGenMap(Configuration configuration) {

        Map<String, ImmutableList.Builder<String>> instancesBuilder = new HashMap<>();

        for (Configuration.CodeGenerators.CodeGenerator codeGen: configuration.getCodeGenerators().getCodeGenerator()) {
            instancesBuilder.put(codeGen.getId(), ImmutableList.builder());
        }
        for (Configuration.Partitioning.Partition part : configuration.getPartitioning().getPartition()) {

            String codeGen = part.getCodeGenerator();
            instancesBuilder.get(codeGen).addAll(
                    part.getInstance().stream()
                            .map(Configuration.Partitioning.Partition.Instance::getId)
                            .collect(ImmutableList.collector()));



        }
        Map<String, List<String>> instances = new HashMap<>();
        for (String codeGen: instancesBuilder.keySet()) {
            instances.put(codeGen, instancesBuilder.get(codeGen).build());
        }
        return instances;
    }


}
