package ca.concordia.jaranalyzer.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Diptopol
 * @since 6/28/2021 12:36 AM
 */
public class FieldSignatureFormalTypeParameterExtractor extends SignatureVisitor {

    private boolean seenTypeArgument;

    private String typeClassName;

    private List<String> typeArgumentClassNameList;

    public FieldSignatureFormalTypeParameterExtractor() {
        super(Opcodes.ASM9);
        this.typeArgumentClassNameList = new ArrayList<>();
    }

    @Override
    public void visitClassType(final String name) {
        if (!seenTypeArgument) {
            typeClassName = name.replaceAll("/", ".");
        } else {
            typeArgumentClassNameList.add(name.replaceAll("/", "."));
        }
    }

    @Override
    public void visitInnerClassType(final String name) {
        if (!seenTypeArgument) {
            typeClassName = name.replaceAll("/", ".");
        } else {
            typeArgumentClassNameList.add(name.replaceAll("/", "."));
        }
    }

    @Override
    public SignatureVisitor visitTypeArgument(char wildcard) {
        seenTypeArgument = true;
        return this;
    }

    public String getTypeClassName() {
        return typeClassName;
    }

    public List<String> getTypeArgumentClassNameList() {
        return typeArgumentClassNameList;
    }

}
