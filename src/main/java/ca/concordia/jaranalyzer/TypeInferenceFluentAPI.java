package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.TinkerGraphStorageUtility;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.lib.Repository;
import org.objectweb.asm.Type;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 2/20/2021 10:35 PM
 */
public class TypeInferenceFluentAPI extends TypeInferenceBase {

    private TinkerGraph tinkerGraph;
    private JarAnalyzer jarAnalyzer;

    private static TypeInferenceFluentAPI instance;

    public static TypeInferenceFluentAPI getInstance() {
        if (instance == null) {
            instance = new TypeInferenceFluentAPI();
        }

        return instance;
    }

    private TypeInferenceFluentAPI() {
        tinkerGraph = TinkerGraphStorageUtility.getTinkerGraph();
        jarAnalyzer = TinkerGraphStorageUtility.getJarAnalyzer();
    }

    public Set<Tuple3<String, String, String>> loadExternalJars(String commitId, String projectName, Repository repository) {
        return jarAnalyzer.loadExternalJars(commitId, projectName, repository);
    }

    public void loadJar(String groupId, String artifactId, String version) {
        jarAnalyzer.loadJar(groupId, artifactId, version);
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
    private List<MethodInfo> getAllMethods(Criteria criteria) {
        Object[] jarVertexIds = getJarVertexIds(criteria.getDependentJarInformationSet(), criteria.getJavaVersion(), tinkerGraph);
        List<String> importList = criteria.getImportList();
        String methodName = criteria.getMethodName();

        List<MethodInfo> qualifiedMethodInfoList = new ArrayList<>();

        Set<String> importedClassQNameSet = getImportedQNameList(importList);
        List<String> packageNameList = getPackageNameList(importList);

        String previousCallerClass = criteria.getCallerClassName();

        criteria.setInvokerType(
                resolveQNameForClass(criteria.getCallerClassName(), jarVertexIds, importedClassQNameSet,
                        packageNameList, tinkerGraph));
        resolveQNameForArgumentTypes(criteria, jarVertexIds, importedClassQNameSet, packageNameList);

        methodName = processMethodName(methodName, importedClassQNameSet);

        /*
          STEP 0
         */
        String callerClassName = criteria.getCallerClassName();
        if (callerClassName != null && StringUtils.countMatches(callerClassName, ".") >= 1) {
            List<ClassInfo> classInfoList = resolveQClassInfoForClass(previousCallerClass, jarVertexIds,
                    importedClassQNameSet, packageNameList, tinkerGraph);
            Set<String> classQNameList = classInfoList.isEmpty()
                    ? Collections.singleton(callerClassName)
                    : classInfoList.stream().map(ClassInfo::getQualifiedName).collect(Collectors.toSet());

            Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

            while (!classQNameList.isEmpty() && qualifiedMethodInfoList.isEmpty()) {
                qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(),
                        jarVertexIds, classQNameList, tinkerGraph);

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarVertexIds);

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

                    deferredQualifiedMethodInfoSet = filteredNonAbstractMethod(deferredQualifiedMethodInfoSet);
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
        qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(),
                jarVertexIds, importedClassQNameSet, tinkerGraph);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarVertexIds);

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
        qualifiedMethodInfoList = getQualifiedMethodInfoListForInnerClass(methodName, criteria.getNumberOfParameters(),
                jarVertexIds, importedClassQNameSet, tinkerGraph);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarVertexIds);

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
        qualifiedMethodInfoList = getQualifiedMethodInfoListForPackageImport(methodName, criteria.getNumberOfParameters(),
                packageNameList, importedClassQNameSet, jarVertexIds, tinkerGraph);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarVertexIds);

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

            qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(), jarVertexIds,
                    classQNameList, tinkerGraph);

            qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarVertexIds);

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

                deferredQualifiedMethodInfoSet = filteredNonAbstractMethod(deferredQualifiedMethodInfoSet);
            }

            qualifiedMethodInfoList.addAll(deferredQualifiedMethodInfoSet);
        }

        if (!qualifiedMethodInfoList.isEmpty()) {
            return qualifiedMethodInfoList;

        } else {
            return Collections.emptyList();
        }
    }

    private List<MethodInfo> filterProcess(List<MethodInfo> methodInfoList, Criteria criteria, Object[] jarVertexIds) {
        if (methodInfoList.isEmpty()) {
            return methodInfoList;
        }

        populateClassInfo(methodInfoList, tinkerGraph);
        modifyMethodInfoForArray(methodInfoList, criteria.getCallerClassName());
        methodInfoList = filterByMethodInvoker(methodInfoList, criteria.getCallerClassName(),
                criteria.isSuperOfCallerClass, jarVertexIds, tinkerGraph);

        methodInfoList = filterByMethodArgumentTypes(methodInfoList, criteria, jarVertexIds);

        methodInfoList = filteredNonAbstractMethod(methodInfoList);

        return methodInfoList;
    }

    private List<MethodInfo> filterByMethodArgumentTypes(List<MethodInfo> methodInfoList, Criteria criteria, Object[] jarVertexIds) {
        if (!methodInfoList.isEmpty() && !criteria.getArgumentTypeWithIndexList().isEmpty()) {
            List<Tuple2<Integer, String>> argumentTypeWithIndexList = criteria.getArgumentTypeWithIndexList();

            methodInfoList = methodInfoList.stream().filter(methodInfo -> {
                argumentTypeWithIndexList.sort(Comparator.comparingInt(Tuple2::_1));
                List<Type> methodArgumentTypeList = new ArrayList<>(Arrays.asList(methodInfo.getArgumentTypes()));
                List<String> argumentTypeClassNameList = new ArrayList<>();
                List<String> methodArgumentClassNameList = new ArrayList<>();

                for (Tuple2<Integer, String> argumentIndexTuple : argumentTypeWithIndexList) {
                    int index = argumentIndexTuple._1();

                    argumentTypeClassNameList.add(argumentIndexTuple._2());

                    if (index < methodArgumentTypeList.size()) {
                        methodArgumentClassNameList.add(methodArgumentTypeList.get(index).getClassName());
                    }
                }

                return matchMethodArguments(argumentTypeClassNameList, methodArgumentClassNameList, jarVertexIds,
                        tinkerGraph, methodInfo);
            }).collect(Collectors.toList());

            if (methodInfoList.size() > 1) {
                int minArgumentMatchingDistance = getMinimumArgumentMatchingDistance(methodInfoList);

                return methodInfoList.stream()
                        .filter(m -> m.getArgumentMatchingDistance() == minArgumentMatchingDistance)
                        .collect(Collectors.toList());
            }

            return methodInfoList;
        } else {
            return methodInfoList;
        }
    }

    private void resolveQNameForArgumentTypes(Criteria criteria, Object[] jarVertexIds,
                                              Set<String> importedClassQNameList, List<String> packageNameList) {
        if (!criteria.getArgumentTypeWithIndexList().isEmpty()) {
            List<Tuple2<Integer, String>> argumentTypeWithIndexList = criteria.getArgumentTypeWithIndexList().stream()
                    .map(argumentTypeWithIndex -> {
                        Integer argumentIndex = argumentTypeWithIndex._1();
                        String argumentType = argumentTypeWithIndex._2();

                        List<ClassInfo> qualifiedClassInfoList =
                                resolveQClassInfoForClass(argumentType, jarVertexIds, importedClassQNameList,
                                        packageNameList, tinkerGraph);

                        qualifiedClassInfoList = filtrationBasedOnPrioritization(argumentType, importedClassQNameList,
                                qualifiedClassInfoList);

                        return qualifiedClassInfoList.isEmpty()
                                ? argumentTypeWithIndex
                                : new Tuple2<>(argumentIndex, qualifiedClassInfoList.get(0).getQualifiedName());
                    }).collect(Collectors.toList());

            criteria.setArgumentTypeWithIndexList(argumentTypeWithIndexList);
        }
    }

    public class Criteria {
        private Set<Tuple3<String, String, String>> dependentJarInformationSet;
        private String javaVersion;
        private List<String> importList;
        private String methodName;
        private int numberOfParameters;
        private String callerClassName;
        private boolean isSuperOfCallerClass;
        private Map<Integer, String> argumentTypeMap;

        private Set<Tuple3<String, String, String>> getDependentJarInformationSet() {
            return dependentJarInformationSet;
        }

        private String getJavaVersion() {
            return javaVersion;
        }

        private List<String> getImportList() {
            return importList;
        }

        private String getMethodName() {
            return methodName;
        }

        private int getNumberOfParameters() {
            return numberOfParameters;
        }

        private String getCallerClassName() {
            return callerClassName;
        }

        private boolean isSuperOfCallerClass() {
            return isSuperOfCallerClass;
        }

        private List<Tuple2<Integer, String>> getArgumentTypeWithIndexList() {
            if (argumentTypeMap.isEmpty()) {
                return Collections.emptyList();
            }

            return argumentTypeMap.entrySet().stream()
                    .map(e -> new Tuple2<>(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
        }

        private void setArgumentTypeWithIndexList(List<Tuple2<Integer, String>> argumentTypeWithIndexList) {
            if (!argumentTypeWithIndexList.isEmpty()) {
                argumentTypeMap = argumentTypeWithIndexList.stream()
                        .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));
            }
        }

        public Criteria(Set<Tuple3<String, String, String>> dependentJarInformationSet,
                        String javaVersion,
                        List<String> importList,
                        String methodName,
                        int numberOfParameters) {

            this.dependentJarInformationSet = dependentJarInformationSet;
            this.javaVersion = javaVersion;
            this.importList = importList;
            this.methodName = methodName;
            this.numberOfParameters = numberOfParameters;

            this.argumentTypeMap = new HashMap<>();
        }

        public Criteria setInvokerType(String callerClassName) {
            this.callerClassName = callerClassName;

            return this;
        }

        public Criteria setSuperInvoker(boolean isSuperOfCallerClass) {
            this.isSuperOfCallerClass = isSuperOfCallerClass;

            return this;
        }

        /**
         * argumentIndex is assumed to be starts with 0 and will consider the max value of argumentIndex as last value.
         */
        public Criteria setArgumentType(int argumentIndex, String argumentType) {
            this.argumentTypeMap.put(argumentIndex, argumentType);

            return this;
        }

        public List<MethodInfo> getMethodList() {
            return getAllMethods(this);
        }
    }
}
