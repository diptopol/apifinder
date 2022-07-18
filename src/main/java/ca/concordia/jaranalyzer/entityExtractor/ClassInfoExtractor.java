package ca.concordia.jaranalyzer.entityExtractor;

import ca.concordia.jaranalyzer.entity.ClassInfo;
import ca.concordia.jaranalyzer.entity.FieldInfo;
import ca.concordia.jaranalyzer.entity.MethodInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Diptopol
 * @since 7/13/2022 6:48 PM
 */
public class ClassInfoExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ClassInfoExtractor.class);

    private static final String ANONYMOUS_INNER_CLASS_NAME_REGEX = ".*\\$[0-9]+";

    public static List<ClassInfo> getClassInfoList(ZipFile jmodFile) {
        Enumeration<? extends ZipEntry> entries = jmodFile.entries();
        List<ClassInfo> classInfoList = new ArrayList<>();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();

            if (entryName.startsWith("classes/")
                    && entryName.endsWith(".class") && !entryName.equals("classes/module-info.class")) {

                ClassNode classNode = getClassNode(jmodFile, entry);

                if (Objects.nonNull(classNode)) {
                    ClassInfo classInfo = getClassInfo(classNode);
                    classInfo.setMethodInfoList(getMethodInfoList(classNode, classInfo));
                    classInfo.setFieldInfoList(getFieldInfoList(classNode));

                    classInfoList.add(classInfo);
                }
            }
        }

        return classInfoList;
    }

    public static List<ClassInfo> getClassInfoList(JarFile jarFile) {
        Enumeration<JarEntry> entries = jarFile.entries();
        List<ClassInfo> classInfoList = new ArrayList<>();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();

            if (entryName.endsWith(".class") && !entryName.equals("module-info.class")) {
                ClassNode classNode = getClassNode(jarFile, entry);

                if (Objects.nonNull(classNode)) {
                    ClassInfo classInfo = getClassInfo(classNode);
                    classInfo.setMethodInfoList(getMethodInfoList(classNode, classInfo));
                    classInfo.setFieldInfoList(getFieldInfoList(classNode));

                    classInfoList.add(classInfo);
                }
            }
        }

        return classInfoList;
    }

    private static ClassInfo getClassInfo(ClassNode classNode) {
        ClassInfo classInfo = new ClassInfo();

        classInfo.setInnerClass(classNode.name.contains("$"));

        classInfo.setAnonymousInnerClass(classNode.name.matches(ANONYMOUS_INNER_CLASS_NAME_REGEX));
        classInfo.setQualifiedName(getQualifiedClassName(classNode.name));

        if (classNode.name.contains("/")) {
            classInfo.setName(classNode.name.substring(classNode.name.lastIndexOf('/') + 1));
            classInfo.setPackageName(classNode.name.substring(0, classNode.name.lastIndexOf('/')).replace('/', '.'));

        } else {
            classInfo.setName(classNode.name);
        }

        classInfo.setType(Type.getObjectType(classNode.name));
        classInfo.setSignature(classNode.signature);

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
            classInfo.setPublic(true);
        } else if ((access & Opcodes.ACC_PROTECTED) != 0) {
            classInfo.setProtected(true);
        } else if ((access & Opcodes.ACC_PRIVATE) != 0) {
            classInfo.setPrivate(true);
        }

        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            classInfo.setAbstract(true);
        }

        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            classInfo.setInterface(true);
        }

        if ((access & Opcodes.ACC_ENUM) != 0) {
            classInfo.setEnum(true);
        }

        if (Objects.nonNull(classNode.superName)) {
            classInfo.setSuperClassQName(getQualifiedClassName(classNode.superName));
        }

        classInfo.setInterfaceQNameList(getInterfaceQNameList(classNode));
        classInfo.setInnerClassQNameList(getInnerClassQNameList(classNode));

        return classInfo;
    }

    private static List<FieldInfo> getFieldInfoList(ClassNode classNode) {
        List<FieldNode> fieldNodeList = classNode.fields;
        List<FieldInfo> fieldInfoList = new ArrayList<>();

        for (FieldNode fieldNode: fieldNodeList) {
            fieldInfoList.add(FieldInfoExtractor.getFieldInfo(fieldNode));
        }

        return fieldInfoList;
    }

    private static List<MethodInfo> getMethodInfoList(ClassNode classNode, ClassInfo classInfo) {
        List<MethodNode> methodNodeList = classNode.methods;
        List<MethodInfo> methodInfoList = new ArrayList<>();

        for (MethodNode methodNode : methodNodeList) {
            methodInfoList.add(MethodInfoExtractor.getMethodInfo(methodNode, classInfo));
        }

        return methodInfoList;
    }

    private static List<String> getInnerClassQNameList(ClassNode classNode) {
        List<String> innerClassQNameList = new ArrayList<>();

        for (InnerClassNode innerClassNode : classNode.innerClasses) {
            if (!innerClassNode.name.matches(ANONYMOUS_INNER_CLASS_NAME_REGEX)) {
                innerClassQNameList.add(getQualifiedClassName(innerClassNode.name));
            }
        }

        return innerClassQNameList;
    }

    private static List<String> getInterfaceQNameList(ClassNode classNode) {
        List<String> implementedInterfaces = classNode.interfaces;

        List<String> interfaceQNameList = new ArrayList<>();
        for (String interfaceName : implementedInterfaces) {
            interfaceQNameList.add(getQualifiedClassName(interfaceName));
        }

        return interfaceQNameList;
    }

    private static String getQualifiedClassName(String className) {
        String qualifiedClassName = className.replace('/', '.');

        return qualifiedClassName.contains("$")
                ? qualifiedClassName.replaceAll("\\$", ".")
                : qualifiedClassName;
    }

    private static ClassNode getClassNode(JarFile jarFile, JarEntry jarEntry) {
        try (InputStream classFileInputStream = jarFile.getInputStream(jarEntry)) {
            ClassNode classNode = new ClassNode();

            ClassReader classReader = new ClassReader(classFileInputStream);
            classReader.accept(classNode, 0);

            return classNode;
        } catch (IOException e) {
            logger.error("Error", e);
        }

        return null;
    }

    private static ClassNode getClassNode(ZipFile zipFile, ZipEntry zipEntry) {
        try (InputStream classFileInputStream = zipFile.getInputStream(zipEntry)) {
            ClassNode classNode = new ClassNode();

            ClassReader classReader = new ClassReader(classFileInputStream);
            classReader.accept(classNode, 0);

            return classNode;
        } catch (IOException e) {
            logger.error("Error", e);
        }

        return null;
    }

}
