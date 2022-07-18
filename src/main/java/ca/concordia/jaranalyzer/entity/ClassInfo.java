package ca.concordia.jaranalyzer.entity;

import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import org.objectweb.asm.Type;

import java.util.List;

/**
 * @author Diptopol
 * @since 7/13/2022 6:44 PM
 */
public class ClassInfo {

    private int id;
    private String name;
    private String qualifiedName;
    private String packageName;

    private Type type;
    private TypeInfo typeInfo;

    private boolean isPublic;
    private boolean isPrivate;
    private boolean isProtected;
    private boolean isAbstract;
    private boolean isInterface;
    private boolean isEnum;
    private boolean isInnerClass;
    private boolean isAnonymousInnerClass;

    private String signature;

    //during save

    private String superClassQName;
    private List<String> interfaceQNameList;
    private List<String> innerClassQNameList;

    private List<MethodInfo> methodInfoList;
    private List<FieldInfo> fieldInfoList;

    public ClassInfo() {
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

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    public void setTypeInfo(TypeInfo typeInfo) {
        this.typeInfo = typeInfo;
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

    public boolean isInterface() {
        return isInterface;
    }

    public void setInterface(boolean anInterface) {
        isInterface = anInterface;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public void setEnum(boolean anEnum) {
        isEnum = anEnum;
    }

    public boolean isInnerClass() {
        return isInnerClass;
    }

    public void setInnerClass(boolean innerClass) {
        isInnerClass = innerClass;
    }

    public boolean isAnonymousInnerClass() {
        return isAnonymousInnerClass;
    }

    public void setAnonymousInnerClass(boolean anonymousInnerClass) {
        isAnonymousInnerClass = anonymousInnerClass;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getSuperClassQName() {
        return superClassQName;
    }

    public void setSuperClassQName(String superClassQName) {
        this.superClassQName = superClassQName;
    }

    public List<String> getInterfaceQNameList() {
        return interfaceQNameList;
    }

    public void setInterfaceQNameList(List<String> interfaceQNameList) {
        this.interfaceQNameList = interfaceQNameList;
    }

    public List<String> getInnerClassQNameList() {
        return innerClassQNameList;
    }

    public void setInnerClassQNameList(List<String> innerClassQNameList) {
        this.innerClassQNameList = innerClassQNameList;
    }

    public List<MethodInfo> getMethodInfoList() {
        return methodInfoList;
    }

    public void setMethodInfoList(List<MethodInfo> methodInfoList) {
        this.methodInfoList = methodInfoList;
    }

    public List<FieldInfo> getFieldInfoList() {
        return fieldInfoList;
    }

    public void setFieldInfoList(List<FieldInfo> fieldInfoList) {
        this.fieldInfoList = fieldInfoList;
    }

    public String getOuterClassQualifiedName() {
        if (!this.isInnerClass) {
            return null;
        }

        return this.qualifiedName.substring(0, this.qualifiedName.lastIndexOf("."));
    }

    public String toString() {
        StringBuilder classDescription = new StringBuilder();

        if (isPublic) {
            classDescription.append("public ");
        } else if (isProtected) {
            classDescription.append("protected ");
        } else if (isPrivate) {
            classDescription.append("private ");
        }

        if (isAbstract) {
            classDescription.append("abstract ");
        }

        if (isInterface) {
            classDescription.append("interface ");
        } else {
            classDescription.append("class ");
        }

        classDescription.append(type.getClassName());

        return classDescription.toString();
    }

}
