package ca.concordia.apifinder.util.signaturevisitor;

import ca.concordia.apifinder.models.typeInfo.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * @author Diptopol
 * @since 10/5/2022 5:35 PM
 */
public class ParameterizedSuperClassTypeInfoExtractor extends SignatureVisitor {

    private List<TypeInfo> typeParameterInfoList;
    private Stack<TypeInfo> superClassTypeInfoStack;

    private boolean visitingSuperClass;

    private int currentIndexArrayDimension;

    private int argumentStack;

    public ParameterizedSuperClassTypeInfoExtractor(List<TypeInfo> typeParameterInfoList) {
        super(Opcodes.ASM9);
        this.typeParameterInfoList = typeParameterInfoList;
        this.superClassTypeInfoStack = new Stack<>();

        this.currentIndexArrayDimension = 0;
    }

    @Override
    public void visitClassType(String name) {
        argumentStack *= 2;

        if (visitingSuperClass) {
            this.superClassTypeInfoStack.add(convertToArrayTypeIfRequired(new QualifiedTypeInfo(getQualifiedName(name))));
        }
    }

    @Override
    public void visitEnd() {
        endArguments();

        if (argumentStack == 0 && visitingSuperClass) {
            this.currentIndexArrayDimension = 0;
        }
    }

    @Override
    public SignatureVisitor visitTypeArgument(final char tag) {
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

        if (visitingSuperClass) {
            this.superClassTypeInfoStack.push(new QualifiedTypeInfo("java.lang.Object"));
        }
    }

    @Override
    public void visitTypeVariable(String name) {
        processForTypeVariableVisit(name);
    }

    @Override
    public SignatureVisitor visitSuperclass() {
        visitingSuperClass = true;
        this.currentIndexArrayDimension = 0;

        return this;
    }

    @Override
    public void visitInnerClassType(final String name) {
        processEndOfTypeArguments();
        argumentStack *= 2;
        processInnerClassVisit(name);
    }

    @Override
    public SignatureVisitor visitInterface() {
        visitingSuperClass = true;
        this.currentIndexArrayDimension = 0;

        return this;
    }

    @Override
    public SignatureVisitor visitArrayType() {
        if (argumentStack == 0 && visitingSuperClass) {
            this.currentIndexArrayDimension++;
        }

        return this;
    }

    private void endArguments() {
        if (argumentStack % 2 == 1) {
            processEndOfTypeArguments();
        }

        argumentStack /= 2;
    }

    public List<TypeInfo> getSuperClassTypeInfoList() {
        return new ArrayList<>(superClassTypeInfoStack)
                .stream()
                .filter(TypeInfo::isParameterizedTypeInfo)
                .collect(Collectors.toList());
    }

    private void processForTypeArgumentVisit() {
        if (!visitingSuperClass) {
            return;
        }

        TypeInfo typeInfo = this.superClassTypeInfoStack.pop();

        if (typeInfo.isArrayTypeInfo()) {
            ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;

            assert arrayTypeInfo.getElementTypeInfo().isQualifiedTypeInfo();

            ParameterizedTypeInfo parameterizedTypeInfo =
                    new ParameterizedTypeInfo(arrayTypeInfo.getElementTypeInfo().getQualifiedClassName());

            this.superClassTypeInfoStack.push(new ArrayTypeInfo(parameterizedTypeInfo, arrayTypeInfo.getDimension()));
        } else if (typeInfo.isQualifiedTypeInfo()) {
            QualifiedTypeInfo qualifiedTypeInfo = (QualifiedTypeInfo) typeInfo;

            superClassTypeInfoStack.push(new ParameterizedTypeInfo(qualifiedTypeInfo));
        } else {
            superClassTypeInfoStack.push(typeInfo);
        }
    }

    private void processForTypeVariableVisit(String name) {
        if (!visitingSuperClass) {
            return;
        }

        TypeInfo typeInfo;

        if (Objects.nonNull(getTypeArgumentInfo(name))) {
            typeInfo = convertToArrayTypeIfRequired(getTypeArgumentInfo(name));

        } else {
            typeInfo = convertToArrayTypeIfRequired(new FormalTypeParameterInfo(name,
                    new QualifiedTypeInfo("java.lang.Object")));
        }

        this.superClassTypeInfoStack.push(typeInfo);
    }

    private void processInnerClassVisit(String name) {
        if (!visitingSuperClass) {
            return;
        }

        TypeInfo typeInfo = this.superClassTypeInfoStack.pop();
        String qualifiedName = typeInfo.getQualifiedClassName() + "." + name;

        if (typeInfo.isArrayTypeInfo()) {
            ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;

            this.superClassTypeInfoStack.push(new ArrayTypeInfo(new QualifiedTypeInfo(qualifiedName),
                    arrayTypeInfo.getDimension()));

        } else if (typeInfo.isParameterizedTypeInfo() || typeInfo.isQualifiedTypeInfo()) {
            this.superClassTypeInfoStack.push(new QualifiedTypeInfo(qualifiedName));

        } else {
            throw new IllegalStateException();
        }
    }

    private void processEndOfTypeArguments() {
        if (!visitingSuperClass) {
            return;
        }

        Integer indexOfParameterizedType = null;
        for (int i = 0; i < this.superClassTypeInfoStack.size(); i++) {
            TypeInfo currentTypeInfo = this.superClassTypeInfoStack.get(i);

            if (isParameterizedType(currentTypeInfo)) {
                indexOfParameterizedType = i;
            }
        }

        if (Objects.nonNull(indexOfParameterizedType)) {
            List<TypeInfo> argumentTypeList = new ArrayList<>(this.superClassTypeInfoStack.subList(indexOfParameterizedType + 1,
                    this.superClassTypeInfoStack.size()));
            this.superClassTypeInfoStack.subList(indexOfParameterizedType + 1, this.superClassTypeInfoStack.size()).clear();

            TypeInfo typeInfo = this.superClassTypeInfoStack.peek();

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

    private TypeInfo convertToArrayTypeIfRequired(TypeInfo typeInfo) {
        if (this.currentIndexArrayDimension > 0) {
            ArrayTypeInfo arrayTypeInfo = new ArrayTypeInfo(typeInfo, this.currentIndexArrayDimension);
            this.currentIndexArrayDimension = 0;

            return arrayTypeInfo;
        }

        return typeInfo;
    }

    private TypeInfo getTypeArgumentInfo(String typeParameter) {
        return this.typeParameterInfoList.stream()
                .filter(TypeInfo::isFormalTypeParameterInfo)
                .map(f -> (FormalTypeParameterInfo) f)
                .filter(f -> f.getTypeParameter().equals(typeParameter))
                .findAny()
                .orElse(null);
    }

    private String getQualifiedName(String name) {
        return name.replaceAll("/", ".").replaceAll("\\$", ".");
    }

}
