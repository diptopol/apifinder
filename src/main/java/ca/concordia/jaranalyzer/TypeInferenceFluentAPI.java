package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.ClassInfo;
import ca.concordia.jaranalyzer.models.MethodInfo;
import ca.concordia.jaranalyzer.models.OwningClassInfo;
import ca.concordia.jaranalyzer.util.TinkerGraphStorageUtility;
import io.vavr.Tuple2;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.api.Git;
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

        String previousInvokerClassName = criteria.getInvokerClassName();

        criteria.setInvokerClassName(
                resolveQNameForClass(criteria.getInvokerClassName(), criteria.getOwningClassInfo(), jarVertexIds, importedClassQNameSet,
                        packageNameList, tinkerGraph));
        resolveQNameForArgumentTypes(criteria, jarVertexIds, importedClassQNameSet, packageNameList);

        methodName = processMethodName(methodName, importedClassQNameSet);

        /*
          STEP 0
         */
        String invokerClassName = criteria.getInvokerClassName();
        if (invokerClassName != null && StringUtils.countMatches(invokerClassName, ".") > 1) {
            List<ClassInfo> classInfoList = resolveQClassInfoForClass(previousInvokerClassName, jarVertexIds,
                    importedClassQNameSet, packageNameList, tinkerGraph, criteria.getOwningClassInfo());
            Set<String> classQNameSet = classInfoList.isEmpty()
                    ? new LinkedHashSet<>(List.of(invokerClassName))
                    : new LinkedHashSet<>(classInfoList.stream().map(ClassInfo::getQualifiedName).collect(Collectors.toList()));

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
        if (Objects.isNull(criteria.getInvokerClassName()) && Objects.nonNull(criteria.getOwningClassInfo())) {
            Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

            for (int i = 0; i < criteria.getOwningClassInfo().getQualifiedClassNameSetInHierarchy().size(); i++) {
                if (i == 0 && criteria.isSuperInvoker()) {
                    continue;
                }

                Set<String> classQNameSet = criteria.getOwningClassInfo().getQualifiedClassNameSetInHierarchy().get(i);

                qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(),
                        jarVertexIds, classQNameSet, tinkerGraph);

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarVertexIds, i == 0);

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
        modifyMethodInfoForArray(methodInfoList, criteria.getInvokerClassName());

        String firstArgumentQualifiedClassName = criteria.getArgumentTypeWithIndexList().stream()
                .filter(a -> a._1() == 0).map(Tuple2::_2)
                .findFirst()
                .orElse(null);
        reduceArgumentForInnerClassConstructorIfRequired(methodInfoList, firstArgumentQualifiedClassName,
                criteria.getNumberOfParameters(), jarVertexIds, tinkerGraph);

        methodInfoList = filterByMethodInvoker(methodInfoList, criteria.getInvokerClassName(),
                criteria.isSuperInvoker(), jarVertexIds, tinkerGraph);

        methodInfoList = filterByMethodArgumentTypes(methodInfoList, criteria, jarVertexIds);

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

            return methodInfoList;
        } else {
            return methodInfoList;
        }
    }

    private void resolveQNameForArgumentTypes(Criteria criteria,
                                              Object[] jarVertexIds,
                                              Set<String> importedClassQNameSet,
                                              List<String> packageNameList) {
        if (!criteria.getArgumentTypeWithIndexList().isEmpty()) {
            List<Tuple2<Integer, String>> argumentTypeWithIndexList = criteria.getArgumentTypeWithIndexList().stream()
                    .map(argumentTypeWithIndex -> {
                        Integer argumentIndex = argumentTypeWithIndex._1();
                        String argumentType = argumentTypeWithIndex._2();

                        List<ClassInfo> qualifiedClassInfoList =
                                resolveQClassInfoForClass(argumentType, jarVertexIds, importedClassQNameSet,
                                        packageNameList, tinkerGraph, criteria.getOwningClassInfo());

                        qualifiedClassInfoList = filtrationBasedOnPrioritization(argumentType, criteria.getOwningClassInfo(),
                                importedClassQNameSet, qualifiedClassInfoList);

                        return qualifiedClassInfoList.isEmpty()
                                ? argumentTypeWithIndex
                                : new Tuple2<>(argumentIndex, qualifiedClassInfoList.get(0).getQualifiedName());
                    }).collect(Collectors.toList());

            criteria.setArgumentTypeWithIndexList(argumentTypeWithIndexList);
        }
    }

    public class Criteria {
        private Set<Artifact> dependentArtifactSet;
        private String javaVersion;
        private List<String> importList;
        private String methodName;
        private int numberOfParameters;
        private String invokerClassName;
        private OwningClassInfo owningClassInfo;

        /*
         * superInvoker will be used to determine appropriate method from invoker type as well as from owning class.
         * In both cases, we will skip the lower order classes assuming that methods will come from any of the super
         * classes.
         */
        private boolean isSuperInvoker;
        private Map<Integer, String> argumentTypeMap;

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

        private String getInvokerClassName() {
            return invokerClassName;
        }

        public OwningClassInfo getOwningClassInfo() {
            return owningClassInfo;
        }

        public String getOwningClassQualifiedName() {
            return Objects.nonNull(owningClassInfo) ? owningClassInfo.getOuterMostClassName() : null;
        }

        private boolean isSuperInvoker() {
            return isSuperInvoker;
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

            this.argumentTypeMap = new HashMap<>();
        }

        public Criteria setInvokerClassName(String invokerClassName) {
            this.invokerClassName = invokerClassName;

            return this;
        }

        public Criteria setSuperInvoker(boolean isSuperInvoker) {
            this.isSuperInvoker = isSuperInvoker;

            return this;
        }

        public Criteria setEnclosingQualifiedClassNameList(List<String> enclosingQualifiedClassNameList) {
            this.owningClassInfo = TypeInferenceFluentAPI.getOwningClassInfo(dependentArtifactSet, javaVersion,
                    enclosingQualifiedClassNameList, tinkerGraph);

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
            this.argumentTypeMap.put(argumentIndex, argumentType);

            return this;
        }

        public List<MethodInfo> getMethodList() {
            return getAllMethods(this);
        }
    }
}
