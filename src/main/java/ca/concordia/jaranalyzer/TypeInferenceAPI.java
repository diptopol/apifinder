package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.entity.ClassInfo;
import ca.concordia.jaranalyzer.entity.FieldInfo;
import ca.concordia.jaranalyzer.entity.MethodInfo;
import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.OwningClassInfo;
import ca.concordia.jaranalyzer.models.typeInfo.NullTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import ca.concordia.jaranalyzer.service.ClassInfoService;
import ca.concordia.jaranalyzer.service.FieldInfoService;
import ca.concordia.jaranalyzer.service.JarInfoService;
import ca.concordia.jaranalyzer.service.MethodInfoService;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import io.vavr.Tuple2;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 12/23/2020 9:01 PM
 */
public class TypeInferenceAPI extends TypeInferenceBase {

    private static JarAnalyzer jarAnalyzer;

    private static JarInfoService jarInfoService;
    private static ClassInfoService classInfoService;
    private static MethodInfoService methodInfoService;
    private static FieldInfoService fieldInfoService;

    static {
        jarAnalyzer = new JarAnalyzer();

        jarInfoService = new JarInfoService();
        classInfoService = new ClassInfoService();
        methodInfoService = new MethodInfoService(classInfoService);
        fieldInfoService = new FieldInfoService(classInfoService);
    }

    public static Tuple2<String, Set<Artifact>> loadJavaAndExternalJars(String commitId, String projectName, Git git) {
        return jarAnalyzer.loadJavaAndExternalJars(commitId, projectName, git);
    }

    public static Tuple2<String, Set<Artifact>> loadJavaAndExternalJars(String commitId, String projectName, String cloneUrl) {
        return jarAnalyzer.loadJavaAndExternalJars(commitId, projectName, cloneUrl);
    }

    public static void loadJavaPackage(int majorJavaVersion) {
        jarAnalyzer.loadJavaPackage(majorJavaVersion);
    }

    public static void loadJar(Artifact artifact) {
        jarAnalyzer.loadJar(artifact);
    }

    public static void loadJar(String groupId, String artifactId, String version) {
        jarAnalyzer.loadJar(new Artifact(groupId, artifactId, version));
    }

    public static List<MethodInfo> getAllMethods(Set<Artifact> dependentArtifactSet,
                                                 String javaVersion,
                                                 List<String> importList,
                                                 String methodName,
                                                 int numberOfParameters) {
        return getAllMethods(dependentArtifactSet, javaVersion, importList, methodName, numberOfParameters,
                null, false, null, null, false);
    }

    /**
     * The process of checking classes for specific method will happen in below steps.<br><br>
     *
     * <strong>Step 0</strong>: If we can resolve qualified invoker class name, we will use caller class to resolve method
     * info.<br>
     *
     * <strong>Step 1</strong>: If invokerClass is null, and we have owningClassInfo, then we can check the owning class
     * hierarchy to resolve the method info.<br>
     *
     * <strong>Step 2</strong>: All the classes who are directly mentioned in the import statement will be checked,
     * if method found it will be returned.<br>
     *
     * <strong>Step 3</strong>: All the inner classes of classes who are directly mentioned in the import statement will
     * be checked, if method found it will be returned.<br>
     *
     * <strong>Step 4</strong>: All the classes under on-demand package import will be searched, if method found
     * it will be returned.<br>
     *
     * <strong>Step 5</strong>: Recursively look for super classes and interfaces from all the import classes (on demand and normal)
     * if in any step method is found it will be returned, otherwise recursion will happen until java.lang.Object is
     * reached, then if no method is found an empty list will be returned.<br>
     */
    public static List<MethodInfo> getAllMethods(Set<Artifact> dependentArtifactSet,
                                                 String javaVersion,
                                                 List<String> importList,
                                                 String methodName,
                                                 int numberOfParameters,
                                                 String invokerClassName,
                                                 boolean isSuperInvoker,
                                                 List<String> enclosingQualifiedClassNameList,
                                                 List<String> nonClosingQualifiedClassNameList,
                                                 boolean isClassInstantiation,
                                                 String... argumentTypes) {
        List<Integer> internalDependencyJarIdList = new ArrayList<>();
        List<Integer> jarIdList = jarInfoService.getJarIdList(dependentArtifactSet, javaVersion, internalDependencyJarIdList);

        Set<String> importedClassQNameSet = getImportedQNameSet(importList);
        List<String> packageNameList = getPackageNameList(importList);

        OwningClassInfo owningClassInfo = getOwningClassInfo(dependentArtifactSet, javaVersion,
                enclosingQualifiedClassNameList, nonClosingQualifiedClassNameList, jarInfoService, classInfoService);

        List<MethodInfo> qualifiedMethodInfoList = new ArrayList<>();

        TypeInfo invokerTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importList, invokerClassName, owningClassInfo);

