package ca.concordia.jaranalyzer.models.typeInfo;

/**
 * @author Diptopol
 * @since 2/8/2022 9:41 AM
 */
public class VoidTypeInfo extends TypeInfo {

    private static final long serialVersionUID = 1L;

    @Override
    public String getQualifiedClassName() {
        return "void";
    }

    @Override
    public void setQualifiedClassName(String qualifiedClassName) {
        throw new IllegalArgumentException();
    }

    @Override
    public String getName() {
        return "void";
    }

    @Override
    public String toString() {
        return "VoidTypeInfo{}";
    }
}
