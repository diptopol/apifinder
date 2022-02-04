package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.typeInfo.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.*;

/**
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

    private TypeInfo currentType;

    private TypeInfo returnTypeInfo;

    private List<TypeInfo> typeArgumentList;

    private Stack<String> formalTypeParameterNameStack;

    private Map<String, TypeInfo> formalTypeParameterMap;

    public MethodReturnTypeExtractor() {
        super(Opcodes.ASM9);
        this.formalTypeParameterNameStack = new Stack<>();
        this.formalTypeParameterMap = new LinkedHashMap<>();
        this.typeArgumentList = new ArrayList<>();
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
        if (visitingReturnType && Objects.isNull(this.currentType)) {
            if (descriptor == 'V') {
                this.returnTypeInfo = new VoidTypeInfo();

            } else {
                this.returnTypeInfo = convertToArrayTypeIfRequired(new PrimitiveTypeInfo(Type.getType(Character.toString(descriptor)).getClassName()));
            }
        }
    }

    @Override
    public void visitClassType(String name) {
        if (classBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.peek();
            currentType = new FormalTypeParameterInfo(typeParameter, new QualifiedTypeInfo(name.replaceAll("/", ".")));
            classBoundVisit = false;
        } else if (interfaceBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.peek();
            currentType = new FormalTypeParameterInfo(typeParameter, new QualifiedTypeInfo(name.replaceAll("/", ".")));
            interfaceBoundVisit = false;
        } else {
            visitingTypeStack *= 2;

            if (visitingFormalTypeParameter || visitingReturnType) {
                if (Objects.nonNull(this.currentType)) {
                    this.typeArgumentList.add(new QualifiedTypeInfo(name.replaceAll("/", ".")));
                } else {
                    currentType = convertToArrayTypeIfRequired(new QualifiedTypeInfo(name.replaceAll("/", ".")));
                }
            }
        }
    }

    @Override
    public void visitEnd() {
        visitingTypeStack /= 2;

        // ignoring internal parameterized type for formalTypeParameter
        //assuming class for formaTypeParameter cannot be an array.
        if (visitingFormalTypeParameter) {
            String typeParameter = formalTypeParameterNameStack.pop();

            currentType = Objects.nonNull(currentType)
                    ? currentType
                    : new FormalTypeParameterInfo(typeParameter, new QualifiedTypeInfo("java.lang.Object"));

            formalTypeParameterMap.put(typeParameter, currentType);
            currentType = null;
        }

        if (visitingTypeStack == 0 && visitingReturnType) {
            if (Objects.nonNull(this.currentType)) {
                if (this.currentType.isQualifiedTypeInfo() && !this.typeArgumentList.isEmpty()) {
                    QualifiedTypeInfo qualifiedTypeInfo = (QualifiedTypeInfo) this.currentType;
                    ParameterizedTypeInfo parameterizedTypeInfo = new ParameterizedTypeInfo(qualifiedTypeInfo);
                    parameterizedTypeInfo.setTypeArgumentList(new ArrayList<>(this.typeArgumentList));

                    this.returnTypeInfo = parameterizedTypeInfo;
                    this.typeArgumentList.clear();
                } else {
                    this.returnTypeInfo = this.currentType;
                }

                this.currentType = null;
            }
        }
    }

    @Override
    public SignatureVisitor visitTypeArgument(final char tag) {
        if (visitingTypeStack % 2 == 0) {
            visitingTypeStack |= 1;
        }

        return this;
    }

    @Override
    public void visitTypeVariable(String name) {
        if (!visitingReturnType) {
            return;
        }

        if (Objects.isNull(this.currentType)) {
            TypeInfo typeInfo;

            if (formalTypeParameterMap.containsKey(name)) {
                typeInfo = convertToArrayTypeIfRequired(formalTypeParameterMap.get(name));

            } else {
                typeInfo = convertToArrayTypeIfRequired(new FormalTypeParameterInfo(name, new QualifiedTypeInfo("java.lang.Object")));
            }

            this.returnTypeInfo = typeInfo;
        } else {
            TypeInfo typeInfo;

            if (formalTypeParameterMap.containsKey(name)) {
                typeInfo = formalTypeParameterMap.get(name);
            } else {
                typeInfo = new FormalTypeParameterInfo(name, new QualifiedTypeInfo("java.lang.Object"));
            }

            this.typeArgumentList.add(typeInfo);
        }
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
        return returnTypeInfo;
    }

    private TypeInfo convertToArrayTypeIfRequired(TypeInfo typeInfo) {
        if (this.currentIndexArrayDimension > 0) {
            return new ArrayTypeInfo(typeInfo, this.currentIndexArrayDimension);
        }

        return typeInfo;
    }

}
