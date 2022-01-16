package ca.concordia.jaranalyzer.Models;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class MethodInfo {

    private Object id;
    private String name;
    private ClassInfo classInfo;
    private Type[] argumentTypes;
    private Type returnType;
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
    private String qualifiedName;
    private String internalClassConstructorPrefix;

    private String signature;

    private int callerClassMatchingDistance;
    private int argumentMatchingDistance;

    public MethodInfo(Vertex vertex) {
        this.id = vertex.id();
        this.name = vertex.<String>property("Name").value();
        this.isAbstract = vertex.<Boolean>property("isAbstract").value();
        this.isConstructor = vertex.<Boolean>property("isConstructor").value();
        this.isStatic = vertex.<Boolean>property("isStatic").value();
        this.isPrivate = vertex.<Boolean>property("isPrivate").value();
        this.isPublic = vertex.<Boolean>property("isPublic").value();
        this.isProtected = vertex.<Boolean>property("isProtected").value();
        this.isSynchronized = vertex.<Boolean>property("isSynchronized").value();
        this.isVarargs = vertex.<Boolean>property("isVarargs").value();
        this.isFinal = vertex.<Boolean>property("isFinal").value();
        this.isBridgeMethod = vertex.<Boolean>property("isBridgeMethod").value();

        VertexProperty<String> internalClassConstructorPrefixProp = vertex.property("internalClassConstructorPrefix");

        if (internalClassConstructorPrefixProp.isPresent()) {
            this.internalClassConstructorPrefix = internalClassConstructorPrefixProp.value();
        }

        VertexProperty<String> signatureProperty = vertex.property("signature");

        if (signatureProperty.isPresent()) {
            this.signature = signatureProperty.value();
        }

        this.returnType = Type.getType(vertex.<String>property("returnTypeDescriptor").value());

        Iterator<VertexProperty<String>> argumentTypeDescriptorListIterator
                = vertex.properties("argumentTypeDescriptorList");

        List<Type> argumentTypeList = new ArrayList<>();

        while (argumentTypeDescriptorListIterator.hasNext()) {
            argumentTypeList.add(Type.getType(argumentTypeDescriptorListIterator.next().value()));
        }

        this.argumentTypes = argumentTypeList.toArray(new Type[0]);

        Iterator<VertexProperty<String>> thrownInternalClassNamesIterator =
                vertex.properties("thrownInternalClassNames");

        this.thrownInternalClassNames = new ArrayList<>();

        while (thrownInternalClassNamesIterator.hasNext()) {
            this.thrownInternalClassNames.add(thrownInternalClassNamesIterator.next().value());
        }

    }

    @SuppressWarnings("unchecked")
    public MethodInfo(MethodNode methodNode, ClassInfo classInfo) {
        this.name = methodNode.name;
        if (name.equals("<init>")) {
            isConstructor = true;

            if (classInfo.getName().contains("$")) {
                internalClassConstructorPrefix = classInfo.getName().substring(0, classInfo.getName().lastIndexOf("$") + 1);
                name = classInfo.getName().replace(internalClassConstructorPrefix, "");
            } else {
                name = classInfo.getName();
            }
        }

        this.classInfo = classInfo;
        this.returnType = Type.getReturnType(methodNode.desc);
        if (isConstructor && Objects.nonNull(internalClassConstructorPrefix)) {
            List<Type> types = new ArrayList<Type>();
            for (Type type : Type.getArgumentTypes(methodNode.desc)) {
                if (!classInfo.getQualifiedName().startsWith(type.getClassName() + ".")) {
                    types.add(type);
                }
            }
            this.argumentTypes = new Type[types.size()];
            this.argumentTypes = types.toArray(this.argumentTypes);
        } else {
            this.argumentTypes = Type.getArgumentTypes(methodNode.desc);
        }

        this.thrownInternalClassNames = methodNode.exceptions;
        this.signature = methodNode.signature;

        if ((methodNode.access & Opcodes.ACC_PUBLIC) != 0) {
            isPublic = true;
        } else if ((methodNode.access & Opcodes.ACC_PROTECTED) != 0) {
            isProtected = true;
        } else if ((methodNode.access & Opcodes.ACC_PRIVATE) != 0) {
            isPrivate = true;
        }
        this.qualifiedName = classInfo.getQualifiedName();
        if ((methodNode.access & Opcodes.ACC_STATIC) != 0) {
            isStatic = true;
            this.qualifiedName = qualifiedName + name;
        }

        if ((methodNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            isAbstract = true;
        }

        if ((methodNode.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            isSynchronized = true;
        }

        if ((methodNode.access & Opcodes.ACC_VARARGS) != 0) {
            isVarargs = true;
        }

        if ((methodNode.access & Opcodes.ACC_FINAL) != 0) {
            isFinal = true;
        }

        if ((methodNode.access & Opcodes.ACC_BRIDGE) != 0) {
            isBridgeMethod = true;
        }
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

        methodDescription.append(returnType.getClassName());
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
            methodDescription.append(argumentType.getClassName());
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

    public Object getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ClassInfo getClassInfo() {
        return classInfo;
    }

    public void setClassInfo(ClassInfo classInfo) {
        this.classInfo = classInfo;
    }

    public String getQualifiedClassName() {
        return classInfo.getQualifiedName();
    }

    public String getClassName() {
        return classInfo.getName();
    }

    public String getPackageName() {
        return classInfo.getPackageName();
    }

    public Type[] getArgumentTypes() {
        return argumentTypes;
    }

    public void setArgumentTypes(Type[] argumentTypes) {
        this.argumentTypes = argumentTypes;
    }

    public boolean isConstructor() {
        return isConstructor;
    }

    public String getReturnType() {
        return returnType.getClassName();
    }

    public Type getReturnTypeAsType() {
        return returnType;
    }

    public void setReturnType(Type returnType) {
        this.returnType = returnType;
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

    public boolean isPrivate() {
        return isPrivate;
    }

    public boolean isProtected() {
        return isProtected;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isSynchronized() {
        return isSynchronized;
    }

    public boolean isVarargs() {
        return isVarargs;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public boolean isBridgeMethod() {
        return isBridgeMethod;
    }

    public String getInternalClassConstructorPrefix() {
        return internalClassConstructorPrefix;
    }

    public String getSignature() {
        return signature;
    }

    public int getCallerClassMatchingDistance() {
        return callerClassMatchingDistance;
    }

    public void setCallerClassMatchingDistance(int callerClassMatchingDistance) {
        this.callerClassMatchingDistance = callerClassMatchingDistance;
    }

    public int getArgumentMatchingDistance() {
        return argumentMatchingDistance;
    }

    public void setArgumentMatchingDistance(int argumentMatchingDistance) {
        this.argumentMatchingDistance = argumentMatchingDistance;
    }

    public boolean matches(String methodName, int numberOfParameters) {
        if (argumentTypes.length != numberOfParameters)
            return false;

        if (name.replace('$', '.').equals(methodName)) {
            return true;
        } else if ((getClassName() + "." + name).replace('$', '.').equals(methodName)) {
            return true;
        } else if ((getQualifiedClassName() + "." + name).replace('$', '.').equals(
                methodName)) {
            return true;
        } else if (isConstructor) {
            if (getQualifiedClassName().replace('$', '.').equals(methodName)) {
                return true;
            }

            if (name.contains("$") && name.endsWith("$" + methodName)) {
                return true;
            }
        }
        return false;
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

    public boolean hasDeferredCriteria() {
        return isAbstract || classInfo.getQualifiedName().equals("java.lang.Object") || argumentMatchingDistance > 0;
    }

}
