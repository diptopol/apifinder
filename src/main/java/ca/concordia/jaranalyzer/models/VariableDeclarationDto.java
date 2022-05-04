package ca.concordia.jaranalyzer.models;

import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import org.eclipse.jdt.core.dom.Type;

/**
 * @author Diptopol
 * @since 2/7/2022 11:05 AM
 */
public class VariableDeclarationDto {

    private final String name;

    private final Type type;

    private final TypeInfo typeInfo;

    private final VariableScope scope;

    public VariableDeclarationDto(String name, TypeInfo typeInfo, VariableScope scope, Type type) {
        this.name = name;
        this.typeInfo = typeInfo;
        this.scope = scope;
        this.type = type;
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
