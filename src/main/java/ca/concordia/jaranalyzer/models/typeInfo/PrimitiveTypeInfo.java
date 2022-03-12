package ca.concordia.jaranalyzer.models.typeInfo;

/**
 * @author Diptopol
 * @since 2/5/2022 8:37 PM
 */
public class PrimitiveTypeInfo extends TypeInfo {

    private String qualifiedClassName;

    public PrimitiveTypeInfo(String qualifiedClassName) {
        this.qualifiedClassName = qualifiedClassName;
    }

    @Override
    public String getQualifiedClassName() {
        return this.qualifiedClassName;
    }

    @Override
    public void setQualifiedClassName(String qualifiedClassName) {
        this.qualifiedClassName = qualifiedClassName;
    }

    @Override
    public String getName() {
        return this.qualifiedClassName;
    }

    @Override
    public String toString() {
        return "PrimitiveTypeInfo{" +
                "qualifiedClassName='" + qualifiedClassName + '\'' +
                '}';
    }
}
