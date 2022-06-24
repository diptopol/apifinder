package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.MethodInfo;
import ca.concordia.jaranalyzer.models.OwningClassInfo;
import ca.concordia.jaranalyzer.models.typeInfo.NullTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.SimpleTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import ca.concordia.jaranalyzer.util.TinkerGraphStorageUtility;
import io.vavr.Tuple2;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.api.Git;

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

    public Set<Artifact> loadExternalJars(String commitId, String projectName, Git git) {
        return jarAnalyzer.loadExternalJars(commitId, projectName, git);
    }

    public void loadJar(Artifact artifact) {
        jarAnalyzer.loadJar(artifact);
    }

    public void loadJar(String groupId, String artifactId, String version) {
        jarAnalyzer.loadJar(new Artifact(groupId, artifactId, version));
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
    private List<MethodInfo> getAllMethods(Criteria criteria) {
        Object[] jarVertexIds = getJarVertexIds(criteria.getDependentArtifactSet(), criteria.getJavaVersion(), tinkerGraph);
        List<String> importList = criteria.getImportList();
        String methodName = criteria.getMethodName();

        List<MethodInfo> qualifiedMethodInfoList = new ArrayList<>();

        Set<String> importedClassQNameSet = getImportedQNameSet(importList);
        List<String> packageNameList = getPackageNameList(importList);

        resolveQNameForInvokerTypeInfo(criteria);
        resolveQNameForArgumentTypes(criteria);

        methodName = processMethodName(methodName, importedClassQNameSet);

        /*
          STEP 0
         */
        if (Objects.nonNull(criteria.getInvokerTypeInfo())) {
            Set<String> classQNameSet = new LinkedHashSet<>(Arrays.asList(criteria.getInvokerTypeInfo().getQualifiedClassName()));

            Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();
            List<String> classQNameDeclarationOrderList = new ArrayList<>(classQNameSet);

            while (!classQNameSet.isEmpty() && qualifiedMethodInfoList.isEmpty()) {
                qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(),
                        jarVertexIds, classQNameSet, tinkerGraph);

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarVertexIds, false);

                if (!qualifiedMethodInfoList.isEmpty()
                        && qualifiedMethodInfoList.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
                    deferredQualifiedMethodInfoSet.addAll(qualifiedMethodInfoList);
                    qualifiedMethodInfoList.clear();
                }

                if (qualifiedMethodInfoList.isEmpty()) {
                    Map<String, List<String>> superClassQNameMap =
                            getSuperClassQNameMapPerClass(classQNameSet, jarVertexIds, tinkerGraph);

                    insertSuperClassQNamePreservingDeclarationOrder(superClassQNameMap, classQNameSet,
                            classQNameDeclarationOrderList);

                    classQNameSet = getSuperClassQNameSet(classQNameSet, jarVertexIds, tinkerGraph);
                }
            }

            if (qualifiedMethodInfoList.isEmpty() && !deferredQualifiedMethodInfoSet.isEmpty()) {
                classQNameDeclarationOrderList = getUniqueClassQNameList(classQNameDeclarationOrderList);
                deferredQualifiedMethodInfoSet = prioritizeDeferredMethodInfoSet(
                        getOrderedDeferredMethodInfoSetBasedOnDeclarationOrder(deferredQualifiedMethodInfoSet,
                                classQNameDeclarationOrderList));

                qualifiedMethodInfoList.addAll(deferredQualifiedMethodInfoSet);
            }

            if (!qualifiedMethodInfoList.isEmpty()) {
                return qualifiedMethodInfoList;
            }
        }

        /*
          STEP 1
         */
        if (Objects.isNull(criteria.getInvokerTypeInfo()) && Objects.nonNull(criteria.getOwningClassInfo())) {
            Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

            for (int i = 0; i < criteria.getOwningClassInfo().getQualifiedClassNameSetInHierarchy().size(); i++) {
                if (i == 0 && criteria.isSuperInvoker()) {
                    continue;
                }

                Set<String> classQNameSet = criteria.getOwningClassInfo().getQualifiedClassNameSetInHierarchy().get(i);

                qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(),
                        jarVertexIds, classQNameSet, tinkerGraph);

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarVertexIds, i == 0);

                if (i != 0
                        && !qualifiedMethodInfoList.isEmpty()
                        && qualifiedMethodInfoList.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
                    deferredQualifiedMethodInfoSet.addAll(qualifiedMethodInfoList);
                    qualifiedMethodInfoList.clear();
                }

                if (!qualifiedMethodInfoList.isEmpty()) {
                    return qualifiedMethodInfoList;
                }
            }

            if (!deferredQualifiedMethodInfoSet.isEmpty()) {
                deferredQualifiedMethodInfoSet = prioritizeDeferredMethodInfoSet(
                        getOrderedDeferredMethodInfoSetBasedOnDeclarationOrder(deferredQualifiedMethodInfoSet,
                                criteria.getOwningClassInfo().getClassQNameDeclarationOrderList()));
                qualifiedMethodInfoList.addAll(deferredQualifiedMethodInfoSet);
            }

            if (!qualifiedMethodInfoList.isEmpty()) {
                return qualifiedMethodInfoList;
            }
        }

        /*
          STEP 2
         */
        qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(),
                jarVertexIds, importedClassQNameSet, tinkerGraph);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarVertexIds, false);

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
        qualifiedMethodInfoList = getQualifiedMethodInfoListForInnerClass(methodName, criteria.getNumberOfParameters(),
                jarVertexIds, importedClassQNameSet, tinkerGraph);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarVertexIds, false);

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
        qualifiedMethodInfoList = getQualifiedMethodInfoListForPackageImport(methodName, criteria.getNumberOfParameters(),
                packageNameList, importedClassQNameSet, jarVertexIds, tinkerGraph);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarVertexIds, false);

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

            qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(), jarVertexIds,
                    classQNameSet, tinkerGraph);

            qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarVertexIds, false);

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

    private List<MethodInfo> filterProcess(List<MethodInfo> methodInfoList,
                                           Criteria criteria,
                                           Object[] jarVertexIds,
                                           boolean isOwningClass) {
        if (methodInfoList.isEmpty()) {
            return methodInfoList;
        }

        populateClassInfo(methodInfoList, tinkerGraph);
        modifyMethodInfoForArray(methodInfoList, criteria.getInvokerTypeInfo());

        TypeInfo firstArgumentTypeInfo = criteria.getArgumentTypeInfoWithIndexList().stream()
                .filter(a -> a._1() == 0).map(Tuple2::_2)
                .findFirst()
                .orElse(null);

        String firstArgumentQualifiedClassName = Objects.nonNull(firstArgumentTypeInfo)
                ? firstArgumentTypeInfo.getQualifiedClassName()
                : null;

        reduceArgumentForInnerClassConstructorIfRequired(methodInfoList, firstArgumentQualifiedClassName,
                criteria.getNumberOfParameters(), jarVertexIds, tinkerGraph);

        methodInfoList = filterByMethodInvoker(methodInfoList, criteria.getInvokerTypeInfo(),
                criteria.isSuperInvoker(), jarVertexIds, tinkerGraph);

        if (!(criteria.getNumberOfParameters() > 0 && criteria.getArgumentTypeInfoWithIndexList().isEmpty())) {
            methodInfoList = filterByMethodArgumentTypes(methodInfoList, criteria, jarVertexIds);
        }

        if (methodInfoList.size() > 1 && !isOwningClass) {
            methodInfoList = methodInfoList.stream()
                    .filter(m -> !m.isPrivate())
                    .collect(Collectors.toList());
        }

        if (methodInfoList.size() > 1 && !methodInfoList.stream().allMatch(m -> m.getArgumentTypes().length == 0)) {
            double minArgumentMatchingDistance = getMinimumArgumentMatchingDistance(methodInfoList);

            methodInfoList = methodInfoList.stream()
                    .filter(m -> m.getArgumentMatchingDistance() == minArgumentMatchingDistance)
                    .collect(Collectors.toList());
        }

        methodInfoList = filteredNonAbstractMethod(methodInfoList);

        return methodInfoList;
    }

    private List<MethodInfo> filterByMethodArgumentTypes(List<MethodInfo> methodInfoList, Criteria criteria, Object[] jarVertexIds) {
        if (!methodInfoList.isEmpty()) {
            List<Tuple2<Integer, TypeInfo>> argumentTypeInfoWithIndexList = criteria.getArgumentTypeInfoWithIndexList();

            methodInfoList = methodInfoList.stream().filter(methodInfo -> {
                argumentTypeInfoWithIndexList.sort(Comparator.comparingInt(Tuple2::_1));
                List<TypeInfo> argumentTypeInfoList = getOrderedArgumentTypeInfoList(argumentTypeInfoWithIndexList);
                List<TypeInfo> methodArgumentTypeInfoList = getOrderedMethodArgumentTypeInfoList(argumentTypeInfoWithIndexList, methodInfo);

                return matchMethodArguments(argumentTypeInfoList, methodArgumentTypeInfoList, jarVertexIds,
                        tinkerGraph, methodInfo);
            }).collect(Collectors.toList());
        }

        return methodInfoList;
    }

    private List<TypeInfo> getOrderedArgumentTypeInfoList(List<Tuple2<Integer, TypeInfo>> argumentTypeInfoWithIndexListOrderedByIndex) {
        List<TypeInfo> argumentTypeInfoList = new ArrayList<>();

        for (Tuple2<Integer, TypeInfo> argumentIndexTuple : argumentTypeInfoWithIndexListOrderedByIndex) {
            argumentTypeInfoList.add(argumentIndexTuple._2());
        }

        return argumentTypeInfoList;
    }

    private List<TypeInfo> getOrderedMethodArgumentTypeInfoList(List<Tuple2<Integer, TypeInfo>> argumentTypeInfoWithIndexListOrderedByIndex,
                                                                        MethodInfo methodInfo) {
        List<TypeInfo> orderedMethodArgumentTypeInfoList = new ArrayList<>();
        List<TypeInfo> methodArgumentTypeInfoList = new ArrayList<>(methodInfo.getArgumentTypeInfoList());

        List<Integer> argumentIndexList = argumentTypeInfoWithIndexListOrderedByIndex
                .stream()
                .map(Tuple2::_1)
                .collect(Collectors.toList());

        int lastIndex = methodArgumentTypeInfoList.size() > 0 ? methodArgumentTypeInfoList.size() - 1 : 0;

        for (int i = 0; i < methodArgumentTypeInfoList.size(); i++) {
            if (argumentIndexList.contains(i) || (methodInfo.isVarargs() && i == lastIndex)) {
                orderedMethodArgumentTypeInfoList.add(methodArgumentTypeInfoList.get(i));
            }
        }

        return orderedMethodArgumentTypeInfoList;
    }

    private void resolveQNameForInvokerTypeInfo(Criteria criteria) {
        if (Objects.nonNull(criteria.getInvokerTypeInfo()) && criteria.getInvokerTypeInfo().isSimpleTypeInfo()) {
            SimpleTypeInfo simpleTypeInfo = (SimpleTypeInfo) criteria.getInvokerTypeInfo();

            TypeInfo invokerTypeInfo;

            if (simpleTypeInfo.getClassName().equals("null")) {
                invokerTypeInfo = new NullTypeInfo();
            } else {
                invokerTypeInfo = InferenceUtility.getTypeInfoFromClassName(criteria.getDependentArtifactSet(),
                        criteria.getJavaVersion(), criteria.getImportList(), simpleTypeInfo.getClassName(),
                        criteria.getOwningClassInfo());
            }

            criteria.setInvokerTypeInfo(invokerTypeInfo);
        }
    }

    private void resolveQNameForArgumentTypes(Criteria criteria) {
        if (!criteria.getArgumentTypeInfoWithIndexList().isEmpty()) {
            List<Tuple2<Integer, TypeInfo>> argumentTypeWithIndexList = criteria.getArgumentTypeInfoWithIndexList().stream()
                    .map(argumentTypeWithIndex -> {
                        Integer argumentIndex = argumentTypeWithIndex._1();
                        TypeInfo argumentTypeInfo = argumentTypeWithIndex._2();

                        if (Objects.nonNull(argumentTypeInfo) && argumentTypeInfo.isSimpleTypeInfo()) {
                            SimpleTypeInfo simpleTypeInfo = (SimpleTypeInfo) argumentTypeInfo;

                            if (simpleTypeInfo.getClassName().equals("null")) {
                                argumentTypeInfo = new NullTypeInfo();

                            } else {
                                argumentTypeInfo = InferenceUtility.getTypeInfoFromClassName(criteria.getDependentArtifactSet(),
                                        criteria.getJavaVersion(), criteria.getImportList(), simpleTypeInfo.getClassName(),
                                        criteria.getOwningClassInfo());
                            }
                        }

                        return new Tuple2<>(argumentIndex, argumentTypeInfo);
                    }).collect(Collectors.toList());

            criteria.setArgumentTypeInfoWithIndexList(argumentTypeWithIndexList);
        }
    }

    public class Criteria {
        private Set<Artifact> dependentArtifactSet;
        private String javaVersion;
        private List<String> importList;
        private String methodName;
        private int numberOfParameters;
        private TypeInfo invokerTypeInfo;
        private OwningClassInfo owningClassInfo;

        /*
         * superInvoker will be used to determine appropriate method from invoker type as well as from owning class.
         * In both cases, we will skip the lower order classes assuming that methods will come from any of the super
         * classes.
         */
        private boolean isSuperInvoker;
        private Map<Integer, TypeInfo> argumentTypeInfoMap;

        private Set<Artifact> getDependentArtifactSet() {
            return dependentArtifactSet;
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

        private TypeInfo getInvokerTypeInfo() {
            return this.invokerTypeInfo;
        }

        public OwningClassInfo getOwningClassInfo() {
            return owningClassInfo;
        }

        private boolean isSuperInvoker() {
            return isSuperInvoker;
        }

        public List<Tuple2<Integer, TypeInfo>> getArgumentTypeInfoWithIndexList() {
            if (argumentTypeInfoMap.isEmpty()) {
                return Collections.emptyList();
            }

            return argumentTypeInfoMap.entrySet().stream()
                    .map(e -> new Tuple2<>(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
        }

        private void setArgumentTypeInfoWithIndexList(List<Tuple2<Integer, TypeInfo>> argumentTypeInfoWithIndexList) {
            if (!argumentTypeInfoWithIndexList.isEmpty()) {
                argumentTypeInfoMap = argumentTypeInfoWithIndexList.stream()
                        .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));
            }
        }

        public Criteria(Set<Artifact> dependentArtifactSet,
                        String javaVersion,
                        List<String> importList,
                        String methodName,
                        int numberOfParameters) {

            this.dependentArtifactSet = dependentArtifactSet;
            this.javaVersion = javaVersion;
            this.importList = importList;
            this.methodName = methodName;
            this.numberOfParameters = numberOfParameters;
            this.argumentTypeInfoMap = new HashMap<>();
        }

        public Criteria setInvokerClassName(String invokerClassName) {
            this.invokerTypeInfo = new SimpleTypeInfo(invokerClassName);

            return this;
        }

        public Criteria setInvokerTypeInfo(TypeInfo invokerTypeInfo) {
            this.invokerTypeInfo = invokerTypeInfo;

            return this;
        }

        public Criteria setSuperInvoker(boolean isSuperInvoker) {
            this.isSuperInvoker = isSuperInvoker;

            return this;
        }

        public Criteria setOwningClassInfo(OwningClassInfo owningClassInfo) {
            this.owningClassInfo = owningClassInfo;

            return this;
        }

        /**
         * argumentIndex is assumed to be starts with 0 and will consider the max value of argumentIndex as last value.
         */
        public Criteria setArgumentType(int argumentIndex, String argumentType) {
            this.argumentTypeInfoMap.put(argumentIndex, new SimpleTypeInfo(argumentType));

            return this;
        }

        public Criteria setArgumentTypeInfo(int argumentIndex, TypeInfo argumentTypeInfo) {
            this.argumentTypeInfoMap.put(argumentIndex, argumentTypeInfo);

            return this;
        }

        public List<MethodInfo> getMethodList() {
            return getAllMethods(this);
        }
    }
}
