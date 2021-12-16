package ca.concordia.jaranalyzer.Models;

import org.eclipse.jdt.core.dom.Type;

/**
 * @author Diptopol
 * @since 9/19/2021 6:29 PM
 */
public class VariableDeclarationDto {

    private String name;

    private Type type;

    private TypeObject typeObj;

    private VariableScope scope;

    public VariableDeclarationDto(String name, TypeObject typeObj, VariableScope scope, Type type) {
        this.name = name;
        this.typeObj = typeObj;
        this.scope = scope;
        this.type = type;
    }

    public String getName() {
        return name;
    }


    public TypeObject getTypeObj() {
        return typeObj;
    }

    public VariableScope getScope() {
        return scope;
    }

    public Type getType() {
        return type;
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
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (scope == null) {
            if (other.scope != null)
                return false;
        } else if (!scope.equals(other.scope))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "VariableDeclarationDto{" +
                "name='" + name + '\'' +
                ", typeObj='" + typeObj + '\'' +
                ", scope=" + scope +
                '}';
    }

}
