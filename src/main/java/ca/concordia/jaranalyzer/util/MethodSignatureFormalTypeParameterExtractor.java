package ca.concordia.jaranalyzer.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Diptopol
 * @since 6/29/2021 10:41 PM
 */
public class MethodSignatureFormalTypeParameterExtractor extends SignatureVisitor {

    private List<String> argumentList;

    private boolean seenParameters;

    private int currentArgumentIndex;

    private int argumentStack;

    private Map<String, String> formalTypeParameterMap;

    public MethodSignatureFormalTypeParameterExtractor(List<String> argumentList) {
        super(Opcodes.ASM9);
        this.argumentList = argumentList;
        formalTypeParameterMap = new HashMap<>();
    }

    @Override
    public void visitFormalTypeParameter(final String name) {
        formalTypeParameterMap.put(name, "");
    }

    @Override
    public SignatureVisitor visitParameterType() {
        seenParameters = true;

        return this;
    }

    @Override
    public SignatureVisitor visitReturnType() {
        seenParameters = false;

        return this;
    }

    @Override
    public void visitTypeVariable(final String name) {
        if (seenParameters && formalTypeParameterMap.containsKey(name)) {
            formalTypeParameterMap.put(name, argumentList.get(currentArgumentIndex));
        }
    }

    @Override
    public void visitInnerClassType(final String name) {
        argumentStack /= 2;
        argumentStack *= 2;
    }

    @Override
    public void visitClassType(final String name) {
        argumentStack *= 2;
    }

    @Override
    public void visitTypeArgument() {
        if (argumentStack % 2 == 0) {
            argumentStack |= 1;
        }
    }

    @Override
    public SignatureVisitor visitTypeArgument(final char tag) {
        if (argumentStack % 2 == 0) {
            argumentStack |= 1;
        }

        return this;
    }

    @Override
    public void visitEnd() {
        argumentStack /= 2;

        if (argumentStack == 0 && seenParameters) {
            currentArgumentIndex++;
        }
    }

    public Map<String, String> getFormalTypeParameterMap() {
        return formalTypeParameterMap;
    }

}
