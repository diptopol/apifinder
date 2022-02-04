package ca.concordia.jaranalyzer.Models.typeInfo;

/**
 * @author Diptopol
 * @since 2/5/2022 12:30 PM
 */
public class FormalTypeParameterInfo extends TypeInfo {

    private String typeParameter;
    private TypeInfo baseTypeInfo;

    public FormalTypeParameterInfo(String typeParameter, TypeInfo baseTypeInfo) {
        this.typeParameter = typeParameter;
        this.baseTypeInfo = baseTypeInfo;
    }

    public String getTypeParameter() {
        return typeParameter;
    }

    @Override
    public String getQualifiedClassName() {
        return this.baseTypeInfo.getQualifiedClassName();
    }

    @Override
    public void setQualifiedClassName(String qualifiedClassName) {
        this.baseTypeInfo.setQualifiedClassName(qualifiedClassName);
    }

    public TypeInfo getBaseTypeInfo() {
        return baseTypeInfo;
    }

    public void setBaseTypeInfo(TypeInfo baseTypeInfo) {
        this.baseTypeInfo = baseTypeInfo;
    }

    @Override
    public String getName() {
        return this.typeParameter;
    }

    @Override
    public String toString() {
        return "FormalTypeParameterInfo{" +
                "typeParameter='" + typeParameter + '\'' +
                ", baseTypeInfo=" + this.baseTypeInfo +
                '}';
    }
}
