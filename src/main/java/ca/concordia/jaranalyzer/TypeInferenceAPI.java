package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.*;
import ca.concordia.jaranalyzer.models.typeInfo.NullTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import ca.concordia.jaranalyzer.util.TinkerGraphStorageUtility;
import io.vavr.Tuple2;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.api.Git;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 12/23/2020 9:01 PM
 */
public class TypeInferenceAPI extends TypeInferenceBase {

    private static TinkerGraph tinkerGraph;
    private static JarAnalyzer jarAnalyzer;

    static {
        tinkerGraph = TinkerGraphStorageUtility.getTinkerGraph();
        jarAnalyzer = TinkerGraphStorageUtility.getJarAnalyzer();
    }

    public static Tuple2<String, Set<Artifact>> loadJavaAndExternalJars(String commitId, String projectName, Git git) {
        return jarAnalyzer.loadJavaAndExternalJars(commitId, projectName, git);
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
                null, false, null, null);
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
                                                 String... argumentTypes) {

        Object[] jarVertexIds = getJarVertexIds(dependentArtifactSet, javaVersion, tinkerGraph);

        Set<String> importedClassQNameSet = getImportedQNameSet(importList);
        List<String> packageNameList = getPackageNameList(importList);

        OwningClassInfo owningClassInfo = getOwningClassInfo(dependentArtifactSet, javaVersion,
                enclosingQualifiedClassNameList, nonClosingQualifiedClassNameList, tinkerGraph);

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

            Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

            while (!classQNameSet.isEmpty() && qualifiedMethodInfoList.isEmpty()) {
                qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters,
                        jarVertexIds, classQNameSet, tinkerGraph);

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerTypeInfo, isSuperInvoker,
                        argumentTypeInfoList, numberOfParameters, jarVertexIds);

                if (!qualifiedMethodInfoList.isEmpty()
                        && qualifiedMethodInfoList.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
                    deferredQualifiedMethodInfoSet.addAll(qualifiedMethodInfoList);
                    qualifiedMethodInfoList.clear();
                }

                if (qualifiedMethodInfoList.isEmpty()) {
                    classQNameSet = getSuperClassQNameSet(classQNameSet, jarVertexIds, tinkerGraph);
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
            Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

            for (int i = 0; i < owningClassInfo.getQualifiedClassNameSetInHierarchy().size(); i++) {
                if (i == 0 && isSuperInvoker) {
                    continue;
                }

                Set<String> classQNameSet = owningClassInfo.getQualifiedClassNameSetInHierarchy().get(i);

                qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters,
                        jarVertexIds, classQNameSet, tinkerGraph);

                boolean isOwningClassAttribute = (i == 0);
                qualifiedMethodInfoList.forEach(m -> m.setOwningClassAttribute(isOwningClassAttribute));

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, null, isSuperInvoker,
                        argumentTypeInfoList, numberOfParameters, jarVertexIds);

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
                jarVertexIds, importedClassQNameSet, tinkerGraph);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerTypeInfo, isSuperInvoker,
                argumentTypeInfoList, numberOfParameters, jarVertexIds);

        Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

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
        qualifiedMethodInfoList = getQualifiedMethodInfoListForInnerClass(methodName, numberOfParameters, jarVertexIds,
                importedClassQNameSet, tinkerGraph);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerTypeInfo, isSuperInvoker,
                argumentTypeInfoList, numberOfParameters, jarVertexIds);

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
                packageNameList, importedClassQNameSet, jarVertexIds, tinkerGraph);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerTypeInfo, isSuperInvoker,
                argumentTypeInfoList, numberOfParameters, jarVertexIds);

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
            classQNameSet = getSuperClassQNameSet(classQNameSet, jarVertexIds, tinkerGraph);

            qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters, jarVertexIds,
                    classQNameSet, tinkerGraph);

            qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerTypeInfo, isSuperInvoker,
                    argumentTypeInfoList, numberOfParameters, jarVertexIds);

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
                                                     List<String> nonEnclosingQualifiedClassNameList) {

        return getOwningClassInfo(dependentArtifactSet, javaVersion, enclosingQualifiedClassNameList,
                nonEnclosingQualifiedClassNameList, tinkerGraph);
    }

    public static List<ClassInfo> getAllTypes(Set<Artifact> dependentArtifactSet,
                                              String javaVersion,
                                              List<String> importList,
                                              String typeName,
                                              OwningClassInfo owningClassInfo) {
        if (Objects.isNull(typeName)) {
            return Collections.emptyList();
        }

        Object[] jarVertexIds = getJarVertexIds(dependentArtifactSet, javaVersion, tinkerGraph);
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

        List<ClassInfo> qualifiedClassInfoList = resolveQClassInfoForClass(typeName, jarVertexIds, importedClassQNameSet,
                packageNameList, tinkerGraph, owningClassInfo);

        qualifiedClassInfoList = filtrationBasedOnPrioritization(typeName, owningClassInfo,
                importedClassQNameSet, qualifiedClassInfoList);

        return qualifiedClassInfoList;
    }

    public static List<FieldInfo> getAllFieldTypes(Set<Artifact> dependentArtifactSet,
                                                   String javaVersion,
                                                   List<String> importList,
                                                   String fieldName,
                                                   OwningClassInfo owningClassInfo) {


        Object[] jarVertexIds = getJarVertexIds(dependentArtifactSet, javaVersion, tinkerGraph);

        Set<String> importedClassQNameSet = getImportedQNameSet(importList);
        List<String> packageNameList = getPackageNameList(importList);

        String invokerClassQName = null;

        if (fieldName.contains(".")) {
            if (StringUtils.countMatches(fieldName, ".") >= 1) {
                String invokerClassName = fieldName.substring(0, fieldName.lastIndexOf("."));
                invokerClassQName = resolveQNameForClass(invokerClassName, owningClassInfo, jarVertexIds,
                        importedClassQNameSet, packageNameList, tinkerGraph);

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
                qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarVertexIds, invokerQualifiedClassNameSet);

                if (qualifiedFieldList.isEmpty()) {
                    invokerQualifiedClassNameSet = getSuperClassQNameSet(invokerQualifiedClassNameSet, jarVertexIds, tinkerGraph);
                }
            }

            if (!qualifiedFieldList.isEmpty()) {
                return populateClassInfoForField(qualifiedFieldList);
            }
        }

        /*
          STEP 1
         */
        if (Objects.nonNull(owningClassInfo)) {
            for (Set<String> qClassNameSet: owningClassInfo.getQualifiedClassNameSetInHierarchy()) {
                qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarVertexIds, qClassNameSet);

                if (!qualifiedFieldList.isEmpty()) {
                    return populateClassInfoForField(qualifiedFieldList);
                }
            }
        }

        /*
          STEP 2
         */
        qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarVertexIds, importedClassQNameSet);

        if (!qualifiedFieldList.isEmpty()) {
            return populateClassInfoForField(qualifiedFieldList);
        }

        /*
          STEP 3
         */
        Set<String> classNameListForPackgage = tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg")
                .has("Kind", "Package")
                .has("Name", TextP.within(packageNameList))
                .out("Contains")
                .has("Kind", "Class")
                .<String>values("QName")
                .toSet();

        importedClassQNameSet.addAll(classNameListForPackgage);

        qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarVertexIds, classNameListForPackgage);

        if (!qualifiedFieldList.isEmpty()) {
            return populateClassInfoForField(qualifiedFieldList);
        }

        /*
          STEP 4
         */
        Set<String> classQNameSet = new HashSet<>(importedClassQNameSet);

        while (!classQNameSet.isEmpty() && qualifiedFieldList.isEmpty()) {
            classQNameSet = tinkerGraph.traversal().V(jarVertexIds)
                    .out("ContainsPkg").out("Contains")
                    .has("Kind", "Class")
                    .has("QName", TextP.within(classQNameSet))
                    .out("extends", "implements")
                    .<String>values("Name")
                    .toSet();

            qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarVertexIds, classQNameSet);
        }

        return qualifiedFieldList.isEmpty() ? qualifiedFieldList : populateClassInfoForField(qualifiedFieldList);
    }

    public static List<String> getQualifiedClassName(String className) {
        return tinkerGraph.traversal().V()
                .has("Kind", "Class")
                .has("Name", className)
                .<String>values("QName")
                .toList();
    }

    private static List<MethodInfo> filterByMethodArgumentTypes(List<MethodInfo> methodInfoList,
                                                                List<TypeInfo> argumentTypeInfoList,
                                                                Object[] jarVertexIds) {
        if (!methodInfoList.isEmpty()) {
            methodInfoList = methodInfoList.stream().filter(methodInfo -> {
                List<TypeInfo> orderedArgumentTypeInfoList = new ArrayList<>(argumentTypeInfoList);
                List<TypeInfo> orderedMethodArgumentTypeInfoList = new ArrayList<>(methodInfo.getArgumentTypeInfoList());

                return matchMethodArguments(orderedArgumentTypeInfoList, orderedMethodArgumentTypeInfoList,
                        jarVertexIds, tinkerGraph, methodInfo);
            }).collect(Collectors.toList());

        }

        return methodInfoList;
    }

    public static String getPrimitiveWrapperClassName(String primitiveClassName) {
        return PRIMITIVE_WRAPPER_CLASS_MAP.get(primitiveClassName);
    }

    private static List<MethodInfo> filterProcess(List<MethodInfo> methodInfoList,
                                                  TypeInfo invokerTypeInfo,
                                                  boolean isSuperInvoker,
                                                  List<TypeInfo> argumentTypeInfoList,
                                                  int numberOfParameters,
                                                  Object[] jarVertexIds) {
        if (methodInfoList.isEmpty()) {
            return methodInfoList;
        }

        populateClassInfo(methodInfoList, tinkerGraph);
        modifyMethodInfoForArray(methodInfoList, invokerTypeInfo);

        TypeInfo firstArgumentTypeInfo = argumentTypeInfoList.isEmpty() ? null : argumentTypeInfoList.get(0);
        String firstArgumentQualifiedClassName = Objects.nonNull(firstArgumentTypeInfo)
                ? firstArgumentTypeInfo.getQualifiedClassName()
                : null;

        reduceArgumentForInnerClassConstructorIfRequired(methodInfoList, firstArgumentQualifiedClassName,
                numberOfParameters, jarVertexIds, tinkerGraph);

        methodInfoList = filterByMethodInvoker(methodInfoList, invokerTypeInfo, isSuperInvoker, jarVertexIds, tinkerGraph);

        if (!(numberOfParameters > 0 && argumentTypeInfoList.isEmpty())) {
            methodInfoList = filterByMethodArgumentTypes(methodInfoList, argumentTypeInfoList, jarVertexIds);
        }

        methodInfoList = filterMethodInfoListBasedOnOwningClass(methodInfoList);

        methodInfoList = prioritizeMethodInfoListBasedOnArguments(methodInfoList);

        methodInfoList = filteredNonAbstractMethod(methodInfoList);

        return methodInfoList;
    }

    private static List<FieldInfo> getQualifiedFieldInfoList(String fieldName, Object[] jarVertexIds, Set<String> classQNameSet) {
        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameSet))
                .out("Declares")
                .has("Kind", "Field")
                .has("Name", fieldName)
                .toStream()
                .map(FieldInfo::new)
                .collect(Collectors.toList());
    }

    private static List<FieldInfo> populateClassInfoForField(List<FieldInfo> qualifiedFieldInfoList) {
        qualifiedFieldInfoList.forEach(f -> {
            Set<ClassInfo> classInfoSet = tinkerGraph.traversal()
                    .V(f.getId())
                    .in("Declares")
                    .toStream()
                    .map(ClassInfo::new)
                    .collect(Collectors.toSet());

            if (!classInfoSet.isEmpty()) {
                f.setClassInfo(classInfoSet.iterator().next());
            }
        });

        return qualifiedFieldInfoList;
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
