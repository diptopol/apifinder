package ca.concordia.jaranalyzer.models.typeInfo;

/**
 * @author Diptopol
 * @since 6/24/2022 12:40 PM
 */
public class SimpleTypeInfo extends TypeInfo {

    private String className;

    public SimpleTypeInfo(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    @Override
    public String getQualifiedClassName() {
        throw new IllegalStateException();
    }

    @Override
    public void setQualifiedClassName(String qualifiedClassName) {
        throw new IllegalStateException();
    }

    @Override
    public String getName() {
        return this.className;
    }
}
