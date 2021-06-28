package ca.concordia.jaranalyzer.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Diptopol
 * @since 6/28/2021 12:39 AM
 */
public class ClassSignatureFormalTypeParameterExtractor extends SignatureVisitor {

    private List<String> typeClassNameList;

    private Map<String, String> formalTypeParameterMap;

    private boolean seenFormalTypeParameter;

    private int currentTypeClassNameIndex;

    public ClassSignatureFormalTypeParameterExtractor(List<String> typeClassNameList) {
        super(Opcodes.ASM9);
        this.formalTypeParameterMap = new HashMap<>();
        this.typeClassNameList = typeClassNameList;
    }

    @Override
    public void visitFormalTypeParameter(final String name) {
        seenFormalTypeParameter = true;

        formalTypeParameterMap.put(name, typeClassNameList.get(currentTypeClassNameIndex));

        if (seenFormalTypeParameter) {
            currentTypeClassNameIndex++;
        }
    }

    @Override
    public SignatureVisitor visitSuperclass() {
        seenFormalTypeParameter = false;

        return this;
    }

    @Override
    public SignatureVisitor visitInterface() {
        seenFormalTypeParameter = false;

        return this;
    }

    public Map<String, String> getFormalTypeParameterMap() {
        return formalTypeParameterMap;
    }
}
