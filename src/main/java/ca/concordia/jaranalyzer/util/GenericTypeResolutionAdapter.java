package ca.concordia.jaranalyzer.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

import java.util.Map;

/**
 * @author Diptopol
 * @since 6/22/2021 12:15 PM
 */
public class GenericTypeResolutionAdapter extends SignatureVisitor {

    private SignatureWriter signatureWriter;

    private Map<String, String> formalParameterMap;

    private boolean seenFormalTypeParameter;

    /**
     * @param formalTypeParameterMap : This map will be used to resolve formal types
     */
    public GenericTypeResolutionAdapter(Map<String, String> formalTypeParameterMap) {
        super(Opcodes.ASM9);
        this.signatureWriter = new SignatureWriter();
        this.formalParameterMap = formalTypeParameterMap;
    }

    @Override
    public SignatureVisitor visitReturnType() {
        signatureWriter.visitReturnType();
        return this;
    }

    @Override
    public void visitFormalTypeParameter(final String name) {
        seenFormalTypeParameter = true;
        signatureWriter.visitFormalTypeParameter(name);
    }

    @Override
    public SignatureVisitor visitClassBound() {
        signatureWriter.visitClassBound();
        return this;
    }

    @Override
    public SignatureVisitor visitInterfaceBound() {
        signatureWriter.visitInterfaceBound();
        return this;
    }

    @Override
    public SignatureVisitor visitParameterType() {
        signatureWriter.visitParameterType();
        return this;
    }

    @Override
    public SignatureVisitor visitExceptionType() {
        signatureWriter.visitExceptionType();
        return this;
    }

    @Override
    public void visitBaseType(final char descriptor) {
        signatureWriter.visitBaseType(descriptor);
    }

    @Override
    public void visitTypeVariable(final String name) {
        if (!seenFormalTypeParameter) {
            String className = formalParameterMap.get(name);
            signatureWriter.visitClassType(className);
            signatureWriter.visitEnd();
        }
    }

    @Override
    public SignatureVisitor visitArrayType() {
        signatureWriter.visitArrayType();
        return this;
    }

    @Override
    public void visitClassType(final String name) {
        if (!seenFormalTypeParameter) {
            signatureWriter.visitClassType(name);
        }
    }

    @Override
    public void visitInnerClassType(final String name) {
        if (!seenFormalTypeParameter) {
            signatureWriter.visitInnerClassType(name);
        }
    }

    @Override
    public void visitTypeArgument() {
        seenFormalTypeParameter = true;
    }

    @Override
    public SignatureVisitor visitTypeArgument(final char tag) {
        seenFormalTypeParameter = true;
        return this;
    }

    @Override
    public void visitEnd() {
        if (seenFormalTypeParameter) {
            signatureWriter.visitEnd();
        }

        if (seenFormalTypeParameter) {
            seenFormalTypeParameter = false;
        }
    }

    public SignatureWriter getSignatureWriter() {
        return signatureWriter;
    }
}
