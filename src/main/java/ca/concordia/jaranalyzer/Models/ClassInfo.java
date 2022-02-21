package ca.concordia.jaranalyzer.Models;

import ca.concordia.jaranalyzer.Models.typeInfo.*;
import ca.concordia.jaranalyzer.util.ClassSignatureFormalTypeParameterExtractor;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class ClassInfo {

    private static final String ANONYMOUS_INNER_CLASS_NAME_REGEX = ".*\\$[0-9]+";

    private String qualifiedName;
    private String name;
    private String packageName;
    private Type type;

    private TypeInfo typeInfo;

    private boolean isPublic;
    private boolean isPrivate;
    private boolean isProtected;
    private boolean isAbstract;
    private boolean isInterface;
    private List<MethodInfo> methods;
    private List<FieldInfo> fields;
    private String superClassName;
    private ClassInfo superClassInfo;
    private Map<String, ClassInfo> superInterfaceMap;
    private List<String> innerClassNameList;
    private boolean isEnum;
    private boolean isInnerClass;
    private boolean isAnonymousInnerClass;

    private String signature;

    public ClassInfo(Vertex vertex) {
        this.name = vertex.<String>property("Name").value();
        this.qualifiedName = vertex.<String>property("QName").value();
        this.packageName = vertex.<String>property("packageName").value();

        this.isPublic = vertex.<Boolean>property("isPublic").value();
        this.isPrivate = vertex.<Boolean>property("isPrivate").value();
        this.isProtected = vertex.<Boolean>property("isProtected").value();
        this.isAbstract = vertex.<Boolean>property("isAbstract").value();
        this.isInterface = vertex.<Boolean>property("isInterface").value();
        this.isEnum = vertex.<Boolean>property("isEnum").value();
        this.isInnerClass = vertex.<Boolean>property("isInnerClass").value();

        this.type = Type.getType(vertex.<String>property("typeDescriptor").value());

        VertexProperty<String> signatureProperty = vertex.property("signature");

        if (signatureProperty.isPresent()) {
            this.signature = signatureProperty.value();
        }

        this.typeInfo = getClassTypeInfo(this.type, this.qualifiedName, this.signature);
    }

    public ClassInfo(ClassNode classNode) {
        try {
            this.methods = new ArrayList<>();
            this.fields = new ArrayList<>();
            this.innerClassNameList = new ArrayList<>();
            this.superInterfaceMap = new LinkedHashMap<>();
            this.qualifiedName = classNode.name.replace('/', '.');
            this.signature = classNode.signature;

            this.isInnerClass = qualifiedName.contains("$");
            this.isAnonymousInnerClass = qualifiedName.matches(ANONYMOUS_INNER_CLASS_NAME_REGEX);

            this.qualifiedName = qualifiedName.contains("$") ? qualifiedName.replace("$", ".") : qualifiedName;
            if (!classNode.name.contains("/")) {
                this.name = classNode.name;
                this.packageName = "";
            } else {
                this.name = classNode.name.substring(
                        classNode.name.lastIndexOf('/') + 1);
                this.packageName = classNode.name.substring(0, classNode.name.lastIndexOf('/')).replace('/', '.');
            }

            if (classNode.superName != null) {
                this.superClassName = classNode.superName.replace('/', '.').replace("$", ".");
            } else {
                this.superClassName = "";
            }

            this.type = Type.getObjectType(classNode.name);
            this.typeInfo = getClassTypeInfo(this.type, this.qualifiedName, this.signature);

            int access = classNode.access;

            /*
             * access (for inner classes) does not reflact acces of classNode. Instead, we have to fetch access property
             * of innerClassNode and use it.
             */
            for (InnerClassNode innerClassNode : classNode.innerClasses) {
                if (innerClassNode.name.equals(classNode.name)) {
                    access = innerClassNode.access;
                }
            }

            if ((access & Opcodes.ACC_PUBLIC) != 0) {
                isPublic = true;
            } else if ((access & Opcodes.ACC_PROTECTED) != 0) {
                isProtected = true;
            } else if ((access & Opcodes.ACC_PRIVATE) != 0) {
                isPrivate = true;
            }

            if ((access & Opcodes.ACC_ABSTRACT) != 0) {
                isAbstract = true;
            }

            if ((access & Opcodes.ACC_INTERFACE) != 0) {
                isInterface = true;
            }

            if ((access & Opcodes.ACC_ENUM) != 0) {
                isEnum = true;
            }

            @SuppressWarnings("unchecked")
            List<String> implementedInterfaces = classNode.interfaces;
            for (String interfaceName : implementedInterfaces) {
                superInterfaceMap.put(interfaceName.replace('/', '.').replace("$", "."), null);
            }

            @SuppressWarnings("unchecked")
            List<MethodNode> methodNodes = classNode.methods;
            for (MethodNode methodNode : methodNodes) {
                methods.add(new MethodInfo(methodNode, this));
            }

            @SuppressWarnings("unchecked")
            List<FieldNode> fieldNodes = classNode.fields;
            for (FieldNode fieldNode : fieldNodes) {
                fields.add(new FieldInfo(fieldNode, this));
            }

            for (InnerClassNode innerClassNode : classNode.innerClasses) {
                if (!innerClassNode.name.matches(ANONYMOUS_INNER_CLASS_NAME_REGEX)) {
                    innerClassNameList.add(innerClassNode.name.replace("/", ".").replace("$", "."));
                }
            }

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public String getName() {
        return name;
    }

    public String getPackageName() {
        return packageName;
    }

    public Type getType() {
        return type;
    }

    public TypeInfo getTypeInfo() {
        return typeInfo;
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

    public boolean isInterface() {
        return isInterface;
    }

    public List<MethodInfo> getMethods() {
        return methods;
    }

    public String getSuperClassName() {
        return superClassName;
    }

    public ClassInfo getSuperClassInfo() {
        return superClassInfo;
    }

    public void setSuperClassInfo(ClassInfo superClassInfo) {
        this.superClassInfo = superClassInfo;
    }

    public Set<String> getSuperInterfaceNames() {
        return superInterfaceMap.keySet();
    }

    public void putSuperInterfaceInfo(String interfaceName, ClassInfo interfaceInfo) {
        interfaceName = interfaceName.replace("$", ".");
        superInterfaceMap.put(interfaceName, interfaceInfo);
    }

    public List<String> getInnerClassNameList() {
        return innerClassNameList;
    }

    public ArrayList<MethodInfo> getPublicMethods() {
        ArrayList<MethodInfo> publicMethods = new ArrayList<>();
        for (MethodInfo methodInfo : getMethods()) {
            if (methodInfo.isPublic())
                publicMethods.add(methodInfo);
        }
        return publicMethods;
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

    public List<FieldInfo> getFieldsByName(String fieldName) {
        List<FieldInfo> matchedFields = new ArrayList<>();
        for (FieldInfo fieldInfo : fields) {
            if (fieldInfo.getName().equals(fieldName)) {
                matchedFields.add(fieldInfo);
            }
        }

        if (matchedFields.size() == 0 && superClassInfo != null) {
            matchedFields.addAll(superClassInfo.getFieldsByName(fieldName));
        }

        if (matchedFields.size() == 0) {
            for (String superInterfaceName : superInterfaceMap.keySet()) {
                ClassInfo interfaceInfo = superInterfaceMap.get(superInterfaceName);
                if (interfaceInfo != null) {
                    matchedFields.addAll(interfaceInfo.getFieldsByName(fieldName));
                }
            }
        }
        return matchedFields;
    }

    public List<FieldInfo> getFields() {
        return fields;
    }

    public List<MethodInfo> getMethods(String methodName,
                                       int numberOfParameters) {
        List<MethodInfo> matchedMethods = new ArrayList<>();

        for (MethodInfo method : getMethods()) {
            if (method.matches(methodName, numberOfParameters)) {
                matchedMethods.add(method);
            }
        }

        if (matchedMethods.size() == 0 && superClassInfo != null) {
            matchedMethods.addAll(superClassInfo.getMethods(methodName, numberOfParameters));
        }

        if (matchedMethods.size() == 0) {
            for (String superInterfaceName : superInterfaceMap.keySet()) {
                ClassInfo interfaceInfo = superInterfaceMap.get(superInterfaceName);
                if (interfaceInfo != null) {
                    matchedMethods.addAll(interfaceInfo.getMethods(methodName, numberOfParameters));
                }
            }
        }
        return matchedMethods;
    }

    public boolean matchesImportStatement(String importedPackage) {
        if (qualifiedName.replace('$', '.').startsWith(importedPackage)) {
            return true;
        } else {
            return false;
        }
    }

    public String getKind() {
        return "CLASS";
    }

    public boolean isEnum() {
        return isEnum;
    }

    public boolean isInnerClass() {
        return isInnerClass;
    }

    public boolean isAnonymousInnerClass() {
        return isAnonymousInnerClass;
    }

    public String getSignature() {
        return this.signature;
    }

    public ParameterizedTypeInfo getParameterizedType() {
        if (Objects.isNull(this.signature)) {
            return null;
        }

        ClassSignatureFormalTypeParameterExtractor extractor = new ClassSignatureFormalTypeParameterExtractor();

        SignatureReader signatureReader = new SignatureReader(this.getSignature());
        signatureReader.accept(extractor);

        String qualifiedClassName = this.qualifiedName;
        ParameterizedTypeInfo parameterizedTypeInfo = new ParameterizedTypeInfo(qualifiedClassName);
        parameterizedTypeInfo.setTypeArgumentList(new ArrayList<>(extractor.getTypeArgumentList()));

        return parameterizedTypeInfo;
    }

    private TypeInfo getClassTypeInfo(Type type, String qualifiedName, String signature) {
        if (Objects.isNull(signature)) {
            return getTypeInfo(type);
        } else {
            ClassSignatureFormalTypeParameterExtractor extractor = new ClassSignatureFormalTypeParameterExtractor();

            SignatureReader signatureReader = new SignatureReader(signature);
            signatureReader.accept(extractor);

            ParameterizedTypeInfo parameterizedTypeInfo = new ParameterizedTypeInfo(qualifiedName);
            parameterizedTypeInfo.setTypeArgumentList(new ArrayList<>(extractor.getTypeArgumentList()));

            return parameterizedTypeInfo;
        }
    }

    /*
     * The current assumption is that type of classInfo can only be a primitive type, or qualified if it is not
     * parameterized type.
     */
    private TypeInfo getTypeInfo(Type type) {
        if (InferenceUtility.PRIMITIVE_TYPE_LIST.contains(type.getClassName())) {
            return new PrimitiveTypeInfo(type.getClassName());
        } else {
            return new QualifiedTypeInfo(type.getClassName());
        }
    }

}
