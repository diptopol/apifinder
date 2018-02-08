package ca.concordia.jaranalyzer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

public class ClassInfo {
	private String name;
	private Type type;
	private boolean isPublic;
	private boolean isPrivate;
	private boolean isProtected;
	private boolean isAbstract;
	private boolean isInterface;
	private ArrayList<MethodInfo> methods;

	public ClassInfo(ClassNode classNode) {
		this.methods = new ArrayList<MethodInfo>();
		this.name = classNode.name.replace('/', '.');
		this.type = Type.getObjectType(classNode.name);

		if ((classNode.access & Opcodes.ACC_PUBLIC) != 0) {
			isPublic = true;
		} else if ((classNode.access & Opcodes.ACC_PROTECTED) != 0) {
			isProtected = true;
		} else if ((classNode.access & Opcodes.ACC_PRIVATE) != 0) {
			isPrivate = true;
		}

		if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
			isAbstract = true;
		}

		if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
			isInterface = true;
		}

		@SuppressWarnings("unchecked")
		List<MethodNode> methodNodes = classNode.methods;
		for (MethodNode methodNode : methodNodes) {
			methods.add(new MethodInfo(methodNode, name));
		}
	}

	public String getName() {
		return name;
	}

	public Type getType() {
		return type;
	}

	public boolean isPublic() {
		return isPublic;
	}

	public boolean isPrivate() {
		return isPrivate;
	}

	public boolean isProtected() {
		return isProtected;
	}

	public boolean isAbstract() {
		return isAbstract;
	}

	public boolean isInterface() {
		return isInterface;
	}

	public ArrayList<MethodInfo> getMethods() {
		return methods;
	}

	public String toString() {
		StringBuilder classDescription = new StringBuilder();

		if (isPublic) {
			classDescription.append("public ");
		} else if (isProtected) {
			classDescription.append("protected ");
		} else if (isPrivate) {
			classDescription.append("private ");
		}

		if (isAbstract) {
			classDescription.append("abstract ");
		}

		if (isInterface) {
			classDescription.append("interface ");
		} else {
			classDescription.append("class ");
		}

		classDescription.append(type.getClassName());

		for (MethodInfo method : methods) {
			classDescription.append("\n\t" + method.toString());
		}

		return classDescription.toString();
	}
}
