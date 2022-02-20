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
 * @since 1/30/2022 4:03 PM
 */
public class MethodArgumentExtractor extends SignatureVisitor {

    private boolean classBoundVisit;
    private boolean interfaceBoundVisit;
    private boolean visitingMethodArguments;
    private boolean visitingFormalTypeParameter;

    private int argumentStack;
    private int currentIndexArrayDimension;

    private Stack<TypeInfo> methodArgumentTypeInfoStack;
    private Stack<String> formalTypeParameterNameStack;

    private Map<String, TypeInfo> formalTypeParameterMap;

    public MethodArgumentExtractor() {
        super(Opcodes.ASM9);
        this.formalTypeParameterNameStack = new Stack<>();
        this.formalTypeParameterMap = new LinkedHashMap<>();
        this.methodArgumentTypeInfoStack = new Stack<>();

        this.currentIndexArrayDimension = 0;
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
        endArguments();
        argumentStack *= 2;
    }

    @Override
    public void visitBaseType(char descriptor) {
        if (visitingMethodArguments) {
            this.methodArgumentTypeInfoStack.add(convertToArrayTypeIfRequired(new PrimitiveTypeInfo(
                    Type.getType(Character.toString(descriptor)).getClassName())));
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
            argumentStack *= 2;

            if (visitingMethodArguments) {
                this.methodArgumentTypeInfoStack.add(
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

        if (argumentStack == 0 && visitingMethodArguments) {
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
    }

    @Override
    public void visitTypeVariable(String name) {
        if (!visitingMethodArguments) {
            return;
        }

        processForTypeVariableVisit(name);
    }

    @Override
    public SignatureVisitor visitParameterType() {
        visitingMethodArguments = true;
        visitingFormalTypeParameter = false;

        return this;
    }

    @Override
    public SignatureVisitor visitReturnType() {
        visitingMethodArguments = false;

        return this;
    }

    @Override
    public SignatureVisitor visitArrayType() {
        if (argumentStack == 0 && visitingMethodArguments) {
            this.currentIndexArrayDimension++;
        }

        return this;
    }

    public List<TypeInfo> getArgumentList() {
        return new ArrayList<>(this.methodArgumentTypeInfoStack);
    }

    private void processForTypeArgumentVisit() {
        if (!visitingMethodArguments) {
            return;
        }

        TypeInfo typeInfo = this.methodArgumentTypeInfoStack.pop();

        if (typeInfo.isArrayTypeInfo()) {
            ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;

            assert arrayTypeInfo.getElementTypeInfo().isQualifiedTypeInfo();

            ParameterizedTypeInfo parameterizedTypeInfo =
                    new ParameterizedTypeInfo(arrayTypeInfo.getElementTypeInfo().getQualifiedClassName());

            this.methodArgumentTypeInfoStack.push(new ArrayTypeInfo(parameterizedTypeInfo, arrayTypeInfo.getDimension()));
        } else if (typeInfo.isQualifiedTypeInfo()) {
            QualifiedTypeInfo qualifiedTypeInfo = (QualifiedTypeInfo) typeInfo;

            methodArgumentTypeInfoStack.push(new ParameterizedTypeInfo(qualifiedTypeInfo));
        } else {
            throw new IllegalStateException();
        }
    }

    private void processForTypeVariableVisit(String name) {
        if (!visitingMethodArguments) {
            return;
        }

        TypeInfo typeInfo;

        if (formalTypeParameterMap.containsKey(name)) {
            typeInfo = convertToArrayTypeIfRequired(formalTypeParameterMap.get(name));

        } else {
            typeInfo = convertToArrayTypeIfRequired(new FormalTypeParameterInfo(name,
                    new QualifiedTypeInfo("java.lang.Object")));
        }

        this.methodArgumentTypeInfoStack.push(typeInfo);
    }

    private void processEndOfTypeArguments() {
        if (!visitingMethodArguments) {
            return;
        }

        int indexOfParameterizedType = 0;
        for (int i = 0; i < this.methodArgumentTypeInfoStack.size(); i++) {
            TypeInfo currentTypeInfo = this.methodArgumentTypeInfoStack.get(i);

            if (currentTypeInfo.isParameterizedTypeInfo()
                    && ((ParameterizedTypeInfo) currentTypeInfo).getTypeArgumentList().isEmpty()) {
                indexOfParameterizedType = i;
            }
        }

        List<TypeInfo> argumentTypeList = new ArrayList<>(this.methodArgumentTypeInfoStack.subList(indexOfParameterizedType + 1,
                this.methodArgumentTypeInfoStack.size()));
        this.methodArgumentTypeInfoStack.subList(indexOfParameterizedType + 1, this.methodArgumentTypeInfoStack.size()).clear();

        TypeInfo typeInfo = this.methodArgumentTypeInfoStack.peek();

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
        if (argumentStack % 2 == 1) {
            processEndOfTypeArguments();
        }

        argumentStack /= 2;
    }

    private TypeInfo convertToArrayTypeIfRequired(TypeInfo typeInfo) {
        if (this.currentIndexArrayDimension > 0) {
            return new ArrayTypeInfo(typeInfo, this.currentIndexArrayDimension);
        }

        return typeInfo;
    }

}
