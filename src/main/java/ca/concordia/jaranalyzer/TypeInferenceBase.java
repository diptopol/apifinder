package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.ClassInfo;
import ca.concordia.jaranalyzer.models.MethodInfo;
import ca.concordia.jaranalyzer.models.OwningClassInfo;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import io.vavr.Tuple2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.WithOptions;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.objectweb.asm.Type;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 4/22/2021 9:05 AM
 */
public abstract class TypeInferenceBase {

    private static final int MAX_SUPER_CLASS_DISTANCE = 1000;
    private static final int PRIMITIVE_TYPE_WIDENING_DISTANCE = 1;
    private static final int PRIMITIVE_TYPE_NARROWING_DISTANCE = 2;
    private static final int PRIMITIVE_TYPE_WRAPPING_DISTANCE = 1;
    private static final int PRIMITIVE_TYPE_COMPARABLE_DISTANCE = 1;

    /*Increased the distance of matching the wrapped objects to primitives*/
    private static final double PRIMITIVE_TYPE_UNWRAPPING_DISTANCE = 1.5;
    private static final int PRIMITIVE_TYPE_NUMBER_DISTANCE = 1;

    private static Map<String, List<String>> PRIMITIVE_TYPE_WIDENING_MAP = new HashMap<>();
    private static Map<String, List<String>> PRIMITIVE_TYPE_NARROWING_MAP = new HashMap<>();

    private static Map<String, String> PRIMITIVE_WRAPPER_CLASS_MAP = new HashMap<>();
    private static Map<String, String> PRIMITIVE_UN_WRAPPER_CLASS_MAP = new HashMap<>();

    private static final List<String> PRIMITIVE_NUMERIC_TYPE_LIST =
            new ArrayList<>(Arrays.asList("byte", "short", "int", "long", "float", "double"));

