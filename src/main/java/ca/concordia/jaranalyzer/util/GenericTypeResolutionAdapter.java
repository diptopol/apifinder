package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.typeInfo.TypeInfo;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

import java.util.Map;

/**
 * @author Diptopol
 * @since 6/22/2021 12:15 PM
 */
public class GenericTypeResolutionAdapter extends SignatureVisitor {

    private SignatureWriter signatureWriter;

    private Map<String, TypeInfo> formalParameterMap;

    private int argumentStack;

    private boolean isFormalTypeParameterTraversalCompleted;

    /**
     * @param formalTypeParameterMap : This map will be used to resolve formal types
     */
    public GenericTypeResolutionAdapter(Map<String, TypeInfo> formalTypeParameterMap) {
        super(Opcodes.ASM9);
        this.signatureWriter = new SignatureWriter();
        this.formalParameterMap = formalTypeParameterMap;
    }

    @Override
    public SignatureVisitor visitReturnType() {
        isFormalTypeParameterTraversalCompleted = true;
        signatureWriter.visitReturnType();
        return this;
    }

    @Override
    public SignatureVisitor visitParameterType() {
        isFormalTypeParameterTraversalCompleted = true;
        signatureWriter.visitParameterType();
        return this;
    }

    @Override
    public SignatureVisitor visitExceptionType() {
        isFormalTypeParameterTraversalCompleted = true;
        signatureWriter.visitExceptionType();
        return this;
    }

    @Override
    public void visitBaseType(final char descriptor) {
        signatureWriter.visitBaseType(descriptor);
    }

    @Override
    public void visitTypeVariable(final String name) {
        if (argumentStack == 0) {
            String className = formalParameterMap.get(name).getName().replaceAll("\\.", "/");
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
        if (argumentStack == 0 && isFormalTypeParameterTraversalCompleted) {
            signatureWriter.visitClassType(name);
        }

        argumentStack *= 2;
    }

    @Override
    public void visitInnerClassType(final String name) {
        argumentStack /= 2;

        if (argumentStack == 0 && isFormalTypeParameterTraversalCompleted) {
            signatureWriter.visitInnerClassType(name);
        }

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

        if (argumentStack == 0 && isFormalTypeParameterTraversalCompleted) {
            signatureWriter.visitEnd();
        }
    }

    public SignatureWriter getSignatureWriter() {
        return signatureWriter;
    }

    public Type[] getMethodArgumentTypes() {
        return Type.getArgumentTypes(signatureWriter.toString());
    }

    public Type getMethodReturnType() {
        return Type.getReturnType(signatureWriter.toString());
    }
}
