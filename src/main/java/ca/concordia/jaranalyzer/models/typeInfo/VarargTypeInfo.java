package ca.concordia.jaranalyzer.models.typeInfo;

/**
 * @author Diptopol
 * @since 2/4/2022 4:12 PM
 */
public class VarargTypeInfo extends TypeInfo {

    private TypeInfo elementTypeInfo;

    public VarargTypeInfo(TypeInfo elementTypeInfo) {
        this.elementTypeInfo = elementTypeInfo;
    }

    @Override
    public String getQualifiedClassName() {
        return this.elementTypeInfo.getQualifiedClassName();
    }

    @Override
    public void setQualifiedClassName(String qualifiedClassName) {
        throw new IllegalStateException();
    }

    public TypeInfo getElementTypeInfo() {
        return elementTypeInfo;
    }

    public void setElementTypeInfo(TypeInfo elementTypeInfo) {
        this.elementTypeInfo = elementTypeInfo;
    }

    @Override
    public String getName() {
        return this.elementTypeInfo.getName();
    }

    @Override
    public String toString() {
        return "VarargTypeInfo{" +
                "elementTypeInfo=" + elementTypeInfo +
                '}';
    }

}
