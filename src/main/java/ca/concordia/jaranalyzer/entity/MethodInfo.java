package ca.concordia.jaranalyzer.entity;

import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import ca.concordia.jaranalyzer.util.AuditInfo;
import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author Diptopol
 * @since 7/13/2022 6:44 PM
 */
public class MethodInfo {

    private int id;
    private String name;

    private int classInfoId;
    private ClassInfo classInfo;

    private Type[] argumentTypes;
    private List<TypeInfo> argumentTypeInfoList;

    private Type returnType;
    private TypeInfo returnTypeInfo;

    private List<TypeInfo> formalTypeParameterList;

    private List<String> thrownInternalClassNames;
    private boolean isPublic;
    private boolean isPrivate;
    private boolean isProtected;
    private boolean isAbstract;
    private boolean isStatic;
    private boolean isSynchronized;
    private boolean isConstructor;
    private boolean isVarargs;
    private boolean isFinal;
    private boolean isBridgeMethod;
    private String internalClassConstructorPrefix;

    private String signature;

    private int invokerClassMatchingDistance;
    private double argumentMatchingDistance;

    private boolean owningClassAttribute;

    private AuditInfo auditInfo;

    public MethodInfo() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getClassInfoId() {
        return classInfoId;
    }

    public void setClassInfoId(int classInfoId) {
        this.classInfoId = classInfoId;
    }

    public ClassInfo getClassInfo() {
        return classInfo;
    }

    public void setClassInfo(ClassInfo classInfo) {
        this.classInfo = classInfo;
    }

    public Type[] getArgumentTypes() {
        return argumentTypes;
    }

    public void setArgumentTypes(Type[] argumentTypes) {
        this.argumentTypes = argumentTypes;
    }

    public List<TypeInfo> getArgumentTypeInfoList() {
        return argumentTypeInfoList;
    }

    public void setArgumentTypeInfoList(List<TypeInfo> argumentTypeInfoList) {
        this.argumentTypeInfoList = argumentTypeInfoList;
    }

    public Type getReturnType() {
        return returnType;
    }

    public void setReturnType(Type returnType) {
        this.returnType = returnType;
    }

    public TypeInfo getReturnTypeInfo() {
        return returnTypeInfo;
    }

    public void setReturnTypeInfo(TypeInfo returnTypeInfo) {
        this.returnTypeInfo = returnTypeInfo;
    }

    public List<TypeInfo> getFormalTypeParameterList() {
        return formalTypeParameterList;
    }

    public void setFormalTypeParameterList(List<TypeInfo> formalTypeParameterList) {
        this.formalTypeParameterList = formalTypeParameterList;
    }

    public List<String> getThrownInternalClassNames() {
        return thrownInternalClassNames;
    }

    public void setThrownInternalClassNames(List<String> thrownInternalClassNames) {
        this.thrownInternalClassNames = thrownInternalClassNames;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }

    public boolean isProtected() {
        return isProtected;
    }

