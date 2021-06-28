package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.util.GenericTypeResolutionAdapter;
import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 6/22/2021 12:17 PM
 */
public class GenericTypeConversionTest {

    @Test
    public void testSignatureForFormalTypeReturn() {
        String methodSignature = "()Ljava/util/Set<Ljava/util/Map$Entry<TK;TV;>;>;";
        SignatureReader signatureReader = new SignatureReader(methodSignature);

        Map<String, String> map = new HashMap<>();
        map.put("K", "java.lang.String");
        map.put("V", "java.lang.String");

        GenericTypeResolutionAdapter genericTypeResolutionAdapter = new GenericTypeResolutionAdapter(map);
        signatureReader.accept(genericTypeResolutionAdapter);

        assert "[]".equals(Arrays.asList(genericTypeResolutionAdapter.getMethodArgumentTypes()).toString())
                && "java.util.Set".equals(genericTypeResolutionAdapter.getMethodReturnType().getClassName());
    }

    @Test
    public void testSignatureForExtendedFormalTypeArgument() {
        String methodSignature = "(Ljava/util/Map<+TK;+TV;>;Z)V";

        SignatureReader signatureReader = new SignatureReader(methodSignature);

        Map<String, String> map = new HashMap<>();
        map.put("K", "java.lang.Object");
        map.put("V", "java.lang.Object");

        GenericTypeResolutionAdapter genericTypeResolutionAdapter = new GenericTypeResolutionAdapter(map);
        signatureReader.accept(genericTypeResolutionAdapter);

        List<String> argumentTypeClassNameList = Arrays.stream(genericTypeResolutionAdapter.getMethodArgumentTypes())
                .map(Type::getClassName)
                .collect(Collectors.toList());

        assert "[java.util.Map, boolean]".equals(argumentTypeClassNameList.toString())
                && "void".equals(genericTypeResolutionAdapter.getMethodReturnType().getClassName());
    }

    @Test
    public void testForArrayAsArgument() {
        String methodSignature = "([TE;)V";

        SignatureReader signatureReader = new SignatureReader(methodSignature);

        Map<String, String> map = new HashMap<>();
        map.put("E", "java.lang.String");

        GenericTypeResolutionAdapter genericTypeResolutionAdapter = new GenericTypeResolutionAdapter(map);
        signatureReader.accept(genericTypeResolutionAdapter);

        List<String> argumentTypeClassNameList = Arrays.stream(genericTypeResolutionAdapter.getMethodArgumentTypes())
                .map(Type::getClassName)
                .collect(Collectors.toList());

        assert "[java.lang.String[]]".equals(argumentTypeClassNameList.toString())
                && "void".equals(genericTypeResolutionAdapter.getMethodReturnType().getClassName());
    }

}
