package ca.concordia.apifinder.entity;

import org.objectweb.asm.Type;

/**
 * @author Diptopol
 * @since 7/13/2022 6:45 PM
 */
public class FieldInfo {

    private int id;
    private String name;

    private int classInfoId;
    private ClassInfo classInfo;

    private Type type;
    private boolean isPublic;
    private boolean isPrivate;
    private boolean isProtected;
    private boolean isStatic;
    private String signature;

    public FieldInfo() {
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

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
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

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean aStatic) {
        isStatic = aStatic;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public static FieldInfo getLengthFieldInfoOfArray() {
        FieldInfo lengthFieldInfo = new FieldInfo();

        lengthFieldInfo.setName("length");
        lengthFieldInfo.setPublic(true);
        lengthFieldInfo.setType(Type.INT_TYPE);

        return lengthFieldInfo;
    }

    public String toString() {
        StringBuilder methodDescription = new StringBuilder();

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

        methodDescription.append(type.getClassName());
        methodDescription.append(" ");
        methodDescription.append(name);

        return methodDescription.toString();
    }

}
