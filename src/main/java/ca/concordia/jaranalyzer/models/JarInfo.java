package ca.concordia.jaranalyzer.models;

import ca.concordia.jaranalyzer.util.Utility;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Diptopol
 * @since 3/14/2022 3:01 PM
 */
public class JarInfo {

    private static final Logger logger = LoggerFactory.getLogger(JarInfo.class);

    private final String name;

    private final Artifact artifact;

    private final Map<String, PackageInfo> packageInfoMap;

    public JarInfo(String groupId, String artifactId, String version, ZipFile jmodFile) {
        logger.info("Processing JarInfo of {}:{}:{}", groupId, artifactId, version);

        this.name = Utility.getJarName(jmodFile.getName());
        this.artifact = new Artifact(groupId, artifactId, version);
        this.packageInfoMap = getPackageInfoMap(jmodFile);

    }

    public JarInfo(String groupId, String artifactId, String version, JarFile jarFile) {
        logger.info("Processing JarInfo of {}:{}:{}", groupId, artifactId, version);

        this.name = Utility.getJarName(jarFile.getName());
        this.artifact = new Artifact(groupId, artifactId, version);
        this.packageInfoMap = getPackageInfoMap(jarFile);
    }

    public String getName() {
        return name;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public Collection<PackageInfo> getPackageInfoCollection() {
        return this.packageInfoMap.values();
    }

    private Map<String, PackageInfo> getPackageInfoMap(ZipFile jmodFile) {
        Map<String, PackageInfo> packageInfoMap = new HashMap<>();
        Enumeration<? extends ZipEntry> entries = jmodFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();

            if (entryName.startsWith("classes/")
                    && entryName.endsWith(".class") && !entryName.equals("classes/module-info.class")) {
                ClassNode classNode = getClassNode(jmodFile, entry);
                populateClassInfo(classNode, packageInfoMap);
            }
        }

        populateSuperClassAndInterface(packageInfoMap);

        return packageInfoMap;
    }

    private Map<String, PackageInfo> getPackageInfoMap(JarFile jarFile) {
        Map<String, PackageInfo> packageInfoMap = new HashMap<>();
        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();

            //only fetching class file (except: module-info.class)
            if (entryName.endsWith(".class") && !entryName.equals("module-info.class")) {
                ClassNode classNode = getClassNode(jarFile, entry);
                populateClassInfo(classNode, packageInfoMap);
            }
        }

        populateSuperClassAndInterface(packageInfoMap);

        return packageInfoMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JarInfo jarInfo = (JarInfo) o;

        return name.equals(jarInfo.name) && artifact.equals(jarInfo.artifact);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, artifact);
    }


    private void populateClassInfo(ClassNode classNode, Map<String, PackageInfo> packageInfoMap) {
        if (Objects.nonNull(classNode)) {
            ClassInfo classInfo = new ClassInfo(classNode);

            int packageNameConcludingIndex = classInfo.isInnerClass()
                    ? classInfo.getQualifiedName().substring(0, classInfo.getQualifiedName().lastIndexOf('.')).lastIndexOf(".")
                    : classInfo.getQualifiedName().lastIndexOf('.');

            if (packageNameConcludingIndex >= 0) {
                String packageName = classInfo.getQualifiedName().substring(0, packageNameConcludingIndex);

                if (packageInfoMap.containsKey(packageName)) {
                    PackageInfo packageInfo = packageInfoMap.get(packageName);
                    packageInfo.addClass(classInfo);
                } else {
                    PackageInfo packageInfo = new PackageInfo(packageName);
                    packageInfo.addClass(classInfo);

                    packageInfoMap.put(packageName, packageInfo);
                }
            }
        }
    }


    private void populateSuperClassAndInterface(Map<String, PackageInfo> packageInfoMap) {
        List<ClassInfo> classInfoList = getClassInfoList(packageInfoMap.values());

        for (ClassInfo classInfo : classInfoList) {
            if (!classInfo.getSuperClassName().equals("java.lang.Object")) {
                for (ClassInfo superClassInfo : classInfoList) {
                    if (superClassInfo.getQualifiedName().equals(classInfo.getSuperClassName())) {
                        classInfo.setSuperClassInfo(superClassInfo);
                    }
                }
            }

            for (String superInterface : classInfo.getSuperInterfaceNames()) {
                for (ClassInfo interfaceClassInfo : classInfoList) {
                    if (interfaceClassInfo.getQualifiedName().equals(superInterface)) {
                        classInfo.putSuperInterfaceInfo(superInterface, interfaceClassInfo);
                    }
                }
            }
        }
    }

    private ClassNode getClassNode(ZipFile zipFile, ZipEntry zipEntry) {
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

    private ClassNode getClassNode(JarFile jarFile, JarEntry jarEntry) {
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

    private List<ClassInfo> getClassInfoList(Collection<PackageInfo> packageInfoCollection) {
        List<ClassInfo> classInfoList = new ArrayList<>();

        for (PackageInfo packageInfo: packageInfoCollection) {
            classInfoList.addAll(packageInfo.getClassList());
        }

        return classInfoList;
    }

}
