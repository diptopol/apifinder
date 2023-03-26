package ca.concordia.apifinder.util.signaturevisitor;

import ca.concordia.apifinder.models.typeInfo.FormalTypeParameterInfo;
import ca.concordia.apifinder.models.typeInfo.QualifiedTypeInfo;
import ca.concordia.apifinder.models.typeInfo.TypeInfo;
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

    public List<TypeInfo> getTypeArgumentList() {
        return new ArrayList<>(this.formalTypeParameterMap.values());
    }

    private void updateByBoundClassOrInterface(String typeParameter, String name) {
        formalTypeParameterMap.put(typeParameter, new FormalTypeParameterInfo(typeParameter, new QualifiedTypeInfo(name.replaceAll("/", "."))));
    }

}
