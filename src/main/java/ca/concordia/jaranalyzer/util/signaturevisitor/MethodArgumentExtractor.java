package ca.concordia.jaranalyzer.util.signaturevisitor;

import ca.concordia.jaranalyzer.models.typeInfo.*;
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

        processInnerClassVisit(name);
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
            /*
             * Formal type parameter can consist of single class and multiple interfaces. Here we are ignoring the other
             * interfaces for simplicity's sake. If we face any issue with this approach, we will facilitate the storing
             * process of all the classes and interfaces.
             *
             * TODO: facilitate the storing process of all the classes and interfaces of formal type parameter
             */
            if (formalTypeParameterNameStack.isEmpty()) {
                interfaceBoundVisit = false;
                return;
            }

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

        if (visitingMethodArguments) {
            this.methodArgumentTypeInfoStack.push(new QualifiedTypeInfo("java.lang.Object"));
        }
    }

    /*
     * during visitFormalParameter this method can be invoked.
     */
    @Override
    public void visitTypeVariable(String name) {
        if (visitingFormalTypeParameter && classBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.pop();

            if (formalTypeParameterMap.containsKey(name)) {
                TypeInfo typeInfo = formalTypeParameterMap.get(name);

                formalTypeParameterMap.put(typeParameter, typeInfo);
            } else {
                TypeInfo typeInfo = new FormalTypeParameterInfo(name,
                        new QualifiedTypeInfo("java.lang.Object"));

                formalTypeParameterMap.put(typeParameter, typeInfo);
            }

            classBoundVisit = false;
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

    public List<TypeInfo> getFormalTypeParameterList() {
        return new ArrayList<>(this.formalTypeParameterMap.values());
    }

    /*
     * For inner class (Lcom/sun/beans/util/Cache<TK;TV;>.CacheEntry<TK;TV;>) we are currently replacing parent class
     * with inner class with full name including child class name. We are also removing type arguments of parent class
     * for simplicity. We will change our approach if required.
     */
    private void processInnerClassVisit(String name) {
        if (!visitingMethodArguments) {
            return;
        }

        TypeInfo typeInfo = this.methodArgumentTypeInfoStack.pop();
        String qualifiedName = typeInfo.getQualifiedClassName() + "." + name;

        if (typeInfo.isArrayTypeInfo()) {
            ArrayTypeInfo arrayTypeInfo = (ArrayTypeInfo) typeInfo;

            this.methodArgumentTypeInfoStack.push(new ArrayTypeInfo(new QualifiedTypeInfo(qualifiedName),
                    arrayTypeInfo.getDimension()));

        } else if (typeInfo.isParameterizedTypeInfo() || typeInfo.isQualifiedTypeInfo()) {
            this.methodArgumentTypeInfoStack.push(new QualifiedTypeInfo(qualifiedName));

        } else {
            throw new IllegalStateException();
        }
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
        } else if (!typeInfo.isParameterizedTypeInfo()) {
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

    private void processEndOfTypeArguments() {
        if (!visitingMethodArguments) {
            return;
        }

        int indexOfParameterizedType = 0;
        for (int i = 0; i < this.methodArgumentTypeInfoStack.size(); i++) {
            TypeInfo currentTypeInfo = this.methodArgumentTypeInfoStack.get(i);

            if (isParameterizedType(currentTypeInfo)) {
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

}