        List<TypeInfo> argumentTypeInfoList = new ArrayList<>();

        for (String argumentType: argumentTypes) {
            TypeInfo argumentTypeInfo = getTypeInfo(dependentArtifactSet, javaVersion, importList,
                    argumentType, owningClassInfo);

            argumentTypeInfoList.add(argumentTypeInfo);
        }

        methodName = processMethodName(methodName, importedClassQNameSet);

        /*
          STEP 0
         */
        if (Objects.nonNull(invokerTypeInfo)) {
            Set<String> classQNameSet = Collections.singleton(invokerTypeInfo.getQualifiedClassName());

            Set<MethodInfo> deferredQualifiedMethodInfoSet = new LinkedHashSet<>();

            while (!classQNameSet.isEmpty() && qualifiedMethodInfoList.isEmpty()) {
                qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters,
                        jarIdList, classQNameSet, classInfoService, methodInfoService);

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerTypeInfo, isSuperInvoker,
                        isClassInstantiation, argumentTypeInfoList, numberOfParameters, jarIdList, internalDependencyJarIdList);

                if (!qualifiedMethodInfoList.isEmpty()
                        && qualifiedMethodInfoList.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
                    deferredQualifiedMethodInfoSet.addAll(qualifiedMethodInfoList);
                    qualifiedMethodInfoList.clear();
                }

