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
    private LinkedHashMap<String, TypeObject> argumentTypeObjectMap;

    public TypeObject() {
        this.argumentTypeObjectMap = new LinkedHashMap<>();
    }

    public TypeObject(String qualifiedClassName) {
        this();
        this.qualifiedClassName = qualifiedClassName;
    }

    public TypeObject(String qualifiedClassName, String signature) {
        this(qualifiedClassName);
        this.signature = signature;
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

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public boolean isParameterized() {
        return isParameterized;
    }

    public List<TypeObject> getArgumentTypeObjectList() {
        return new ArrayList<>(this.argumentTypeObjectMap.values());
    }

    public void setArgumentTypeObjectMap(LinkedHashMap<String, TypeObject> argumentTypeObjectMap) {
        this.isParameterized = true;
        this.argumentTypeObjectMap.putAll(argumentTypeObjectMap);
    }

}
