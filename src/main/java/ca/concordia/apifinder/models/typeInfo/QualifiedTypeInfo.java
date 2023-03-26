package ca.concordia.apifinder.models.typeInfo;

/**
 * @author Diptopol
 * @since 1/30/2022 3:40 PM
 */
public class QualifiedTypeInfo extends TypeInfo {

    private static final long serialVersionUID = 1L;

    private String qualifiedClassName;

    public QualifiedTypeInfo(String qualifiedClassName) {
        this.qualifiedClassName = qualifiedClassName;
    }

    @Override
    public String getQualifiedClassName() {
        return qualifiedClassName;
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
        return "QualifiedTypeInfo{" +
                "qualifiedClassName='" + qualifiedClassName + '\'' +
                '}';
    }
}
