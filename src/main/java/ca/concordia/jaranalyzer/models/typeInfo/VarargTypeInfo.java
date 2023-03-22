package ca.concordia.jaranalyzer.models.typeInfo;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Diptopol
 * @since 2/4/2022 4:12 PM
 */
public class VarargTypeInfo extends TypeInfo {

    private static final long serialVersionUID = 1L;

    private TypeInfo elementTypeInfo;

    public VarargTypeInfo(TypeInfo elementTypeInfo) {
        this.elementTypeInfo = elementTypeInfo;
    }

    @Override
    public String getQualifiedClassName() {
        return this.elementTypeInfo.getQualifiedClassName().concat(StringUtils.repeat("[]", getDimension()));
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
        return this.elementTypeInfo.getName().concat("...");
    }

    public int getDimension() {
        return 1;
    }

    @Override
    public String toString() {
        return "VarargTypeInfo{" +
                "elementTypeInfo=" + elementTypeInfo +
                '}';
    }

}
