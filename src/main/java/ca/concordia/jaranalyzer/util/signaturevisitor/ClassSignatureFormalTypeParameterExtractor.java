package ca.concordia.jaranalyzer.util.signaturevisitor;

import ca.concordia.jaranalyzer.Models.typeInfo.FormalTypeParameterInfo;
import ca.concordia.jaranalyzer.Models.typeInfo.QualifiedTypeInfo;
import ca.concordia.jaranalyzer.Models.typeInfo.TypeInfo;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Stack;

/**
 * @author Diptopol
 * @since 2/5/2022 4:01 AM
 */
public class ClassSignatureFormalTypeParameterExtractor extends SignatureVisitor {

    private LinkedHashMap<String, TypeInfo> formalTypeParameterMap;
    private Stack<String> formalTypeParameterNameStack;

    private boolean classBoundVisit;
    private boolean interfaceBoundVisit;

    public ClassSignatureFormalTypeParameterExtractor() {
        super(Opcodes.ASM9);
        this.formalTypeParameterMap = new LinkedHashMap<>();
        this.formalTypeParameterNameStack = new Stack<>();
    }

    @Override
    public void visitFormalTypeParameter(final String name) {
        formalTypeParameterNameStack.push(name);
        formalTypeParameterMap.put(name, new FormalTypeParameterInfo(name, new QualifiedTypeInfo("java.lang.Object")));
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
    public SignatureVisitor visitClassBound() {
        classBoundVisit = true;
        return super.visitClassBound();
    }

    @Override
    public SignatureVisitor visitInterfaceBound() {
        interfaceBoundVisit = true;
        return super.visitInterfaceBound();
    }

    public LinkedHashMap<String, TypeInfo> getFormalTypeParameterMap() {
        return formalTypeParameterMap;
    }

    public List<TypeInfo> getTypeArgumentList() {
        return new ArrayList<>(this.formalTypeParameterMap.values());
    }

    private void updateByBoundClassOrInterface(String typeParameter, String name) {
        formalTypeParameterMap.put(typeParameter, new FormalTypeParameterInfo(typeParameter, new QualifiedTypeInfo(name.replaceAll("/", "."))));
    }

}
