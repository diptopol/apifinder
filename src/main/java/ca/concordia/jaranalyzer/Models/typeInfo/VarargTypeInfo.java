package ca.concordia.jaranalyzer.Models.typeInfo;

/**
 * @author Diptopol
 * @since 2/4/2022 4:12 PM
 */
public class VarargTypeInfo extends TypeInfo {

    private String qualifiedClassName;

    public VarargTypeInfo(String qualifiedClassName) {
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
        return "VarargTypeInfo{" +
                "qualifiedClassName='" + qualifiedClassName + '\'' +
                '}';
    }
}
