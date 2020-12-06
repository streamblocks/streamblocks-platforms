package ch.epfl.vlsc.compiler.ir;

import se.lth.cs.tycho.ir.entity.PortDecl;
import se.lth.cs.tycho.ir.type.TypeExpr;

public class BankedPortDecl extends PortDecl {

    private final int bankId;

    public BankedPortDecl(String name, int bankId) {
        super(name);
        this.bankId = bankId;
    }

    public BankedPortDecl(String name, int bankId, TypeExpr type) {
        super(name, type);
        this.bankId = bankId;
    }

    public int getBankId() {
        return bankId;
    }
}
