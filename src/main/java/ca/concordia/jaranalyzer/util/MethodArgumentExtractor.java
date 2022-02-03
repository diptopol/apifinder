package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.TypeObject;
import org.apache.commons.lang3.StringUtils;
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

    private TypeObject currentTypeObject;

    private List<TypeObject> argumentList;
    private Map<String, TypeObject> parameterizedTypeArgumentMap;

    private Stack<String> formalTypeParameterNameStack;

    private Map<String, TypeObject> formalTypeParameterMap;

    public MethodArgumentExtractor() {
        super(Opcodes.ASM9);
        this.argumentList = new ArrayList<>();
        this.formalTypeParameterNameStack = new Stack<>();
        this.formalTypeParameterMap = new LinkedHashMap<>();
        this.parameterizedTypeArgumentMap = new LinkedHashMap<>();

        currentArgumentIndex = 0;
        this.currentIndexArrayDimension = 0;
    }

    @Override
    public void visitFormalTypeParameter(final String name) {
        formalTypeParameterNameStack.push(name);
        visitingFormalTypeParameter = true;
    }

    public List<TypeObject> getArgumentList() {
        return argumentList;
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
    public void visitBaseType(char descriptor) {
        if (visitingMethodArguments && Objects.isNull(this.currentTypeObject)) {
            this.argumentList.add(new TypeObject(addArrayDimensions(Type.getType(Character.toString(descriptor)).getClassName())));
        }
    }

    @Override
    public void visitClassType(String name) {
        if (classBoundVisit) {
            currentTypeObject = new TypeObject(name.replaceAll("/", "."));
            classBoundVisit = false;
        } else if (interfaceBoundVisit) {
            currentTypeObject = new TypeObject(name.replaceAll("/", "."));
            interfaceBoundVisit = false;
        } else {
            argumentStack *= 2;

            if (visitingFormalTypeParameter || visitingMethodArguments) {
                currentTypeObject = new TypeObject(addArrayDimensions(name.replaceAll("/", ".")));
            }
        }
    }

    @Override
    public void visitEnd() {
        argumentStack /= 2;

        if (visitingFormalTypeParameter) {
            String typeParameter = formalTypeParameterNameStack.pop();

            currentTypeObject = Objects.nonNull(currentTypeObject)
                    ? currentTypeObject
                    : new TypeObject(addArrayDimensions("java.lang.Object"));

            if (!this.parameterizedTypeArgumentMap.isEmpty()) {
                currentTypeObject.setArgumentTypeObjectMap(this.parameterizedTypeArgumentMap);
                this.parameterizedTypeArgumentMap.clear();
            }

            formalTypeParameterMap.put(typeParameter, currentTypeObject);
            currentTypeObject = null;
        }

        if (argumentStack == 0 && visitingMethodArguments) {
            if (!this.parameterizedTypeArgumentMap.isEmpty()) {
                if (Objects.nonNull(this.currentTypeObject)) {
                    this.currentTypeObject.setArgumentTypeObjectMap(this.parameterizedTypeArgumentMap);
                    this.argumentList.add(currentArgumentIndex, this.currentTypeObject);
                    this.currentTypeObject = null;
                    this.parameterizedTypeArgumentMap.clear();
                }
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

        if (Objects.isNull(this.currentTypeObject)) {
            TypeObject typeObject;
            if (formalTypeParameterMap.containsKey(name)) {
                typeObject = formalTypeParameterMap.get(name);

                typeObject.setQualifiedClassName(addArrayDimensions(typeObject.getQualifiedClassName()));

            } else {
                typeObject = new TypeObject(addArrayDimensions("java.lang.Object"));
            }

            this.argumentList.add(currentArgumentIndex, typeObject);
        } else {
            TypeObject typeObject;
            if (formalTypeParameterMap.containsKey(name)) {
                typeObject = formalTypeParameterMap.get(name);
            } else {
                typeObject = new TypeObject("java.lang.Object");
            }

            this.parameterizedTypeArgumentMap.put(name, typeObject);
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

    private String addArrayDimensions(String qualifiedClassName) {
        return qualifiedClassName.concat(StringUtils.repeat("[]", this.currentIndexArrayDimension));
    }

}
