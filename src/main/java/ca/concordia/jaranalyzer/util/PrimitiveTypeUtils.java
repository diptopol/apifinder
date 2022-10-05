package ca.concordia.jaranalyzer.util;

import java.util.*;

/**
 * @author Diptopol
 * @since 9/28/2022 12:25 PM
 */
public class PrimitiveTypeUtils {

    private static Map<String, String> PRIMITIVE_WRAPPER_CLASS_MAP = new HashMap<>();

    private static final List<String> PRIMITIVE_TYPE_LIST =
            new ArrayList<>(Arrays.asList("byte", "short", "int", "long", "float", "double", "char", "boolean"));

    private static final List<String> PRIMITIVE_NUMERIC_TYPE_LIST =
            new ArrayList<>(Arrays.asList("byte", "short", "int", "long", "float", "double"));

    private static Map<String, String> PRIMITIVE_UN_WRAPPER_CLASS_MAP = new HashMap<>();

    private static Map<String, List<String>> PRIMITIVE_TYPE_WIDENING_MAP = new HashMap<>();
    private static Map<String, List<String>> PRIMITIVE_TYPE_NARROWING_MAP = new HashMap<>();

    static {
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
    }

    public static String getPrimitiveWrapperClassQName(String primitiveClassName) {
        return PRIMITIVE_WRAPPER_CLASS_MAP.get(primitiveClassName);
    }

    public static boolean isPrimitiveType(String argumentTypeClassName) {
        return PRIMITIVE_TYPE_LIST.contains(argumentTypeClassName);
    }

    public static boolean isPrimitiveNumericType(String primitiveClassName) {
        return PRIMITIVE_NUMERIC_TYPE_LIST.contains(primitiveClassName);
    }

    public static boolean isPrimitiveUnWrapperClass(String probablePrimitiveClassQName, String probablePrimitiveWrapperClassQName) {
        return isPrimitiveType(probablePrimitiveClassQName)
                && PRIMITIVE_UN_WRAPPER_CLASS_MAP.containsKey(probablePrimitiveWrapperClassQName)
                && PRIMITIVE_UN_WRAPPER_CLASS_MAP.get(probablePrimitiveWrapperClassQName).equals(probablePrimitiveClassQName);
    }

    public static List<String> getPrimitiveWideningClassNameList(String primitiveClassName) {
        return PRIMITIVE_TYPE_WIDENING_MAP.get(primitiveClassName);
    }

    public static List<String> getPrimitiveNarrowingClassNameList(String primitiveClassName) {
        return PRIMITIVE_TYPE_NARROWING_MAP.get(primitiveClassName);
    }

    public static boolean isWideningPrimitiveConversion(String type1, String type2) {
        return PRIMITIVE_TYPE_WIDENING_MAP.containsKey(type1) && PRIMITIVE_TYPE_WIDENING_MAP.get(type1).contains(type2);
    }

    public static boolean isNarrowingPrimitiveConversion(String type1, String type2) {
        return PRIMITIVE_TYPE_NARROWING_MAP.containsKey(type1) && PRIMITIVE_TYPE_NARROWING_MAP.get(type1).contains(type2);
    }

}
