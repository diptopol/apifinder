package ca.concordia.jaranalyzer.util;

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

    private List<String> typeClassNameList;

    private Map<String, String> formalTypeParameterMap;
    private Stack<String> formalTypeParameterNameStack;

    private boolean seenFormalTypeParameter;
    private boolean classBoundVisit;
    private boolean interfaceBoundVisit;

    private int currentTypeClassNameIndex;

    public ClassSignatureFormalTypeParameterExtractor(List<String> typeClassNameList) {
        super(Opcodes.ASM9);
        this.formalTypeParameterMap = new HashMap<>();
        this.formalTypeParameterNameStack = new Stack<>();
        this.typeClassNameList = typeClassNameList;
    }

    @Override
    public void visitFormalTypeParameter(final String name) {
        seenFormalTypeParameter = true;
        formalTypeParameterNameStack.push(name);

        if (!typeClassNameList.isEmpty()) {
            formalTypeParameterMap.put(name, typeClassNameList.get(currentTypeClassNameIndex));

            if (seenFormalTypeParameter) {
                currentTypeClassNameIndex++;
            }
        } else {
            formalTypeParameterMap.put(name, "java.lang.Object");
        }
    }

    @Override
    public void visitClassType(String name) {
        if (classBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.pop();

            if (formalTypeParameterMap.get(typeParameter).equals("java.lang.Object")) {
                formalTypeParameterMap.put(typeParameter, name.replaceAll("/", "."));
            }

            classBoundVisit = false;
        } else if (interfaceBoundVisit) {
            String typeParameter = formalTypeParameterNameStack.pop();

            if (formalTypeParameterMap.get(typeParameter).equals("java.lang.Object")) {
                formalTypeParameterMap.put(typeParameter, name.replaceAll("/", "."));
            }

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

    public Map<String, String> getFormalTypeParameterMap() {
        return formalTypeParameterMap;
    }
}
