package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.models.typeInfo.ArrayTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.PrimitiveTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.QualifiedTypeInfo;
import ca.concordia.jaranalyzer.models.typeInfo.TypeInfo;
import org.objectweb.asm.Type;

/**
 * @author Diptopol
 * @since 7/28/2022 10:33 AM
 */
public class EntityUtils {

    public static TypeInfo getTypeInfo(Type type) {
        String typeClassName = type.getClassName()
                .replaceAll("\\$", ".");

        if (typeClassName.endsWith("[]")) {
            int dimension = type.getDimensions();
            String className = typeClassName.replaceAll("\\[]", "");

            if (InferenceUtility.PRIMITIVE_TYPE_LIST.contains(className)) {
                return new ArrayTypeInfo(new PrimitiveTypeInfo(className), dimension);
            } else {
                return new ArrayTypeInfo(new QualifiedTypeInfo(className), dimension);
            }
        } else {
            if (InferenceUtility.PRIMITIVE_TYPE_LIST.contains(typeClassName)) {
                return new PrimitiveTypeInfo(typeClassName);
            } else {
                return new QualifiedTypeInfo(typeClassName);
            }
        }
    }

}
