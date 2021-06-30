package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.JarInformation;
import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.ExternalJarExtractionUtility;
import ca.concordia.jaranalyzer.util.Utility;
import io.vavr.Tuple3;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.IO;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;
import static ca.concordia.jaranalyzer.util.Utility.getJarStoragePath;

/**
 * @author Diptopol
 * @since 4/22/2021 9:05 AM
 */
public abstract class TypeInferenceBase {

    private static Logger logger = LoggerFactory.getLogger(TypeInferenceBase.class);

    private static final int MAX_SUPER_CLASS_DISTANCE = 1000;
    private static final int PRIMITIVE_TYPE_WIDENING_NARROWING_DISTANCE = 1;

    private static final List<String> PRIMITIVE_TYPE_LIST =
            new ArrayList<>(Arrays.asList("byte", "short", "int", "long", "float", "double", "char", "boolean"));

    private static Map<String, List<String>> PRIMITIVE_TYPE_WIDENING_MAP = new HashMap<>();
    private static Map<String, List<String>> PRIMITIVE_TYPE_NARROWING_MAP = new HashMap<>();

    private static Map<String, String> PRIMITIVE_WRAPPER_CLASS_MAP = new HashMap<>();
    private static Map<String, String> PRIMITIVE_UN_WRAPPER_CLASS_MAP = new HashMap<>();

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
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put( "java.lang.Byte", "byte");
        PRIMITIVE_UN_WRAPPER_CLASS_MAP.put( "java.lang.Character", "char");
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

    static void createClassStructureGraphForJavaJars(JarAnalyzer jarAnalyzer) {
        String javaJarDirectory = getProperty("java.jar.directory");
        String javaVersion = getProperty("java.version");

        logger.info("Java Jar Directory: {}", javaJarDirectory);
        logger.info("Java Version: {}", javaVersion);

        if (javaJarDirectory != null) {
            List<String> jarFiles = Utility.getFiles(javaJarDirectory, "jar");
            for (String jarLocation : jarFiles) {
                try {
                    Path path = Paths.get(jarLocation);
                    if (Files.exists(path)) {
                        JarFile jarFile = new JarFile(new File(jarLocation));
                        jarAnalyzer.jarToGraph(jarFile, path.getFileName().toString(), "Java", javaVersion);
                    }
                } catch (Exception e) {
                    logger.error("Could not open the JAR", e);
                }
            }
        }
    }


    static void storeClassStructureGraph(TinkerGraph tinkerGraph) {
        logger.info("storing graph");

        tinkerGraph.traversal().io(getJarStoragePath().toString())
                .with(IO.writer, IO.gryo)
                .write().iterate();
    }

    static void loadClassStructureGraph(TinkerGraph tinkerGraph) {
        logger.info("loading graph");

        tinkerGraph.traversal().io(getJarStoragePath().toString())
                .with(IO.reader, IO.gryo)
                .read().iterate();
    }

    static boolean isJarExists(String groupId, String artifactId, String version, TinkerGraph tinkerGraph) {
        return tinkerGraph.traversal().V()
                .has("Kind", "Jar")
                .has("GroupId", groupId)
                .has("ArtifactId", artifactId)
                .has("Version", version)
                .toSet().size() > 0;
    }

    static void loadJar(String groupId, String artifactId, String version, TinkerGraph tinkerGraph, JarAnalyzer jarAnalyzer) {
        if (!isJarExists(groupId, artifactId, version, tinkerGraph)) {
            JarInformation jarInformation =
                    ExternalJarExtractionUtility.getJarInfo(groupId, artifactId, version);

            jarAnalyzer.toGraph(jarInformation);
            storeClassStructureGraph(tinkerGraph);
        }
    }

