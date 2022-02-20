package ca.concordia.jaranalyzer.Models.typeInfo;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Diptopol
 * @since 2/13/2022 2:30 AM
 */
public class ArrayTypeInfo extends TypeInfo {

    private TypeInfo elementTypeInfo;

    private int dimension;

    public ArrayTypeInfo(TypeInfo elementTypeInfo, int dimension) {
        this.elementTypeInfo = elementTypeInfo;
        this.dimension = dimension;
    }

    @Override
    public String getQualifiedClassName() {
        return this.elementTypeInfo.getQualifiedClassName().concat(StringUtils.repeat("[]", this.dimension));
    }

    @Override
    public void setQualifiedClassName(String qualifiedClassName) {
        throw new IllegalStateException();
    }

    public TypeInfo getElementTypeInfo() {
        return elementTypeInfo;
    }

    public int getDimension() {
        return dimension;
    }

    @Override
    public String getName() {
        return this.elementTypeInfo.getName().concat(StringUtils.repeat("[]", this.dimension));
    }

    @Override
    public String toString() {
        return "ArrayTypeInfo{" +
                "elementTypeInfo=" + elementTypeInfo +
                ", dimension=" + dimension +
                '}';
    }
}
