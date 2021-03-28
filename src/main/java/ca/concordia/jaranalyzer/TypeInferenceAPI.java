package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.JarInformation;
import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.ExternalJarExtractionUtility;
import ca.concordia.jaranalyzer.util.Utility;
import io.vavr.Tuple3;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.IO;
import org.apache.tinkerpop.gremlin.process.traversal.TextP;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.eclipse.jgit.lib.Repository;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;
import static ca.concordia.jaranalyzer.util.Utility.getJarStoragePath;

/**
 * @author Diptopol
 * @since 12/23/2020 9:01 PM
 */
public class TypeInferenceAPI {

    private static Logger logger = LoggerFactory.getLogger(TypeInferenceAPI.class);

    private static TinkerGraph tinkerGraph;
    private static JarAnalyzer jarAnalyzer;

    private static final List<String> PRIMITIVE_TYPE_LIST =
            new ArrayList<>(Arrays.asList("byte", "short", "int", "long", "float", "double", "char", "boolean"));

    private static Map<String, String> PRIMITIVE_WRAPPER_CLASS_MAP = new HashMap<>();

    private static Map<String, List<String>> PRIMITIVE_TYPE_WIDENING_MAP = new HashMap<>();

    private static Map<String, List<String>> PRIMITIVE_TYPE_NARROWING_MAP = new HashMap<>();

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

        Configuration configuration = new BaseConfiguration();
        configuration.addProperty("gremlin.tinkergraph.defaultVertexPropertyCardinality", "list");

        tinkerGraph = TinkerGraph.open(configuration);
        jarAnalyzer = new JarAnalyzer(tinkerGraph);

