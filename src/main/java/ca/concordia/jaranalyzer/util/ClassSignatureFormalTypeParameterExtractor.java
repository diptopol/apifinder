package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.TypeObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * @author Diptopol
 * @since 6/28/2021 12:39 AM
 */
public class ClassSignatureFormalTypeParameterExtractor extends SignatureVisitor {

    private List<TypeObject> typeClassObjList;

    private Map<String, TypeObject> formalTypeParameterMap;
    private Stack<String> formalTypeParameterNameStack;

    private boolean seenFormalTypeParameter;
    private boolean classBoundVisit;
    private boolean interfaceBoundVisit;

    private int currentTypeClassNameIndex;

    public ClassSignatureFormalTypeParameterExtractor(List<TypeObject> typeClassObjList) {
        super(Opcodes.ASM9);
        this.formalTypeParameterMap = new HashMap<>();
        this.formalTypeParameterNameStack = new Stack<>();
        this.typeClassObjList = typeClassObjList;
    }

    @Override
    public void visitFormalTypeParameter(final String name) {
        seenFormalTypeParameter = true;
        formalTypeParameterNameStack.push(name);

        if (!typeClassObjList.isEmpty()) {
            formalTypeParameterMap.put(name, typeClassObjList.get(currentTypeClassNameIndex));

            if (seenFormalTypeParameter) {
                currentTypeClassNameIndex++;
            }
        } else {
            formalTypeParameterMap.put(name, new TypeObject("java.lang.Object"));
        }
    }

    @Override
    public void visitClassType(String name) {
        if (classBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.pop();
            updateByBoundClassOrInterface(typeParameter, name);
            classBoundVisit = false;

        } else if (interfaceBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.pop();
            updateByBoundClassOrInterface(typeParameter, name);
            interfaceBoundVisit = false;
        }
    }

    @Override
    public SignatureVisitor visitSuperclass() {
        seenFormalTypeParameter = false;

        return this;
    }

    @Override
    public SignatureVisitor visitInterface() {
        seenFormalTypeParameter = false;

        return this;
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

    public Map<String, TypeObject> getFormalTypeParameterMap() {
        return formalTypeParameterMap;
    }

    private void updateByBoundClassOrInterface(String typeParameter, String name) {
        if ("java.lang.Object".equals(formalTypeParameterMap.get(typeParameter).getQualifiedClassName())) {
            formalTypeParameterMap.put(typeParameter, new TypeObject(name.replaceAll("/", ".")));
        }
    }

}
