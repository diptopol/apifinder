package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.entity.MethodInfo;
import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.OwningClassInfo;
import ca.concordia.jaranalyzer.models.typeInfo.NullTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.SimpleTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import ca.concordia.jaranalyzer.service.ClassInfoService;
import ca.concordia.jaranalyzer.service.JarInfoService;
import ca.concordia.jaranalyzer.service.MethodInfoService;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import io.vavr.Tuple2;
import org.eclipse.jgit.api.Git;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 2/20/2021 10:35 PM
 */
public class TypeInferenceFluentAPI extends TypeInferenceBase {

    private JarAnalyzer jarAnalyzer;

    private static TypeInferenceFluentAPI instance;

    private JarInfoService jarInfoService;
    private ClassInfoService classInfoService;
    private MethodInfoService methodInfoService;

    public static TypeInferenceFluentAPI getInstance() {
        if (instance == null) {
            instance = new TypeInferenceFluentAPI();
        }

        return instance;
    }

    private TypeInferenceFluentAPI() {
        jarAnalyzer = new JarAnalyzer();

        jarInfoService = new JarInfoService();
        classInfoService = new ClassInfoService();
        methodInfoService = new MethodInfoService(classInfoService);
    }

    public Tuple2<String, Set<Artifact>> loadJavaAndExternalJars(String commitId, String projectName, Git git) {
        return jarAnalyzer.loadJavaAndExternalJars(commitId, projectName, git);
    }

