package ca.concordia.jaranalyzer.models.typeInfo;

import java.util.List;

/**
 * @author Diptopol
 * @since 6/25/2022 1:50 AM
 */
public class FunctionTypeInfo extends TypeInfo {

    private boolean innerClassConstructor;

    private final List<FunctionDefinition> functionDefinitionList;

    public FunctionTypeInfo(List<FunctionDefinition> functionDefinitionList) {
        this.functionDefinitionList = functionDefinitionList;
    }

    public FunctionTypeInfo(boolean innerClassConstructor, List<FunctionDefinition> functionDefinitionList) {
        this.innerClassConstructor = innerClassConstructor;
        this.functionDefinitionList = functionDefinitionList;
    }

    public boolean isInnerClassConstructor() {
        return innerClassConstructor;
    }

    public List<FunctionDefinition> getFunctionDefinitionList() {
        return functionDefinitionList;
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
        throw new IllegalStateException();
    }

    public static class FunctionDefinition {

        private TypeInfo returnTypeInfo;
        private List<TypeInfo> argumentTypeInfoList;

        public FunctionDefinition(TypeInfo returnTypeInfo, List<TypeInfo> argumentTypeInfoList) {
            this.returnTypeInfo = returnTypeInfo;
            this.argumentTypeInfoList = argumentTypeInfoList;
        }

        public TypeInfo getReturnTypeInfo() {
            return returnTypeInfo;
        }

        public List<TypeInfo> getArgumentTypeInfoList() {
            return argumentTypeInfoList;
        }

        public void setArgumentTypeInfoList(List<TypeInfo> argumentTypeInfoList) {
            this.argumentTypeInfoList = argumentTypeInfoList;
        }
    }
}
