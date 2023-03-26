package ca.concordia.apifinder.util.signaturevisitor;

import ca.concordia.apifinder.models.typeInfo.TypeInfo;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * @author Diptopol
 * @since 6/22/2021 12:15 PM
 */
public class GenericTypeResolutionAdapter extends SignatureVisitor {

    private SignatureWriter signatureWriter;

    private Map<String, TypeInfo> formalParameterMap;

    private int argumentStack;

    private boolean classBoundVisit;
    private boolean visitingFormalTypeParameter;

    private Stack<String> formalTypeParameterNameStack;

    private Map<String, String> formalTypeParameterConversionMap;

    private boolean isFormalTypeParameterTraversalCompleted;

    /**
     * @param formalTypeParameterMap : This map will be used to resolve formal types
     */
    public GenericTypeResolutionAdapter(Map<String, TypeInfo> formalTypeParameterMap) {
        super(Opcodes.ASM9);
        this.formalTypeParameterNameStack = new Stack<>();
        this.formalTypeParameterConversionMap = new HashMap<>();
        this.signatureWriter = new SignatureWriter();
        this.formalParameterMap = formalTypeParameterMap;
    }

    @Override
    public void visitFormalTypeParameter(final String name) {
        formalTypeParameterNameStack.push(name);
        visitingFormalTypeParameter = true;
    }

    @Override
    public SignatureVisitor visitClassBound() {
        classBoundVisit = true;
        return this;
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
        if (visitingFormalTypeParameter && classBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.pop();

            formalTypeParameterConversionMap.put(typeParameter, name);
            classBoundVisit = false;
        }

        if (argumentStack == 0 && isFormalTypeParameterTraversalCompleted) {
            /*
             * Sometimes, formalType parameter map may not contain all the formal type parameters that required for the
             * method. In that case, we are treating as type parameter as it is without resolving class name.
             */
            if (formalParameterMap.containsKey(name)) {
                String className = formalParameterMap.get(name).getName().replaceAll("\\.", "/");
                signatureWriter.visitClassType(className);

            } else if (formalTypeParameterConversionMap.containsKey(name)
                    && formalParameterMap.containsKey(formalTypeParameterConversionMap.get(name))) {

                String className = formalParameterMap.get(formalTypeParameterConversionMap.get(name)).getName().replaceAll("\\.", "/");
                signatureWriter.visitClassType(className);

            } else {
                signatureWriter.visitClassType(name);
            }

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
        if (visitingFormalTypeParameter && classBoundVisit) {
            if (!formalTypeParameterNameStack.isEmpty()) {
                formalTypeParameterNameStack.pop();
            }

            classBoundVisit = false;
        }

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
