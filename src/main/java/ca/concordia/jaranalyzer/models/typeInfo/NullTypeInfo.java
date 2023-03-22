package ca.concordia.jaranalyzer.models.typeInfo;

/**
 * @author Diptopol
 * @since 2/5/2022 8:32 PM
 */
public class NullTypeInfo extends TypeInfo {

    private static final long serialVersionUID = 1L;

    public NullTypeInfo() {
    }

    @Override
    public String getQualifiedClassName() {
        return "null";
    }

    @Override
    public void setQualifiedClassName(String qualifiedClassName) {
        throw new IllegalStateException();
    }

    @Override
    public String getName() {
        return "null";
    }

    @Override
    public String toString() {
        return "NullTypeInfo{}";
    }
}
