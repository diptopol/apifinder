package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.TypeObject;
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

    private List<TypeObject> typeArgumentClassObjList;

    public FieldSignatureFormalTypeParameterExtractor() {
        super(Opcodes.ASM9);
        this.typeArgumentClassObjList = new ArrayList<>();
    }

    @Override
    public void visitClassType(final String name) {
        if (!seenTypeArgument) {
            typeClassName = name.replaceAll("/", ".");
        } else {
            typeArgumentClassObjList.add(new TypeObject(name.replaceAll("/", ".")));
        }
    }

    @Override
    public void visitInnerClassType(final String name) {
        if (!seenTypeArgument) {
            typeClassName = name.replaceAll("/", ".");
        } else {
            typeArgumentClassObjList.add(new TypeObject(name.replaceAll("/", ".")));
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

    public List<TypeObject> getTypeArgumentClassObjList() {
        return typeArgumentClassObjList;
    }

}
