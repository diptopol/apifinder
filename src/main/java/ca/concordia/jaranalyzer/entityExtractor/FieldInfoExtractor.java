package ca.concordia.jaranalyzer.entityExtractor;

import ca.concordia.jaranalyzer.entity.FieldInfo;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

/**
 * @author Diptopol
 * @since 7/13/2022 6:50 PM
 */
public class FieldInfoExtractor {

    public static FieldInfo getFieldInfo(FieldNode fieldNode) {
        FieldInfo fieldInfo = new FieldInfo();

        fieldInfo.setName(fieldNode.name);
        fieldInfo.setType(Type.getType(fieldNode.desc));

        if ((fieldNode.access & Opcodes.ACC_PUBLIC) != 0) {
            fieldInfo.setPublic(true);
        } else if ((fieldNode.access & Opcodes.ACC_PROTECTED) != 0) {
            fieldInfo.setProtected(true);
        } else if ((fieldNode.access & Opcodes.ACC_PRIVATE) != 0) {
            fieldInfo.setPrivate(true);
        }

        if ((fieldNode.access & Opcodes.ACC_STATIC) != 0) {
            fieldInfo.setStatic(true);
        }

        fieldInfo.setSignature(fieldNode.signature);

        return fieldInfo;
    }
}
