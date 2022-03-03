package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.FieldInfo;
import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.TinkerGraphStorageUtility;
import io.vavr.Tuple3;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.lib.Repository;
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

    public static Set<Tuple3<String, String, String>> loadExternalJars(String commitId, String projectName, Repository repository) {
        return jarAnalyzer.loadExternalJars(commitId, projectName, repository);
    }

    public static void loadJar(String groupId, String artifactId, String version) {
        jarAnalyzer.loadJar(groupId, artifactId, version);
    }

    public static List<MethodInfo> getAllMethods(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                 String javaVersion,
                                                 List<String> importList,
                                                 String methodName,
                                                 int numberOfParameters) {
        return getAllMethods(dependentJarInformationSet, javaVersion, importList, methodName, numberOfParameters,
                null, false, null);
    }

    /**
     * The process of checking classes for specific method will happen in below steps.<br><br>
     *
     * <strong>Step 0</strong>: If we can resolve qualified caller class name, we will use caller class to resolve method
     * info.<br>
     *
     * <strong>Step 1</strong>: All the classes who are directly mentioned in the import statement will be checked,
     * if method found it will be returned.<br>
     *
     * <strong>Step 2</strong>: All the inner classes of classes who are directly mentioned in the import statement will
     * be checked, if method found it will be returned.<br>
     *
     * <strong>Step 3</strong>: All the classes under on-demand package import will be searched, if method found
     * it will be returned.<br>
     *
     * <strong>Step 4</strong>: Recursively look for super classes and interfaces from all the import classes (on demand and normal)
     * if in any step method is found it will be returned, otherwise recursion will happen until java.lang.Object is
     * reached, then if no method is found an empty list will be returned.<br>
     */
    public static List<MethodInfo> getAllMethods(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                 String javaVersion,
                                                 List<String> importList,
                                                 String methodName,
                                                 int numberOfParameters,
                                                 String callerClassName,
                                                 boolean isSuperOfCallerClass,
                                                 String owningClassQualifiedName,
                                                 String... argumentTypes) {

        Object[] jarVertexIds = getJarVertexIds(dependentJarInformationSet, javaVersion, tinkerGraph);

        Set<String> importedClassQNameSet = getImportedQNameList(importList);
        List<String> packageNameList = getPackageNameList(importList);

        List<MethodInfo> qualifiedMethodInfoList = new ArrayList<>();

        String previousCallerClass = callerClassName;
        callerClassName = resolveQNameForClass(callerClassName, owningClassQualifiedName, jarVertexIds, importedClassQNameSet, packageNameList, tinkerGraph);
        List<String> argumentTypeList = resolveQNameForArgumentTypes(argumentTypes, owningClassQualifiedName, jarVertexIds, importedClassQNameSet, packageNameList);

        methodName = processMethodName(methodName, importedClassQNameSet);

        /*
          STEP 0
         */

        if (callerClassName != null && StringUtils.countMatches(callerClassName, ".") >= 1) {
            List<ClassInfo> classInfoList = resolveQClassInfoForClass(previousCallerClass, jarVertexIds,
                    importedClassQNameSet, packageNameList, tinkerGraph, owningClassQualifiedName);
            Set<String> classQNameList = classInfoList.isEmpty()
                    ? Collections.singleton(callerClassName)
                    : classInfoList.stream().map(ClassInfo::getQualifiedName).collect(Collectors.toSet());

            Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

            while (!classQNameList.isEmpty() && qualifiedMethodInfoList.isEmpty()) {
                qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters,
                        jarVertexIds, classQNameList, tinkerGraph);

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, callerClassName, isSuperOfCallerClass,
                        argumentTypeList, jarVertexIds);

                if (!qualifiedMethodInfoList.isEmpty()
                        && qualifiedMethodInfoList.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
                    deferredQualifiedMethodInfoSet.addAll(qualifiedMethodInfoList);
                    qualifiedMethodInfoList.clear();
                }

                if (qualifiedMethodInfoList.isEmpty()) {
                    classQNameList = getSuperClasses(classQNameList, jarVertexIds, tinkerGraph);
                }
            }

            if (qualifiedMethodInfoList.isEmpty() && !deferredQualifiedMethodInfoSet.isEmpty()) {
                deferredQualifiedMethodInfoSet = prioritizeMethodInfoSet(deferredQualifiedMethodInfoSet);
                qualifiedMethodInfoList.addAll(deferredQualifiedMethodInfoSet);
            }

            if (!qualifiedMethodInfoList.isEmpty()) {
                return qualifiedMethodInfoList;
            }
        }

        /*
          STEP 1
         */
        qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters,
                jarVertexIds, importedClassQNameSet, tinkerGraph);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, callerClassName, isSuperOfCallerClass,
                argumentTypeList, jarVertexIds);

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
          STEP 2
         */
        qualifiedMethodInfoList = getQualifiedMethodInfoListForInnerClass(methodName, numberOfParameters, jarVertexIds,
                importedClassQNameSet, tinkerGraph);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, callerClassName, isSuperOfCallerClass,
                argumentTypeList, jarVertexIds);

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
        qualifiedMethodInfoList = getQualifiedMethodInfoListForPackageImport(methodName, numberOfParameters,
                packageNameList, importedClassQNameSet, jarVertexIds, tinkerGraph);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, callerClassName, isSuperOfCallerClass,
                argumentTypeList, jarVertexIds);

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
        Set<String> classQNameList = new HashSet<>(importedClassQNameSet);

        while (!classQNameList.isEmpty() && qualifiedMethodInfoList.isEmpty()) {
            classQNameList = getSuperClasses(classQNameList, jarVertexIds, tinkerGraph);

            qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters, jarVertexIds,
                    classQNameList, tinkerGraph);

            qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, callerClassName, isSuperOfCallerClass,
                    argumentTypeList, jarVertexIds);

            if (!qualifiedMethodInfoList.isEmpty()
                    && qualifiedMethodInfoList.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
                deferredQualifiedMethodInfoSet.addAll(qualifiedMethodInfoList);
                qualifiedMethodInfoList.clear();
            }
        }

        if (qualifiedMethodInfoList.isEmpty() && !deferredQualifiedMethodInfoSet.isEmpty()) {
            deferredQualifiedMethodInfoSet = prioritizeMethodInfoSet(deferredQualifiedMethodInfoSet);

            qualifiedMethodInfoList.addAll(deferredQualifiedMethodInfoSet);
        }

        if (!qualifiedMethodInfoList.isEmpty()) {
            return qualifiedMethodInfoList;

        } else {
            return Collections.emptyList();
        }
    }

    public static List<ClassInfo> getAllTypes(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                              String javaVersion,
                                              List<String> importList,
                                              String typeName,
                                              String owningClassQualifiedName) {
        if (Objects.isNull(typeName)) {
            return Collections.emptyList();
        }

        Object[] jarVertexIds = getJarVertexIds(dependentJarInformationSet, javaVersion, tinkerGraph);
        Set<String> importedClassQNameSet = getImportedQNameList(importList);
        List<String> packageNameList = getPackageNameList(importList);

        if (typeName.contains(".")) {
            if (StringUtils.countMatches(typeName, ".") > 1) {
                importedClassQNameSet.add(typeName.replaceAll("\\$", "."));
                typeName = typeName.substring(typeName.lastIndexOf(".") + 1);
            }
        }

        List<ClassInfo> qualifiedClassInfoList = resolveQClassInfoForClass(typeName, jarVertexIds, importedClassQNameSet,
                packageNameList, tinkerGraph, owningClassQualifiedName);
        qualifiedClassInfoList = filtrationBasedOnPrioritization(jarVertexIds, typeName, owningClassQualifiedName,
                importedClassQNameSet, qualifiedClassInfoList, tinkerGraph);

        return qualifiedClassInfoList;
    }

    public static List<FieldInfo> getAllFieldTypes(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                   String javaVersion,
                                                   List<String> importList,
                                                   String fieldName,
                                                   String owningClassQualifiedName) {


        Object[] jarVertexIds = getJarVertexIds(dependentJarInformationSet, javaVersion, tinkerGraph);

        Set<String> importedClassQNameList = getImportedQNameList(importList);
        List<String> packageNameList = getPackageNameList(importList);

        String callerClassQName = null;

        if (fieldName.contains(".")) {
            if (StringUtils.countMatches(fieldName, ".") >= 1) {
                String callerClassName = fieldName.substring(0, fieldName.lastIndexOf("."));
                callerClassQName = resolveQNameForClass(callerClassName, owningClassQualifiedName, jarVertexIds,
                        importedClassQNameList, packageNameList, tinkerGraph);

                importedClassQNameList.add(callerClassQName);
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
        if (callerClassQName != null) {
            Set<String> qCallerClassNameSet = new HashSet<>(Collections.singletonList(callerClassQName));

            while (!qCallerClassNameSet.isEmpty() && qualifiedFieldList.isEmpty()) {
                qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarVertexIds, qCallerClassNameSet);

                if (qualifiedFieldList.isEmpty()) {
                    qCallerClassNameSet = tinkerGraph.traversal().V(jarVertexIds)
                            .out("ContainsPkg").out("Contains")
                            .has("Kind", "Class")
                            .has("QName", TextP.within(qCallerClassNameSet))
                            .out("extends", "implements")
                            .<String>values("Name")
                            .toSet();
                }
            }

            if (!qualifiedFieldList.isEmpty()) {
                return populateClassInfoForField(qualifiedFieldList);
            }
        }

        /*
          STEP 1
         */
        qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarVertexIds, importedClassQNameList);

        if (!qualifiedFieldList.isEmpty()) {
            return populateClassInfoForField(qualifiedFieldList);
        }

        /*
          STEP 2
         */
        Set<String> classNameListForPackgage = tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg")
                .has("Kind", "Package")
                .has("Name", TextP.within(packageNameList))
                .out("Contains")
                .has("Kind", "Class")
                .<String>values("QName")
                .toSet();

        importedClassQNameList.addAll(classNameListForPackgage);

        qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarVertexIds, classNameListForPackgage);

        if (!qualifiedFieldList.isEmpty()) {
            return populateClassInfoForField(qualifiedFieldList);
        }

        /*
          STEP 3
         */
        Set<String> classQNameList = new HashSet<>(importedClassQNameList);

        while (!classQNameList.isEmpty() && qualifiedFieldList.isEmpty()) {
            classQNameList = tinkerGraph.traversal().V(jarVertexIds)
                    .out("ContainsPkg").out("Contains")
                    .has("Kind", "Class")
                    .has("QName", TextP.within(classQNameList))
                    .out("extends", "implements")
                    .<String>values("Name")
                    .toSet();

            qualifiedFieldList = getQualifiedFieldInfoList(fieldName, jarVertexIds, classQNameList);
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
                int minArgumentMatchingDistance = getMinimumArgumentMatchingDistance(methodInfoList);

                return methodInfoList.stream()
                        .filter(m -> m.getArgumentMatchingDistance() >= minArgumentMatchingDistance)
                        .collect(Collectors.toList());
            }

            return methodInfoList;
        } else {
            return methodInfoList;
        }
    }

    private static List<MethodInfo> filterProcess(List<MethodInfo> methodInfoList, String callerClassName,
                                                  boolean isSuperOfCallerClass, List<String> argumentTypeList,
                                                  Object[] jarVertexIds) {
        if (methodInfoList.isEmpty()) {
            return methodInfoList;
        }

        populateClassInfo(methodInfoList, tinkerGraph);
        modifyMethodInfoForArray(methodInfoList, callerClassName);
        methodInfoList = filterByMethodInvoker(methodInfoList, callerClassName, isSuperOfCallerClass, jarVertexIds, tinkerGraph);

        methodInfoList = filterByMethodArgumentTypes(methodInfoList, argumentTypeList, jarVertexIds);

        methodInfoList = filteredNonAbstractMethod(methodInfoList);

        return methodInfoList;
    }

    private static List<FieldInfo> getQualifiedFieldInfoList(String fieldName, Object[] jarVertexIds, Set<String> classQNameList) {
        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameList))
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
                                                             String owningPackageName,
                                                             Object[] jarVertexIds,
                                                             Set<String> importedClassQNameList,
                                                             List<String> packageNameList) {
        if (argumentTypes.length > 0) {
            return new ArrayList<>(Arrays.asList(argumentTypes)).stream()
                    .map(typeClassName ->
                            resolveQNameForClass(typeClassName, owningPackageName, jarVertexIds, importedClassQNameList,
                                    packageNameList, tinkerGraph))
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>(Arrays.asList(argumentTypes));
        }
    }

}
