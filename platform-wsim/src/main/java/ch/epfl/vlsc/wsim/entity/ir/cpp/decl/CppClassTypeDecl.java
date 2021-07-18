package ch.epfl.vlsc.wsim.entity.ir.cpp.decl;

import ch.epfl.vlsc.wsim.entity.ir.cpp.types.ObjectTypeCpp;

import se.lth.cs.tycho.ir.IRNode;

import se.lth.cs.tycho.ir.decl.TypeDecl;
import se.lth.cs.tycho.ir.util.ImmutableList;
import se.lth.cs.tycho.ir.util.Lists;


import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class CppClassTypeDecl extends TypeDecl {

    public CppClassTypeDecl(CppClassTypeDecl original, String name, Optional<ObjectTypeCpp> baseClass,
                            ImmutableList<CppMemberVarDecl> memberVarDecls,
                            ImmutableList<CppMemberFunctionDecl> memberFunctionDecls,
                            ImmutableList<CppParameterVarDecl> constructorParams) {
        super(original, name);
        this.memberFunctionDecls = memberFunctionDecls;
        this.memberVarDecls = memberVarDecls;
        this.consParams = constructorParams;
        this.baseClass = baseClass;
    }

    public CppClassTypeDecl(String name, Optional<ObjectTypeCpp> baseClass,
                            ImmutableList<CppMemberVarDecl> memberVarDecls,
                            ImmutableList<CppMemberFunctionDecl> memberFunctionDecls,
                            ImmutableList<CppParameterVarDecl> constructorParams) {
        this(null, name, baseClass, memberVarDecls, memberFunctionDecls, constructorParams);
    }

    private final ImmutableList<CppMemberVarDecl> memberVarDecls;
    private final ImmutableList<CppMemberFunctionDecl> memberFunctionDecls;
    private final ImmutableList<CppParameterVarDecl> consParams;
    private final Optional<ObjectTypeCpp> baseClass;

    private CppClassTypeDecl copy(String name, Optional<ObjectTypeCpp> baseClass,
                                  ImmutableList<CppMemberVarDecl> memberVarDecls,
                                  ImmutableList<CppMemberFunctionDecl> memberFunctionDecls,
                                  ImmutableList<CppParameterVarDecl> constructorParams) {
        if (Lists.sameElements(this.memberVarDecls, memberVarDecls) &&
                Lists.sameElements(memberFunctionDecls, this.memberFunctionDecls) &&
                Lists.sameElements(consParams, constructorParams) &&
                Objects.equals(name, this.getName()) && baseClass.equals(this.baseClass))
            return this;
        else
            return new CppClassTypeDecl(
                    name, baseClass, memberVarDecls, memberFunctionDecls, constructorParams);

    }

    @Override
    public CppClassTypeDecl withName(String name) {
        return copy(name, this.baseClass, this.memberVarDecls, this.memberFunctionDecls, this.consParams);
    }

    @Override
    public void forEachChild(Consumer<? super IRNode> action) {
        this.baseClass.ifPresent(action);
        this.memberVarDecls.forEach(action);
        this.memberFunctionDecls.forEach(action);
        this.consParams.forEach(action);
    }

    @Override
    public CppClassTypeDecl transformChildren(Transformation transformation) {

        return copy(
                this.getName(), this.baseClass.map(x -> transformation.applyChecked(ObjectTypeCpp.class, x)),
                ImmutableList.from(transformation.mapChecked(CppMemberVarDecl.class, this.memberVarDecls)),
                ImmutableList.from(transformation.mapChecked(CppMemberFunctionDecl.class, this.memberFunctionDecls)),
                ImmutableList.from(transformation.mapChecked(CppParameterVarDecl.class, this.consParams))
        );
    }
}
