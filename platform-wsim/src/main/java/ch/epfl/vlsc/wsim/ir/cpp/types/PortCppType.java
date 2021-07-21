package ch.epfl.vlsc.wsim.ir.cpp.types;

import se.lth.cs.tycho.ir.IRNode;

import java.util.Objects;
import java.util.function.Consumer;

public class PortCppType extends LibraryObjectCppType{

    private final CppType portType;
//    private final String portName;
    private final boolean isInput;

    public static PortCppType InputPort(CppType portType) {
        return new PortCppType(portType, true);
    }
    public static PortCppType OutputPort(CppType portType) {
        return new PortCppType(portType, false);
    }
    public PortCppType(PortCppType original, CppType portType, boolean isInput) {
        super(original, false);
        this.portType = portType;
        this.isInput = isInput;
    }
    public PortCppType(CppType portType, boolean isInput) {
        this(null, portType, isInput);
    }

    public boolean isInput() {
        return isInput;
    }

    private PortCppType copy(CppType portType, boolean isInput) {
        if (Objects.equals(portType, this.portType) && this.isInput == isInput) {
            return this;
        } else {
            return new PortCppType(portType, isInput);
        }
    }
    public CppType getPortType() { return this.portType; }
    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (that == null) {
            return false;
        } else if (!(that instanceof PortCppType)) {
            return false;
        } else {
            return Objects.equals(((PortCppType) that).getPortType(), this.portType) &&
                    this.isInput == ((PortCppType) that).isInput;
        }
    }
    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        action.accept(portType);
    }


    @Override
    public IRNode transformChildren(Transformation transformation) {
        return copy(transformation.applyChecked(CppType.class, this.getPortType()), this.isInput);
    }

    @Override
    public String toString() {
        return "::wsim::" + (isInput ? "InputPort<" : "OutputPort<") + portType + ">";
    }

}
