package ca.concordia.jaranalyzer;

import ca.concordia.jaranalyzer.Models.TypeObject;
import ca.concordia.jaranalyzer.util.*;
import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;

import java.util.*;
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
    public void testRemovalOfFormalTypeParameter() {
        String methodSignature = "<T::Ljava/util/EventListener;>(Ljava/lang/Class<TT;>;TT;)V";

        SignatureReader signatureReader = new SignatureReader(methodSignature);

        Map<String, String> map = new HashMap<>();
        map.put("T", "org.jfree.chart.plot.dial.DialLayerChangeListener");

        GenericTypeResolutionAdapter genericTypeResolutionAdapter = new GenericTypeResolutionAdapter(map);
        signatureReader.accept(genericTypeResolutionAdapter);

        List<String> argumentTypeClassNameList = Arrays.stream(genericTypeResolutionAdapter.getMethodArgumentTypes())
                .map(Type::getClassName)
                .collect(Collectors.toList());

        assert "[java.lang.Class, org.jfree.chart.plot.dial.DialLayerChangeListener]".equals(argumentTypeClassNameList.toString())
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

    @Test
    public void testNonFormalTypeArgument() {
        String signature = "(Ljava/lang/Object;)TV;";

        Map<String, String> map = new HashMap<>();
        map.put("V", "java.lang.String");

        SignatureReader signatureReader = new SignatureReader(signature);
        GenericTypeResolutionAdapter genericTypeResolutionAdapter = new GenericTypeResolutionAdapter(map);
        signatureReader.accept(genericTypeResolutionAdapter);

        assert "(Ljava/lang/Object;)Ljava/lang/String;".equals(genericTypeResolutionAdapter.getSignatureWriter().toString());
    }

    @Test
    public void testFormalTypParameterExtractionFromClassSignature() {
        String classSignature = "<K:Ljava/lang/Object;V:Ljava/lang/Object;>Ljava/util/AbstractMap<TK;TV;>;Ljava/util/Map<TK;TV;>;Ljava/lang/Cloneable;Ljava/io/Serializable;";

        SignatureReader signatureReader = new SignatureReader(classSignature);
        List<TypeObject> typeClassNameList = new ArrayList<>(Arrays.asList(new TypeObject("java.lang.String"),
                new TypeObject("java.lang.Integer")));

        ClassSignatureFormalTypeParameterExtractor classSignatureFormalTypeParameterExtractor =
                new ClassSignatureFormalTypeParameterExtractor(typeClassNameList);

        signatureReader.accept(classSignatureFormalTypeParameterExtractor);

        assert "[V=TypeObject{qualifiedClassName='java.lang.Integer'}, K=TypeObject{qualifiedClassName='java.lang.String'}]"
                .equals(classSignatureFormalTypeParameterExtractor.getFormalTypeParameterMap().entrySet().toString());
    }

    @Test
    public void testFormalTypParameterExtractionFromClassSignatureWithInterfaceBound() {
        String classSignature = "<K::Ljava/lang/Comparable<TK;>;>Ljava/lang/Object;Lorg/jfree/data/KeyedValues<TK;>;Ljava/lang/Cloneable;Lorg/jfree/chart/util/PublicCloneable;Ljava/io/Serializable;";

        SignatureReader signatureReader = new SignatureReader(classSignature);
        List<TypeObject> typeClassObjList = new ArrayList<>();

        ClassSignatureFormalTypeParameterExtractor classSignatureFormalTypeParameterExtractor =
                new ClassSignatureFormalTypeParameterExtractor(typeClassObjList);

        signatureReader.accept(classSignatureFormalTypeParameterExtractor);

        assert "{K=TypeObject{qualifiedClassName='java.lang.Comparable'}}"
                .equals(classSignatureFormalTypeParameterExtractor.getFormalTypeParameterMap().toString());
    }

    @Test
    public void testFormalTypeParameterExtractionFromFieldSignature() {
        String signature = "Ljava/util/Map<Ljava/lang/Integer;Lorg/jfree/data/xy/XYDataset;>;";

        SignatureReader signatureReader = new SignatureReader(signature);
        FieldSignatureFormalTypeParameterExtractor fieldSignatureFormalTypeParameterExtractor = new FieldSignatureFormalTypeParameterExtractor();

        signatureReader.accept(fieldSignatureFormalTypeParameterExtractor);

        assert "java.util.Map".equals(fieldSignatureFormalTypeParameterExtractor.getTypeClassName())
                && "[java.lang.Integer, org.jfree.data.xy.XYDataset]"
                .equals(fieldSignatureFormalTypeParameterExtractor.getTypeArgumentClassNameList().toString());
    }

    @Test
    public void testFormalTypeParameterExtractionFromMethodSignature() {
        String signature = "<T::Ljava/util/EventListener;>(Ljava/lang/Class<TT;>;TT;)V";

        List<String> argumentList = new ArrayList<>(Arrays.asList("org.jfree.chart.plot.dial.DialLayerChangeListener",
                "org.jfree.chart.plot.dial.DialLayerChangeListener"));

        SignatureReader signatureReader = new SignatureReader(signature);
        MethodSignatureFormalTypeParameterExtractor extractor = new MethodSignatureFormalTypeParameterExtractor(argumentList);

        signatureReader.accept(extractor);

        assert "[T=org.jfree.chart.plot.dial.DialLayerChangeListener]"
                .equals(extractor.getFormalTypeParameterMap().entrySet().toString());
    }

    @Test
    public void testFormalTypeParameterExtractionFromMethodArguments() {
        String signature = "<P:Ljava/lang/String;R:Ljava/lang/Object;Q:Ljava/lang/Object;>(Ljava/util/Map<TP;TR;>;TQ;)V";
        SignatureReader signatureReader = new SignatureReader(signature);

        List<TypeObject> methodArgumentList = new ArrayList<>();

        TypeObject firstArgument = new TypeObject("java.util.Map")
                .setParameterized(true);

        LinkedHashMap<String, TypeObject> typeArgumentMap = new LinkedHashMap<>();
        typeArgumentMap.put("K", new TypeObject("java.lang.String"));
        typeArgumentMap.put("V", new TypeObject("java.lang.String"));

        firstArgument.setArgumentTypeObjectMap(typeArgumentMap);

        methodArgumentList.add(firstArgument);
        methodArgumentList.add(new TypeObject("java.lang.Integer"));

        MethodArgumentFormalTypeParameterExtractor extractor =
                new MethodArgumentFormalTypeParameterExtractor(methodArgumentList);
        signatureReader.accept(extractor);

        assert "{P=java.lang.String, Q=java.lang.Integer, R=java.lang.String}"
                .equals(extractor.getFormalTypeParameterMap().toString());
    }

    @Test
    public void testFormalTypeParameterExtractionForArrayArguments() {
        String signature = "<T:Ljava/lang/Object;>([TT;)[TT;";
        SignatureReader signatureReader = new SignatureReader(signature);

        List<TypeObject> methodArgumentList = new ArrayList<>();
        methodArgumentList.add(new TypeObject("java.net.URL[]"));

        MethodArgumentFormalTypeParameterExtractor extractor =
                new MethodArgumentFormalTypeParameterExtractor(methodArgumentList);
        signatureReader.accept(extractor);

        assert "{T=java.net.URL}".equals(extractor.getFormalTypeParameterMap().toString());
    }

}
