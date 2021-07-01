package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.MethodInfo;
import io.vavr.Tuple3;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.lib.Repository;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ca.concordia.jaranalyzer.util.Utility.getJarStoragePath;

/**
 * @author Diptopol
 * @since 12/23/2020 9:01 PM
 */
public class TypeInferenceAPI extends TypeInferenceBase {

    private static TinkerGraph tinkerGraph;
    private static JarAnalyzer jarAnalyzer;

    static {
        Configuration configuration = new BaseConfiguration();
        configuration.addProperty("gremlin.tinkergraph.defaultVertexPropertyCardinality", "list");

        tinkerGraph = TinkerGraph.open(configuration);
        jarAnalyzer = new JarAnalyzer(tinkerGraph);

        if (!Files.exists(getJarStoragePath())) {
            createClassStructureGraphForJavaJars(jarAnalyzer);
            storeClassStructureGraph(tinkerGraph);
        } else {
            loadClassStructureGraph(tinkerGraph);
        }
    }

    public static Set<Tuple3<String, String, String>> loadExternalJars(String commitId, String projectName, Repository repository) {
        return loadExternalJars(commitId, projectName, repository, tinkerGraph, jarAnalyzer);
    }

    public static void loadJar(String groupId, String artifactId, String version) {
        loadJar(groupId, artifactId, version, tinkerGraph, jarAnalyzer);
    }

    public static List<MethodInfo> getAllMethods(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                                                 String javaVersion,
                                                 List<String> importList,
                                                 String methodName,
                                                 int numberOfParameters) {
        return getAllMethods(dependentJarInformationSet, javaVersion, importList, methodName, numberOfParameters,
                null, false);
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
                                                 String... argumentTypes) {

        Object[] jarVertexIds = getJarVertexIds(dependentJarInformationSet, javaVersion, tinkerGraph);

        Set<String> importedClassQNameSet = getImportedQNameList(importList);
        List<String> packageNameList = getPackageNameList(importList);

        List<MethodInfo> qualifiedMethodInfoList = new ArrayList<>();

        String previousCallerClass = callerClassName;
        callerClassName = resolveQNameForClass(callerClassName, jarVertexIds, importedClassQNameSet, packageNameList, tinkerGraph);
        List<String> argumentTypeList = resolveQNameForArgumentTypes(argumentTypes, jarVertexIds, importedClassQNameSet, packageNameList);

        methodName = processMethodName(methodName, importedClassQNameSet);

        /*
          STEP 0
         */

        if (callerClassName != null && StringUtils.countMatches(callerClassName, ".") >= 1) {
            List<ClassInfo> classInfoList = resolveQClassInfoForClass(previousCallerClass, jarVertexIds,
                    importedClassQNameSet, packageNameList, tinkerGraph);
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
                int minimumArgumentMatchingDistance = getMinimumArgumentMatchingDistance(deferredQualifiedMethodInfoSet);
                int minimumCallerClassMatchingDistance = getMinimumCallerClassMatchingDistance(deferredQualifiedMethodInfoSet);

                if (deferredQualifiedMethodInfoSet.size() > 1) {
                    deferredQualifiedMethodInfoSet = deferredQualifiedMethodInfoSet.stream()
                            .filter(m -> m.getArgumentMatchingDistance() == minimumArgumentMatchingDistance
                                    && m.getCallerClassMatchingDistance() == minimumCallerClassMatchingDistance)
                            .collect(Collectors.toSet());
                }

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
            int minimumArgumentMatchingDistance = getMinimumArgumentMatchingDistance(deferredQualifiedMethodInfoSet);
            int minimumCallerClassMatchingDistance = getMinimumCallerClassMatchingDistance(deferredQualifiedMethodInfoSet);

            if (deferredQualifiedMethodInfoSet.size() > 1) {
                deferredQualifiedMethodInfoSet = deferredQualifiedMethodInfoSet.stream()
                        .filter(m -> m.getArgumentMatchingDistance() == minimumArgumentMatchingDistance
                                && m.getCallerClassMatchingDistance() == minimumCallerClassMatchingDistance)
                        .collect(Collectors.toSet());
            }

            qualifiedMethodInfoList.addAll(deferredQualifiedMethodInfoSet);
        }

        if (!qualifiedMethodInfoList.isEmpty()) {
            return qualifiedMethodInfoList;

        } else {
            return Collections.emptyList();
        }
    }

    private static List<MethodInfo> filterProcess(List<MethodInfo> methodInfoList, String callerClassName,
                                                  boolean isSuperOfCallerClass, List<String> argumentTypeList,
                                                  Object[] jarVertexIds) {
        if (methodInfoList.isEmpty()) {
            return methodInfoList;
        }

        populateClassInfo(methodInfoList, tinkerGraph);
        methodInfoList = filterByMethodInvoker(methodInfoList, callerClassName, isSuperOfCallerClass, jarVertexIds, tinkerGraph);

        methodInfoList = filterByMethodArgumentTypes(methodInfoList, argumentTypeList, jarVertexIds);

        if (methodInfoList.size() > 1 && methodInfoList.stream().anyMatch(m -> !m.isAbstract())) {
            return methodInfoList.stream().filter(m -> !m.isAbstract()).collect(Collectors.toList());
        }

        return methodInfoList;
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

            int minArgumentMatchingDistance = getMinimumArgumentMatchingDistance(methodInfoList);

            if (methodInfoList.size() > 1) {
                return methodInfoList.stream()
                        .filter(m -> m.getArgumentMatchingDistance() >= minArgumentMatchingDistance)
                        .collect(Collectors.toList());
            }

            return methodInfoList;
        } else {
            return methodInfoList;
        }
    }

    private static List<String> resolveQNameForArgumentTypes(String[] argumentTypes, Object[] jarVertexIds,
                                                             Set<String> importedClassQNameList, List<String> packageNameList) {
        if (argumentTypes.length > 0) {
            return new ArrayList<>(Arrays.asList(argumentTypes)).stream()
                    .map(typeClassName ->
                            resolveQNameForClass(typeClassName, jarVertexIds, importedClassQNameList,
                                    packageNameList, tinkerGraph))
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>(Arrays.asList(argumentTypes));
        }
    }

    public static List<String> getQualifiedClassName(String className) {
        return tinkerGraph.traversal().V()
                .has("Kind", "Class")
                .has("Name", className)
                .<String>values("QName")
                .toList();
    }

}