        if (!Files.exists(getJarStoragePath())) {
            createClassStructureGraphForJavaJars();
            storeClassStructureGraph();
        } else {
            loadClassStructureGraph();
        }
    }

    public static Set<Tuple3<String, String, String>> loadExternalJars(String commitId, String projectName, Repository repository) {
        Set<Tuple3<String, String, String>> jarArtifactInfoSet =
                ExternalJarExtractionUtility.getDependenciesFromEffectivePom(commitId, projectName, repository);

        Set<Tuple3<String, String, String>> jarArtifactInfoSetForLoad = jarArtifactInfoSet.stream()
                .filter(jarArtifactInfo -> !isJarExists(jarArtifactInfo._1, jarArtifactInfo._2, jarArtifactInfo._3))
                .collect(Collectors.toSet());

        jarArtifactInfoSetForLoad.forEach(jarArtifactInfo -> {
            JarInformation jarInformation =
                    ExternalJarExtractionUtility.getJarInfo(jarArtifactInfo._1, jarArtifactInfo._2, jarArtifactInfo._3);

            jarAnalyzer.toGraph(jarInformation);
        });

        if (jarArtifactInfoSetForLoad.size() > 0) {
            storeClassStructureGraph();
        }

        return jarArtifactInfoSet;
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
     * The process of checking classes for specific method will happen in four steps.<br><br>
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

        Object[] jarVertexIds = getJarVertexIds(dependentJarInformationSet, javaVersion);

        List<String> importStaticList = importList.stream().filter(im -> im.startsWith("import static")).collect(Collectors.toList());
        List<String> nonImportStaticList = importList.stream().filter(im -> !im.startsWith("import static")).collect(Collectors.toList());
        Set<String> importedClassQNameList = new HashSet<>();

        List<String> argumentTypeList = resolveQNameForArgumentTypes(argumentTypes, jarVertexIds);
        /*
          STEP 1
         */
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
            importedClassQNameList.add(methodName);
            methodName = methodName.substring(methodName.lastIndexOf(".") + 1);
        }

        List<MethodInfo> qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters,
                jarVertexIds, importedClassQNameList);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, callerClassName, isSuperOfCallerClass,
                argumentTypeList, jarVertexIds);

        if (!qualifiedMethodInfoList.isEmpty()) {
            return qualifiedMethodInfoList;
        }

        /*
          STEP 2
         */
        qualifiedMethodInfoList = tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(importedClassQNameList))
                .out("ContainsInnerClass")
                .out("Declares")
                .has("Kind", "Method")
                .has("Name", methodName)
                .toStream()
                .map(MethodInfo::new)
                .filter(methodInfo -> methodInfo.getArgumentTypes().length == numberOfParameters)
                .collect(Collectors.toList());

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, callerClassName, isSuperOfCallerClass,
                argumentTypeList, jarVertexIds);

        if (!qualifiedMethodInfoList.isEmpty()) {
            return qualifiedMethodInfoList;
        }

        /*
          STEP 3
         */
        List<String> packageNameList = nonImportStaticList.stream()
                .filter(im -> im.endsWith(".*"))
                .map(im -> im.substring(0, im.lastIndexOf(".*")).replace("import", "").trim())
                .collect(Collectors.toList());

        Set<String> classNameListForPackgage = tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg")
                .has("Kind", "Package")
                .has("Name", TextP.within(packageNameList))
                .out("Contains")
                .has("Kind", "Class")
                .<String>values("QName")
                .toSet();

        importedClassQNameList.addAll(classNameListForPackgage);

        qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters, jarVertexIds, classNameListForPackgage);

        qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, callerClassName, isSuperOfCallerClass,
                argumentTypeList, jarVertexIds);

        if (!qualifiedMethodInfoList.isEmpty()) {
            return qualifiedMethodInfoList;
        }

        /*
          STEP 4
         */
        Set<String> classQNameList = new HashSet<>(importedClassQNameList);

        while (!classQNameList.isEmpty() && qualifiedMethodInfoList.isEmpty()) {
            classQNameList = tinkerGraph.traversal().V(jarVertexIds)
                    .out("ContainsPkg").out("Contains")
                    .has("Kind", "Class")
                    .has("QName", TextP.within(classQNameList))
                    .out("extends", "implements")
                    .<String>values("Name")
                    .toSet();

            qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, numberOfParameters, jarVertexIds, classQNameList);

            qualifiedMethodInfoList = filterProcess(qualifiedMethodInfoList, callerClassName, isSuperOfCallerClass,
                    argumentTypeList, jarVertexIds);
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

        populateClassInfo(methodInfoList);
        methodInfoList = filterByMethodInvoker(methodInfoList, callerClassName, isSuperOfCallerClass, jarVertexIds);

        return filterByMethodArgumentTypes(methodInfoList, argumentTypeList, jarVertexIds);
    }

    private static List<MethodInfo> filterByMethodInvoker(List<MethodInfo> methodInfoList, String callerClassName,
                                                          boolean isSuperOfCallerClass, Object[] jarVertexIds) {
        if (!methodInfoList.isEmpty() && Objects.nonNull(callerClassName) && !callerClassName.equals("")) {
            Map<String, List<MethodInfo>> methodInfoDeclaringClassNameMap = new HashMap<>();

            String methodInfoClassName;
            for (MethodInfo methodInfo : methodInfoList) {
                if (StringUtils.countMatches(callerClassName, ".") <= 0) {
                    methodInfoClassName = methodInfo.getClassInfo().getName();

                } else {
                    methodInfoClassName = methodInfo.getQualifiedClassName();
                }

                List<MethodInfo> methodInfoListForClass = methodInfoDeclaringClassNameMap.containsKey(methodInfoClassName)
                        ? methodInfoDeclaringClassNameMap.get(methodInfoClassName) : new ArrayList<>();

                methodInfoListForClass.add(methodInfo);
                methodInfoDeclaringClassNameMap.put(methodInfoClassName, methodInfoListForClass);
            }

            List<String> methodInfoClassNameList = new ArrayList<>(methodInfoDeclaringClassNameMap.keySet());

            List<MethodInfo> filteredListByCallerClassName = new ArrayList<>();

            if (methodInfoClassNameList.contains(callerClassName) && !isSuperOfCallerClass) {
                filteredListByCallerClassName.addAll(methodInfoDeclaringClassNameMap.get(callerClassName));

            } else {
                Set<String> classNameSet = new HashSet<>();
                classNameSet.add(callerClassName);

                String[] allOutGoingEdges = new String[]{"extends", "implements"};
                String[] superClassOutGoingEdgeLabels = isSuperOfCallerClass
                        ? new String[]{"extends"}
                        : allOutGoingEdges;

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

                    if (!filteredListByCallerClassName.isEmpty()) {
                        break;
                    }
                }
            }

            return filteredListByCallerClassName;
        } else {
            return methodInfoList;
        }
    }

    private static List<MethodInfo> filterByMethodArgumentTypes(List<MethodInfo> methodInfoList, List<String> argumentTypeList,
                                                                Object[] jarVertexIds) {
        if (!methodInfoList.isEmpty() && !argumentTypeList.isEmpty()) {
            return methodInfoList.stream().filter(methodInfo -> {
                List<String> argumentTypeClassNameList = new ArrayList<>(argumentTypeList);
                List<String> methodArgumentClassNameList = Stream.of(methodInfo.getArgumentTypes())
                        .map(Type::getClassName)
                        .collect(Collectors.toList());

                List<String> commonClassNameList = getCommonClassNameList(argumentTypeClassNameList, methodArgumentClassNameList);

                argumentTypeClassNameList.removeAll(commonClassNameList);
                methodArgumentClassNameList.removeAll(commonClassNameList);

                if (argumentTypeClassNameList.isEmpty() && methodArgumentClassNameList.isEmpty()) {
                    return true;
                }

                List<String> matchedMethodArgumentTypeList = new ArrayList<>();

                for (int index = 0; index < argumentTypeClassNameList.size(); index++) {
                    String argumentTypeClassName = argumentTypeClassNameList.get(index);
                    String methodArgumentTypeClassName = methodArgumentClassNameList.get(index);

                    if (isPrimitiveType(argumentTypeClassName) && isPrimitiveType(methodArgumentTypeClassName)) {
                        if (argumentTypeClassName.equals("short")
                                && Arrays.asList("int", "double", "long").contains(methodArgumentTypeClassName)) {

                            matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);

                        } else if (argumentTypeClassName.equals("int")
                                && Arrays.asList("double", "long").contains(methodArgumentTypeClassName)) {

                            matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);

                        } else {
                            continue;
                        }
                    }

                    if (isArrayDimensionMismatch(argumentTypeClassName, methodArgumentTypeClassName)) {
                        continue;
                    }

                    if (isPrimitiveType(argumentTypeClassName)
                            && PRIMITIVE_WRAPPER_CLASS_MAP.get(argumentTypeClassName).equals(methodArgumentTypeClassName)) {

                        matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                    }

                    /*
                     * Trimmed down array dimension before searching for super classes.
                     */
                    argumentTypeClassName = argumentTypeClassName.replaceAll("[/]", "");
                    methodArgumentTypeClassName = methodArgumentTypeClassName.replaceAll("[/]", "");

                    Set<String> classNameList = new HashSet<>();
                    classNameList.add(argumentTypeClassName);

                    while (!classNameList.isEmpty()) {
                        classNameList = tinkerGraph.traversal().V(jarVertexIds)
                                .out("ContainsPkg").out("Contains")
                                .has("Kind", "Class")
                                .has("QName", TextP.within(classNameList))
                                .out("extends", "implements")
                                .<String>values("Name")
                                .toSet();

                        if (classNameList.contains(methodArgumentTypeClassName)) {
                            matchedMethodArgumentTypeList.add(methodArgumentTypeClassName);
                            break;
                        }
                    }
                }

                methodArgumentClassNameList.removeAll(matchedMethodArgumentTypeList);

                return methodArgumentClassNameList.isEmpty();
            }).collect(Collectors.toList());
        } else {
            return methodInfoList;
        }
    }

    private static List<String> resolveQNameForArgumentTypes(String[] argumentTypes, Object[] jarVertexIds) {
        if (argumentTypes.length > 0) {
            return new ArrayList<>(Arrays.asList(argumentTypes)).stream()
                    .map(argumentType -> {
                        if (!isPrimitiveType(argumentType) && StringUtils.countMatches(argumentType, ".") <= 1) {
                            argumentType = argumentType.replace(".", "$");

                            List<ClassInfo> qualifiedClassInfoList = tinkerGraph.traversal().V(jarVertexIds)
                                    .out("ContainsPkg").out("Contains")
                                    .has("Kind", "Class")
                                    .has("Name", argumentType)
                                    .toStream()
                                    .map(ClassInfo::new)
                                    .collect(Collectors.toList());

                            return qualifiedClassInfoList.isEmpty()
                                    ? argumentType
                                    : qualifiedClassInfoList.get(0).getQualifiedName();
                        }

                        return argumentType;
                    }).collect(Collectors.toList());
        } else {
            return new ArrayList<>(Arrays.asList(argumentTypes));
        }
    }

    private static List<MethodInfo> populateClassInfo(List<MethodInfo> qualifiedMethodInfoList) {
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

    private static List<MethodInfo> getQualifiedMethodInfoList(String methodName, int numberOfParameters,
                                                               Object[] jarVertexIds, Set<String> classQNameList) {

        return tinkerGraph.traversal().V(jarVertexIds)
                .out("ContainsPkg").out("Contains")
                .has("Kind", "Class")
                .has("QName", TextP.within(classQNameList))
                .out("Declares")
                .has("Kind", "Method")
                .has("Name", methodName)
                .toStream()
                .map(MethodInfo::new)
                .filter(methodInfo -> methodInfo.getArgumentTypes().length == numberOfParameters)
                .collect(Collectors.toList());
    }

    private static Object[] getJarVertexIds(Set<Tuple3<String, String, String>> jarInformationSet, String javaVersion) {
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

    private static List<String> getCommonClassNameList(List<String> argumentTypeClassNameList,
                                                       List<String> methodArgumentClassNameList) {

        List<String> commonClassNameList = new ArrayList<>(argumentTypeClassNameList);
        commonClassNameList.retainAll(methodArgumentClassNameList);

        return commonClassNameList;
    }

    private static boolean isWideningPrimitiveConversion(String type1, String type2) {
        return PRIMITIVE_TYPE_WIDENING_MAP.containsKey(type1) && PRIMITIVE_TYPE_WIDENING_MAP.get(type1).contains(type2);
    }

    private static boolean isNarrowingPrimitiveConversion(String type1, String type2) {
        return PRIMITIVE_TYPE_NARROWING_MAP.containsKey(type1) && PRIMITIVE_TYPE_NARROWING_MAP.get(type1).contains(type2);
    }

    private static boolean isPrimitiveType(String argumentTypeClassName) {
        return PRIMITIVE_TYPE_LIST.contains(argumentTypeClassName);
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

    public static List<String> getQualifiedClassName(String className) {
        return tinkerGraph.traversal().V()
                .has("Kind", "Class")
                .has("Name", className)
                .<String>values("QName")
                .toList();
    }


    public static void loadJar(String groupId, String artifactId, String version) {
        if (!isJarExists(groupId, artifactId, version)) {
            JarInformation jarInformation =
                    ExternalJarExtractionUtility.getJarInfo(groupId, artifactId, version);

            jarAnalyzer.toGraph(jarInformation);
            storeClassStructureGraph();
        }
    }

    private static boolean isJarExists(String groupId, String artifactId, String version) {
        return tinkerGraph.traversal().V()
                .has("Kind", "Jar")
                .has("GroupId", groupId)
                .has("ArtifactId", artifactId)
                .has("Version", version)
                .toSet().size() > 0;
    }

    private static void createClassStructureGraphForJavaJars() {
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

    private static void storeClassStructureGraph() {
        logger.info("storing graph");

        tinkerGraph.traversal().io(getJarStoragePath().toString())
                .with(IO.writer, IO.gryo)
                .write().iterate();
    }

    private static void loadClassStructureGraph() {
        logger.info("loading graph");

        tinkerGraph.traversal().io(getJarStoragePath().toString())
                .with(IO.reader, IO.gryo)
                .read().iterate();
    }
}
