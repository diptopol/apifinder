package ca.concordia.jaranalyzer.Models;

import ca.concordia.jaranalyzer.util.ClassSignatureFormalTypeParameterExtractor;
import org.objectweb.asm.signature.SignatureReader;

import java.util.*;

/**
 * @author Diptopol
 * @since 11/2/2021 11:07 AM
 */
public class TypeObject {

    private String qualifiedClassName;
    private String signature;

    private boolean isVararg;
    private boolean isParameterized;

    private Map<String, TypeObject> argumentTypeObjectMap;

    public TypeObject(String qualifiedClassName) {
        this.argumentTypeObjectMap = new LinkedHashMap<>();
        this.qualifiedClassName = qualifiedClassName;
    }

    public String getQualifiedClassName() {
        return qualifiedClassName;
    }

    public TypeObject setQualifiedClassName(String qualifiedClassName) {
        this.qualifiedClassName = qualifiedClassName;

        return this;
    }

    public String getSignature() {
        return signature;
    }

    public TypeObject setSignature(String signature) {
        this.signature = signature;

        return this;
    }

    public boolean isVararg() {
        return isVararg;
    }

    public TypeObject setVararg(boolean vararg) {
        isVararg = vararg;

        return this;
    }

    public boolean isParameterized() {
        return isParameterized;
    }

    public boolean isRawType() {
        return !isParameterized && !argumentTypeObjectMap.isEmpty();
    }

    public TypeObject setParameterized(boolean parameterized) {
        isParameterized = parameterized;

        return this;
    }

    public List<TypeObject> getArgumentTypeObjectList() {
        return new ArrayList<>(this.argumentTypeObjectMap.values());
    }

    public Map<String, TypeObject> getArgumentTypeObjectMap() {
        return argumentTypeObjectMap;
    }

    public void setArgumentTypeObjectMap(Map<String, TypeObject> argumentTypeObjectMap) {
        this.argumentTypeObjectMap.putAll(argumentTypeObjectMap);
    }

    public void setArgumentTypeObjectList(List<TypeObject> argumentTypeObjectList) {
        if (Objects.nonNull(this.signature)) {
            ClassSignatureFormalTypeParameterExtractor formalTypeParameterExtractorFromClass =
                    new ClassSignatureFormalTypeParameterExtractor(argumentTypeObjectList);
            SignatureReader reader = new SignatureReader(this.signature);
            reader.accept(formalTypeParameterExtractorFromClass);

            setArgumentTypeObjectMap(formalTypeParameterExtractorFromClass.getFormalTypeParameterMap());
        }
    }

    @Override
    public String toString() {
        return "TypeObject{" +
                "qualifiedClassName='" + qualifiedClassName + '\'' +
                '}';
    }
}