    public Tuple2<String, Set<Artifact>> loadJavaAndExternalJars(String commitId, String projectName, String cloneUrl) {
        return jarAnalyzer.loadJavaAndExternalJars(commitId, projectName, cloneUrl);
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
        List<Integer> internalDependencyJarIdList = new ArrayList<>();
        List<Integer> jarIdList = jarInfoService.getJarIdList(criteria.getDependentArtifactSet(), criteria.getJavaVersion(), internalDependencyJarIdList);
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
        if (Objects.nonNull(criteria.getInvokerTypeInfo()) && !criteria.getInvokerTypeInfo().isNullTypeInfo()) {
            Set<String> classQNameSet = new LinkedHashSet<>(Arrays.asList(criteria.getInvokerTypeInfo().getQualifiedClassName()));

            Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();
            List<String> classQNameDeclarationOrderList = new ArrayList<>(classQNameSet);

            while (!classQNameSet.isEmpty() && qualifiedMethodInfoList.isEmpty()) {
                qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(),
                        jarIdList, classQNameSet, classInfoService, methodInfoService);

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarIdList, internalDependencyJarIdList);

                if (!qualifiedMethodInfoList.isEmpty()
                        && qualifiedMethodInfoList.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
                    deferredQualifiedMethodInfoSet.addAll(qualifiedMethodInfoList);
                    qualifiedMethodInfoList.clear();
                }

                if (qualifiedMethodInfoList.isEmpty()) {
                    Map<String, List<String>> superClassQNameMap =
                            getSuperClassQNameMapPerClass(classQNameSet, jarIdList, classInfoService);

                    insertSuperClassQNamePreservingDeclarationOrder(superClassQNameMap, classQNameSet,
                            classQNameDeclarationOrderList);

                    classQNameSet = getSuperClassQNameSet(classQNameSet, jarIdList, classInfoService);
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
                        jarIdList, classQNameSet, classInfoService, methodInfoService);

                boolean isOwningClassAttribute = (i == 0);
                qualifiedMethodInfoList.forEach(m -> m.setOwningClassAttribute(isOwningClassAttribute));

                qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarIdList, internalDependencyJarIdList);

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
                jarIdList, importedClassQNameSet, classInfoService, methodInfoService);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarIdList, internalDependencyJarIdList);

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
                jarIdList, importedClassQNameSet, methodInfoService);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarIdList, internalDependencyJarIdList);

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
                packageNameList, importedClassQNameSet, jarIdList, classInfoService, methodInfoService);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarIdList, internalDependencyJarIdList);

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

            qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(), jarIdList,
                    classQNameSet, classInfoService, methodInfoService);

            qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, criteria, jarIdList, internalDependencyJarIdList);

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

    public static void reduceByteCodeAddedArgumentsForInnerClassConstructor(List<MethodInfo> methodInfoList,
                                                                            Criteria criteria,
                                                                            List<Integer> jarIdList,
                                                                            ClassInfoService classInfoService) {
        if (Objects.nonNull(criteria.getNumberOfParameters())) {
            TypeInfo firstArgumentTypeInfo = criteria.getArgumentTypeInfoWithIndexList().stream()
                    .filter(a -> a._1() == 0).map(Tuple2::_2)
                    .findFirst()
                    .orElse(null);

            String firstArgumentQualifiedClassName = (Objects.nonNull(firstArgumentTypeInfo)
                    && !firstArgumentTypeInfo.isFunctionTypeInfo() && !firstArgumentTypeInfo.isFormalTypeParameterInfo())
                    ? firstArgumentTypeInfo.getQualifiedClassName()
                    : null;

            reduceArgumentForInnerClassConstructorIfRequired(methodInfoList, firstArgumentQualifiedClassName,
                    criteria.getNumberOfParameters(), jarIdList, classInfoService);
        }
    }

    public static List<MethodInfo> filterMethodInfoListBasedOnArguments(List<MethodInfo> methodInfoList,
                                                                        Criteria criteria,
                                                                        List<Integer> jarIdList,
                                                                        ClassInfoService classInfoService,
                                                                        MethodInfoService methodInfoService) {
        if (Objects.nonNull(criteria.getNumberOfParameters())
                && !(criteria.getNumberOfParameters() > 0 && criteria.getArgumentTypeInfoWithIndexList().isEmpty())) {
            methodInfoList = filterByMethodArgumentTypes(methodInfoList, criteria, jarIdList, classInfoService, methodInfoService);
        }

        return methodInfoList;
    }

    private List<MethodInfo> filterProcess(List<MethodInfo> methodInfoList,
                                           Criteria criteria,
                                           List<Integer> jarIdList,
                                           List<Integer> internalDependencyJarIdList) {
        if (methodInfoList.isEmpty()) {
            return methodInfoList;
        }

        modifyMethodInfoForArray(methodInfoList, criteria.getInvokerTypeInfo());

        setInternalDependencyProperty(methodInfoList, internalDependencyJarIdList);

        reduceByteCodeAddedArgumentsForInnerClassConstructor(methodInfoList, criteria, jarIdList, classInfoService);

        methodInfoList = filterByMethodInvoker(methodInfoList, criteria.getInvokerTypeInfo(),
                criteria.isSuperInvoker(), jarIdList, classInfoService);

        methodInfoList = filterMethodInfoListBasedOnArguments(methodInfoList, criteria, jarIdList, classInfoService,
                methodInfoService);

        if (criteria.isClassInstantiation()) {
            methodInfoList = filterBasedOnClassInstantiation(methodInfoList);
        }

        methodInfoList = filterMethodInfoListBasedOnOwningClass(methodInfoList);

        methodInfoList = prioritizeMethodInfoListBasedOnArguments(methodInfoList);

        methodInfoList = filteredNonAbstractMethod(methodInfoList);

        return methodInfoList;
    }

    private static List<MethodInfo> filterByMethodArgumentTypes(List<MethodInfo> methodInfoList,
                                                                Criteria criteria,
                                                                List<Integer> jarIdList,
                                                                ClassInfoService classInfoService,
                                                                MethodInfoService methodInfoService) {
        if (!methodInfoList.isEmpty()) {
            List<Tuple2<Integer, TypeInfo>> argumentTypeInfoWithIndexList = criteria.getArgumentTypeInfoWithIndexList();

            methodInfoList = methodInfoList.stream().filter(methodInfo -> {
                argumentTypeInfoWithIndexList.sort(Comparator.comparingInt(Tuple2::_1));
                List<TypeInfo> argumentTypeInfoList = getOrderedArgumentTypeInfoList(argumentTypeInfoWithIndexList);
                List<TypeInfo> methodArgumentTypeInfoList = getOrderedMethodArgumentTypeInfoList(argumentTypeInfoWithIndexList, methodInfo);

                return matchMethodArguments(argumentTypeInfoList, methodArgumentTypeInfoList, jarIdList,
                        methodInfoService, classInfoService, methodInfo);
            }).collect(Collectors.toList());
        }

        return methodInfoList;
    }

    private static List<TypeInfo> getOrderedArgumentTypeInfoList(List<Tuple2<Integer, TypeInfo>> argumentTypeInfoWithIndexListOrderedByIndex) {
        List<TypeInfo> argumentTypeInfoList = new ArrayList<>();

        for (Tuple2<Integer, TypeInfo> argumentIndexTuple : argumentTypeInfoWithIndexListOrderedByIndex) {
            argumentTypeInfoList.add(argumentIndexTuple._2());
        }

        return argumentTypeInfoList;
    }

    private static List<TypeInfo> getOrderedMethodArgumentTypeInfoList(List<Tuple2<Integer, TypeInfo>> argumentTypeInfoWithIndexListOrderedByIndex,
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
        private Integer numberOfParameters;
        private TypeInfo invokerTypeInfo;
        private boolean isClassInstantiation;
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

        private Integer getNumberOfParameters() {
            return numberOfParameters;
        }

        private TypeInfo getInvokerTypeInfo() {
            return this.invokerTypeInfo;
        }

        public boolean isClassInstantiation() {
            return isClassInstantiation;
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

        public Criteria(Set<Artifact> dependentArtifactSet,
                        String javaVersion,
                        List<String> importList,
                        String methodName) {

            this.dependentArtifactSet = dependentArtifactSet;
            this.javaVersion = javaVersion;
            this.importList = importList;
            this.methodName = methodName;

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

        public Criteria setClassInstantiation(boolean isClassInstantiation) {
            this.isClassInstantiation = isClassInstantiation;

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