                if (qualifiedMethodInfoList.isEmpty()) {
                    classQNameSet = getSuperClassQNameSet(classQNameSet, jarIdList, classInfoService);
                }
            }

            if (qualifiedMethodInfoList.isEmpty() && !deferredQualifiedMethodInfoSet.isEmpty()) {
                deferredQualifiedMethodInfoSet = prioritizeDeferredMethodInfoSet(deferredQualifiedMethodInfoSet);
                qualifiedMethodInfoList.addAll(deferredQualifiedMethodInfoSet);
            }

            if (!qualifiedMethodInfoList.isEmpty()) {
                return qualifiedMethodInfoList;
            }
        }

        /*
          STEP 1
         */
        if (Objects.isNull(invokerTypeInfo) && Objects.nonNull(owningClassInfo)) {
            Set<MethodInfo> deferredQualifiedMethodInfoSet = new LinkedHashSet<>();

            for (int i = 0; i < owningClassInfo.getQualifiedClassNameSetInHierarchy().size(); i++) {
                if (i == 0 && isSuperInvoker) {
                    continue;
                }

                Set<String> classQNameSet = owningClassInfo.getQualifiedClassNameSetInHierarchy().get(i);

                qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters,
                        jarIdList, classQNameSet, classInfoService, methodInfoService);

                boolean isOwningClassAttribute = (i == 0);
                qualifiedMethodInfoList.forEach(m -> m.setOwningClassAttribute(isOwningClassAttribute));

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, null, isSuperInvoker,
                        isClassInstantiation, argumentTypeInfoList, numberOfParameters, jarIdList, internalDependencyJarIdList);

                if (!qualifiedMethodInfoList.isEmpty()
                        && qualifiedMethodInfoList.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
                    deferredQualifiedMethodInfoSet.addAll(qualifiedMethodInfoList);
                    qualifiedMethodInfoList.clear();
                }

                if (!qualifiedMethodInfoList.isEmpty()) {
                    return qualifiedMethodInfoList;
                }
            }

            if (!deferredQualifiedMethodInfoSet.isEmpty()) {
                deferredQualifiedMethodInfoSet = prioritizeDeferredMethodInfoSet(deferredQualifiedMethodInfoSet);
                qualifiedMethodInfoList.addAll(deferredQualifiedMethodInfoSet);
            }

            if (!qualifiedMethodInfoList.isEmpty()) {
                return qualifiedMethodInfoList;
            }
        }

        /*
          STEP 2
         */
        qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters,
                jarIdList, importedClassQNameSet, classInfoService, methodInfoService);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerTypeInfo, isSuperInvoker,
                isClassInstantiation, argumentTypeInfoList, numberOfParameters, jarIdList, internalDependencyJarIdList);

        Set<MethodInfo> deferredQualifiedMethodInfoSet = new LinkedHashSet<>();

        if (!qualifiedMethodInfoList.isEmpty()
                && qualifiedMethodInfoList.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
            deferredQualifiedMethodInfoSet.addAll(qualifiedMethodInfoList);
            qualifiedMethodInfoList.clear();
        }

        if (!qualifiedMethodInfoList.isEmpty()) {
            return qualifiedMethodInfoList;
        }

        /*
          STEP 3
         */
        qualifiedMethodInfoList = getQualifiedMethodInfoListForInnerClass(methodName, numberOfParameters, jarIdList, importedClassQNameSet, methodInfoService);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerTypeInfo, isSuperInvoker,
                isClassInstantiation, argumentTypeInfoList, numberOfParameters, jarIdList, internalDependencyJarIdList);

        if (!qualifiedMethodInfoList.isEmpty()
                && qualifiedMethodInfoList.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
            deferredQualifiedMethodInfoSet.addAll(qualifiedMethodInfoList);
            qualifiedMethodInfoList.clear();
        }

        if (!qualifiedMethodInfoList.isEmpty()) {
            return qualifiedMethodInfoList;
        }

        /*
          STEP 4
         */
        qualifiedMethodInfoList = getQualifiedMethodInfoListForPackageImport(methodName, numberOfParameters,
                packageNameList, importedClassQNameSet, jarIdList, classInfoService, methodInfoService);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerTypeInfo, isSuperInvoker,
                isClassInstantiation, argumentTypeInfoList, numberOfParameters, jarIdList, internalDependencyJarIdList);

        if (!qualifiedMethodInfoList.isEmpty()
                && qualifiedMethodInfoList.stream().allMatch(MethodInfo::hasDeferredCriteria)) {

            deferredQualifiedMethodInfoSet.addAll(qualifiedMethodInfoList);
            qualifiedMethodInfoList.clear();
        }

        if (!qualifiedMethodInfoList.isEmpty()) {
            return qualifiedMethodInfoList;
        }

        /*
          STEP 5
         */
        Set<String> classQNameSet = new HashSet<>(importedClassQNameSet);

        while (!classQNameSet.isEmpty() && qualifiedMethodInfoList.isEmpty()) {
            classQNameSet = getSuperClassQNameSet(classQNameSet, jarIdList, classInfoService);

            qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters, jarIdList,
                    classQNameSet, classInfoService, methodInfoService);

            qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerTypeInfo, isSuperInvoker,
                    isClassInstantiation, argumentTypeInfoList, numberOfParameters, jarIdList, internalDependencyJarIdList);

            if (!qualifiedMethodInfoList.isEmpty()
                    && qualifiedMethodInfoList.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
                deferredQualifiedMethodInfoSet.addAll(qualifiedMethodInfoList);
                qualifiedMethodInfoList.clear();
            }
        }

        if (qualifiedMethodInfoList.isEmpty() && !deferredQualifiedMethodInfoSet.isEmpty()) {
            deferredQualifiedMethodInfoSet = prioritizeDeferredMethodInfoSet(deferredQualifiedMethodInfoSet);

            qualifiedMethodInfoList.addAll(deferredQualifiedMethodInfoSet);
        }

        if (!qualifiedMethodInfoList.isEmpty()) {
            return qualifiedMethodInfoList;

        } else {
            return Collections.emptyList();
        }
    }

    public static OwningClassInfo getOwningClassInfo(Set<Artifact> dependentArtifactSet,
                                                     String javaVersion,
                                                     List<String> enclosingQualifiedClassNameList,
                                                     List<String> nonEnclosingQualifiedClassNameList,
                                                     JarInfoService jarInfoService,
                                                     ClassInfoService classInfoService) {

        return getOwningClassInfoInternal(dependentArtifactSet, javaVersion, enclosingQualifiedClassNameList,
                nonEnclosingQualifiedClassNameList, jarInfoService, classInfoService);
    }

    public static MethodInfo getAbstractMethodInfoOfFunctionalInterface(Set<Artifact> dependentArtifactSet,
                                                                        String javaVersion,
                                                                        String classQName) {

        List<Integer> jarIdList = jarInfoService.getJarIdList(dependentArtifactSet, javaVersion, null);

        List<MethodInfo> abstractMethodInfoList =
                getAbstractMethodInfoListForFunctionalInterface(classQName, jarIdList, methodInfoService, classInfoService);

        if (abstractMethodInfoList.size() != 1) {
            return null;
        }

        return abstractMethodInfoList.get(0);
    }

    public static List<ClassInfo> getAllTypes(Set<Artifact> dependentArtifactSet,
                                              String javaVersion,
                                              List<String> importList,
                                              String typeName,
                                              OwningClassInfo owningClassInfo) {
        if (Objects.isNull(typeName)) {
            return Collections.emptyList();
        }

        List<Integer> jarIdList = jarInfoService.getJarIdList(dependentArtifactSet, javaVersion, null);
        Set<String> importedClassQNameSet = getImportedQNameSet(importList);
        List<String> packageNameList = getPackageNameList(importList);

        if (typeName.contains(".")) {
            if (StringUtils.countMatches(typeName, ".") > 1) {
                /*
                 * class name can be array (will end with []) or inner class (ends with $inner_class_name). We need to
                 * resolve those before adding those to import class names.
                 */
                importedClassQNameSet.add(typeName.replaceAll("\\$", ".").replaceAll("\\[]", ""));
                typeName = typeName.substring(typeName.lastIndexOf(".") + 1);
            }
        }

        List<ClassInfo> qualifiedClassInfoList = resolveQClassInfoForClass(typeName, jarIdList, importedClassQNameSet,
                packageNameList, classInfoService, owningClassInfo);

        qualifiedClassInfoList = filtrationBasedOnPrioritization(typeName, owningClassInfo,
                importedClassQNameSet, qualifiedClassInfoList);

        return qualifiedClassInfoList;
    }

    public static List<FieldInfo> getAllFieldTypes(Set<Artifact> dependentArtifactSet,
                                                   String javaVersion,
                                                   List<String> importList,
                                                   String fieldName,
                                                   OwningClassInfo owningClassInfo) {
        List<Integer> jarIdList = jarInfoService.getJarIdList(dependentArtifactSet, javaVersion, null);

        Set<String> importedClassQNameSet = getImportedQNameSet(importList);
        List<String> packageNameList = getPackageNameList(importList);

        String invokerClassQName = null;

        if (fieldName.contains(".")) {
            if (StringUtils.countMatches(fieldName, ".") >= 1) {
                String invokerClassName = fieldName.substring(0, fieldName.lastIndexOf("."));
                invokerClassQName = resolveQNameForClass(invokerClassName, owningClassInfo, jarIdList,
                        importedClassQNameSet, packageNameList, classInfoService);

                importedClassQNameSet.add(invokerClassQName);
            }

            if (fieldName.contains("[]") && fieldName.endsWith(".length")) {
                return Collections.singletonList(FieldInfo.getLengthFieldInfoOfArray());
            }

            fieldName = fieldName.substring(fieldName.lastIndexOf(".") + 1);
        }

        List<FieldInfo> qualifiedFieldList = new ArrayList<>();
        /*
          STEP 0
         */
        if (invokerClassQName != null) {
            Set<String> invokerQualifiedClassNameSet = new HashSet<>(Collections.singletonList(invokerClassQName));

            while (!invokerQualifiedClassNameSet.isEmpty() && qualifiedFieldList.isEmpty()) {
                qualifiedFieldList = fieldInfoService.getFieldInfoList(invokerQualifiedClassNameSet, jarIdList, fieldName);

                if (qualifiedFieldList.isEmpty()) {
                    invokerQualifiedClassNameSet = getSuperClassQNameSet(invokerQualifiedClassNameSet, jarIdList, classInfoService);
                }
            }

            if (!qualifiedFieldList.isEmpty()) {
                return qualifiedFieldList;
            }
        }

        /*
          STEP 1
         */
        if (Objects.nonNull(owningClassInfo)) {
            for (Set<String> qClassNameSet: owningClassInfo.getQualifiedClassNameSetInHierarchy()) {
                qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarIdList, qClassNameSet);

                if (!qualifiedFieldList.isEmpty()) {
                    return qualifiedFieldList;
                }
            }
        }

        /*
          STEP 2
         */
        qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarIdList, importedClassQNameSet);

        if (!qualifiedFieldList.isEmpty()) {
            return qualifiedFieldList;
        }

        /*
          STEP 3
         */
        Set<String> classNameListForPackage = classInfoService.getClassQNameSet(jarIdList, packageNameList);

        importedClassQNameSet.addAll(classNameListForPackage);

        qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarIdList, classNameListForPackage);

        if (!qualifiedFieldList.isEmpty()) {
            return qualifiedFieldList;
        }

        /*
          STEP 4
         */
        Set<String> classQNameSet = new HashSet<>(importedClassQNameSet);

        while (!classQNameSet.isEmpty() && qualifiedFieldList.isEmpty()) {
            classQNameSet = classInfoService.getSuperClassQNameSet(classQNameSet, jarIdList, null);

            if (!classQNameSet.isEmpty()) {
                qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarIdList, classQNameSet);
            }
        }

        return qualifiedFieldList;
    }

    private static List<MethodInfo> filterByMethodArgumentTypes(List<MethodInfo> methodInfoList,
                                                                List<TypeInfo> argumentTypeInfoList,
                                                                List<Integer> jarIdList) {
        if (!methodInfoList.isEmpty()) {
            methodInfoList = methodInfoList.stream().filter(methodInfo -> {
                List<TypeInfo> orderedArgumentTypeInfoList = new ArrayList<>(argumentTypeInfoList);
                List<TypeInfo> orderedMethodArgumentTypeInfoList = new ArrayList<>(methodInfo.getArgumentTypeInfoList());

                return matchMethodArguments(orderedArgumentTypeInfoList, orderedMethodArgumentTypeInfoList,
                        jarIdList, methodInfoService, classInfoService, methodInfo);
            }).collect(Collectors.toList());
        }

        return methodInfoList;
    }

    private static List<MethodInfo> filterProcess(List<MethodInfo> methodInfoList,
                                                  TypeInfo invokerTypeInfo,
                                                  boolean isSuperInvoker,
                                                  boolean isClassInstantiation,
                                                  List<TypeInfo> argumentTypeInfoList,
                                                  int numberOfParameters,
                                                  List<Integer> jarIdList,
                                                  List<Integer> internalDependencyJarIdList) {
        if (methodInfoList.isEmpty()) {
            return methodInfoList;
        }

        modifyMethodInfoForArray(methodInfoList, invokerTypeInfo);
        setInternalDependencyProperty(methodInfoList, internalDependencyJarIdList);

        TypeInfo firstArgumentTypeInfo = argumentTypeInfoList.isEmpty() ? null : argumentTypeInfoList.get(0);
        String firstArgumentQualifiedClassName = Objects.nonNull(firstArgumentTypeInfo)
                ? firstArgumentTypeInfo.getQualifiedClassName()
                : null;

        reduceArgumentForInnerClassConstructorIfRequired(methodInfoList, firstArgumentQualifiedClassName,
                numberOfParameters, jarIdList, classInfoService);

        if (isClassInstantiation) {
            methodInfoList = filterBasedOnClassInstantiation(methodInfoList);
        }

        methodInfoList = filterByMethodInvoker(methodInfoList, invokerTypeInfo, isSuperInvoker, jarIdList, classInfoService);

        if (!(numberOfParameters > 0 && argumentTypeInfoList.isEmpty())) {
            methodInfoList = filterByMethodArgumentTypes(methodInfoList, argumentTypeInfoList, jarIdList);
        }

        methodInfoList = filterMethodInfoListBasedOnOwningClass(methodInfoList);

        methodInfoList = filteredNonAbstractMethod(methodInfoList);

        methodInfoList = prioritizeMethodInfoListBasedOnArguments(methodInfoList);

        return methodInfoList;
    }

    private static List<FieldInfo> getQualifiedFieldInfoList(String fieldName, List<Integer> jarIdList, Set<String> classQNameSet) {
        return fieldInfoService.getFieldInfoList(classQNameSet, jarIdList, fieldName);
    }

    private static TypeInfo getTypeInfo(Set<Artifact> dependentArtifactSet,
                                        String javaVersion,
                                        List<String> importList,
                                        String className,
                                        OwningClassInfo owningClassInfo) {

        if (Objects.isNull(className)) {
            return null;
        }

        if (className.equals("null")) {
            return new NullTypeInfo();
        }

        return InferenceUtility.getTypeInfoFromClassName(dependentArtifactSet, javaVersion, importList,
                className, owningClassInfo);
    }

}
