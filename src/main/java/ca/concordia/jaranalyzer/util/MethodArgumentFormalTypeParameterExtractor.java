package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.TypeObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.*;

/**
 * @author Diptopol
 * @since 11/2/2021 11:12 AM
 */
public class MethodArgumentFormalTypeParameterExtractor extends SignatureVisitor {

    private boolean seenParameters;
    private boolean hasArgumentClassName;
    private boolean classBoundVisit;
    private boolean interfaceBoundVisit;
    private boolean formalTypeArgument;

    private int argumentStack;
    private int currentArgumentIndex;
    private int currentFormalTypeParameterIndexPerArgument;
    private int currentIndexArrayDimension;

    private List<TypeObject> methodArgumentList;
    private List<String> traversedFormalTypeParameterList;
    private Stack<String> formalTypeParameterNameStack;

    private Map<String, TypeObject> formalTypeParameterMap;


    public MethodArgumentFormalTypeParameterExtractor(List<TypeObject> methodArgumentList) {
        super(Opcodes.ASM9);
        formalTypeParameterNameStack = new Stack<>();
        formalTypeParameterMap = new HashMap<>();
        traversedFormalTypeParameterList = new ArrayList<>();
        this.methodArgumentList = methodArgumentList;
        currentArgumentIndex = 0;
        currentFormalTypeParameterIndexPerArgument = 0;
        currentIndexArrayDimension = 0;
    }

    @Override
    public void visitFormalTypeParameter(final String name) {
        formalTypeParameterNameStack.push(name);
    }

    @Override
    public SignatureVisitor visitClassBound() {
        classBoundVisit = true;
        return super.visitClassBound();
    }

    @Override
    public SignatureVisitor visitInterfaceBound() {
        interfaceBoundVisit = true;
        return super.visitInterfaceBound();
    }

    @Override
    public void visitInnerClassType(final String name) {
        argumentStack /= 2;
        argumentStack *= 2;
    }

    @Override
    public void visitClassType(String name) {
        if (classBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.pop();
            formalTypeParameterMap.put(typeParameter, new TypeObject(name.replaceAll("/", ".")));
            classBoundVisit = false;
        } else if (interfaceBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.pop();
            formalTypeParameterMap.put(typeParameter, new TypeObject(name.replaceAll("/", ".")));
            interfaceBoundVisit = false;
        } else {
            argumentStack *= 2;
            hasArgumentClassName = true;

            if (argumentStack == 0 && seenParameters) {
                TypeObject typeObject = methodArgumentList.get(currentArgumentIndex);

                assert name.replaceAll("/", ".").equals(typeObject.getQualifiedClassName());
            }
        }
    }

    @Override
    public void visitTypeArgument() {
        if (argumentStack % 2 == 0) {
            argumentStack |= 1;
        }

        formalTypeArgument = true;
    }

    @Override
    public SignatureVisitor visitTypeArgument(final char tag) {
        if (argumentStack % 2 == 0) {
            argumentStack |= 1;
        }

        formalTypeArgument = true;

        return this;
    }

    @Override
    public void visitTypeVariable(String name) {
        if (traversedFormalTypeParameterList.contains(name)) {
            return;
        }

        if (currentArgumentIndex >= methodArgumentList.size()) {
            return;
        }

        TypeObject argumentType = methodArgumentList.get(currentArgumentIndex);

        if (!formalTypeArgument) {
            currentArgumentIndex++;
        }

        if (argumentType.isParameterized()) {
            List<TypeObject> argumentTypeList = argumentType.getArgumentTypeObjectList();
            TypeObject argumentTypeClassObj = argumentTypeList.get(currentFormalTypeParameterIndexPerArgument);
            argumentTypeClassObj.setQualifiedClassName(resolveArrayDimensions(argumentTypeClassObj.getQualifiedClassName()));

            formalTypeParameterMap.put(name, argumentTypeClassObj);
            traversedFormalTypeParameterList.add(name);

            currentFormalTypeParameterIndexPerArgument++;

        } else {
            if (!hasArgumentClassName) {
                argumentType.setQualifiedClassName(resolveArrayDimensions(argumentType.getQualifiedClassName()));
                formalTypeParameterMap.put(name, argumentType);
                traversedFormalTypeParameterList.add(name);
            }
        }
    }

    @Override
    public SignatureVisitor visitParameterType() {
        seenParameters = true;

        return this;
    }

    @Override
    public void visitEnd() {
        argumentStack /= 2;

        if (argumentStack == 0 && seenParameters) {
            currentArgumentIndex++;
            currentFormalTypeParameterIndexPerArgument = 0;
            currentIndexArrayDimension = 0;
            hasArgumentClassName = false;
            formalTypeArgument = false;
        }
    }

    @Override
    public void visitBaseType(char descriptor) {
        if (argumentStack == 0 && seenParameters) {
            currentArgumentIndex++;
            currentIndexArrayDimension = 0;
        }
    }

    @Override
    public SignatureVisitor visitArrayType() {
        if (argumentStack == 0 && seenParameters) {
            currentIndexArrayDimension++;
        }

        return this;
    }

    public Map<String, TypeObject> getFormalTypeParameterMap() {
        return formalTypeParameterMap;
    }

    private String resolveArrayDimensions(String qualifiedClassName) {
        int dimensionCount = 0;

        while (dimensionCount++ < currentIndexArrayDimension) {
            qualifiedClassName = qualifiedClassName.replaceFirst("\\[]", "");
        }

        return qualifiedClassName;
    }

}
