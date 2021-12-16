package ca.concordia.jaranalyzer.Models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Diptopol
 * @since 11/2/2021 11:07 AM
 */
public class TypeObject {

    private String qualifiedClassName;
    private String signature;
    private boolean isParameterized;
    private Map<String, TypeObject> argumentTypeObjectMap;

    public TypeObject(String qualifiedClassName) {
        this.argumentTypeObjectMap = new LinkedHashMap<>();
        this.qualifiedClassName = qualifiedClassName;
    }

    public String getQualifiedClassName() {
        return qualifiedClassName;
    }

    public void setQualifiedClassName(String qualifiedClassName) {
        this.qualifiedClassName = qualifiedClassName;
    }

    public String getSignature() {
        return signature;
    }

    public TypeObject setSignature(String signature) {
        this.signature = signature;

        return this;
    }

    public boolean isParameterized() {
        return isParameterized;
    }

    public List<TypeObject> getArgumentTypeObjectList() {
        return new ArrayList<>(this.argumentTypeObjectMap.values());
    }

    public void setArgumentTypeObjectMap(Map<String, TypeObject> argumentTypeObjectMap) {
        this.isParameterized = true;
        this.argumentTypeObjectMap.putAll(argumentTypeObjectMap);
    }

}
