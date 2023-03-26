package ca.concordia.apifinder.models;

import ca.concordia.apifinder.models.typeInfo.TypeInfo;
import org.eclipse.jdt.core.dom.Type;

/**
 * @author Diptopol
 * @since 2/7/2022 11:05 AM
 */
public class VariableDeclarationDto {

    private final String name;

    private Type type;

    private TypeInfo typeInfo;

    private final VariableScope scope;

    private OwningClassInfo owningClassInfo;

    private boolean isVarargs;

    public VariableDeclarationDto(String name, TypeInfo typeInfo, VariableScope scope) {
        this.name = name;
        this.typeInfo = typeInfo;
        this.scope = scope;
    }

    public VariableDeclarationDto(String name, VariableScope scope, Type type, OwningClassInfo owningClassInfo, boolean isVarargs) {
        this.name = name;
        this.scope = scope;
        this.type = type;
        this.owningClassInfo = owningClassInfo;
        this.isVarargs = isVarargs;
    }

    public String getName() {
        return name;
    }

    public TypeInfo getTypeInfo() {
        return this.typeInfo;
    }

    public VariableScope getScope() {
        return this.scope;
    }

    public Type getType() {
        return this.type;
    }

    public OwningClassInfo getOwningClassInfo() {
        return owningClassInfo;
    }

    public boolean isVarargs() {
        return isVarargs;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((scope == null) ? 0 : scope.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        VariableDeclarationDto other = (VariableDeclarationDto) obj;
        if (name == null) {
            if (other.getName() != null)
                return false;
        } else if (!name.equals(other.getName()))
            return false;
        if (scope == null) {
            if (other.getScope() != null)
                return false;
        } else if (!scope.equals(other.getScope()))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "VariableDeclarationDto{" +
                "name='" + name + '\'' +
                ", typeInfo='" + typeInfo + '\'' +
                ", scope=" + scope +
                '}';
    }

}
