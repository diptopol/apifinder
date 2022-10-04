package ca.concordia.jaranalyzer.models.typeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Diptopol
 * @since 1/30/2022 3:44 PM
 */
public class ParameterizedTypeInfo extends TypeInfo {

    private String qualifiedClassName;

    private boolean isParameterized;

    private List<TypeInfo> typeArgumentList;

    public ParameterizedTypeInfo(String qualifiedClassName) {
        this.qualifiedClassName = qualifiedClassName;
        this.typeArgumentList = new ArrayList<>();
    }

    public ParameterizedTypeInfo(QualifiedTypeInfo qualifiedTypeInfo) {
        this(qualifiedTypeInfo.getQualifiedClassName());
    }

    @Override
    public String getQualifiedClassName() {
        return qualifiedClassName;
    }

    @Override
    public void setQualifiedClassName(String qualifiedClassName) {
        this.qualifiedClassName = qualifiedClassName;
    }

    public boolean isParameterized() {
        return isParameterized;
    }

    public void setParameterized(boolean parameterized) {
        isParameterized = parameterized;
    }

    public List<TypeInfo> getTypeArgumentList() {
        return this.typeArgumentList;
    }

    public void setTypeArgumentList(List<TypeInfo> typeArgumentList) {
        if (this.typeArgumentList.isEmpty() || this.typeArgumentList.size() == typeArgumentList.size()) {
            this.typeArgumentList = typeArgumentList;
        }
    }

    @Override
    public String getName() {
        return this.qualifiedClassName;
    }

    @Override
    public String toString() {
        return "ParameterizedTypeInfo{" +
                "qualifiedClassName='" + qualifiedClassName + '\'' +
                ", isParameterized=" + isParameterized +
                ", typeArgumentList=" + typeArgumentList +
                '}';
    }

}
