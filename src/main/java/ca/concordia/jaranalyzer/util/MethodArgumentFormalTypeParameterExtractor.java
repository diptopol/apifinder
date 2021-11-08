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

    private int argumentStack;
    private int currentArgumentIndex;
    private int currentFormalTypeParameterIndexPerArgument;
    private int currentIndexArrayDimension;

    private List<TypeObject> methodArgumentList;
    private List<String> traversedFormalTypeParameterList;
    private Stack<String> formalTypeParameterNameStack;

    private Map<String, String> formalTypeParameterMap;


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
            formalTypeParameterMap.put(typeParameter, name.replaceAll("/", "."));
            classBoundVisit = false;
        } else if (interfaceBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.pop();
            formalTypeParameterMap.put(typeParameter, name.replaceAll("/", "."));
            interfaceBoundVisit = false;
        } else {
            argumentStack *= 2;
            hasArgumentClassName = true;

            TypeObject typeObject = methodArgumentList.get(currentArgumentIndex);
            assert name.replaceAll("/", ".").equals(typeObject.getQualifiedClassName());
        }
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
    public void visitTypeVariable(String name) {
        if (traversedFormalTypeParameterList.contains(name)) {
            return;
        }

        if (currentArgumentIndex >= methodArgumentList.size()) {
            return;
        }

        traversedFormalTypeParameterList.add(name);
        TypeObject argumentType = methodArgumentList.get(currentArgumentIndex);

        if (argumentType.isParameterized()) {
            List<TypeObject> argumentTypeList = argumentType.getArgumentTypeObjectList();
            String argumentTypeClassName = resolveArrayDimensions(
                    argumentTypeList.get(currentFormalTypeParameterIndexPerArgument).getQualifiedClassName());

            formalTypeParameterMap.put(name, argumentTypeClassName);

            currentFormalTypeParameterIndexPerArgument++;

        } else {
            if (!hasArgumentClassName) {
                formalTypeParameterMap.put(name, resolveArrayDimensions(argumentType.getQualifiedClassName()));
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

    public Map<String, String> getFormalTypeParameterMap() {
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
