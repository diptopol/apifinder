package ca.concordia.jaranalyzer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

public class FieldInfo {
	private String name;
	private String qualifiedClassName;
	private String className;
	private Type type;
	private boolean isPublic;
	private boolean isPrivate;
	private boolean isProtected;
	private boolean isStatic;
	private String signature;

	public FieldInfo(FieldNode fieldNode, String qualifiedClassName,
			String className) {
		this.className = className;
		this.qualifiedClassName = qualifiedClassName;
		this.name = fieldNode.name;
		this.type = Type.getType(fieldNode.desc);

		if ((fieldNode.access & Opcodes.ACC_PUBLIC) != 0) {
			isPublic = true;
		} else if ((fieldNode.access & Opcodes.ACC_PROTECTED) != 0) {
			isProtected = true;
		} else if ((fieldNode.access & Opcodes.ACC_PRIVATE) != 0) {
			isPrivate = true;
		}

		if (isPrivate == false && isProtected == false && isPublic == false) {
			isPrivate = true;
		}

		if ((fieldNode.access & Opcodes.ACC_STATIC) != 0) {
			isStatic = true;
		}

		this.signature = fieldNode.signature;
	}

	public String toString() {
		StringBuilder methodDescription = new StringBuilder();
		if (isPublic) {
			methodDescription.append("public ");
		} else if (isProtected) {
			methodDescription.append("protected ");
		} else if (isPrivate) {
			methodDescription.append("private ");
		}

		if (isStatic) {
			methodDescription.append("static ");
		}

		methodDescription.append(type.getClassName());
		methodDescription.append(" ");
		methodDescription.append(name);

		return methodDescription.toString();
	}

	public String getName() {
		return name;
	}

	public String getQualifiedClassName() {
		return qualifiedClassName;
	}

	public String getClassName() {
		return className;
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

	public boolean isStatic() {
		return isStatic;
	}
	
	public String getSignature() {
		return signature;
	}
}
