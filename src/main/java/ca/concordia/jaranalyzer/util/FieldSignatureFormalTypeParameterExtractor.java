package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.typeInfo.*;
import io.vavr.Tuple3;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
 * @author Diptopol
 * @since 2/5/2022 12:16 PM
 */
public class FieldSignatureFormalTypeParameterExtractor extends SignatureVisitor {

    private int argumentStack;
    private int currentIndexArrayDimension;

    private Stack<TypeInfo> typeArgumentStack;

    public FieldSignatureFormalTypeParameterExtractor() {
        super(Opcodes.ASM9);
        this.typeArgumentStack = new Stack<>();

        this.currentIndexArrayDimension = 0;
    }

    @Override
    public void visitClassType(final String name) {
        argumentStack *= 2;
        this.typeArgumentStack.push(
                convertToArrayTypeIfRequired(new QualifiedTypeInfo(name.replaceAll("/", "."))));
    }

    @Override
    public void visitTypeVariable(String name) {
        this.typeArgumentStack.push(convertToArrayTypeIfRequired(new FormalTypeParameterInfo(name,
                new QualifiedTypeInfo("java.lang.Object"))));
    }

    @Override
    public void visitInnerClassType(final String name) {
        endArguments();
        argumentStack *= 2;

        processInnerClassVisit(name);
    }

    @Override
    public void visitEnd() {
        endArguments();

        if (argumentStack == 0) {
            this.currentIndexArrayDimension = 0;
        }
    }

    @Override
    public SignatureVisitor visitTypeArgument(char wildcard) {
        if (argumentStack % 2 == 0) {
            argumentStack |= 1;

            processForTypeArgumentVisit();
        }

        return this;
    }

    @Override
    public void visitTypeArgument() {
        if (argumentStack % 2 == 0) {
            argumentStack |= 1;

            processForTypeArgumentVisit();
        }

        this.typeArgumentStack.push(new QualifiedTypeInfo("java.lang.Object"));
    }

    @Override
    public SignatureVisitor visitArrayType() {
        if (argumentStack == 0) {
            this.currentIndexArrayDimension++;
        }

        return this;
    }

    private void processForTypeArgumentVisit() {
        TypeInfo typeInfo = this.typeArgumentStack.pop();

        if (typeInfo.isArrayTypeInfo()) {
            ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;

            assert arrayTypeInfo.getElementTypeInfo().isQualifiedTypeInfo();

            ParameterizedTypeInfo parameterizedTypeInfo =
                    new ParameterizedTypeInfo(arrayTypeInfo.getElementTypeInfo().getQualifiedClassName());

            this.typeArgumentStack.push(new ArrayTypeInfo(parameterizedTypeInfo, arrayTypeInfo.getDimension()));
        } else if (typeInfo.isQualifiedTypeInfo()) {
            QualifiedTypeInfo qualifiedTypeInfo = (QualifiedTypeInfo) typeInfo;

            typeArgumentStack.push(new ParameterizedTypeInfo(qualifiedTypeInfo));
        } else if (!typeInfo.isParameterizedTypeInfo()) {
            throw new IllegalStateException();
        }
    }

    private void processInnerClassVisit(String name) {
        TypeInfo typeInfo = this.typeArgumentStack.pop();
        String qualifiedName = typeInfo.getQualifiedClassName() + "." + name;

        if (typeInfo.isArrayTypeInfo()) {
            ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;

            this.typeArgumentStack.push(new ArrayTypeInfo(new QualifiedTypeInfo(qualifiedName),
                    arrayTypeInfo.getDimension()));

        } else if (typeInfo.isParameterizedTypeInfo() || typeInfo.isQualifiedTypeInfo()) {
            this.typeArgumentStack.push(new QualifiedTypeInfo(qualifiedName));

        } else {
            throw new IllegalStateException();
        }

    }

    private void processEndOfTypeArguments() {
        int indexOfParameterizedType = 0;
        for (int i = 0; i < this.typeArgumentStack.size(); i++) {
            TypeInfo currentTypeInfo = this.typeArgumentStack.get(i);

            if (isParameterizedType(currentTypeInfo)) {
                indexOfParameterizedType = i;
            }
        }

        List<TypeInfo> argumentTypeList = new ArrayList<>(this.typeArgumentStack.subList(indexOfParameterizedType + 1,
                this.typeArgumentStack.size()));
        this.typeArgumentStack.subList(indexOfParameterizedType + 1, this.typeArgumentStack.size()).clear();

        TypeInfo typeInfo = this.typeArgumentStack.peek();

        if (typeInfo.isArrayTypeInfo()) {
            ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;
            assert arrayTypeInfo.getElementTypeInfo().isParameterizedTypeInfo();

            ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) arrayTypeInfo.getElementTypeInfo();
            parameterizedTypeInfo.setParameterized(true);
            parameterizedTypeInfo.setTypeArgumentList(argumentTypeList);

        } else if (typeInfo.isParameterizedTypeInfo()) {
            ParameterizedTypeInfo parameterizedTypeInfo = (ParameterizedTypeInfo) typeInfo;
            parameterizedTypeInfo.setParameterized(true);
            parameterizedTypeInfo.setTypeArgumentList(argumentTypeList);

        } else {
            throw new IllegalStateException();
        }
    }

    private boolean isParameterizedType(TypeInfo typeInfo) {
        if (typeInfo.isParameterizedTypeInfo()) {
            return ((ParameterizedTypeInfo) typeInfo).getTypeArgumentList().isEmpty();
        } else if (typeInfo.isArrayTypeInfo()) {
            ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;

            TypeInfo elementTypeInfo = arrayTypeInfo.getElementTypeInfo();

            return elementTypeInfo.isParameterizedTypeInfo()
                    && ((ParameterizedTypeInfo) elementTypeInfo).getTypeArgumentList().isEmpty();

        }

        return false;
    }

    private void endArguments() {
        if (argumentStack % 2 == 1) {
            processEndOfTypeArguments();
        }

        argumentStack /= 2;
    }

    private TypeInfo convertToArrayTypeIfRequired(TypeInfo typeInfo) {
        if (this.currentIndexArrayDimension > 0) {
            ArrayTypeInfo arrayTypeInfo = new ArrayTypeInfo(typeInfo, this.currentIndexArrayDimension);
            this.currentIndexArrayDimension = 0;

            return arrayTypeInfo;
        }

        return typeInfo;
    }

    public TypeInfo getTypeInfo() {
        assert typeArgumentStack.size() == 1;

        return typeArgumentStack.get(0);
    }

}
