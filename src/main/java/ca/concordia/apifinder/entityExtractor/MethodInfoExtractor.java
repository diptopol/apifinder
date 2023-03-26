package ca.concordia.apifinder.entityExtractor;

import ca.concordia.apifinder.entity.ClassInfo;
import ca.concordia.apifinder.entity.MethodInfo;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

/**
 * @author Diptopol
 * @since 7/13/2022 6:49 PM
 */
public class MethodInfoExtractor {

    public static MethodInfo getMethodInfo(MethodNode methodNode, ClassInfo classInfo) {
        MethodInfo methodInfo = new MethodInfo();

        String internalClassConstructorPrefix = null;
        String methodName = methodNode.name;

        if (methodName.equals("<init>")) {
            methodInfo.setConstructor(true);

            if (classInfo.isInnerClass()) {
                internalClassConstructorPrefix =
                        classInfo.getName().substring(0, classInfo.getName().lastIndexOf("$") + 1);
                methodName = classInfo.getName().replace(internalClassConstructorPrefix, "");
            } else {
                methodName = classInfo.getName();
            }
        }

        methodInfo.setInternalClassConstructorPrefix(internalClassConstructorPrefix);
        methodInfo.setName(methodName);

        methodInfo.setReturnType(Type.getReturnType(methodNode.desc));
        methodInfo.setArgumentTypes(Type.getArgumentTypes(methodNode.desc));
        methodInfo.setThrownInternalClassNames(methodNode.exceptions);
        methodInfo.setSignature(methodNode.signature);

        if ((methodNode.access & Opcodes.ACC_PUBLIC) != 0) {
            methodInfo.setPublic(true);
        } else if ((methodNode.access & Opcodes.ACC_PROTECTED) != 0) {
            methodInfo.setProtected(true);
        } else if ((methodNode.access & Opcodes.ACC_PRIVATE) != 0) {
            methodInfo.setPrivate(true);
        }

        if ((methodNode.access & Opcodes.ACC_STATIC) != 0) {
            methodInfo.setStatic(true);
        }

        if ((methodNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            methodInfo.setAbstract(true);
        }

        if ((methodNode.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            methodInfo.setSynchronized(true);
        }

        if ((methodNode.access & Opcodes.ACC_VARARGS) != 0) {
            methodInfo.setVarargs(true);
        }

        if ((methodNode.access & Opcodes.ACC_FINAL) != 0) {
            methodInfo.setFinal(true);
        }

        if ((methodNode.access & Opcodes.ACC_BRIDGE) != 0) {
            methodInfo.setBridgeMethod(true);
        }

        return methodInfo;
    }
}
