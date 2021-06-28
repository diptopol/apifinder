package ca.concordia.jaranalyzer.Models;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

public class FieldInfo {

	private Object id;
	private String name;
	private ClassInfo classInfo;
	private Type type;
	private boolean isPublic;
	private boolean isPrivate;
	private boolean isProtected;
	private boolean isStatic;
	private String signature;

	public FieldInfo(Vertex vertex) {
		this.id = vertex.id();
		this.name = vertex.<String>property("Name").value();

		this.isPublic = vertex.<Boolean>property("isPublic").value();
		this.isPrivate = vertex.<Boolean>property("isPrivate").value();
		this.isProtected = vertex.<Boolean>property("isProtected").value();
		this.isStatic = vertex.<Boolean>property("isStatic").value();

		this.type = Type.getType(vertex.<String>property("returnTypeDescriptor").value());

		VertexProperty<String> signatureProperty = vertex.property("signature");

		if (signatureProperty.isPresent()) {
			this.signature = signatureProperty.value();
		}
	}

	public FieldInfo(FieldNode fieldNode, ClassInfo classInfo) {
		this.classInfo = classInfo;
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

	public Object getId() {
		return id;
	}

	public ClassInfo getClassInfo() {
		return classInfo;
	}

	public String getQualifiedClassName() {
		return classInfo.getQualifiedName();
	}

	public String getClassName() {
		return classInfo.getName();
	}

	public String getPackageName() {
		return classInfo.getPackageName();
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
