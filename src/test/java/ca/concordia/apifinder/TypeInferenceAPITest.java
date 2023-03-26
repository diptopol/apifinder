package ca.concordia.apifinder;

import ca.concordia.apifinder.entity.FieldInfo;
import ca.concordia.apifinder.entity.MethodInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static ca.concordia.apifinder.util.PropertyReader.getProperty;

/**
 * @author Diptopol
 * @since 12/23/2020 9:54 PM
 */
public class TypeInferenceAPITest {

    @Test
    public void testLoadingJava11() {
        TypeInferenceAPI.loadJavaPackage(11);
    }

    @Test
    public void testGetAllMethods() {
        String javaVersion = getProperty("java.version");

        List<String> importList = new ArrayList<>();
        importList.add("java.util.Collections");

        List<MethodInfo> methodInfoList =
                TypeInferenceAPI.getAllMethods(new HashSet<>(), javaVersion, importList,
                        "reverse", 1);

        assert methodInfoList.size() == 1;

        assert "java.util.Collections::public static void reverse(java.util.List)".equals(methodInfoList.get(0).toString());
    }

    @Test
    public void testAllFieldTypes() {
        String javaVersion = getProperty("java.version");

        List<String> importList = new ArrayList<>();
        importList.add("import java.awt.*");

        List<FieldInfo> fieldInfoList = TypeInferenceAPI.getAllFieldTypes(new HashSet<>(), javaVersion, importList,
                "KEY_FRACTIONALMETRICS", null);

        assert "[public static java.awt.RenderingHints$Key KEY_FRACTIONALMETRICS]".equals(fieldInfoList.toString());
    }

}
