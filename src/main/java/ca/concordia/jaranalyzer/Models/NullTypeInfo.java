package ca.concordia.jaranalyzer.Models;

import ca.concordia.jaranalyzer.Models.typeInfo.TypeInfo;

/**
 * @author Diptopol
 * @since 2/5/2022 8:32 PM
 */
//TODO: Need to evaluate later
public class NullTypeInfo extends TypeInfo {

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
