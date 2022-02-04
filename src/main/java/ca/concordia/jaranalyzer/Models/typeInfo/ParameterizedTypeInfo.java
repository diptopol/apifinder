package ca.concordia.jaranalyzer.Models.typeInfo;

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
        if (!this.typeArgumentList.isEmpty()) {
            assert this.typeArgumentList.size() == typeArgumentList.size();

            for (int i = 0; i < this.typeArgumentList.size(); i++) {
                TypeInfo thisTypeArgument = this.typeArgumentList.get(i);
                TypeInfo otherTypeArgument = typeArgumentList.get(i);

                if (thisTypeArgument.isFormalTypeParameterInfo()) {
                    FormalTypeParameterInfo thisFormalTypeArgument = (FormalTypeParameterInfo) thisTypeArgument;
                    //assuming that otherTypeArgument can only be qualifiedTypeInfo
                    thisFormalTypeArgument.setBaseTypeInfo(otherTypeArgument);

                    this.typeArgumentList.set(i, thisFormalTypeArgument);
                } else {
                    this.typeArgumentList.set(i, otherTypeArgument);
                }
            }

        } else {
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
