package ca.concordia.jaranalyzer.models.typeInfo;

/**
 * @author Diptopol
 * @since 1/30/2022 3:39 PM
 */
public abstract class TypeInfo {

    public abstract String getQualifiedClassName();

    public abstract void setQualifiedClassName(String qualifiedClassName);

    public abstract String getName();

    public boolean isParameterizedTypeInfo() {
        return this instanceof ParameterizedTypeInfo;
    }

    public boolean isQualifiedTypeInfo() {
        return this instanceof QualifiedTypeInfo;
    }

    public boolean isVarargTypeInfo() {
        return this instanceof VarargTypeInfo;
    }

    public boolean isNullTypeInfo() {
        return this instanceof NullTypeInfo;
    }

    public boolean isPrimitiveTypeInfo() {
        return this instanceof PrimitiveTypeInfo;
    }

    public boolean isFormalTypeParameterInfo() {
        return this instanceof FormalTypeParameterInfo;
    }

    public boolean isArrayTypeInfo() {
        return this instanceof ArrayTypeInfo;
    }

    public boolean isSimpleTypeInfo() {
        return this instanceof SimpleTypeInfo;
    }

}