    static Set<Tuple3<String, String, String>> loadExternalJars(String commitId, String projectName,
                                                                          Repository repository, TinkerGraph tinkerGraph,
                                                                          JarAnalyzer jarAnalyzer) {
        Set<Tuple3<String, String, String>> jarArtifactInfoSet =
                ExternalJarExtractionUtility.getDependenciesFromEffectivePom(commitId, projectName, repository);

        Set<Tuple3<String, String, String>> jarArtifactInfoSetForLoad = jarArtifactInfoSet.stream()
                .filter(jarArtifactInfo -> !isJarExists(jarArtifactInfo._1, jarArtifactInfo._2, jarArtifactInfo._3, tinkerGraph))
                .collect(Collectors.toSet());

        jarArtifactInfoSetForLoad.forEach(jarArtifactInfo -> {
            JarInformation jarInformation =
                    ExternalJarExtractionUtility.getJarInfo(jarArtifactInfo._1, jarArtifactInfo._2, jarArtifactInfo._3);

            jarAnalyzer.toGraph(jarInformation);
        });

        if (jarArtifactInfoSetForLoad.size() > 0) {
            storeClassStructureGraph(tinkerGraph);
        }

        return jarArtifactInfoSet;
    }

    static List<MethodInfo> filterByMethodInvoker(List<MethodInfo> methodInfoList, String callerClassName,
                                                          boolean isSuperOfCallerClass, Object[] jarVertexIds,
                                                            TinkerGraph tinkerGraph) {
        if (!methodInfoList.isEmpty() && Objects.nonNull(callerClassName) && !callerClassName.equals("")) {
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

            List<MethodInfo> filteredListByCallerClassName = new ArrayList<>();

            if (methodInfoClassNameList.contains(callerClassName) && !isSuperOfCallerClass) {
                List<MethodInfo> qualifiedMethodInfoList = methodInfoDeclaringClassNameMap.get(callerClassName);
                qualifiedMethodInfoList.forEach(m -> m.setCallerClassExactMatch(true));

                filteredListByCallerClassName.addAll(qualifiedMethodInfoList);

            } else {
                Set<String> classNameSet = new HashSet<>();
                classNameSet.add(callerClassName);

                String[] allOutGoingEdges = new String[]{"extends", "implements"};
                String[] superClassOutGoingEdgeLabels = isSuperOfCallerClass
                        ? new String[]{"extends"}
                        : allOutGoingEdges;

                Set<MethodInfo> deferredQualifiedMethodInfoSet = new HashSet<>();

                while (!classNameSet.isEmpty()) {
                    classNameSet = tinkerGraph.traversal().V(jarVertexIds)
                            .out("ContainsPkg").out("Contains")
                            .has("Kind", "Class")
                            .has("QName", TextP.within(classNameSet))
                            .out(superClassOutGoingEdgeLabels)
                            .<String>values("Name")
                            .toSet();

                    superClassOutGoingEdgeLabels = allOutGoingEdges;

                    for (String className : methodInfoClassNameList) {
                        if (classNameSet.contains(className)) {
                            filteredListByCallerClassName.addAll(methodInfoDeclaringClassNameMap.get(className));
                        }
                    }

                    if (!filteredListByCallerClassName.isEmpty() && isDeferredMethodList(filteredListByCallerClassName)) {
                        deferredQualifiedMethodInfoSet.addAll(filteredListByCallerClassName);
                        filteredListByCallerClassName.clear();
                    }

                    if (!filteredListByCallerClassName.isEmpty()) {
                        break;
                    }
                }

                if (filteredListByCallerClassName.isEmpty() && !deferredQualifiedMethodInfoSet.isEmpty()) {
                    filteredListByCallerClassName.addAll(deferredQualifiedMethodInfoSet);
                }
            }

            if (filteredListByCallerClassName.size() > 1 && filteredListByCallerClassName.stream().anyMatch(MethodInfo::isCallerClassExactMatch)) {
                return filteredListByCallerClassName.stream().filter(MethodInfo::isCallerClassExactMatch).collect(Collectors.toList());
            }

            return filteredListByCallerClassName;
        } else {
            return methodInfoList;
        }
    }

