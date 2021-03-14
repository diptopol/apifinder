package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.ClassInfo;
import ca.concordia.jaranalyzer.Models.JarInformation;
import ca.concordia.jaranalyzer.Models.MethodInfo;
import ca.concordia.jaranalyzer.util.ExternalJarExtractionUtility;
import ca.concordia.jaranalyzer.util.Utility;
import io.vavr.Tuple2;
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
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static ca.concordia.jaranalyzer.util.PropertyReader.getProperty;
import static ca.concordia.jaranalyzer.util.Utility.getJarStoragePath;

/**
 * @author Diptopol
 * @since 2/20/2021 10:35 PM
 */
public class TypeInferenceFluentAPI {

    private static Logger logger = LoggerFactory.getLogger(TypeInferenceAPI.class);

    private static Map<String, String> PRIMITIVE_WRAPPER_CLASS_MAP = new HashMap<>();

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
    }

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

    public Set<Tuple3<String, String, String>> loadExternalJars(String commitId, String projectName, Repository repository) {
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

    private List<MethodInfo> getFilteredMethodList(Criteria criteria) {
        String callerClassName = criteria.getCallerClassName();
        List<Tuple2<Integer, String>> argumentTypeWithIndexList = criteria.getArgumentTypeWithIndexList();

        Object[] jarVertexIds = getJarVertexIds(criteria);
        List<MethodInfo> methodInfoList = getAllMethods(jarVertexIds, criteria);

        if (methodInfoList.size() == 1) {
            return methodInfoList;
        }

        /*
         * Caller class name filter. class name can be qualified name or simple name
         *
         */
        if (Objects.nonNull(callerClassName) && !callerClassName.equals("")) {
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

            if (methodInfoClassNameList.contains(callerClassName)) {
                filteredListByCallerClassName.addAll(methodInfoDeclaringClassNameMap.get(callerClassName));

            } else {
                Set<String> classNameSet = new HashSet<>();
                classNameSet.add(callerClassName);

                while (!classNameSet.isEmpty()) {
                    classNameSet = tinkerGraph.traversal().V(jarVertexIds)
                            .out("ContainsPkg").out("Contains")
                            .has("Kind", "Class")
                            .has("QName", TextP.within(classNameSet))
                            .out("extends", "implements")
                            .<String>values("Name")
                            .toSet();

                    for (String className: methodInfoClassNameList) {
                        if (classNameSet.contains(className)) {
                            filteredListByCallerClassName.addAll(methodInfoDeclaringClassNameMap.get(className));
                            break;
                        }
                    }
                }
            }

            methodInfoList = filteredListByCallerClassName;
        }

        if (methodInfoList.size() == 1) {
            return methodInfoList;
        }

        /*
         * Argument type check filter.
         */
        if (!argumentTypeWithIndexList.isEmpty()) {
            return methodInfoList.stream().filter(methodInfo -> {

                argumentTypeWithIndexList.sort(Comparator.comparingInt(Tuple2::_1));
                List<Type> methodArgumentTypeList = new ArrayList<>(Arrays.asList(methodInfo.getArgumentTypes()));
                List<String> argumentTypeClassNameList = new ArrayList<>();
                List<String> methodArgumentClassNameList = new ArrayList<>();

                for (Tuple2<Integer, String> argumentIndexTuple : argumentTypeWithIndexList) {
                    int index = argumentIndexTuple._1();

                    argumentTypeClassNameList.add(argumentIndexTuple._2());
                    methodArgumentClassNameList.add(methodArgumentTypeList.get(index).getClassName());
                }

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
                            return false;
                        }
                    }

                    if (isArrayDimensionMismatch(argumentTypeClassName, methodArgumentTypeClassName)) {
                        return false;
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

                    if (StringUtils.countMatches(argumentTypeClassName, ".") <= 0) {
                        argumentTypeClassName = argumentTypeClassName.replace(".", "$");

                        classNameList.addAll(tinkerGraph.traversal().V(jarVertexIds)
                                .out("ContainsPkg").out("Contains")
                                .has("Kind", "Class")
                                .has("Name", TextP.within(argumentTypeClassName))
                                .<String>values("QName")
                                .toSet());

                    } else {
                        classNameList.add(argumentTypeClassName);
                    }


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
        }

        return methodInfoList;
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
    private List<MethodInfo> getAllMethods(Object[] jarVertexIds, Criteria criteria) {
        List<String> importList = criteria.getImportList();
        String methodName = criteria.getMethodName();
        List<String> importStaticList = importList.stream().filter(im -> im.startsWith("import static")).collect(Collectors.toList());
        List<String> nonImportStaticList = importList.stream().filter(im -> !im.startsWith("import static")).collect(Collectors.toList());
        Set<String> importedClassQNameList = new HashSet<>();

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

        List<MethodInfo> qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(),
                jarVertexIds, importedClassQNameList);

        if (!qualifiedMethodInfoList.isEmpty()) {
            return populateClassInfo(qualifiedMethodInfoList);
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
                .filter(methodInfo -> methodInfo.getArgumentTypes().length == criteria.getNumberOfParameters())
                .collect(Collectors.toList());

        if (!qualifiedMethodInfoList.isEmpty()) {
            return populateClassInfo(qualifiedMethodInfoList);
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

        qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(), jarVertexIds, classNameListForPackgage);

        if (!qualifiedMethodInfoList.isEmpty()) {
            return populateClassInfo(qualifiedMethodInfoList);
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

            qualifiedMethodInfoList = getQualifiedMethodInfoList(methodName, criteria.getNumberOfParameters(), jarVertexIds, classQNameList);
        }

        if (!qualifiedMethodInfoList.isEmpty()) {
            return populateClassInfo(qualifiedMethodInfoList);

        } else {
            return Collections.emptyList();
        }
    }

    private List<MethodInfo> populateClassInfo(List<MethodInfo> qualifiedMethodInfoList) {
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

    private List<MethodInfo> getQualifiedMethodInfoList(String methodName, int numberOfParameters,
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

    private Object[] getJarVertexIds(Criteria criteria) {
        Set<Object> jarVertexIdSet = new HashSet<>();

        criteria.getDependentJarInformationSet().forEach(j -> {
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
                        .has("Version", criteria.getJavaVersion())
                        .toStream()
                        .map(Element::id)
                        .collect(Collectors.toSet())
        );

        return jarVertexIdSet.toArray(new Object[0]);
    }

    private List<String> getCommonClassNameList(List<String> argumentTypeClassNameList,
                                                List<String> methodArgumentClassNameList) {

        List<String> commonClassNameList = new ArrayList<>(argumentTypeClassNameList);
        commonClassNameList.retainAll(methodArgumentClassNameList);

        return commonClassNameList;
    }

    private boolean isPrimitiveType(String argumentTypeClassName) {
        List<String> primitiveTypeList =
                new ArrayList<>(Arrays.asList("byte", "short", "int", "long", "float", "double", "char", "boolean"));

        return primitiveTypeList.contains(argumentTypeClassName);
    }

    private boolean isArrayDimensionMismatch(String argumentTypeClassName, String methodArgumentTypeClassName) {
        boolean isArgumentTypeArray = argumentTypeClassName.endsWith("[]");
        int argumentTypeArrayDimension = StringUtils.countMatches(argumentTypeClassName, "[]");

        boolean isMethodArgumentTypeArray = methodArgumentTypeClassName.endsWith("[]");
        int methodArgumentTypeArrayDimension = StringUtils.countMatches(methodArgumentTypeClassName, "[]");

        return (isArgumentTypeArray && !isMethodArgumentTypeArray)
                || (!isArgumentTypeArray && isMethodArgumentTypeArray)
                || argumentTypeArrayDimension != methodArgumentTypeArrayDimension;
    }

    private void createClassStructureGraphForJavaJars() {
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

    private boolean isJarExists(String groupId, String artifactId, String version) {
        return tinkerGraph.traversal().V()
                .has("Kind", "Jar")
                .has("GroupId", groupId)
                .has("ArtifactId", artifactId)
                .has("Version", version)
                .toSet().size() > 0;
    }

    private void storeClassStructureGraph() {
        logger.info("storing graph");

        tinkerGraph.traversal().io(getJarStoragePath().toString())
                .with(IO.writer, IO.gryo)
                .write().iterate();
    }

    private void loadClassStructureGraph() {
        logger.info("loading graph");

        tinkerGraph.traversal().io(getJarStoragePath().toString())
                .with(IO.reader, IO.gryo)
                .read().iterate();
    }

    public class Criteria {
        private Set<Tuple3<String, String, String>> dependentJarInformationSet;
        private String javaVersion;
        private List<String> importList;
        private String methodName;
        private int numberOfParameters;
        private String callerClassName;
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

        private List<Tuple2<Integer, String>> getArgumentTypeWithIndexList() {
            if (argumentTypeMap.isEmpty()) {
                return Collections.emptyList();
            }

            return argumentTypeMap.entrySet().stream()
                    .map(e -> new Tuple2<>(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
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

        public Criteria setInvokerTypeAsCriteria(String callerClassName) {
            this.callerClassName = callerClassName;

            return this;
        }

        /**
         * argumentIndex is assumed to be starts with 0 and will consider the max value of argumentIndex as last value.
         */
        public Criteria setArgumentTypeAsCriteria(int argumentIndex, String argumentType) {
            this.argumentTypeMap.put(argumentIndex, argumentType);

            return this;
        }

        public List<MethodInfo> getMethodList() {
            return getFilteredMethodList(this);
        }
    }
}
