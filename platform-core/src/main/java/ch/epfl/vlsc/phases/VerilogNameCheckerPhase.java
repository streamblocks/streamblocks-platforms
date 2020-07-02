package ch.epfl.vlsc.phases;


import se.lth.cs.tycho.compiler.CompilationTask;
import se.lth.cs.tycho.compiler.Context;
import se.lth.cs.tycho.ir.network.Connection;
import se.lth.cs.tycho.ir.network.Instance;
import se.lth.cs.tycho.ir.network.Network;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.phase.Phase;
import se.lth.cs.tycho.reporting.CompilationException;
import se.lth.cs.tycho.reporting.Diagnostic;

import java.util.HashMap;
import java.util.Map;


public class VerilogNameCheckerPhase implements Phase {


    private final ImmutableList<String> keywords;
    private Map<String, String> newNames;
    private Context context;

    @Override
    public String getDescription() {
        return "checks for usages of Verilog keywords as actor names and renames the actors with such names";
    }



    @Override
    public CompilationTask execute(CompilationTask task, Context context) throws CompilationException {

        this.context = context;
        ImmutableList<Instance> instances = task.getNetwork().getInstances().map(this::renameInstance);
        ImmutableList<Connection> connections = task.getNetwork().getConnections().map(this::renameConnection);
        return task.withNetwork(task.getNetwork().withInstances(instances).withConnections(connections));
    }

    private ImmutableList<Instance> getUnsafeInstances(Network network) {

        return network.getInstances().stream().filter(this::isReserved).collect(ImmutableList.collector());

    }

    private Instance renameInstance(Instance instance) {
        String newName;
        if (isReserved(instance)) {
            context.getReporter().report(
                    new Diagnostic(Diagnostic.Kind.WARNING,
                            String.format("Renaming instance %s to %1$s_renamed because " +
                                    "%1$s is a reserved SystemVerilog keyword", instance.getInstanceName())));
            newName = instance.getInstanceName() + "_renamed";

        }
        else
            newName = instance.getInstanceName();
        newNames.put(instance.getInstanceName(), newName);
        return instance.withInstanceName(newName);
    }

    private Connection renameConnection(Connection connection) {
        Connection.End source = connection.getSource().withInstance(
                connection.getSource().getInstance().map(newNames::get));

        Connection.End target = connection.getTarget().withInstance(
                connection.getTarget().getInstance().map(newNames::get));

        return connection.copy(source, target);
    }
    private boolean isReserved(Instance instance) {
        return keywords.stream().filter(key -> key.equals(instance.getInstanceName())).count() > 0;
    }


    public VerilogNameCheckerPhase() {
        this.newNames = new HashMap<>();
        this.keywords = ImmutableList.of(
                "alias",
                "always",
                "always_comb",
                "always_ff",
                "always_latch",
                "and",
                "assert",
                "assign",
                "assume",
                "automatic",
                "before",
                "begin",
                "bind",
                "bins",
                "binsof",
                "bit",
                "break",
                "buf",
                "bufif0",
                "bufif1",
                "byte",
                "case",
                "casex",
                "casez",
                "cell",
                "chandle",
                "class",
                "clocking",
                "cmos",
                "config",
                "const",
                "constraint",
                "context",
                "continue",
                "cover",
                "covergroup",
                "coverpoint",
                "cross",
                "deassign",
                "default",
                "defparam",
                "design",
                "disable",
                "dist",
                "do",
                "edge",
                "else",
                "end",
                "endcase",
                "endclass",
                "endclocking",
                "endconfig",
                "endfunction",
                "endgenerate",
                "endgroup",
                "endinterface",
                "endmodule",
                "endpackage",
                "endprimitive",
                "endprogram",
                "endproperty",
                "endspecify",
                "endsequence",
                "endtable",
                "endtask",
                "enum",
                "event",
                "expect",
                "export",
                "extends",
                "extern",
                "final",
                "first_match",
                "for",
                "force",
                "foreach",
                "forever",
                "fork",
                "forkjoin",
                "function",
                "generate",
                "genvar",
                "highz0",
                "highz1",
                "if",
                "iff",
                "ifnone",
                "ignore_bins",
                "illegal_bins",
                "import",
                "incdir",
                "include",
                "initial",
                "inout",
                "input",
                "inside",
                "instance",
                "int",
                "integer",
                "interface",
                "intersect",
                "join",
                "join_any",
                "join_none",
                "large",
                "liblist",
                "library",
                "local",
                "localparam",
                "logic",
                "longint",
                "macromodule",
                "matches",
                "medium",
                "modport",
                "module",
                "nand",
                "negedge",
                "new",
                "nmos",
                "nor",
                "noshowcancelled",
                "not",
                "notif0",
                "notif1",
                "null",
                "or",
                "output",
                "package",
                "packed",
                "parameter",
                "pmos",
                "posedge",
                "primitive",
                "priority",
                "program",
                "property",
                "protected",
                "pull0",
                "pull1",
                "pulldown",
                "pullup",
                "pulsestyle_onevent",
                "pulsestyle_ondetect",
                "pure",
                "rand",
                "randc",
                "randcase",
                "randsequence",
                "rcmos",
                "real",
                "realtime",
                "ref",
                "reg",
                "release",
                "repeat",
                "return",
                "rnmos",
                "rpmos",
                "rtran",
                "rtranif0",
                "rtranif1",
                "scalared",
                "sequence",
                "shortint",
                "shortreal",
                "showcancelled",
                "signed",
                "small",
                "solve",
                "specify",
                "specparam",
                "static",
                "string",
                "strong0",
                "strong1",
                "struct",
                "super",
                "supply0",
                "supply1",
                "table",
                "tagged",
                "task",
                "this",
                "throughout",
                "time",
                "timeprecision",
                "timeunit",
                "tran",
                "tranif0",
                "tranif1",
                "tri",
                "tri0",
                "tri1",
                "triand",
                "trior",
                "trireg",
                "type",
                "typedef",
                "union",
                "unique",
                "unsigned",
                "use",
                "uwire",
                "var",
                "vectored",
                "virtual",
                "void",
                "wait",
                "wait_order",
                "wand",
                "weak0",
                "weak1",
                "while",
                "wildcard",
                "wire",
                "with",
                "within",
                "wor",
                "xnor",
                "xor"
        );
    }
}