    static boolean isDeferredMethodList(List<MethodInfo> methodInfoList) {
        return methodInfoList.stream().allMatch(MethodInfo::isAbstract)
                || methodInfoList.stream().allMatch(m -> m.getClassInfo().getQualifiedName().equals("java.lang.Object"));
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

            if (isPrimitiveType(argumentTypeClassName) && isPrimitiveType(methodArgumentTypeClassName)) {
                if (isWideningPrimitiveConversion(argumentTypeClassName, methodArgumentTypeClassName)) {
                    methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance()
                            + PRIMITIVE_TYPE_WIDENING_NARROWING_DISTANCE);
                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);

                } else if (isNarrowingPrimitiveConversion(argumentTypeClassName, methodArgumentTypeClassName)) {
                    methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance()
                            + PRIMITIVE_TYPE_WIDENING_NARROWING_DISTANCE);
                    matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);

                } else {
                    return false;
                }
            }

            if (isNullType(argumentTypeClassName) && !isPrimitiveType(methodArgumentTypeClassName)) {
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
                    && isArrayDimensionMismatch(argumentTypeClassName, methodArgumentTypeClassName)) {
                return false;
            }

            if (isPrimitiveType(argumentTypeClassName)
                    && PRIMITIVE_WRAPPER_CLASS_MAP.get(argumentTypeClassName).equals(methodArgumentTypeClassName)) {

                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
            }

            if (isPrimitiveType(methodArgumentTypeClassName)
                    && PRIMITIVE_UN_WRAPPER_CLASS_MAP.containsKey(argumentTypeClassName)
                    && PRIMITIVE_UN_WRAPPER_CLASS_MAP.get(argumentTypeClassName).equals(methodArgumentTypeClassName)) {

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
            }

            if (isPrimitiveType(argumentTypeClassName) && methodArgumentTypeClassName.equals("java.lang.Object")) {
                methodInfo.setArgumentMatchingDistance(methodInfo.getArgumentMatchingDistance() + MAX_SUPER_CLASS_DISTANCE);
                matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
            }

            Set<String> classNameList = new HashSet<>();
            classNameList.add(argumentTypeClassName);

            int distance = 0;

            while (!classNameList.isEmpty()) {
                classNameList = getSuperClasses(classNameList, jarVertexIds, tinkerGraph);

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
                                                 Set<String> importedClassQNameList,
                                                 List<String> packageNameList,
                                                 TinkerGraph tinkerGraph) {

        if (Objects.nonNull(typeClassName) && !isPrimitiveType(typeClassName)
                && StringUtils.countMatches(typeClassName, ".") <= 1) {

            typeClassName = typeClassName.replace(".", "$");

            List<ClassInfo> qualifiedClassInfoList = tinkerGraph.traversal().V(jarVertexIds)
                    .out("ContainsPkg").out("Contains")
                    .has("Kind", "Class")
                    .has("Name", typeClassName)
                    .toStream()
                    .map(ClassInfo::new)
                    .collect(Collectors.toList());

            qualifiedClassInfoList = qualifiedClassInfoList.stream().filter(classInfo ->
                    importedClassQNameList.contains(classInfo.getQualifiedName())
                            || packageNameList.contains(classInfo.getPackageName()))
                    .collect(Collectors.toList());

            return qualifiedClassInfoList;
        }

        return Collections.emptyList();
    }

    static String resolveQNameForClass(String typeClassName,
                                                 Object[] jarVertexIds,
                                                 Set<String> importedClassQNameList,
                                                 List<String> packageNameList,
                                                 TinkerGraph tinkerGraph) {

        List<ClassInfo> qualifiedClassInfoList = resolveQClassInfoForClass(typeClassName, jarVertexIds,
                importedClassQNameList, packageNameList, tinkerGraph);

        return qualifiedClassInfoList.isEmpty()
                ? typeClassName
                : qualifiedClassInfoList.get(0).getQualifiedName();
    }


    static List<MethodInfo> getQualifiedMethodInfoList(String methodName, int numberOfParameters,
                                                        Object[] jarVertexIds, Set<String> classQNameList, TinkerGraph tinkerGraph) {

        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameList))
                .out("Declares")
                .has("Kind", "Method")
                .has("Name", methodName)
                .toStream()
                .map(MethodInfo::new)
                .filter(methodInfo -> methodInfo.getArgumentTypes().length == numberOfParameters || methodInfo.isVarargs())
                .collect(Collectors.toList());
    }

    static List<MethodInfo> getQualifiedMethodInfoListForInnerClass(String methodName, int numberOfParameters,
                                                                              Object[] jarVertexIds, Set<String> classQNameList, TinkerGraph tinkerGraph) {
        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameList))
                .out("ContainsInnerClass")
                .out("Declares")
                .has("Kind", "Method")
                .has("Name", methodName)
                .toStream()
                .map(MethodInfo::new)
                .filter(methodInfo -> methodInfo.getArgumentTypes().length == numberOfParameters || methodInfo.isVarargs())
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


    static Set<String> getSuperClasses(Set<String> classQNameList,
                                                 Object[] jarVertexIds,
                                                 TinkerGraph tinkerGraph) {
        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameList))
                .out("extends", "implements")
                .<String>values("Name")
                .toSet();
    }

    static Object[] getJarVertexIds(Set<Tuple3<String, String, String>> jarInformationSet,
                                              String javaVersion, TinkerGraph tinkerGraph) {
        Set<Object> jarVertexIdSet = new HashSet<>();

        jarInformationSet.forEach(j -> {
            jarVertexIdSet.addAll(
                    tinkerGraph.traversal().V()
                            .has("Kind", "Jar")
                            .has("GroupId", j._1)
                            .has("ArtifactId", j._2)
                            .has("Version", j._3)
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
        List<String> nonImportStaticList = importList.stream().filter(im -> !im.startsWith("import static")).collect(Collectors.toList());

        return nonImportStaticList.stream()
                .filter(im -> im.endsWith(".*"))
                .map(im -> im.substring(0, im.lastIndexOf(".*")).replace("import", "").trim())
                .collect(Collectors.toList());
    }

    static Set<String> getImportedQNameList(List<String> importList) {
        Set<String> importedClassQNameList = new HashSet<>();
        List<String> importStaticList = importList.stream().filter(im -> im.startsWith("import static")).collect(Collectors.toList());
        List<String> nonImportStaticList = importList.stream().filter(im -> !im.startsWith("import static")).collect(Collectors.toList());

        importedClassQNameList.addAll(
                nonImportStaticList.stream()
                        .filter(im -> !im.endsWith(".*"))
                        .map(im -> im.replace("import", "").trim())
                        .collect(Collectors.toSet())
        );

        importedClassQNameList.addAll(
                importStaticList.stream()
                        .map(im -> im.substring(0, im.lastIndexOf(".")).replace("import static", "").trim())
                        .collect(Collectors.toSet())
        );

        return importedClassQNameList;
    }


    static String processMethodName(String methodName, Set<String> importedClassQNameSet) {
        /*
          Method name may contains parameterized type (e.g ArrayList<String>). So removal of parameterized type is required
          before method name matching.
         */
        if (methodName.contains("<") && methodName.contains(">")) {
            int startIndex = methodName.lastIndexOf("<");
            int endIndex = methodName.lastIndexOf(">") + 1;

            methodName = methodName.replace(methodName.substring(startIndex, endIndex), "");
        }

        /*
          For fully qualified method expression, We are extracting fully qualified class name as import and method name
         */
        if (methodName.contains(".")) {
            importedClassQNameSet.add(methodName);
            methodName = methodName.substring(methodName.lastIndexOf(".") + 1);
        }

        return methodName;
    }

    private static boolean isNullType(String name) {
        return "null".equals(name);
    }

    private static boolean isPrimitiveType(String argumentTypeClassName) {
        return PRIMITIVE_TYPE_LIST.contains(argumentTypeClassName);
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

            if (isPrimitiveType(varArgTypeName)) {
                varArgTypeName = PRIMITIVE_WRAPPER_CLASS_MAP.get(varArgTypeName);
            }

            Set<String> classNameSet = Collections.singleton(varArgTypeName);
            boolean matched = false;

            while (!classNameSet.isEmpty()) {
                classNameSet = getSuperClasses(classNameSet, jarVertexIds, tinkerGraph);

                if (classNameSet.contains(methodArgumentTypeName)) {
                    matched = true;
                    break;
                }
            }

            return matched;
        });
    }

}