    static {
        PRIMITIVE_WRAPPER_CLASS_MAP.put("boolean", "java.lang.Boolean");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("byte", "java.lang.Byte");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("char", "java.lang.Character");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("float", "java.lang.Float");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("int", "java.lang.Integer");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("long", "java.lang.Long");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("short", "java.lang.Short");
        PRIMITIVE_WRAPPER_CLASS_MAP.put("double", "java.lang.Double");

        PRIMITIVE_WRAPPER_CLASS_MAP = Collections.unmodifiableMap(PRIMITIVE_WRAPPER_CLASS_MAP);

        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Boolean", "boolean");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Byte", "byte");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Character", "char");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Float", "float");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Integer", "int");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Long", "long");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Short", "short");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put("java.lang.Double", "double");

        PRIMITIVE_UN_WRAPPER_CLASS_MAP = Collections.unmodifiableMap(PRIMITIVE_UN_WRAPPER_CLASS_MAP);

        PRIMITIVE_TYPE_WIDENING_MAP.put("byte", Arrays.asList("short", "int", "long", "float", "double"));
        PRIMITIVE_TYPE_WIDENING_MAP.put("short", Arrays.asList("int", "long", "float", "double"));
        PRIMITIVE_TYPE_WIDENING_MAP.put("char", Arrays.asList("int", "long", "float", "double"));
        PRIMITIVE_TYPE_WIDENING_MAP.put("int", Arrays.asList("long", "float", "double"));
        PRIMITIVE_TYPE_WIDENING_MAP.put("long", Arrays.asList("float", "double"));
        PRIMITIVE_TYPE_WIDENING_MAP.put("float", Arrays.asList("double"));

        PRIMITIVE_TYPE_WIDENING_MAP = Collections.unmodifiableMap(PRIMITIVE_TYPE_WIDENING_MAP);

        PRIMITIVE_TYPE_NARROWING_MAP.put("short", Arrays.asList("byte", "char"));
        PRIMITIVE_TYPE_NARROWING_MAP.put("char", Arrays.asList("byte", "short"));
        PRIMITIVE_TYPE_NARROWING_MAP.put("int", Arrays.asList("byte", "short", "char"));
        PRIMITIVE_TYPE_NARROWING_MAP.put("long", Arrays.asList("byte", "short", "char", "int"));
        PRIMITIVE_TYPE_NARROWING_MAP.put("float", Arrays.asList("byte", "short", "char", "int", "long"));
        PRIMITIVE_TYPE_NARROWING_MAP.put("double", Arrays.asList("byte", "short", "char", "int", "long", "float"));

        PRIMITIVE_TYPE_NARROWING_MAP = Collections.unmodifiableMap(PRIMITIVE_TYPE_NARROWING_MAP);
    }

    static List<MethodInfo> filterByMethodInvoker(List<MethodInfo> methodInfoList,
                                                  String invokerClassName,
                                                  boolean isSuperInvoker,
                                                  Object[] jarVertexIds,
                                                  TinkerGraph tinkerGraph) {
        if (!methodInfoList.isEmpty() && Objects.nonNull(invokerClassName) && !invokerClassName.equals("")) {
            Map<String, List<MethodInfo>> methodInfoDeclaringClassNameMap = new HashMap<>();

            String methodInfoClassName;
            for (MethodInfo methodInfo : methodInfoList) {
                methodInfoClassName = methodInfo.getQualifiedClassName();

                List<MethodInfo> methodInfoListForClass = methodInfoDeclaringClassNameMap.containsKey(methodInfoClassName)
                        ? methodInfoDeclaringClassNameMap.get(methodInfoClassName) : new ArrayList<>();

                methodInfoListForClass.add(methodInfo);
                methodInfoDeclaringClassNameMap.put(methodInfoClassName, methodInfoListForClass);
            }

            List<String> methodInfoClassNameList = new ArrayList<>(methodInfoDeclaringClassNameMap.keySet());

            List<MethodInfo> filteredListByInvokerClassName = new ArrayList<>();

            if (methodInfoClassNameList.contains(invokerClassName) && !isSuperInvoker) {
                filteredListByInvokerClassName.addAll(methodInfoDeclaringClassNameMap.get(invokerClassName));

            } else if (invokerClassName.contains("[]") && methodInfoClassNameList.contains("java.lang.Object")) {
                List<MethodInfo> qualifiedMethodInfoList = methodInfoDeclaringClassNameMap.get("java.lang.Object");
                qualifiedMethodInfoList.forEach(m -> m.setInvokerClassMatchingDistance(MAX_SUPER_CLASS_DISTANCE));
                filteredListByInvokerClassName.addAll(qualifiedMethodInfoList);

            } else {
                Set<String> classNameSet = new HashSet<>();
                classNameSet.add(invokerClassName);

                String[] allOutGoingEdges = new String[]{"extends", "implements"};
                String[] superClassOutGoingEdgeLabels = isSuperInvoker
                        ? new String[]{"extends"}
                        : allOutGoingEdges;

                Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

                int distance = 0;

                while (!classNameSet.isEmpty()) {
                    classNameSet = tinkerGraph.traversal().V(jarVertexIds)
                            .out("ContainsPkg").out("Contains")
                            .has("Kind", "Class")
                            .has("QName", TextP.within(classNameSet))
                            .out(superClassOutGoingEdgeLabels)
                            .<String>values("Name")
                            .toSet();

                    distance++;

                    superClassOutGoingEdgeLabels = allOutGoingEdges;

                    for (String className : methodInfoClassNameList) {
                        List<MethodInfo> qualifiedMethodInfoList = methodInfoDeclaringClassNameMap.get(className);

                        if (classNameSet.contains(className)) {
                            int finalDistance = className.equals("java.lang.Object") ? MAX_SUPER_CLASS_DISTANCE : distance;

                            qualifiedMethodInfoList.forEach(m -> m.setInvokerClassMatchingDistance(finalDistance));
                            filteredListByInvokerClassName.addAll(qualifiedMethodInfoList);
                        }
                    }

                    if (!filteredListByInvokerClassName.isEmpty()
                            && filteredListByInvokerClassName.stream().allMatch(MethodInfo::hasDeferredCriteria)) {
                        deferredQualifiedMethodInfoSet.addAll(filteredListByInvokerClassName);
                        filteredListByInvokerClassName.clear();
                    }

                    if (!filteredListByInvokerClassName.isEmpty()) {
                        break;
                    }
                }

                if (filteredListByInvokerClassName.isEmpty() && !deferredQualifiedMethodInfoSet.isEmpty()) {
                    filteredListByInvokerClassName.addAll(deferredQualifiedMethodInfoSet);
                }
            }

            return filteredListByInvokerClassName;
        } else {
            return methodInfoList;
        }
    }

    static boolean matchMethodArguments(List<String> argumentTypeClassNameList,
                                        List<String> methodArgumentClassNameList,
                                        Object[] jarVertexIds,
                                        TinkerGraph tinkerGraph,
                                        MethodInfo methodInfo) {
        List<String> commonClassNameList = getCommonClassNameList(argumentTypeClassNameList, methodArgumentClassNameList);

        for (String commonClassName : commonClassNameList) {
            argumentTypeClassNameList.remove(commonClassName);
            methodArgumentClassNameList.remove(commonClassName);
        }

        if (argumentTypeClassNameList.isEmpty() && methodArgumentClassNameList.isEmpty()) {
            return true;
        }

        List<String> matchedMethodArgumentTypeList = new ArrayList<>();

        for (int index = 0; index < argumentTypeClassNameList.size(); index++) {
            String argumentTypeClassName = argumentTypeClassNameList.get(index);
            String methodArgumentTypeClassName = methodArgumentClassNameList.get(index);

            if (InferenceUtility.isPrimitiveType(argumentTypeClassName) && InferenceUtility.isPrimitiveType(methodArgumentTypeClassName)) {
                if (isWideningPrimitiveConversion(argumentTypeClassName, methodArgumentTypeClassName)) {
                    methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance()
                            + PRIMITIVE_TYPE_WIDENING_DISTANCE);
                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);

                } else if (isNarrowingPrimitiveConversion(argumentTypeClassName, methodArgumentTypeClassName)) {
                    methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance()
                            + PRIMITIVE_TYPE_NARROWING_DISTANCE);
                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);

                } else {
                    return false;
                }
            }

            if (isNullType(argumentTypeClassName) && !InferenceUtility.isPrimitiveType(methodArgumentTypeClassName)) {
                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);

                continue;
            }

            // this check has to be done before `isArrayDimensionMismatch` checking
            if (methodArgumentTypeClassName.endsWith("[]") && methodInfo.isVarargs()
                    && isVarArgsMatch(methodArgumentTypeClassName,
                    argumentTypeClassNameList.subList(index, argumentTypeClassNameList.size()), jarVertexIds, tinkerGraph)) {

                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                break;
            }

            if (!methodArgumentTypeClassName.equals("java.lang.Object")
                    && matchObjectArrayDimensionForArgument(argumentTypeClassName, methodArgumentTypeClassName)) {
                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                continue;
            }

            if (!methodArgumentTypeClassName.equals("java.lang.Object")
                    && isArrayDimensionMismatch(argumentTypeClassName, methodArgumentTypeClassName)) {
                return false;
            }

            if (InferenceUtility.isPrimitiveType(argumentTypeClassName)) {
                if (PRIMITIVE_WRAPPER_CLASS_MAP.get(argumentTypeClassName).equals(methodArgumentTypeClassName)) {
                    methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + PRIMITIVE_TYPE_WRAPPING_DISTANCE);
                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);

                } else if ("java.lang.Comparable".equals(methodArgumentTypeClassName)) {
                    methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + PRIMITIVE_TYPE_COMPARABLE_DISTANCE);
                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                }
            }

            if (InferenceUtility.isPrimitiveType(methodArgumentTypeClassName)
                    && PRIMITIVE_UN_WRAPPER_CLASS_MAP.containsKey(argumentTypeClassName)
                    && PRIMITIVE_UN_WRAPPER_CLASS_MAP.get(argumentTypeClassName).equals(methodArgumentTypeClassName)) {

                methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + PRIMITIVE_TYPE_UNWRAPPING_DISTANCE);
                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                continue;
            }

            /*
             * Trimmed down array dimension before searching for super classes.
             */
            argumentTypeClassName = argumentTypeClassName.replaceAll("\\[]", "");
            methodArgumentTypeClassName = methodArgumentTypeClassName.replaceAll("\\[]", "");
            methodArgumentClassNameList.set(index, methodArgumentTypeClassName);

            if (methodArgumentTypeClassName.contains("$")) {
                methodArgumentTypeClassName = methodArgumentTypeClassName.replace("$", ".");
                methodArgumentClassNameList.set(index, methodArgumentTypeClassName);

                if (methodArgumentTypeClassName.equals(argumentTypeClassName)) {
                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                    continue;
                }
            }

            if (methodArgumentTypeClassName.equals("java.lang.Object")) {
                if (InferenceUtility.isPrimitiveType(argumentTypeClassName)) {
                    methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + MAX_SUPER_CLASS_DISTANCE);
                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                    continue;
                } else if (argumentTypeClassName.equals("java.lang.Object")) {
                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                    continue;
                }
            }

            if (methodArgumentTypeClassName.equals("java.lang.Number")
                    && PRIMITIVE_NUMERIC_TYPE_LIST.contains(argumentTypeClassName)) {
                methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + PRIMITIVE_TYPE_NUMBER_DISTANCE);
                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                continue;
            }

            Set<String> classNameList = new HashSet<>();
            classNameList.add(argumentTypeClassName);

            int distance = 0;

            while (!classNameList.isEmpty()) {
                classNameList = getSuperClassQNameSet(classNameList, jarVertexIds, tinkerGraph);

                distance++;

                if (classNameList.contains(methodArgumentTypeClassName)) {
                    if (methodArgumentTypeClassName.equals("java.lang.Object")) {
                        methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + MAX_SUPER_CLASS_DISTANCE);
                    } else {
                        methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + distance);
                    }

                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                    break;
                }
            }
        }

        methodArgumentClassNameList.removeAll(matchedMethodArgumentTypeList);

        return methodArgumentClassNameList.isEmpty();
    }

    static List<MethodInfo> populateClassInfo(List<MethodInfo> qualifiedMethodInfoList, TinkerGraph tinkerGraph) {
        qualifiedMethodInfoList.forEach(m -> {
            Set<ClassInfo> classInfoSet = tinkerGraph.traversal()
                    .V(m.getId())
                    .in("Declares")
                    .toStream()
                    .map(ClassInfo::new)
                    .collect(Collectors.toSet());

            assert classInfoSet.size() == 1;

            m.setClassInfo(classInfoSet.iterator().next());
        });

        return qualifiedMethodInfoList;
    }


    static List<ClassInfo> resolveQClassInfoForClass(String typeClassName,
                                                     Object[] jarVertexIds,
                                                     Set<String> importedClassQNameSet,
                                                     List<String> packageNameList,
                                                     TinkerGraph tinkerGraph,
                                                     OwningClassInfo owningClassInfo) {

        if (Objects.nonNull(typeClassName) && !InferenceUtility.isPrimitiveType(typeClassName)
                && StringUtils.countMatches(typeClassName, ".") <= 1) {

            String postProcessedOwningClassQualifiedName = Objects.nonNull(owningClassInfo)
                    && Objects.nonNull(owningClassInfo.getOuterMostClassName())
                    ? owningClassInfo.getOuterMostClassName().replace("$", ".")
                    : null;

            String postProcessedTypeClassName = typeClassName.replace(".", "$")
                    .replaceAll("\\[]", "");

            if (Objects.nonNull(owningClassInfo) && !owningClassInfo.getAvailableQualifiedClassNameSet().isEmpty()) {
                importedClassQNameSet.addAll(owningClassInfo.getAvailableQualifiedClassNameSet());
            }

            List<ClassInfo> qualifiedClassInfoList = tinkerGraph.traversal().V(jarVertexIds)
                    .out("ContainsPkg").out("Contains")
                    .has("Kind", "Class")
                    .has("Name", TextP.containing(postProcessedTypeClassName))
                    .toStream()
                    .map(ClassInfo::new)
                    .collect(Collectors.toList());

            qualifiedClassInfoList = qualifiedClassInfoList.stream().filter(classInfo -> {
                if (classInfo.isInnerClass()) {
                    if (classInfo.isPrivate()) {
                        if (Objects.isNull(postProcessedOwningClassQualifiedName)
                                || !classInfo.getQualifiedName().startsWith(postProcessedOwningClassQualifiedName)) {
                            return false;
                        }
                    }

                    boolean classNameCheck;
                    if (postProcessedTypeClassName.contains("$")) {
                        classNameCheck = classInfo.getName().equals(postProcessedTypeClassName);
                    } else {
                        classNameCheck = classInfo.getName().endsWith("$" + postProcessedTypeClassName);
                    }

                    if (classNameCheck) {
                        String qualifiedClassName = classInfo.getQualifiedName();
                        String qualifiedOuterClassName = classInfo.getQualifiedName()
                                .substring(0, classInfo.getQualifiedName().lastIndexOf("."));

                        return importedClassQNameSet.contains(qualifiedClassName)
                                || importedClassQNameSet.contains(qualifiedOuterClassName)
                                || packageNameList.contains(classInfo.getPackageName());

                    } else {
                        return false;
                    }
                } else {
                    return classInfo.getName().equals(postProcessedTypeClassName)
                            && (importedClassQNameSet.contains(classInfo.getQualifiedName())
                            || packageNameList.contains(classInfo.getPackageName()));
                }
            }).collect(Collectors.toList());

            return qualifiedClassInfoList;
        }

        return Collections.emptyList();
    }

    static String resolveQNameForClass(String typeClassName,
                                       OwningClassInfo owningClassInfo,
                                       Object[] jarVertexIds,
                                       Set<String> importedClassQNameSet,
                                       List<String> packageNameList,
                                       TinkerGraph tinkerGraph) {
        if (Objects.nonNull(typeClassName)) {
            typeClassName = typeClassName.replace("$", ".");
        }

        int numberOfArrayDimensions = StringUtils.countMatches(typeClassName, "[]");

        List<ClassInfo> qualifiedClassInfoList = resolveQClassInfoForClass(typeClassName, jarVertexIds,
                importedClassQNameSet, packageNameList, tinkerGraph, owningClassInfo);

        qualifiedClassInfoList = filtrationBasedOnPrioritization(typeClassName, owningClassInfo,
                importedClassQNameSet, qualifiedClassInfoList);

        return qualifiedClassInfoList.isEmpty()
                ? typeClassName
                : getQualifiedNameWithArrayDimension(qualifiedClassInfoList.get(0).getQualifiedName(), numberOfArrayDimensions);
    }

    static List<ClassInfo> filtrationBasedOnPrioritization(String typeClassName,
                                                           OwningClassInfo owningClassInfo,
                                                           Set<String> importedClassQNameSet,
                                                           List<ClassInfo> qualifiedClassInfoList) {
        /*
         * If there are multiple result, we want to give priority for classes who are directly mentioned in import
         * statement or belongs to 'java.lang' package. Because * package import can have many classes which satisfies
         * the same condition. But we will only want to consider * package import if there is no directly mentioned class.
         *
         * For inner classes, we will be only able to find type name as inner class name if import contains name of inner class
         * otherwise we will find outer class suffix during type declaration.
         *
         * Hierarchy must be maintained. First we have to check type direct import and then java.lang package.
         */
        Predicate<ClassInfo> isClassNameDirectImport = c -> (StringUtils.countMatches(typeClassName, ".") == 1
                ? importedClassQNameSet.contains(c.getQualifiedName().substring(0, c.getQualifiedName().lastIndexOf(".")))
                : importedClassQNameSet.contains(c.getQualifiedName()));

        if (qualifiedClassInfoList.size() > 1 && qualifiedClassInfoList.stream().anyMatch(isClassNameDirectImport)) {
            qualifiedClassInfoList = qualifiedClassInfoList.stream()
                    .filter(isClassNameDirectImport)
                    .collect(Collectors.toList());
        }

        if (Objects.nonNull(owningClassInfo) && qualifiedClassInfoList.size() > 1) {
            List<Set<String>> qClassNameSetInHierarchy = owningClassInfo.getQualifiedClassNameSetInHierarchy();

            for (Set<String> qClassNameSet : qClassNameSetInHierarchy) {
                if (qualifiedClassInfoList.stream().anyMatch(c -> qClassNameSet.contains(c.getQualifiedName()))) {
                    return qualifiedClassInfoList.stream().filter(c -> qClassNameSet.contains(c.getQualifiedName()))
                            .collect(Collectors.toList());
                }
            }
        }

        if (qualifiedClassInfoList.size() > 1 && qualifiedClassInfoList.stream().anyMatch(c -> c.getPackageName().equals("java.lang"))) {
            qualifiedClassInfoList = qualifiedClassInfoList.stream()
                    .filter(c -> c.getPackageName().equals("java.lang"))
                    .collect(Collectors.toList());
        }

        String fullyQualifiedTypeName = StringUtils.countMatches(typeClassName, ".") > 1 ? typeClassName : null;
        if (qualifiedClassInfoList.size() > 1 && fullyQualifiedTypeName != null
                && qualifiedClassInfoList.stream().anyMatch(c -> c.getQualifiedName().equals(fullyQualifiedTypeName))) {

            return qualifiedClassInfoList.stream()
                    .filter(c -> c.getQualifiedName().equals(fullyQualifiedTypeName))
                    .collect(Collectors.toList());
        }

        return qualifiedClassInfoList;
    }

    static Set<MethodInfo> prioritizeDeferredMethodInfoSet(Set<MethodInfo> methodInfoSet) {
        double minimumArgumentMatchingDistance = getMinimumArgumentMatchingDistance(methodInfoSet);

        if (methodInfoSet.size() > 1
                && minimumArgumentMatchingDistance == 0
                && !methodInfoSet.stream().allMatch(m -> m.getArgumentTypes().length == 0)) {

            Set<MethodInfo> filteredMethodInfoSet = new LinkedHashSet<>();

            for (MethodInfo methodInfo: methodInfoSet) {
                if (methodInfo.getArgumentMatchingDistance() == minimumArgumentMatchingDistance) {
                    filteredMethodInfoSet.add(methodInfo);
                }
            }

            methodInfoSet = filteredMethodInfoSet;
        }

        int minimumInvokerClassMatchingDistance = getMinimumInvokerClassMatchingDistance(methodInfoSet);

        if (methodInfoSet.size() > 1
                && !methodInfoSet.stream().allMatch(MethodInfo::isAbstract)
                && !methodInfoSet.stream().allMatch(m -> m.getInvokerClassMatchingDistance() == minimumInvokerClassMatchingDistance)) {
            Set<MethodInfo> filteredMethodInfoSet = new LinkedHashSet<>();

            for (MethodInfo methodInfo: methodInfoSet) {
                if (methodInfo.getInvokerClassMatchingDistance() == minimumInvokerClassMatchingDistance) {
                    filteredMethodInfoSet.add(methodInfo);
                }
            }

            methodInfoSet = filteredMethodInfoSet;
        }

        if (methodInfoSet.size() > 1
                && (methodInfoSet.stream()
                .allMatch(m -> m.getInvokerClassMatchingDistance() == minimumInvokerClassMatchingDistance
                        && m.getArgumentMatchingDistance() == minimumArgumentMatchingDistance)
                || methodInfoSet.stream().allMatch(MethodInfo::isAbstract))) {
            methodInfoSet = Collections.singleton(methodInfoSet.iterator().next());
        }

        return methodInfoSet;
    }

    static List<MethodInfo> getQualifiedMethodInfoList(String methodName,
                                                       int numberOfParameters,
                                                       Object[] jarVertexIds,
                                                       Set<String> classQNameSet,
                                                       TinkerGraph tinkerGraph) {

        String outerClassPrefix = StringUtils.countMatches(methodName, ".") == 1
                ? methodName.substring(0, methodName.indexOf("."))
                : null;

        methodName = StringUtils.countMatches(methodName, ".") == 1
                ? methodName.substring(methodName.indexOf(".") + 1)
                : methodName;

        List<Long> classVertexIdList = getQualifiedClassVertexIdList(classQNameSet, jarVertexIds, tinkerGraph);

        if (classVertexIdList.isEmpty()) {
            return new ArrayList<>();
        }

        return tinkerGraph.traversal().V(classVertexIdList.toArray(new Long[0]))
                .out("Declares")
                .has("Kind", "Method")
                .has("Name", methodName)
                .toStream()
                .map(MethodInfo::new)
                .filter(methodInfo -> filtrationBasedOnCriteria(numberOfParameters, outerClassPrefix, methodInfo))
                .collect(Collectors.toList());
    }

    static List<MethodInfo> getQualifiedMethodInfoListForInnerClass(String methodName,
                                                                    int numberOfParameters,
                                                                    Object[] jarVertexIds,
                                                                    Set<String> classQNameSet,
                                                                    TinkerGraph tinkerGraph) {

        String outerClassPrefix = StringUtils.countMatches(methodName, ".") == 1
                ? methodName.substring(0, methodName.indexOf("."))
                : null;

        methodName = StringUtils.countMatches(methodName, ".") == 1
                ? methodName.substring(methodName.indexOf(".") + 1)
                : methodName;

        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameSet))
                .out("ContainsInnerClass")
                .out("Declares")
                .has("Kind", "Method")
                .has("Name", methodName)
                .toStream()
                .map(MethodInfo::new)
                .filter(methodInfo -> filtrationBasedOnCriteria(numberOfParameters, outerClassPrefix, methodInfo))
                .collect(Collectors.toList());
    }


    static List<MethodInfo> getQualifiedMethodInfoListForPackageImport(String methodName,
                                                                       int numberOfParameters,
                                                                       List<String> packageNameList,
                                                                       Set<String> importedClassQNameSet,
                                                                       Object[] jarVertexIds,
                                                                       TinkerGraph tinkerGraph) {
        Set<String> classNameListForPackgage = tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg")
                .has("Kind", "Package")
                .has("Name", TextP.within(packageNameList))
                .out("Contains")
                .has("Kind", "Class")
                .<String>values("QName")
                .toSet();

        importedClassQNameSet.addAll(classNameListForPackgage);

        return getQualifiedMethodInfoList(methodName, numberOfParameters, jarVertexIds, classNameListForPackgage, tinkerGraph);
    }

    static Map<String, List<String>> getSuperClassQNameMapPerClass(Set<String> classQNameSet,
                                                                   Object[] jarVertexIds,
                                                                   TinkerGraph tinkerGraph) {

        Map<String, List<String>> superClassNameMap = new HashMap<>();

        Map<String, Long> classVertexIdMap = getClassVertexIdMap(classQNameSet, jarVertexIds, tinkerGraph);

        for (String classQName : classQNameSet) {
            if (classVertexIdMap.containsKey(classQName) && Objects.nonNull(classVertexIdMap.get(classQName))) {
                Long classVertexId = classVertexIdMap.get(classQName);

                List<String> superClassNameList = tinkerGraph.traversal().V(classVertexId)
                        .out("extends", "implements")
                        .order().by("Order")
                        .<String>values("Name")
                        .toList();

                superClassNameMap.put(classQName, superClassNameList);
            }
        }

        return superClassNameMap;
    }

    static Set<String> getSuperClassQNameSet(Set<String> classQNameSet,
                                             Object[] jarVertexIds,
                                             TinkerGraph tinkerGraph) {

        if (classQNameSet.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> superClassSet = new LinkedHashSet<>();
        Map<String, List<String>> superClassQNameMap = getSuperClassQNameMapPerClass(classQNameSet, jarVertexIds,
                tinkerGraph);

        for (String classQName : classQNameSet) {
            if (superClassQNameMap.containsKey(classQName)) {
                superClassSet.addAll(superClassQNameMap.get(classQName));
            }
        }

        return superClassSet;
    }

    static Set<String> getInnerClassQualifiedNameSet(Object[] jarVertexIds,
                                            Set<String> classQNameSet,
                                            TinkerGraph tinkerGraph) {

        if (classQNameSet.isEmpty()) {
            return Collections.emptySet();
        }

        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameSet))
                .out("ContainsInnerClass")
                .<String>values("QName")
                .toSet();
    }

    static Object[] getJarVertexIds(Set<Artifact> artifactSet,
                                    String javaVersion,
                                    TinkerGraph tinkerGraph) {
        Set<Object> jarVertexIdSet = new HashSet<>();

        artifactSet.forEach(artifact -> {
            jarVertexIdSet.addAll(
                    tinkerGraph.traversal().V()
                            .has("Kind", "Jar")
                            .has("GroupId", artifact.getGroupId())
                            .has("ArtifactId", artifact.getArtifactId())
                            .has("Version", artifact.getVersion())
                            .toStream()
                            .map(Element::id)
                            .collect(Collectors.toSet())
            );
        });

        jarVertexIdSet.addAll(
                tinkerGraph.traversal().V()
                        .has("Kind", "Jar")
                        .has("ArtifactId", "Java")
                        .has("Version", javaVersion)
                        .toStream()
                        .map(Element::id)
                        .collect(Collectors.toSet())
        );

        return jarVertexIdSet.toArray(new Object[0]);
    }


    static List<String> getPackageNameList(List<String> importList) {
        List<String> nonImportStaticList = importList.stream()
                .filter(im -> !im.startsWith("import static"))
                .collect(Collectors.toList());

        return nonImportStaticList.stream()
                .filter(im -> im.endsWith(".*"))
                .map(im -> im.substring(0, im.lastIndexOf(".*")).replace("import", "").trim())
                .collect(Collectors.toList());
    }

    static Set<String> getImportedQNameSet(List<String> importList) {
        Set<String> importedClassQNameSet = new HashSet<>();
        List<String> importStaticList = importList.stream()
                .filter(im -> im.startsWith("import static"))
                .collect(Collectors.toList());

        List<String> nonImportStaticList = importList.stream()
                .filter(im -> !im.startsWith("import static"))
                .collect(Collectors.toList());

        importedClassQNameSet.addAll(
                nonImportStaticList.stream()
                        .filter(im -> !im.endsWith(".*"))
                        .map(im -> im.replace("import", "").trim())
                        .collect(Collectors.toSet())
        );

        importedClassQNameSet.addAll(
                importStaticList.stream()
                        .map(im -> im.substring(0, im.lastIndexOf(".")).replace("import static", "").trim())
                        .collect(Collectors.toSet())
        );

        return importedClassQNameSet;
    }


    static String processMethodName(String methodName, Set<String> importedClassQNameSet) {
        /*
          Method name may contains parameterized type (e.g ArrayList<String>). So removal of parameterized type is required
          before method name matching.
         */
        if (methodName.contains("<") && methodName.contains(">")) {
            int startIndex = methodName.indexOf("<");
            int endIndex = methodName.lastIndexOf(">") + 1;

            methodName = methodName.replace(methodName.substring(startIndex, endIndex), "");
        }

        /*
          For fully qualified method expression (method name should have dot more than 1), We are extracting fully
          qualified class name as import and method name
         */
        if (StringUtils.countMatches(methodName, ".") > 1) {
            importedClassQNameSet.add(methodName);
            methodName = methodName.substring(methodName.lastIndexOf(".") + 1);
        }

        return methodName;
    }

    static int getMinimumInvokerClassMatchingDistance(Collection<MethodInfo> methodInfoCollection) {
        return Optional.of(methodInfoCollection)
                .orElse(Collections.emptyList())
                .stream()
                .map(MethodInfo::getInvokerClassMatchingDistance)
                .mapToInt(v -> v)
                .min()
                .orElse(0);
    }

    static double getMinimumArgumentMatchingDistance(Collection<MethodInfo> methodInfoCollection) {
        return Optional.of(methodInfoCollection)
                .orElse(Collections.emptyList())
                .stream()
                .map(MethodInfo::getArgumentMatchingDistance)
                .mapToDouble(v -> v)
                .min()
                .orElse(0);
    }

    static void modifyMethodInfoForArray(List<MethodInfo> methodInfoList, String invokerClassName) {
        if (invokerClassName != null && invokerClassName.endsWith("[]")) {
            int dimension = StringUtils.countMatches(invokerClassName, "[]");
            String typeName = invokerClassName.replaceAll("\\[]", "");

            typeName = InferenceUtility.isPrimitiveType(typeName)
                    ? getTypeDescriptorForPrimitive(typeName)
                    : "L" + typeName.replaceAll("\\.", "/") + ";";

            Type returnType = Type.getType(StringUtils.repeat("[", dimension) + typeName);

            methodInfoList.forEach(m -> {
                m.setReturnType(returnType);
                m.setThrownInternalClassNames(Collections.emptyList());
            });
        }
    }


    /*
     * Java Compiler can add outer class as first argument to the inner class constructors if not present. We will
     * always try to remove first argument for inner class constructor unless sent argument name is outer class name.
     *
     * Limitation: For now, it's not possible to determine from only number of parameters only whether to remove
     * the first argument (assuming that was added by compiler). We also need to know the class name of first argument
     * in order to recognize that.
     */
    static void reduceArgumentForInnerClassConstructorIfRequired(List<MethodInfo> methodInfoList,
                                                                 String firstArgumentQualifiedClassName,
                                                                 int numberOfParameters,
                                                                 Object[] jarVertexIds,
                                                                 TinkerGraph tinkerGraph) {

        Set<String> eligibleFirstArgumentClassNameSet = new LinkedHashSet<>();

        eligibleFirstArgumentClassNameSet.add(firstArgumentQualifiedClassName);
        eligibleFirstArgumentClassNameSet.addAll(
                getAllSuperClassSet(Collections.singleton(firstArgumentQualifiedClassName), jarVertexIds, tinkerGraph));
        eligibleFirstArgumentClassNameSet.remove("java.lang.Object");

        for (MethodInfo methodInfo: methodInfoList) {
            if (methodInfo.isInnerClassConstructor()) {
                if (Objects.isNull(firstArgumentQualifiedClassName)
                        || !eligibleFirstArgumentClassNameSet.contains(methodInfo.getClassInfo().getOuterClassQualifiedName())) {

                    if (CollectionUtils.isNotEmpty(methodInfo.getArgumentTypeInfoList())
                            && methodInfo.getArgumentTypeInfoList().get(0).getQualifiedClassName()
                            .equals(methodInfo.getClassInfo().getOuterClassQualifiedName())) {

                        List<Type> methodArgumentList = new ArrayList<>(Arrays.asList(methodInfo.getArgumentTypes()));
                        methodArgumentList.remove(0);

                        methodInfo.setArgumentTypes(methodArgumentList.toArray(new Type[0]));
                        methodInfo.getArgumentTypeInfoList().remove(0);
                    }
                }
            }
        }

        methodInfoList.removeIf(m -> m.isInnerClassConstructor() && m.getArgumentTypes().length != numberOfParameters);
    }

    static String getTypeDescriptorForPrimitive(String type) {
        if (!InferenceUtility.isPrimitiveType(type)) {
            return null;
        }

        switch (type) {
            case "boolean":
                return "Z";
            case "char":
                return "C";
            case "byte":
                return "B";
            case "short":
                return "S";
            case "int":
                return "I";
            case "float":
                return "F";
            case "long":
                return "J";
            case "double":
                return "D";
            default:
                throw new IllegalStateException();
        }
    }

    static Set<MethodInfo> filteredNonAbstractMethod(Set<MethodInfo> methodInfoSet) {
        return new HashSet<>(filteredNonAbstractMethod(new ArrayList<>(methodInfoSet)));
    }

    static List<MethodInfo> filteredNonAbstractMethod(List<MethodInfo> methodInfoList) {
        if (methodInfoList.size() > 1 && methodInfoList.stream().anyMatch(m -> !m.isAbstract())) {
            return methodInfoList.stream().filter(m -> !m.isAbstract()).collect(Collectors.toList());
        } else {
            return methodInfoList;
        }
    }

    static OwningClassInfo getOwningClassInfo(Set<Artifact> dependentArtifactSet,
                                              String javaVersion,
                                              List<String> enclosingQualifiedClassNameList,
                                              TinkerGraph tinkerGraph) {

        if (Objects.isNull(enclosingQualifiedClassNameList) || enclosingQualifiedClassNameList.size() == 0) {
            return null;
        }

        Object[] jarVertexIds = getJarVertexIds(dependentArtifactSet, javaVersion, tinkerGraph);
        List<Set<String>> qClassNameSetInHierarchy = new ArrayList<>();

        String outerMostClassName = enclosingQualifiedClassNameList.get(enclosingQualifiedClassNameList.size() - 1);
        Set<String> innerClassQualifiedNameSet =
                getInnerClassQualifiedNameSet(jarVertexIds, Collections.singleton(outerMostClassName), tinkerGraph);

        /*
         * Here conversion is needed. Because inner class inside method can have position
         * number ahead of class name. But enclosingQualifiedClassNameList can not provide such information.
         * So we are fetching inner classes using outer-most class and then replacing with appropriate name
         * of inner class.
         */
        convertEnclosingClassNameList(enclosingQualifiedClassNameList, innerClassQualifiedNameSet);
        List<String> classQNameDeclarationOrderList = new ArrayList<>(enclosingQualifiedClassNameList);

        /*
         * Adding inner class that is outside enclosing class name list because method can be a class construction
         * of that inner class.
         */
        qClassNameSetInHierarchy.add(
                getCombinedClassAndInnerClassQualifiedNameSet(new LinkedHashSet<>(enclosingQualifiedClassNameList),
                        innerClassQualifiedNameSet));

        Set<String> classQNameSet = new LinkedHashSet<>(enclosingQualifiedClassNameList);

        while (!classQNameSet.isEmpty()) {
            Map<String, List<String>> superClassQNameMap =
                    getSuperClassQNameMapPerClass(classQNameSet, jarVertexIds, tinkerGraph);

            insertSuperClassQNamePreservingDeclarationOrder(superClassQNameMap, classQNameSet,
                    classQNameDeclarationOrderList);

            classQNameSet = getOrderedSuperClassQNameSet(superClassQNameMap, classQNameSet);

            if (!classQNameSet.isEmpty()) {
                /*
                 * Class can be used without import if inner class of super classes. In order to find those classes
                 * we need to add these inner class in hierarchy.
                 */
                qClassNameSetInHierarchy.add(getCombinedClassAndInnerClassQualifiedNameSet(classQNameSet,
                        getInnerClassQualifiedNameSet(jarVertexIds, classQNameSet, tinkerGraph)));
            }
        }

        return new OwningClassInfo(enclosingQualifiedClassNameList, qClassNameSetInHierarchy, classQNameDeclarationOrderList);
    }

    static List<String> getUniqueClassQNameList(List<String> classQNameList) {
        return new ArrayList<>(new LinkedHashSet<>(classQNameList));
    }

    static void insertSuperClassQNamePreservingDeclarationOrder(Map<String, List<String>> superClassQNameMap,
                                                                Set<String> classQNameSet,
                                                                List<String> classQNameDeclarationOrderList) {

        assert classQNameSet instanceof LinkedHashSet;

        for (String classQName: classQNameSet) {
            if (superClassQNameMap.containsKey(classQName)
                    && classQNameDeclarationOrderList.contains(classQName)) {

                List<String> superClassQNameList = superClassQNameMap.get(classQName);
                int insertionIndex = classQNameDeclarationOrderList.indexOf(classQName) + 1;

                if (insertionIndex < classQNameDeclarationOrderList.size()) {
                    for (String superClassQName: superClassQNameList) {
                        classQNameDeclarationOrderList.add(insertionIndex, superClassQName);
                        insertionIndex++;
                    }

                } else {
                    classQNameDeclarationOrderList.addAll(superClassQNameList);
                }
            }
        }
    }

    static Set<MethodInfo> getOrderedDeferredMethodInfoSetBasedOnDeclarationOrder(Set<MethodInfo> deferredQualifiedMethodInfoSet,
                                                                                  List<String> classQNameDeclarationOrderList) {
        List<MethodInfo> orderedDeferredMethodInfoList = new ArrayList<>(deferredQualifiedMethodInfoSet);
        orderedDeferredMethodInfoList.sort(Comparator.comparingInt(m ->
                classQNameDeclarationOrderList.indexOf(m.getQualifiedClassName())));

        return new LinkedHashSet<>(orderedDeferredMethodInfoList);
    }

    private static Set<String> getAllSuperClassSet(Set<String> classSet, Object[] jarVertexIds, TinkerGraph tinkerGraph) {
        Set<String> allSuperClassQualifiedNameSet = new LinkedHashSet<>();

        while (!classSet.isEmpty()) {
            Set<String> superClassQualifiedNameSet = getSuperClassQNameSet(classSet, jarVertexIds, tinkerGraph);
            allSuperClassQualifiedNameSet.addAll(superClassQualifiedNameSet);

            classSet = new LinkedHashSet<>(superClassQualifiedNameSet);
        }

        return allSuperClassQualifiedNameSet;
    }

    private static Set<String> getCombinedClassAndInnerClassQualifiedNameSet(Set<String> classQualifiedNameSet,
                                                                  Set<String> innerClassQualifiedNameSet) {

        Set<String> combinedQualifiedClassNameSet = new LinkedHashSet<>();

        combinedQualifiedClassNameSet.addAll(classQualifiedNameSet);
        combinedQualifiedClassNameSet.addAll(innerClassQualifiedNameSet);

        return combinedQualifiedClassNameSet;
    }

    private static Set<String> getOrderedSuperClassQNameSet(Map<String, List<String>> superClassQNameMap, Set<String> classQNameSet) {
        Set<String> superClassQNameSet = new LinkedHashSet<>();

        for (String classQName: classQNameSet) {
            if (superClassQNameMap.containsKey(classQName)) {
                superClassQNameSet.addAll(superClassQNameMap.get(classQName));
            }
        }

        return superClassQNameSet;
    }

    private static void convertEnclosingClassNameList(List<String> enclosingClassNameList,
                                                      Set<String> innerClassQualifiedNameSet) {

        assert enclosingClassNameList.size() > 0;

        for (String innerClassQualifiedName : innerClassQualifiedNameSet) {
            if (innerClassQualifiedName.matches(".*\\.[0-9]+.*")) {
                String innerClassQualifiedNameWithoutPosition = innerClassQualifiedName.replaceAll("\\.[0-9]+", "\\.");

                if (enclosingClassNameList.contains(innerClassQualifiedNameWithoutPosition)) {
                    int index = enclosingClassNameList.indexOf(innerClassQualifiedNameWithoutPosition);
                    enclosingClassNameList.set(index, innerClassQualifiedName);
                }
            }
        }

    }

    private static List<Long> getQualifiedClassVertexIdList(Set<String> classQNameSet, Object[] jarVertexIds, TinkerGraph tinkerGraph) {
        Map<String, Long> classVertexIdMap = getClassVertexIdMap(classQNameSet, jarVertexIds, tinkerGraph);

        List<Long> classVertexIdList = new ArrayList<>();

        for (String classQName: classQNameSet) {
            if (Objects.nonNull(classVertexIdMap.get(classQName))) {
                classVertexIdList.add(classVertexIdMap.get(classQName));
            }
        }

        return classVertexIdList;
    }

    private static Map<String, Long> getClassVertexIdMap(Set<String> classQNameSet, Object[] jarVertexIds, TinkerGraph tinkerGraph) {
        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameSet))
                .valueMap("QName").with(WithOptions.tokens)
                .toStream()
                .map(m -> new ArrayList(m.values()))
                .map(a -> new Tuple2<>((Long) a.get(0), (String) ((ArrayList) a.get(2)).get(0)))
                .collect(Collectors.toMap(Tuple2::_2, Tuple2::_1));
    }

    private static boolean filtrationBasedOnCriteria(int numberOfParameters,
                                                     String outerClassPrefix,
                                                     MethodInfo methodInfo) {
        boolean outerClassPrefixMatchingForInnerClassConstructor = true;

        if (Objects.nonNull(outerClassPrefix) && methodInfo.isInnerClassConstructor()) {
            outerClassPrefixMatchingForInnerClassConstructor =
                    methodInfo.getInternalClassConstructorPrefix().equals(outerClassPrefix + "$");
        }

        return outerClassPrefixMatchingForInnerClassConstructor
                && !methodInfo.isBridgeMethod()
                && checkMethodArgumentLength(numberOfParameters, methodInfo);
    }

    private static boolean checkMethodArgumentLength(int numberOfParameters, MethodInfo methodInfo) {
        if (methodInfo.isVarargs()) {
            return methodInfo.getArgumentTypes().length - 1 <= numberOfParameters;
        } else if (methodInfo.isInnerClassConstructor()) {
            return methodInfo.getArgumentTypes().length == numberOfParameters
                    || methodInfo.getArgumentTypes().length - 1 == numberOfParameters;

        } else {
            return methodInfo.getArgumentTypes().length == numberOfParameters;
        }
    }

    private static String getQualifiedNameWithArrayDimension(String qualifiedClassName, int arrayDimension) {
        StringBuilder qualifiedClassNameBuilder = new StringBuilder(qualifiedClassName);

        for (int i = 0; i < arrayDimension; i++) {
            qualifiedClassNameBuilder.append("[]");
        }

        return qualifiedClassNameBuilder.toString();
    }

    private static boolean isNullType(String name) {
        return "null".equals(name);
    }

    private static boolean isWideningPrimitiveConversion(String type1, String type2) {
        return PRIMITIVE_TYPE_WIDENING_MAP.containsKey(type1) && PRIMITIVE_TYPE_WIDENING_MAP.get(type1).contains(type2);
    }

    private static boolean isNarrowingPrimitiveConversion(String type1, String type2) {
        return PRIMITIVE_TYPE_NARROWING_MAP.containsKey(type1) && PRIMITIVE_TYPE_NARROWING_MAP.get(type1).contains(type2);
    }

    private static List<String> getCommonClassNameList(List<String> argumentTypeClassNameList,
                                                       List<String> methodArgumentClassNameList) {

        int size = Math.min(argumentTypeClassNameList.size(), methodArgumentClassNameList.size());
        List<String> commonClassNameList = new ArrayList<>();

        for (int index = 0; index < size; index++) {
            if (argumentTypeClassNameList.get(index).equals(methodArgumentClassNameList.get(index))) {
                commonClassNameList.add(argumentTypeClassNameList.get(index));
            }
        }

        return commonClassNameList;
    }

    private static boolean matchObjectArrayDimensionForArgument(String argumentTypeClassName,
                                                                String methodArgumentTypeClassName) {

        boolean isArgumentTypeArray = argumentTypeClassName.endsWith("[]");
        int argumentTypeArrayDimension = StringUtils.countMatches(argumentTypeClassName, "[]");

        boolean isMethodArgumentTypeArray = methodArgumentTypeClassName.endsWith("[]");
        int methodArgumentTypeArrayDimension = StringUtils.countMatches(methodArgumentTypeClassName, "[]");

        return isMethodArgumentTypeArray && isArgumentTypeArray
                && methodArgumentTypeClassName.startsWith("java.lang.Object")
                && methodArgumentTypeArrayDimension + 1 == argumentTypeArrayDimension;
    }

    private static boolean isArrayDimensionMismatch(String argumentTypeClassName, String methodArgumentTypeClassName) {
        boolean isArgumentTypeArray = argumentTypeClassName.endsWith("[]");
        int argumentTypeArrayDimension = StringUtils.countMatches(argumentTypeClassName, "[]");

        boolean isMethodArgumentTypeArray = methodArgumentTypeClassName.endsWith("[]");
        int methodArgumentTypeArrayDimension = StringUtils.countMatches(methodArgumentTypeClassName, "[]");

        return (isArgumentTypeArray && !isMethodArgumentTypeArray)
                || (!isArgumentTypeArray && isMethodArgumentTypeArray)
                || argumentTypeArrayDimension != methodArgumentTypeArrayDimension;
    }

    private static boolean isVarArgsMatch(String methodArgumentTypeClassName,
                                          List<String> varArgsTypeClassNameList,
                                          Object[] jarVertexIds,
                                          TinkerGraph tinkerGraph) {
        String typeClassName = methodArgumentTypeClassName.replaceAll("\\[]$", "");

        if (varArgsTypeClassNameList.stream().anyMatch(name -> isArrayDimensionMismatch(name, typeClassName))) {
            return false;
        }

        if (varArgsTypeClassNameList.stream().allMatch(name -> name.equals(typeClassName))) {
            return true;
        }

        String methodArgumentTypeName = typeClassName.replaceAll("\\[]", "");

        varArgsTypeClassNameList = varArgsTypeClassNameList.stream().distinct().collect(Collectors.toList());

        return varArgsTypeClassNameList.stream().allMatch(varArgTypeName -> {
            varArgTypeName = varArgTypeName.replaceAll("\\[]", "");

            if (InferenceUtility.isPrimitiveType(varArgTypeName)) {
                varArgTypeName = PRIMITIVE_WRAPPER_CLASS_MAP.get(varArgTypeName);
            }

            Set<String> classNameSet = Collections.singleton(varArgTypeName);
            boolean matched = false;

            while (!classNameSet.isEmpty()) {
                classNameSet = getSuperClassQNameSet(classNameSet, jarVertexIds, tinkerGraph);

                if (classNameSet.contains(methodArgumentTypeName)) {
                    matched = true;
                    break;
                }
            }

            return matched;
        });
    }

}
