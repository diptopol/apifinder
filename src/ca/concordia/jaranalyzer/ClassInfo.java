package ca.concordia.jaranalyzer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

public class ClassInfo {
	private String qualifiedName;
	private String name;
	private Type type;
	private boolean isPublic;
	private boolean isPrivate;
	private boolean isProtected;
	private boolean isAbstract;
	private boolean isInterface;
	private ArrayList<MethodInfo> methods;
	private ArrayList<FieldInfo> fields;

	public ClassInfo(ClassNode classNode) {
		this.methods = new ArrayList<MethodInfo>();
		this.fields = new ArrayList<FieldInfo>();
		
		this.qualifiedName = classNode.name.replace('/', '.');
		if(!classNode.name.contains("/")) {
			this.name = classNode.name;
		} else {
			this.name = classNode.name.substring(classNode.name.lastIndexOf('/')+1, classNode.name.length());
		}
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
			methods.add(new MethodInfo(methodNode, qualifiedName, name));
		}
		
		List<FieldNode> fieldNodes = classNode.fields;
		for (FieldNode fieldNode : fieldNodes) {
			fields.add(new FieldInfo(fieldNode, qualifiedName, name));
		}
	}

	public String getQualifiedName() {
		return qualifiedName;
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

	public ArrayList<MethodInfo> getPublicMethods() {
		ArrayList<MethodInfo> publicMethods = new ArrayList<MethodInfo>();
		for (MethodInfo methodInfo : getMethods()) {
			if (methodInfo.isPublic())
				publicMethods.add(methodInfo);
		}
		return publicMethods;
	}

	public String toString() {
		StringBuilder classDescription = new StringBuilder();

		classDescription.append(getSignature());

		for (MethodInfo method : methods) {
			classDescription.append("\n\t" + method.toString());
		}

		return classDescription.toString();
	}

	public String getSignature() {
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

		return classDescription.toString();
	}

	public List<FieldInfo> getFieldsByName(String fieldName) {
		List<FieldInfo> matchedFields = new ArrayList<FieldInfo>();
		for (FieldInfo fieldInfo : fields) {
			if(fieldInfo.getName().equals(fieldName)){
				matchedFields.add(fieldInfo);
			}
		}
		return matchedFields;		
	}
}
