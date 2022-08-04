package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.entity.ClassInfo;
import ca.concordia.jaranalyzer.entity.MethodInfo;
import ca.concordia.jaranalyzer.models.Artifact;
import ca.concordia.jaranalyzer.models.OwningClassInfo;
import ca.concordia.jaranalyzer.models.typeInfo.*;
import ca.concordia.jaranalyzer.service.ClassInfoService;
import ca.concordia.jaranalyzer.service.JarInfoService;
import ca.concordia.jaranalyzer.service.MethodInfoService;
import ca.concordia.jaranalyzer.util.EntityUtils;
import ca.concordia.jaranalyzer.util.InferenceUtility;
import ca.concordia.jaranalyzer.util.Utility;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
    private static final int PRIMITIVE_OBJECT_DISTANCE = 1;
    private static final int PRIMITIVE_TYPE_WIDENING_DISTANCE = 1;
    private static final int PRIMITIVE_TYPE_NARROWING_DISTANCE = 2;
    private static final int PRIMITIVE_TYPE_WRAPPING_DISTANCE = 1;
    private static final int PRIMITIVE_TYPE_COMPARABLE_DISTANCE = 1;
    private static final int OBJECT_ARRAY_TO_OBJECT_DISTANCE = 1;

    /*Increased the distance of matching the wrapped objects to primitives*/
    private static final double PRIMITIVE_TYPE_UNWRAPPING_DISTANCE = 1.5;
    private static final int PRIMITIVE_TYPE_NUMBER_DISTANCE = 1;

    public static final int VARARGS_DISTANCE = 10001;

    private static Map<String, List<String>> PRIMITIVE_TYPE_WIDENING_MAP = new HashMap<>();
    private static Map<String, List<String>> PRIMITIVE_TYPE_NARROWING_MAP = new HashMap<>();
    private static Map<String, String> PRIMITIVE_UN_WRAPPER_CLASS_MAP = new HashMap<>();

    private static final List<String> PRIMITIVE_NUMERIC_TYPE_LIST =
            new ArrayList<>(Arrays.asList("byte", "short", "int", "long", "float", "double"));

    static Map<String, String> PRIMITIVE_WRAPPER_CLASS_MAP = new HashMap<>();

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
                                                  TypeInfo invokerTypeInfo,
                                                  boolean isSuperInvoker,
                                                  List<Integer> jarIdList,
                                                  ClassInfoService classInfoService) {
        if (!methodInfoList.isEmpty() && Objects.nonNull(invokerTypeInfo)) {
            String invokerClassName = invokerTypeInfo.getQualifiedClassName();
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

                String type = isSuperInvoker ? "SUPER_CLASS" : null;

                Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

                int distance = 0;

                while (!classNameSet.isEmpty()) {
                    classNameSet = classInfoService.getSuperClassQNameSet(classNameSet, jarIdList, type);

                    distance++;

                    type = null;

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

    static boolean convertFunctionalTypeInfo(List<TypeInfo> argumentTypeInfoList,
                                             List<TypeInfo> methodArgumentTypeInfoList,
                                             MethodInfo methodInfo,
                                             List<Integer> jarIdList,
                                             MethodInfoService methodInfoService,
                                             ClassInfoService classInfoService) {

        for (int index = 0; index < argumentTypeInfoList.size(); index++) {
            TypeInfo argumentTypeInfo = argumentTypeInfoList.get(index);

            if (Objects.nonNull(argumentTypeInfo) && argumentTypeInfo.isFunctionTypeInfo()) {
                TypeInfo methodArgumentTypeInfo = methodArgumentTypeInfoList.get(index);

                FunctionTypeInfo functionTypeInfo = (FunctionTypeInfo) argumentTypeInfo;
                List<FunctionTypeInfo.FunctionDefinition> functionDefinitionList = functionTypeInfo.getFunctionDefinitionList();

                List<MethodInfo> abstractMethodInfoList =
                        getAbstractMethodInfoListForFunctionalInterface(methodArgumentTypeInfo.getQualifiedClassName(),
                                jarIdList, methodInfoService, classInfoService);
                ;

                if (abstractMethodInfoList.size() != 1) {
                    return false;
                }

                MethodInfo abstractMethodInfo = abstractMethodInfoList.get(0);

                if (functionTypeInfo.isInnerClassConstructor()) {
                    for (FunctionTypeInfo.FunctionDefinition functionDefinition : functionDefinitionList) {
                        if (functionDefinition.getArgumentTypeInfoList().size() > abstractMethodInfo.getArgumentTypeInfoList().size()) {
                            int numberOfArgumentDifference = functionDefinition.getArgumentTypeInfoList().size()
                                    - abstractMethodInfo.getArgumentTypeInfoList().size();

                            functionDefinition.setArgumentTypeInfoList(
                                    functionDefinition.getArgumentTypeInfoList()
                                            .subList(numberOfArgumentDifference, functionDefinition.getArgumentTypeInfoList().size()));
                        }
                    }

                }

                boolean matches = false;

                for (FunctionTypeInfo.FunctionDefinition functionDefinition : functionDefinitionList) {
                    boolean isFunctionDefinitionMatches =
                            (functionDefinition.getArgumentTypeInfoList().size() == abstractMethodInfo.getArgumentTypeInfoList().size())
                                    || (functionDefinition.getArgumentTypeInfoList().size() > 0
                                    && functionDefinition.getArgumentTypeInfoList().get(functionDefinition.getArgumentTypeInfoList().size() - 1).isVarargTypeInfo());

                    if (isFunctionDefinitionMatches) {
                        populateFormalTypeParameterForFunctionalInterfaceArgument(abstractMethodInfo, functionDefinition,
                                methodInfo.getArgumentTypeInfoList().get(index));
                        argumentTypeInfoList.set(index, methodArgumentTypeInfo);
                        matches = true;
                        break;
                    }
                }

                if (!matches) {
                    return false;
                }
            }
        }

        return true;
    }

    static boolean matchMethodArguments(List<TypeInfo> argumentTypeInfoList,
                                        List<TypeInfo> methodArgumentTypeInfoList,
                                        List<Integer> jarIdList,
                                        MethodInfoService methodInfoService,
                                        ClassInfoService classInfoService,
                                        MethodInfo methodInfo) {

        boolean isSuccess = convertFunctionalTypeInfo(argumentTypeInfoList, methodArgumentTypeInfoList, methodInfo,
                jarIdList, methodInfoService, classInfoService);

        if (!isSuccess) {
            return false;
        }

        List<TypeInfo> commonTypeInfoList = getCommonTypeInfoList(argumentTypeInfoList, methodArgumentTypeInfoList);

        for (TypeInfo commonTypeInfo : commonTypeInfoList) {
            Predicate<TypeInfo> removalCondition = a -> Objects.nonNull(a)
                    && a.getQualifiedClassName().equals(commonTypeInfo.getQualifiedClassName());

            Utility.removeSingleElementFromCollection(argumentTypeInfoList, removalCondition);
            Utility.removeSingleElementFromCollection(methodArgumentTypeInfoList, removalCondition);
        }

        if (argumentTypeInfoList.isEmpty() && methodArgumentTypeInfoList.isEmpty()) {
            return true;
        }

        //varargs can be matched with first arguments.
        if (methodArgumentTypeInfoList.isEmpty()) {
            return false;
        }

        List<TypeInfo> matchedMethodArgumentTypeInfoList = new ArrayList<>();

        for (int index = 0; index < argumentTypeInfoList.size(); index++) {
            TypeInfo argumentTypeInfo = argumentTypeInfoList.get(index);
            TypeInfo methodArgumentTypeInfo = methodArgumentTypeInfoList.get(index);

            if (Objects.nonNull(argumentTypeInfo) && Objects.nonNull(methodArgumentTypeInfo)) {
                String argumentTypeClassName = argumentTypeInfo.getQualifiedClassName();
                String methodArgumentTypeClassName = methodArgumentTypeInfo.getQualifiedClassName();

                if (InferenceUtility.isPrimitiveType(argumentTypeClassName) && InferenceUtility.isPrimitiveType(methodArgumentTypeClassName)) {
                    if (isWideningPrimitiveConversion(argumentTypeClassName, methodArgumentTypeClassName)) {
                        methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance()
                                + PRIMITIVE_TYPE_WIDENING_DISTANCE);
                        matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);

                    } else if (isNarrowingPrimitiveConversion(argumentTypeClassName, methodArgumentTypeClassName)) {
                        methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance()
                                + PRIMITIVE_TYPE_NARROWING_DISTANCE);
                        matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);

                    } else {
                        return false;
                    }
                }

                if (isNullType(argumentTypeClassName) && !InferenceUtility.isPrimitiveType(methodArgumentTypeClassName)) {
                    matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);

                    continue;
                }

                // this check has to be done before `isArrayDimensionMismatch` checking
                if (methodArgumentTypeClassName.endsWith("[]") && methodInfo.isVarargs()
                        && isVarArgsMatch(methodArgumentTypeClassName,
                        argumentTypeInfoList.subList(index, argumentTypeInfoList.size()), jarIdList, classInfoService)) {

                    methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + VARARGS_DISTANCE);
                    matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);
                    break;
                }

                if (!methodArgumentTypeClassName.equals("java.lang.Object")
                        && matchObjectArrayDimensionForArgument(argumentTypeClassName, methodArgumentTypeClassName)) {
                    matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);
                    continue;
                }

                if (!methodArgumentTypeClassName.equals("java.lang.Object")
                        && isArrayDimensionMismatch(argumentTypeClassName, methodArgumentTypeClassName)) {
                    return false;
                }

                if (InferenceUtility.isPrimitiveType(argumentTypeClassName)) {
                    if (PRIMITIVE_WRAPPER_CLASS_MAP.get(argumentTypeClassName).equals(methodArgumentTypeClassName)) {
                        methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + PRIMITIVE_TYPE_WRAPPING_DISTANCE);
                        matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);

                    } else if ("java.lang.Comparable".equals(methodArgumentTypeClassName)) {
                        methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + PRIMITIVE_TYPE_COMPARABLE_DISTANCE);
                        matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);
                    }
                }

                if (InferenceUtility.isPrimitiveType(methodArgumentTypeClassName)
                        && PRIMITIVE_UN_WRAPPER_CLASS_MAP.containsKey(argumentTypeClassName)
                        && PRIMITIVE_UN_WRAPPER_CLASS_MAP.get(argumentTypeClassName).equals(methodArgumentTypeClassName)) {

                    methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + PRIMITIVE_TYPE_UNWRAPPING_DISTANCE);
                    matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);
                    continue;
                }

                /*
                 * Trimmed down array dimension before searching for super classes.
                 */
                boolean isArgumentArray = argumentTypeClassName.contains("[]");
                argumentTypeClassName = argumentTypeClassName.replaceAll("\\[]", "");
                methodArgumentTypeClassName = methodArgumentTypeClassName.replaceAll("\\[]", "");

                if (methodArgumentTypeClassName.contains("$")) {
                    methodArgumentTypeClassName = methodArgumentTypeClassName.replace("$", ".");

                    if (methodArgumentTypeClassName.equals(argumentTypeClassName)) {
                        matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);
                        continue;
                    }
                }

                if (methodArgumentTypeClassName.equals("java.lang.Object")) {
                    if (InferenceUtility.isPrimitiveType(argumentTypeClassName)) {
                        methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + PRIMITIVE_OBJECT_DISTANCE);
                        matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);
                        continue;
                    } else if (argumentTypeClassName.equals("java.lang.Object")) {
                        if (isArgumentArray) {
                            methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + OBJECT_ARRAY_TO_OBJECT_DISTANCE);
                        }

                        matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);
                        continue;
                    }
                }

                if (methodArgumentTypeClassName.equals("java.lang.Number")
                        && PRIMITIVE_NUMERIC_TYPE_LIST.contains(argumentTypeClassName)) {
                    methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + PRIMITIVE_TYPE_NUMBER_DISTANCE);
                    matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);
                    continue;
                }

                Set<String> classNameSet = new HashSet<>();
                classNameSet.add(argumentTypeClassName);

                int distance = 0;

                while (!classNameSet.isEmpty()) {
                    classNameSet = classInfoService.getSuperClassQNameSet(classNameSet, jarIdList, null);

                    distance++;

                    if (classNameSet.contains(methodArgumentTypeClassName)) {
                        if (methodArgumentTypeClassName.equals("java.lang.Object")) {
                            methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + MAX_SUPER_CLASS_DISTANCE);
                        } else {
                            methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + distance);
                        }

                        matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfo);
                        break;
                    }
                }
            }
        }

        /*
         * For varargs method number of arguments can be one less. So considered that as matching.
         */
        if (methodInfo.isVarargs()
                && argumentTypeInfoList.size() == methodArgumentTypeInfoList.size() - 1) {
            methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + VARARGS_DISTANCE);
            matchedMethodArgumentTypeInfoList.add(methodArgumentTypeInfoList.get(methodArgumentTypeInfoList.size() - 1));
        }

        List<String> matchedMethodArgumentClassNameList = matchedMethodArgumentTypeInfoList.stream()
                .map(TypeInfo::getQualifiedClassName)
                .collect(Collectors.toList());

        for (String matchedMethodArgumentClassName: matchedMethodArgumentClassNameList) {
            Utility.removeSingleElementFromCollection(methodArgumentTypeInfoList,
                    a -> matchedMethodArgumentClassName.equals(a.getQualifiedClassName()));
        }

        return methodArgumentTypeInfoList.isEmpty();
    }

    static List<ClassInfo> resolveQClassInfoForClass(String typeClassName,
                                                     List<Integer> jarIdList,
                                                     Set<String> importedClassQNameSet,
                                                     List<String> packageNameList,
                                                     ClassInfoService classInfoService,
                                                     OwningClassInfo owningClassInfo) {
        Set<String> importedClassQNameSetForSelection = new LinkedHashSet<>(importedClassQNameSet);

        if (Objects.nonNull(typeClassName) && !InferenceUtility.isPrimitiveType(typeClassName)
                && StringUtils.countMatches(typeClassName, ".") <= 1) {

            String postProcessedOwningClassQualifiedName = Objects.nonNull(owningClassInfo)
                    && Objects.nonNull(owningClassInfo.getOuterMostClassName())
                    ? owningClassInfo.getOuterMostClassName().replace("$", ".")
                    : null;

            String postProcessedTypeClassName = typeClassName.replace(".", "$")
                    .replaceAll("\\[]", "");

            if (Objects.nonNull(owningClassInfo) && !owningClassInfo.getAvailableQualifiedClassNameSet().isEmpty()) {
                importedClassQNameSetForSelection.addAll(owningClassInfo.getAvailableQualifiedClassNameSet());
            }

            List<ClassInfo> qualifiedClassInfoList = classInfoService.getClassInfoList(jarIdList, postProcessedTypeClassName);

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
                        classNameCheck = classInfo.getName().endsWith(postProcessedTypeClassName);
                    } else {
                        classNameCheck = classInfo.getName().endsWith("$" + postProcessedTypeClassName);
                    }

                    if (classNameCheck) {
                        String qualifiedClassName = classInfo.getQualifiedName();
                        String qualifiedOuterClassName = classInfo.getQualifiedName()
                                .substring(0, classInfo.getQualifiedName().lastIndexOf("."));

                        return importedClassQNameSetForSelection.contains(qualifiedClassName)
                                || importedClassQNameSetForSelection.contains(qualifiedOuterClassName)
                                || packageNameList.contains(classInfo.getPackageName());

                    } else {
                        return false;
                    }
                } else {
                    return classInfo.getName().equals(postProcessedTypeClassName)
                            && (importedClassQNameSetForSelection.contains(classInfo.getQualifiedName())
                            || packageNameList.contains(classInfo.getPackageName()));
                }
            }).collect(Collectors.toList());

            return qualifiedClassInfoList;
        }

        return Collections.emptyList();
    }

    static String resolveQNameForClass(String typeClassName,
                                       OwningClassInfo owningClassInfo,
                                       List<Integer> jarIdList,
                                       Set<String> importedClassQNameSet,
                                       List<String> packageNameList,
                                       ClassInfoService classInfoService) {
        if (Objects.nonNull(typeClassName)) {
            typeClassName = typeClassName.replace("$", ".");
        }

        int numberOfArrayDimensions = StringUtils.countMatches(typeClassName, "[]");

        List<ClassInfo> qualifiedClassInfoList = resolveQClassInfoForClass(typeClassName, jarIdList,
                importedClassQNameSet, packageNameList, classInfoService, owningClassInfo);

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

                    Map<String, ClassInfo> qualifiedClassInfoMap = qualifiedClassInfoList.stream()
                            .collect(Collectors.toMap(ClassInfo::getQualifiedName, c -> c, (p, q) -> p));

                    List<ClassInfo> orderedQualifiedClassInfoList = new ArrayList<>();
                    for (String qClassName: qClassNameSet) {
                        if (qualifiedClassInfoMap.containsKey(qClassName)) {
                            orderedQualifiedClassInfoList.add(qualifiedClassInfoMap.get(qClassName));
                        }
                    }

                    return orderedQualifiedClassInfoList;
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
                && ((!methodInfoSet.stream().allMatch(MethodInfo::isAbstract)) || methodInfoSet.stream().allMatch(MethodInfo::isAbstract))
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
                                                       Integer numberOfParameters,
                                                       List<Integer> jarIdList,
                                                       Set<String> classQNameSet,
                                                       ClassInfoService classInfoService,
                                                       MethodInfoService methodInfoService) {
        List<Integer> classInfoIdList = classInfoService.getClassInfoIdList(jarIdList, classQNameSet);

        if (classInfoIdList.isEmpty()) {
            return new ArrayList<>();
        }

        return getQualifiedMethodInfoList(methodName, numberOfParameters, classInfoIdList, methodInfoService);
    }

    static List<MethodInfo> getQualifiedMethodInfoList(String methodName,
                                                       Integer numberOfParameters,
                                                       List<Integer> classInfoIdList,
                                                       MethodInfoService methodInfoService) {
        String outerClassPrefix = StringUtils.countMatches(methodName, ".") == 1
                ? methodName.substring(0, methodName.indexOf("."))
                : null;

        methodName = StringUtils.countMatches(methodName, ".") == 1
                ? methodName.substring(methodName.indexOf(".") + 1)
                : methodName;

        List<MethodInfo> methodInfoList = methodInfoService.getMethodInfoList(classInfoIdList, methodName);

        return methodInfoList.stream()
                .filter(methodInfo -> filtrationBasedOnCriteria(numberOfParameters, outerClassPrefix, methodInfo))
                .collect(Collectors.toList());
    }

    static List<MethodInfo> getQualifiedMethodInfoListForInnerClass(String methodName,
                                                                    Integer numberOfParameters,
                                                                    List<Integer> jarIdList,
                                                                    Set<String> classQNameSet,
                                                                    MethodInfoService methodInfoService) {

        String outerClassPrefix = StringUtils.countMatches(methodName, ".") == 1
                ? methodName.substring(0, methodName.indexOf("."))
                : null;

        methodName = StringUtils.countMatches(methodName, ".") == 1
                ? methodName.substring(methodName.indexOf(".") + 1)
                : methodName;

        List<MethodInfo> methodInfoList = methodInfoService.getInnerClassMethodInfoList(classQNameSet, jarIdList, methodName);

        return methodInfoList.stream()
                .filter(methodInfo -> filtrationBasedOnCriteria(numberOfParameters, outerClassPrefix, methodInfo))
                .collect(Collectors.toList());
    }


    static List<MethodInfo> getQualifiedMethodInfoListForPackageImport(String methodName,
                                                                       Integer numberOfParameters,
                                                                       List<String> packageNameList,
                                                                       Set<String> importedClassQNameSet,
                                                                       List<Integer> jarIdList,
                                                                       ClassInfoService classInfoService,
                                                                       MethodInfoService methodInfoService) {
        Set<Integer> clientInfoIdSet = new LinkedHashSet<>();

        clientInfoIdSet.addAll(classInfoService.getClassInfoIdList(jarIdList, importedClassQNameSet));
        clientInfoIdSet.addAll(classInfoService.getClassInfoIdList(jarIdList, packageNameList));

        return getQualifiedMethodInfoList(methodName, numberOfParameters, new ArrayList<>(clientInfoIdSet), methodInfoService);
    }

    static List<MethodInfo> getAbstractMethodInfoListForFunctionalInterface(String qualifiedClassName,
                                                                            List<Integer> jarIdList,
                                                                            MethodInfoService methodInfoService,
                                                                            ClassInfoService classInfoService) {

        Set<String> classQNameSet = Collections.singleton(qualifiedClassName);

        while (!classQNameSet.isEmpty()) {
            List<MethodInfo> methodInfoList = methodInfoService.getAbstractMethodInfoList(jarIdList, classQNameSet);;

            if (!methodInfoList.isEmpty()) {
                return methodInfoList;
            }

            classQNameSet = classInfoService.getSuperClassQNameSet(classQNameSet, jarIdList, "INTERFACE");
        }

        return Collections.emptyList();
    }

    static Map<String, List<String>> getSuperClassQNameMapPerClass(Set<String> classQNameSet,
                                                                   List<Integer> jarIdList,
                                                                   ClassInfoService classInfoService) {

        Map<String, List<String>> superClassNameMap = new HashMap<>();

        Map<String, Integer> classIdMap = classInfoService.getClientIdMap(jarIdList, classQNameSet);

        for (String classQName : classQNameSet) {
            if (classIdMap.containsKey(classQName) && Objects.nonNull(classIdMap.get(classQName))) {
                Integer classInfoId = classIdMap.get(classQName);
                List<String> superClassNameList = classInfoService.getSuperClassQNameList(classInfoId, null);

                superClassNameMap.put(classQName, superClassNameList);
            }
        }

        return superClassNameMap;
    }

    static Set<String> getSuperClassQNameSet(Set<String> classQNameSet,
                                             List<Integer> jarIdList,
                                             ClassInfoService classInfoService) {

        if (classQNameSet.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> superClassSet = new LinkedHashSet<>();
        Map<String, List<String>> superClassQNameMap = getSuperClassQNameMapPerClass(classQNameSet, jarIdList,
                classInfoService);

        for (String classQName : classQNameSet) {
            if (superClassQNameMap.containsKey(classQName)) {
                superClassSet.addAll(superClassQNameMap.get(classQName));
            }
        }

        return superClassSet;
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

    static void modifyMethodInfoForArray(List<MethodInfo> methodInfoList, TypeInfo invokerTypeInfo) {
        if (Objects.nonNull(invokerTypeInfo)) {
            if (invokerTypeInfo.isArrayTypeInfo()) {
                ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) invokerTypeInfo;

                int dimension = arrayTypeInfo.getDimension();
                TypeInfo elementTypeInfo = arrayTypeInfo.getElementTypeInfo();

                String typeName = elementTypeInfo.isPrimitiveTypeInfo()
                        ? getTypeDescriptorForPrimitive(elementTypeInfo.getQualifiedClassName())
                        : "L" + elementTypeInfo.getQualifiedClassName().replaceAll("\\.", "/") + ";";

                Type returnType = Type.getType(StringUtils.repeat("[", dimension) + typeName);

                methodInfoList.forEach(m -> {
                    m.setReturnType(returnType);
                    m.setReturnTypeInfo(EntityUtils.getTypeInfo(m.getReturnType()));
                    m.setThrownInternalClassNames(Collections.emptyList());
                });
            } else if (invokerTypeInfo.isVarargTypeInfo()) {
                VarargTypeInfo varargTypeInfo = (VarargTypeInfo) invokerTypeInfo;

                int dimension = varargTypeInfo.getDimension();
                TypeInfo elementTypeInfo = varargTypeInfo.getElementTypeInfo();

                String typeName = elementTypeInfo.isPrimitiveTypeInfo()
                        ? getTypeDescriptorForPrimitive(elementTypeInfo.getQualifiedClassName())
                        : "L" + elementTypeInfo.getQualifiedClassName().replaceAll("\\.", "/") + ";";

                Type returnType = Type.getType(StringUtils.repeat("[", dimension) + typeName);

                methodInfoList.forEach(m -> {
                    m.setReturnType(returnType);
                    m.setReturnTypeInfo(EntityUtils.getTypeInfo(m.getReturnType()));
                    m.setThrownInternalClassNames(Collections.emptyList());
                });
            }
        }
    }

    static void setInternalDependencyProperty(List<MethodInfo> methodInfoList, List<Integer> internalDependencyJarIdList) {

        for (MethodInfo methodInfo: methodInfoList) {
            ClassInfo classInfo = methodInfo.getClassInfo();

            if (Objects.nonNull(classInfo)
                    && Objects.nonNull(internalDependencyJarIdList)
                    && internalDependencyJarIdList.contains(classInfo.getJarId())) {
                classInfo.setInternalDependency(true);
            }
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
                                                                 Integer numberOfParameters,
                                                                 List<Integer> jarIdList,
                                                                 ClassInfoService classInfoService) {

        Set<String> eligibleFirstArgumentClassNameSet = new LinkedHashSet<>();

        if (Objects.nonNull(firstArgumentQualifiedClassName)) {
            eligibleFirstArgumentClassNameSet.add(firstArgumentQualifiedClassName);

            eligibleFirstArgumentClassNameSet.addAll(
                    getAllSuperClassSet(Collections.singleton(firstArgumentQualifiedClassName), jarIdList, classInfoService));

            eligibleFirstArgumentClassNameSet.remove("java.lang.Object");
        }

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

        methodInfoList.removeIf(m -> m.isInnerClassConstructor() && m.getArgumentTypeInfoList().size() != numberOfParameters);
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

    public static List<MethodInfo> filterBasedOnClassInstantiation(List<MethodInfo> methodInfoList) {
        return methodInfoList.stream()
                .filter(MethodInfo::isConstructor)
                .collect(Collectors.toList());
    }

    public static List<MethodInfo> filterMethodInfoListBasedOnOwningClass(List<MethodInfo> methodInfoList) {
        if (methodInfoList.size() > 1 && methodInfoList.stream().noneMatch(MethodInfo::isOwningClassAttribute)) {
            methodInfoList = methodInfoList.stream()
                    .filter(m -> !m.isPrivate())
                    .collect(Collectors.toList());
        }
        return methodInfoList;
    }

    public static List<MethodInfo> prioritizeMethodInfoListBasedOnArguments(List<MethodInfo> methodInfoList) {
        if (methodInfoList.size() > 1 && !methodInfoList.stream().allMatch(m -> m.getArgumentTypes().length == 0)) {
            double minArgumentMatchingDistance = getMinimumArgumentMatchingDistance(methodInfoList);

            methodInfoList = methodInfoList.stream()
                    .filter(m -> m.getArgumentMatchingDistance() == minArgumentMatchingDistance)
                    .collect(Collectors.toList());
        }
        return methodInfoList;
    }

    public static List<MethodInfo> filteredNonAbstractMethod(List<MethodInfo> methodInfoList) {
        if (methodInfoList.size() > 1 && methodInfoList.stream().anyMatch(m -> !m.isAbstract())) {
            return methodInfoList.stream().filter(m -> !m.isAbstract()).collect(Collectors.toList());
        } else {
            return methodInfoList;
        }
    }

    static OwningClassInfo getOwningClassInfoInternal(Set<Artifact> dependentArtifactSet,
                                              String javaVersion,
                                              List<String> enclosingQualifiedClassNameList,
                                              List<String> nonEnclosingQualifiedClassNameList,
                                              JarInfoService jarInfoService,
                                              ClassInfoService classInfoService) {

        if (Objects.isNull(enclosingQualifiedClassNameList) || enclosingQualifiedClassNameList.size() == 0) {
            return null;
        }

        List<Integer> jarIdList = jarInfoService.getJarIdList(dependentArtifactSet, javaVersion, null);
        List<Set<String>> qClassNameSetInHierarchy = new ArrayList<>();

        String outerMostClassName = enclosingQualifiedClassNameList.get(enclosingQualifiedClassNameList.size() - 1);

        Set<String> innerClassQualifiedNameSet =
                classInfoService.getInnerClassQualifiedNameSet(Collections.singleton(outerMostClassName), jarIdList);

        /*
         * Here conversion is needed. Because inner class inside method can have position
         * number ahead of class name. But enclosingQualifiedClassNameList can not provide such information.
         * So we are fetching inner classes using outer-most class and then replacing with appropriate name
         * of inner class.
         */
        convertAccessibleClassNameList(enclosingQualifiedClassNameList, innerClassQualifiedNameSet);
        convertAccessibleClassNameList(nonEnclosingQualifiedClassNameList, innerClassQualifiedNameSet);


        List<String> accessibleQClassNameList = new ArrayList<>(enclosingQualifiedClassNameList);

        if (CollectionUtils.isNotEmpty(nonEnclosingQualifiedClassNameList)) {
            accessibleQClassNameList.addAll(nonEnclosingQualifiedClassNameList);
        }

        List<String> classQNameDeclarationOrderList = new ArrayList<>(enclosingQualifiedClassNameList);

        qClassNameSetInHierarchy.add(new LinkedHashSet<>(accessibleQClassNameList));

        Set<String> classQNameSet = new LinkedHashSet<>(enclosingQualifiedClassNameList);

        while (!classQNameSet.isEmpty()) {
            Map<String, List<String>> superClassQNameMap =
                    getSuperClassQNameMapPerClass(classQNameSet, jarIdList, classInfoService);

            insertSuperClassQNamePreservingDeclarationOrder(superClassQNameMap, classQNameSet,
                    classQNameDeclarationOrderList);

            classQNameSet = getOrderedSuperClassQNameSet(superClassQNameMap, classQNameSet);

            if (!classQNameSet.isEmpty()) {
                /*
                 * Class can be used without import if inner class of super classes. In order to find those classes
                 * we need to add these inner class in hierarchy.
                 */
                qClassNameSetInHierarchy.add(getCombinedClassAndInnerClassQualifiedNameSet(classQNameSet,
                        classInfoService.getInnerClassQualifiedNameSet(classQNameSet, jarIdList)));
            }
        }

        return new OwningClassInfo(enclosingQualifiedClassNameList, qClassNameSetInHierarchy,
                classQNameDeclarationOrderList);
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

    private static void populateFormalTypeParameterForFunctionalInterfaceArgument(MethodInfo abstractMethodInfo,
                                                                                  FunctionTypeInfo.FunctionDefinition functionDefinition,
                                                                                  TypeInfo functionalInterfaceTypeInfo) {
        TypeInfo classTypeInfo = abstractMethodInfo.getClassInfo().getTypeInfo();

        if (classTypeInfo.isParameterizedTypeInfo()) {
            ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) classTypeInfo;

            Map<String, FormalTypeParameterInfo> formalTypeParameterInfoMap = new LinkedHashMap<>();
            for (TypeInfo typeArgument: parameterizedTypeInfo.getTypeArgumentList()) {
                if (typeArgument.isFormalTypeParameterInfo()) {
                    FormalTypeParameterInfo formalTypeParameterInfo = (FormalTypeParameterInfo) typeArgument;
                    formalTypeParameterInfoMap.put(formalTypeParameterInfo.getTypeParameter(), formalTypeParameterInfo);
                }
            }

            for (int argumentIndex = 0; argumentIndex < abstractMethodInfo.getArgumentTypeInfoList().size(); argumentIndex++) {
                TypeInfo argument = abstractMethodInfo.getArgumentTypeInfoList().get(argumentIndex);

                if (argument.isFormalTypeParameterInfo()) {
                    FormalTypeParameterInfo argumentFormalTypeParameterInfo = (FormalTypeParameterInfo) argument;

                    if (formalTypeParameterInfoMap.containsKey(argumentFormalTypeParameterInfo.getTypeParameter())) {
                        TypeInfo baseTypeInfo = functionDefinition.getArgumentTypeInfoList().get(argumentIndex);

                        FormalTypeParameterInfo formalTypeParameterInfo
                                = formalTypeParameterInfoMap.get(argumentFormalTypeParameterInfo.getTypeParameter());
                        formalTypeParameterInfo.setBaseTypeInfo(baseTypeInfo);

                        formalTypeParameterInfoMap.put(formalTypeParameterInfo.getTypeParameter(), formalTypeParameterInfo);
                    }
                }
            }

            if (abstractMethodInfo.getReturnTypeInfo().isFormalTypeParameterInfo() && Objects.nonNull(functionDefinition.getReturnTypeInfo())) {
                FormalTypeParameterInfo returnFormalTypeParameterInfo = (FormalTypeParameterInfo) abstractMethodInfo.getReturnTypeInfo();

                if (formalTypeParameterInfoMap.containsKey(returnFormalTypeParameterInfo.getTypeParameter())) {
                    TypeInfo baseTypeInfo = functionDefinition.getReturnTypeInfo();

                    FormalTypeParameterInfo formalTypeParameterInfo = formalTypeParameterInfoMap.get(returnFormalTypeParameterInfo.getTypeParameter());
                    formalTypeParameterInfo.setBaseTypeInfo(baseTypeInfo);

                    formalTypeParameterInfoMap.put(formalTypeParameterInfo.getTypeParameter(), formalTypeParameterInfo);
                }
            }

            if (functionalInterfaceTypeInfo.isParameterizedTypeInfo()) {
                ParameterizedTypeInfo funcParameterizedTypeInfo = (ParameterizedTypeInfo) functionalInterfaceTypeInfo;
                funcParameterizedTypeInfo.getTypeArgumentList().clear();
                funcParameterizedTypeInfo.setTypeArgumentList(new ArrayList<>(formalTypeParameterInfoMap.values()));
            }
        }
    }

    private static Set<String> getAllSuperClassSet(Set<String> classSet, List<Integer> jarIdList, ClassInfoService classInfoService) {
        Set<String> allSuperClassQualifiedNameSet = new LinkedHashSet<>();

        while (!classSet.isEmpty()) {
            Set<String> superClassQualifiedNameSet = getSuperClassQNameSet(classSet, jarIdList, classInfoService);
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

    private static void convertAccessibleClassNameList(List<String> accessibleClassNameList,
                                                       Set<String> innerClassQualifiedNameSet) {

        if (Objects.isNull(accessibleClassNameList) || accessibleClassNameList.isEmpty()) {
            return;
        }

        for (String innerClassQualifiedName : innerClassQualifiedNameSet) {
            if (innerClassQualifiedName.matches(".*\\.[0-9]+.*")) {
                String innerClassQualifiedNameWithoutPosition = innerClassQualifiedName.replaceAll("\\.[0-9]+", "\\.");

                if (accessibleClassNameList.contains(innerClassQualifiedNameWithoutPosition)) {
                    int index = accessibleClassNameList.indexOf(innerClassQualifiedNameWithoutPosition);
                    accessibleClassNameList.set(index, innerClassQualifiedName);
                }
            }
        }

    }

    private static boolean filtrationBasedOnCriteria(Integer numberOfParameters,
                                                     String outerClassPrefix,
                                                     MethodInfo methodInfo) {
        boolean outerClassPrefixMatchingForInnerClassConstructor = true;

        if (Objects.nonNull(outerClassPrefix) && methodInfo.isInnerClassConstructor()) {
            outerClassPrefixMatchingForInnerClassConstructor =
                    methodInfo.getInternalClassConstructorPrefix().endsWith(outerClassPrefix + "$");
        }

        return outerClassPrefixMatchingForInnerClassConstructor
                && !methodInfo.isBridgeMethod()
                && checkMethodArgumentLength(numberOfParameters, methodInfo);
    }

    private static boolean checkMethodArgumentLength(Integer numberOfParameters, MethodInfo methodInfo) {
        //for finding all methodReference instances we want to circumvent numberOfParameters check
        if (Objects.isNull(numberOfParameters)) {
            return true;
        }

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

    //TODO: check the impact of notnull check
    private static List<TypeInfo> getCommonTypeInfoList(List<TypeInfo> argumentTypeInfoList,
                                                        List<TypeInfo> methodArgumentTypeInfoList) {

        int size = Math.min(argumentTypeInfoList.size(), methodArgumentTypeInfoList.size());
        List<TypeInfo> commonTypeInfoList = new ArrayList<>();

        for (int index = 0; index < size; index++) {
            TypeInfo argumentTypeInfo = argumentTypeInfoList.get(index);

            if (Objects.nonNull(argumentTypeInfo)
                    && Objects.nonNull(methodArgumentTypeInfoList.get(index))
                    && argumentTypeInfo.getQualifiedClassName().equals(methodArgumentTypeInfoList.get(index).getQualifiedClassName())) {
                commonTypeInfoList.add(argumentTypeInfo);
            }
        }

        return commonTypeInfoList;
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
                                          List<TypeInfo> varArgsTypeTypeInfoList,
                                          List<Integer> jarIdList,
                                          ClassInfoService classInfoService) {
        String typeClassName = methodArgumentTypeClassName.replaceAll("\\[]$", "");

        if (varArgsTypeTypeInfoList.stream()
                .filter(Objects::nonNull)
                .map(TypeInfo::getQualifiedClassName)
                .anyMatch(name -> isArrayDimensionMismatch(name, typeClassName))) {
            return false;
        }

        if (varArgsTypeTypeInfoList.stream()
                .filter(Objects::nonNull)
                .map(TypeInfo::getQualifiedClassName)
                .allMatch(name -> name.equals(typeClassName))) {
            return true;
        }

        String methodArgumentTypeName = typeClassName.replaceAll("\\[]", "");

        List<String> varArgsTypeClassNameList = varArgsTypeTypeInfoList.stream()
                .filter(Objects::nonNull)
                .map(TypeInfo::getQualifiedClassName).distinct().collect(Collectors.toList());

        return varArgsTypeClassNameList.stream().allMatch(varArgTypeName -> {
            varArgTypeName = varArgTypeName.replaceAll("\\[]", "");

            if (InferenceUtility.isPrimitiveType(varArgTypeName)) {
                varArgTypeName = PRIMITIVE_WRAPPER_CLASS_MAP.get(varArgTypeName);
            }

            Set<String> classNameSet = Collections.singleton(varArgTypeName);
            boolean matched = false;

            while (!classNameSet.isEmpty()) {
                classNameSet = classInfoService.getSuperClassQNameSet(classNameSet, jarIdList, null);

                if (classNameSet.contains(methodArgumentTypeName)) {
                    matched = true;
                    break;
                }
            }

            return matched;
        });
    }

}
