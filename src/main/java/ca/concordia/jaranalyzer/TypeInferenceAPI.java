package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.*;
import ca.concordia.jaranalyzer.util.TinkerGraphStorageUtility;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.api.Git;
import org.objectweb.asm.Type;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public static Set<Artifact> loadExternalJars(String commitId, String projectName, Git git) {
        return jarAnalyzer.loadExternalJars(commitId, projectName, git);
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
                null, false, null);
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
                                                 String... argumentTypes) {

        Object[] jarVertexIds = getJarVertexIds(dependentArtifactSet, javaVersion, tinkerGraph);

        Set<String> importedClassQNameSet = getImportedQNameSet(importList);
        List<String> packageNameList = getPackageNameList(importList);

        OwningClassInfo owningClassInfo = getOwningClassInfo(dependentArtifactSet, javaVersion,
                enclosingQualifiedClassNameList, tinkerGraph);

        List<MethodInfo> qualifiedMethodInfoList = new ArrayList<>();

        String previousInvokerClassName = invokerClassName;
        invokerClassName = resolveQNameForClass(invokerClassName, owningClassInfo, jarVertexIds, importedClassQNameSet,
                packageNameList, tinkerGraph);
        List<String> argumentTypeList = resolveQNameForArgumentTypes(argumentTypes, owningClassInfo, jarVertexIds,
                importedClassQNameSet, packageNameList);

        methodName = processMethodName(methodName, importedClassQNameSet);

        /*
          STEP 0
         */
        if (invokerClassName != null && StringUtils.countMatches(invokerClassName, ".") > 1) {
            List<ClassInfo> classInfoList = resolveQClassInfoForClass(previousInvokerClassName, jarVertexIds,
                    importedClassQNameSet, packageNameList, tinkerGraph, owningClassInfo);
            Set<String> classQNameSet = classInfoList.isEmpty()
                    ? Collections.singleton(invokerClassName)
                    : classInfoList.stream().map(ClassInfo::getQualifiedName).collect(Collectors.toSet());

            Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

            while (!classQNameSet.isEmpty() && qualifiedMethodInfoList.isEmpty()) {
                qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters,
                        jarVertexIds, classQNameSet, tinkerGraph);

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerClassName, isSuperInvoker,
                        argumentTypeList, numberOfParameters, jarVertexIds);

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
        if (Objects.isNull(invokerClassName) && Objects.nonNull(owningClassInfo)) {
            Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

            for (int i = 0; i < owningClassInfo.getQualifiedClassNameSetInHierarchy().size(); i++) {
                if (i == 0 && isSuperInvoker) {
                    continue;
                }

                Set<String> classQNameSet = owningClassInfo.getQualifiedClassNameSetInHierarchy().get(i);

                qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters,
                        jarVertexIds, classQNameSet, tinkerGraph);

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerClassName, isSuperInvoker,
                        argumentTypeList, numberOfParameters, jarVertexIds);

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

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerClassName, isSuperInvoker,
                argumentTypeList, numberOfParameters, jarVertexIds);

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

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerClassName, isSuperInvoker,
                argumentTypeList, numberOfParameters, jarVertexIds);

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

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerClassName, isSuperInvoker,
                argumentTypeList, numberOfParameters, jarVertexIds);

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

            qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, invokerClassName, isSuperInvoker,
                    argumentTypeList, numberOfParameters, jarVertexIds);

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
                                                     List<String> enclosingQualifiedClassNameList) {

        return getOwningClassInfo(dependentArtifactSet, javaVersion, enclosingQualifiedClassNameList, tinkerGraph);
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

    private static List<MethodInfo> filterByMethodArgumentTypes(List<MethodInfo> methodInfoList, List<String> argumentTypeList,
                                                                Object[] jarVertexIds) {
        if (!methodInfoList.isEmpty() && !argumentTypeList.isEmpty()) {
            methodInfoList = methodInfoList.stream().filter(methodInfo -> {
                List<String> argumentTypeClassNameList = new ArrayList<>(argumentTypeList);
                List<String> methodArgumentClassNameList = Stream.of(methodInfo.getArgumentTypes())
                        .map(Type::getClassName)
                        .collect(Collectors.toList());

                return matchMethodArguments(argumentTypeClassNameList, methodArgumentClassNameList, jarVertexIds, tinkerGraph, methodInfo);
            }).collect(Collectors.toList());

            if (methodInfoList.size() > 1 && !methodInfoList.stream().allMatch(m -> m.getArgumentTypes().length == 0)) {
                double minArgumentMatchingDistance = getMinimumArgumentMatchingDistance(methodInfoList);

                return methodInfoList.stream()
                        .filter(m -> m.getArgumentMatchingDistance() >= minArgumentMatchingDistance)
                        .collect(Collectors.toList());
            }

            return methodInfoList;
        } else {
            return methodInfoList;
        }
    }

    private static List<MethodInfo> filterProcess(List<MethodInfo> methodInfoList,
                                                  String invokerClassName,
                                                  boolean isSuperInvoker,
                                                  List<String> argumentTypeList,
                                                  int numberOfParameters,
                                                  Object[] jarVertexIds) {
        if (methodInfoList.isEmpty()) {
            return methodInfoList;
        }

        populateClassInfo(methodInfoList, tinkerGraph);
        modifyMethodInfoForArray(methodInfoList, invokerClassName);

        String firstArgumentQualifiedClassName = argumentTypeList.isEmpty() ? null : argumentTypeList.get(0);
        reduceArgumentForInnerClassConstructorIfRequired(methodInfoList, firstArgumentQualifiedClassName,
                numberOfParameters, jarVertexIds, tinkerGraph);

        methodInfoList = filterByMethodInvoker(methodInfoList, invokerClassName, isSuperInvoker, jarVertexIds, tinkerGraph);

        methodInfoList = filterByMethodArgumentTypes(methodInfoList, argumentTypeList, jarVertexIds);

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

            assert classInfoSet.size() == 1;

            f.setClassInfo(classInfoSet.iterator().next());
        });

        return qualifiedFieldInfoList;
    }

    private static List<String> resolveQNameForArgumentTypes(String[] argumentTypes,
                                                             OwningClassInfo owningClassInfo,
                                                             Object[] jarVertexIds,
                                                             Set<String> importedClassQNameSet,
                                                             List<String> packageNameList) {
        if (argumentTypes.length > 0) {
            return new ArrayList<>(Arrays.asList(argumentTypes)).stream()
                    .map(typeClassName ->
                            resolveQNameForClass(typeClassName, owningClassInfo, jarVertexIds, importedClassQNameSet,
                                    packageNameList, tinkerGraph))
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>(Arrays.asList(argumentTypes));
        }
    }

}
