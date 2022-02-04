package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.typeInfo.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.*;

/**
 * @author Diptopol
 * @since 1/30/2022 4:03 PM
 */
public class MethodArgumentExtractor extends SignatureVisitor {

    private boolean classBoundVisit;
    private boolean interfaceBoundVisit;
    private boolean visitingMethodArguments;
    private boolean visitingFormalTypeParameter;

    private int argumentStack;
    private int currentArgumentIndex;
    private int currentIndexArrayDimension;

    private TypeInfo currentType;

    private List<TypeInfo> argumentList;
    private List<TypeInfo> typeArgumentList;

    private Stack<String> formalTypeParameterNameStack;

    private Map<String, TypeInfo> formalTypeParameterMap;

    public MethodArgumentExtractor() {
        super(Opcodes.ASM9);
        this.argumentList = new ArrayList<>();
        this.formalTypeParameterNameStack = new Stack<>();
        this.formalTypeParameterMap = new LinkedHashMap<>();
        this.typeArgumentList = new ArrayList<>();

        currentArgumentIndex = 0;
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
        argumentStack /= 2;
        argumentStack *= 2;
    }

    @Override
    public void visitBaseType(char descriptor) {
        if (visitingMethodArguments && Objects.isNull(this.currentType)) {
            this.argumentList.add(convertToArrayTypeIfRequired(new PrimitiveTypeInfo(
                    Type.getType(Character.toString(descriptor)).getClassName())));
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
            argumentStack *= 2;

            if (visitingFormalTypeParameter || visitingMethodArguments) {
                currentType = convertToArrayTypeIfRequired(new QualifiedTypeInfo(name.replaceAll("/", ".")));
            }
        }
    }

    @Override
    public void visitEnd() {
        argumentStack /= 2;

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

        if (argumentStack == 0 && visitingMethodArguments) {
            if (Objects.nonNull(this.currentType)) {
                if (this.currentType.isQualifiedTypeInfo() && !this.typeArgumentList.isEmpty()) {
                    QualifiedTypeInfo qualifiedTypeInfo = (QualifiedTypeInfo) this.currentType;
                    ParameterizedTypeInfo parameterizedTypeInfo = new ParameterizedTypeInfo(qualifiedTypeInfo);
                    parameterizedTypeInfo.setTypeArgumentList(new ArrayList<>(this.typeArgumentList));

                    this.argumentList.add(currentArgumentIndex, parameterizedTypeInfo);
                    this.typeArgumentList.clear();
                } else {
                    this.argumentList.add(currentArgumentIndex, this.currentType);
                }

                this.currentType = null;
            }

            currentArgumentIndex++;
            this.currentIndexArrayDimension = 0;
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
    public void visitTypeVariable(String name) {
        if (!visitingMethodArguments) {
            return;
        }

        if (Objects.isNull(this.currentType)) {
            TypeInfo typeInfo;

            if (formalTypeParameterMap.containsKey(name)) {
                typeInfo = convertToArrayTypeIfRequired(formalTypeParameterMap.get(name));

            } else {
                typeInfo = convertToArrayTypeIfRequired(new FormalTypeParameterInfo(name, new QualifiedTypeInfo("java.lang.Object")));
            }

            this.argumentList.add(currentArgumentIndex, typeInfo);
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
        return argumentList;
    }

    private TypeInfo convertToArrayTypeIfRequired(TypeInfo typeInfo) {
        if (this.currentIndexArrayDimension > 0) {
            return new ArrayTypeInfo(typeInfo, this.currentIndexArrayDimension);
        }

        return typeInfo;
    }

}