    public void setProtected(boolean aProtected) {
        isProtected = aProtected;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract(boolean anAbstract) {
        isAbstract = anAbstract;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean aStatic) {
        isStatic = aStatic;
    }

    public boolean isSynchronized() {
        return isSynchronized;
    }

    public void setSynchronized(boolean aSynchronized) {
        isSynchronized = aSynchronized;
    }

    public boolean isConstructor() {
        return isConstructor;
    }

    public void setConstructor(boolean constructor) {
        isConstructor = constructor;
    }

    public boolean isVarargs() {
        return isVarargs;
    }

    public void setVarargs(boolean varargs) {
        isVarargs = varargs;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }

    public boolean isBridgeMethod() {
        return isBridgeMethod;
    }

    public void setBridgeMethod(boolean bridgeMethod) {
        isBridgeMethod = bridgeMethod;
    }

    public String getInternalClassConstructorPrefix() {
        return internalClassConstructorPrefix;
    }

    public void setInternalClassConstructorPrefix(String internalClassConstructorPrefix) {
        this.internalClassConstructorPrefix = internalClassConstructorPrefix;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public int getInvokerClassMatchingDistance() {
        return invokerClassMatchingDistance;
    }

    public void setInvokerClassMatchingDistance(int invokerClassMatchingDistance) {
        this.invokerClassMatchingDistance = invokerClassMatchingDistance;
    }

    public double getArgumentMatchingDistance() {
        return argumentMatchingDistance;
    }

    public void setArgumentMatchingDistance(double argumentMatchingDistance) {
        this.argumentMatchingDistance = argumentMatchingDistance;
    }

    public boolean isOwningClassAttribute() {
        return owningClassAttribute;
    }

    public void setOwningClassAttribute(boolean owningClassAttribute) {
        this.owningClassAttribute = owningClassAttribute;
    }

    public AuditInfo getAuditInfo() {
        return auditInfo;
    }

    public void setAuditInfo(AuditInfo auditInfo) {
        this.auditInfo = auditInfo;
    }

    public boolean hasDeferredCriteria() {
        return isAbstract || classInfo.getQualifiedName().equals("java.lang.Object") || argumentMatchingDistance > 0;
    }

    public boolean isInnerClassConstructor() {
        return isConstructor && Objects.nonNull(internalClassConstructorPrefix);
    }

    public String getQualifiedClassName() {
        return Objects.nonNull(classInfo) ? classInfo.getQualifiedName() : null;
    }

    public String toString() {
        StringBuilder methodDescription = new StringBuilder();

        if (classInfo != null) {
            methodDescription.append(classInfo.getQualifiedName()).append("::");
        }

        if (isPublic) {
            methodDescription.append("public ");
        } else if (isProtected) {
            methodDescription.append("protected ");
        } else if (isPrivate) {
            methodDescription.append("private ");
        }

        if (isStatic) {
            methodDescription.append("static ");
        }

        if (isAbstract) {
            methodDescription.append("abstract ");
        }

        if (isSynchronized) {
            methodDescription.append("synchronized ");
        }

        if (isFinal) {
            methodDescription.append("final ");
        }

        methodDescription.append(returnType.getClassName().replace("$", "."));
        methodDescription.append(" ");

        if (Objects.nonNull(internalClassConstructorPrefix)) {
            methodDescription.append(internalClassConstructorPrefix);
        }

        methodDescription.append(this.name);

        methodDescription.append("(");
        for (int i = 0; i < argumentTypes.length; i++) {
            Type argumentType = argumentTypes[i];
            if (i > 0) {
                methodDescription.append(", ");
            }
            methodDescription.append(argumentType.getClassName().replace("$", "."));
        }
        methodDescription.append(")");

        if (!thrownInternalClassNames.isEmpty()) {
            methodDescription.append(" throws ");
            int i = 0;
            for (String thrownInternalClassName : thrownInternalClassNames) {
                if (i > 0) {
                    methodDescription.append(", ");
                }
                methodDescription.append(Type.getObjectType(
                        thrownInternalClassName).getClassName());
                i++;
            }
        }

        return methodDescription.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodInfo that = (MethodInfo) o;

        return name.equals(that.name) &&
                (classInfo != null && that.classInfo != null && classInfo.getQualifiedName().equals(that.classInfo.getQualifiedName())) &&
                Arrays.equals(argumentTypes, that.argumentTypes) &&
                Objects.equals(returnType, that.returnType) &&
                Objects.equals(thrownInternalClassNames, that.thrownInternalClassNames) &&
                Objects.equals(internalClassConstructorPrefix, that.internalClassConstructorPrefix);
    }

    @Override
    public int hashCode() {
        String classInfoQName = classInfo != null ? classInfo.getQualifiedName() : null;

        int result = Objects.hash(name, classInfoQName, returnType, thrownInternalClassNames, internalClassConstructorPrefix);
        result = 31 * result + Arrays.hashCode(argumentTypes);

        return result;
    }

}
