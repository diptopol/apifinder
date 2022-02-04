package ca.concordia.jaranalyzer.util;

import ca.concordia.jaranalyzer.Models.typeInfo.FormalTypeParameterInfo;
import ca.concordia.jaranalyzer.Models.typeInfo.QualifiedTypeInfo;
import ca.concordia.jaranalyzer.Models.typeInfo.TypeInfo;
import io.vavr.Tuple3;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Diptopol
 * @since 2/5/2022 12:16 PM
 */
public class FieldSignatureFormalTypeParameterExtractor extends SignatureVisitor {

    private boolean seenTypeArgument;

    private String fieldFormalTypeParameterName;
    private String fieldTypeClassName;
    private List<TypeInfo> typeArgumentList;

    public FieldSignatureFormalTypeParameterExtractor() {
        super(Opcodes.ASM9);
        this.typeArgumentList = new ArrayList<>();
    }

    @Override
    public void visitClassType(final String name) {
        if (!seenTypeArgument) {
            fieldTypeClassName = name.replaceAll("/", ".");
        } else {
            typeArgumentList.add(new QualifiedTypeInfo(name.replaceAll("/", ".")));
        }
    }

    @Override
    public void visitTypeVariable(String name) {
        if (!seenTypeArgument) {
            fieldFormalTypeParameterName = name;
        } else {
            typeArgumentList.add(new FormalTypeParameterInfo(name, new QualifiedTypeInfo("java.lang.Object")));
        }
    }

    @Override
    public void visitInnerClassType(final String name) {
        if (!seenTypeArgument) {
            fieldTypeClassName = name.replaceAll("/", ".");
        } else {
            typeArgumentList.add(new QualifiedTypeInfo(name.replaceAll("/", ".")));
        }
    }

    @Override
    public SignatureVisitor visitTypeArgument(char wildcard) {
        seenTypeArgument = true;
        return this;
    }

    /*
    '* TODO: JAVADOC
     */
    public Tuple3<String, String, List<TypeInfo>> getFieldSignatureInfo() {
        return new Tuple3<>(this.fieldTypeClassName, this.fieldFormalTypeParameterName, this.typeArgumentList);
    }

}
