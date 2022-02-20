package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.typeInfo.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.*;

/**
 * For Formal parameter <K::Ljava/lang/Comparable<TK;> we are currently considering only java/lang/Comparable as
 * base Type of formal type parameter K.
 *
 * @author Diptopol
 * @since 2/7/2022 2:40 PM
 */
public class MethodReturnTypeExtractor extends SignatureVisitor {

    private boolean classBoundVisit;
    private boolean interfaceBoundVisit;
    private boolean visitingReturnType;
    private boolean visitingFormalTypeParameter;

    private int currentIndexArrayDimension;
    private int visitingTypeStack;

    private Stack<TypeInfo> methodReturnTypeInfoStack;
    private Stack<String> formalTypeParameterNameStack;

    private Map<String, TypeInfo> formalTypeParameterMap;

    public MethodReturnTypeExtractor() {
        super(Opcodes.ASM9);
        this.formalTypeParameterNameStack = new Stack<>();
        this.formalTypeParameterMap = new LinkedHashMap<>();
        this.methodReturnTypeInfoStack = new Stack<>();
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
    public SignatureVisitor visitInterfaceBound() {
        interfaceBoundVisit = true;
        return this;
    }

    @Override
    public void visitInnerClassType(final String name) {
        visitingTypeStack /= 2;
        visitingTypeStack *= 2;
    }

    @Override
    public void visitBaseType(char descriptor) {
        if (visitingReturnType) {
            if (descriptor == 'V') {
                this.methodReturnTypeInfoStack.push(new VoidTypeInfo());

            } else {
                this.methodReturnTypeInfoStack.push(
                        convertToArrayTypeIfRequired(new PrimitiveTypeInfo(
                                Type.getType(Character.toString(descriptor)).getClassName())));
            }
        }
    }

    @Override
    public void visitClassType(String name) {
        if (classBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.pop();
            TypeInfo baseType = new FormalTypeParameterInfo(typeParameter,
                    new QualifiedTypeInfo(name.replaceAll("/", ".")));
            formalTypeParameterMap.put(typeParameter, baseType);

            classBoundVisit = false;
        } else if (interfaceBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.pop();
            TypeInfo baseType = new FormalTypeParameterInfo(typeParameter,
                    new QualifiedTypeInfo(name.replaceAll("/", ".")));
            formalTypeParameterMap.put(typeParameter, baseType);

            interfaceBoundVisit = false;
        } else {
            visitingTypeStack *= 2;

            if (visitingReturnType) {
                this.methodReturnTypeInfoStack.push(
                        convertToArrayTypeIfRequired(new QualifiedTypeInfo(name.replaceAll("/", "."))));
            }
        }
    }

    @Override
    public void visitEnd() {
        endArguments();

        if (visitingFormalTypeParameter && !formalTypeParameterNameStack.isEmpty()) {
            String typeParameter = formalTypeParameterNameStack.pop();
            formalTypeParameterMap.put(typeParameter,
                    new FormalTypeParameterInfo(typeParameter, new QualifiedTypeInfo("java.lang.Object")));
        }
    }

    @Override
    public SignatureVisitor visitTypeArgument(final char tag) {
        if (visitingTypeStack % 2 == 0) {
            visitingTypeStack |= 1;

            processForTypeArgumentVisit();
        }

        return this;
    }

    @Override
    public void visitTypeArgument() {
        if (visitingTypeStack % 2 == 0) {
            visitingTypeStack |= 1;

            processForTypeArgumentVisit();
        }
    }

    @Override
    public void visitTypeVariable(String name) {
        if (!visitingReturnType) {
            return;
        }

        processForTypeVariableVisit(name);
    }

    @Override
    public SignatureVisitor visitReturnType() {
        visitingReturnType = true;

        return this;
    }

    @Override
    public SignatureVisitor visitParameterType() {
        visitingFormalTypeParameter = false;

        return this;
    }

    @Override
    public SignatureVisitor visitExceptionType() {
        visitingReturnType = false;

        return this;
    }

    @Override
    public SignatureVisitor visitArrayType() {
        if (visitingTypeStack == 0 && visitingReturnType) {
            this.currentIndexArrayDimension++;
        }

        return this;
    }

    public TypeInfo getReturnTypeInfo() {
        assert this.methodReturnTypeInfoStack.size() == 1;

        return this.methodReturnTypeInfoStack.peek();
    }

    private void processForTypeArgumentVisit() {
        if (!visitingReturnType) {
            return;
        }

        TypeInfo typeInfo = this.methodReturnTypeInfoStack.pop();

        if (typeInfo.isArrayTypeInfo()) {
            ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;

            assert arrayTypeInfo.getElementTypeInfo().isQualifiedTypeInfo();

            ParameterizedTypeInfo parameterizedTypeInfo =
                    new ParameterizedTypeInfo(arrayTypeInfo.getElementTypeInfo().getQualifiedClassName());

            this.methodReturnTypeInfoStack.push(new ArrayTypeInfo(parameterizedTypeInfo, arrayTypeInfo.getDimension()));
        } else if (typeInfo.isQualifiedTypeInfo()) {
            QualifiedTypeInfo qualifiedTypeInfo = (QualifiedTypeInfo) typeInfo;

            methodReturnTypeInfoStack.push(new ParameterizedTypeInfo(qualifiedTypeInfo));
        } else {
            throw new IllegalStateException();
        }
    }

    private void processForTypeVariableVisit(String name) {
        if (!visitingReturnType) {
            return;
        }

        TypeInfo typeInfo;

        if (formalTypeParameterMap.containsKey(name)) {
            typeInfo = convertToArrayTypeIfRequired(formalTypeParameterMap.get(name));

        } else {
            typeInfo = convertToArrayTypeIfRequired(new FormalTypeParameterInfo(name,
                    new QualifiedTypeInfo("java.lang.Object")));
        }

        this.methodReturnTypeInfoStack.push(typeInfo);
    }

    private void processEndOfTypeArguments() {
        if (!visitingReturnType) {
            return;
        }

        int indexOfParameterizedType = 0;
        for (int i = 0; i < this.methodReturnTypeInfoStack.size(); i++) {
            TypeInfo currentTypeInfo = this.methodReturnTypeInfoStack.get(i);

            if (currentTypeInfo.isParameterizedTypeInfo()
                    && ((ParameterizedTypeInfo) currentTypeInfo).getTypeArgumentList().isEmpty()) {
                indexOfParameterizedType = i;
            }
        }

        List<TypeInfo> argumentTypeList = new ArrayList<>(this.methodReturnTypeInfoStack.subList(indexOfParameterizedType + 1,
                this.methodReturnTypeInfoStack.size()));
        this.methodReturnTypeInfoStack.subList(indexOfParameterizedType + 1, this.methodReturnTypeInfoStack.size()).clear();

        TypeInfo typeInfo = this.methodReturnTypeInfoStack.peek();

        if (typeInfo.isArrayTypeInfo()) {
            ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;
            assert arrayTypeInfo.getElementTypeInfo().isParameterizedTypeInfo();

            ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) arrayTypeInfo.getElementTypeInfo();
            parameterizedTypeInfo.setTypeArgumentList(argumentTypeList);

        } else if (typeInfo.isParameterizedTypeInfo()) {
            ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;
            parameterizedTypeInfo.setTypeArgumentList(argumentTypeList);

        } else {
            throw new IllegalStateException();
        }
    }

    private void endArguments() {
        if (visitingTypeStack % 2 == 1) {
            processEndOfTypeArguments();
        }

        visitingTypeStack /= 2;
    }

    private TypeInfo convertToArrayTypeIfRequired(TypeInfo typeInfo) {
        if (this.currentIndexArrayDimension > 0) {
            return new ArrayTypeInfo(typeInfo, this.currentIndexArrayDimension);
        }

        return typeInfo;
    }

}
