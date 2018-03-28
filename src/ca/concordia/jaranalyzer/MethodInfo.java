package ca.concordia.jaranalyzer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class MethodInfo {
	private String name;
	private String qualifiedClassName;
	private String className;
	private int jar;
	private Type[] argumentTypes;
	private Type returnType;
	private List<String> thrownInternalClassNames;
	private boolean isPublic;
	private boolean isPrivate;
	private boolean isProtected;
	private boolean isAbstract;
	private boolean isStatic;
	private boolean isSynchronized;
	private boolean isConstructor;

	@SuppressWarnings("unchecked")
	public MethodInfo(MethodNode methodNode, String qualifiedClassName,
			String className) {
		this.name = methodNode.name;
		if (name.equals("<init>"))
			name = className;
		this.qualifiedClassName = qualifiedClassName;
		this.className = className;
		
		if(name.equals(className)){
			isConstructor = true;
		}
		
		this.returnType = Type.getReturnType(methodNode.desc);
		this.argumentTypes = Type.getArgumentTypes(methodNode.desc);
		this.thrownInternalClassNames = methodNode.exceptions;

		if ((methodNode.access & Opcodes.ACC_PUBLIC) != 0) {
			isPublic = true;
		} else if ((methodNode.access & Opcodes.ACC_PROTECTED) != 0) {
			isProtected = true;
		} else if ((methodNode.access & Opcodes.ACC_PRIVATE) != 0) {
			isPrivate = true;
		}

		if ((methodNode.access & Opcodes.ACC_STATIC) != 0) {
			isStatic = true;
		}

		if ((methodNode.access & Opcodes.ACC_ABSTRACT) != 0) {
			isAbstract = true;
		}

		if ((methodNode.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
			isSynchronized = true;
		}
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

		if (isAbstract) {
			methodDescription.append("abstract ");
		}

		if (isSynchronized) {
			methodDescription.append("synchronized ");
		}

		methodDescription.append(returnType.getClassName());
		methodDescription.append(" ");
		methodDescription.append(this.name);

		methodDescription.append("(");
		for (int i = 0; i < argumentTypes.length; i++) {
			Type argumentType = argumentTypes[i];
			if (i > 0) {
				methodDescription.append(", ");
			}
			methodDescription.append(argumentType.getClassName());
		}
		methodDescription.append(")");

		if (!thrownInternalClassNames.isEmpty()) {
			methodDescription.append(" throws ");
			int i = 0;
			for (String thrownInternalClassName : thrownInternalClassNames) {
				if (i > 0) {
					methodDescription.append(", ");
				}
				methodDescription.append(Type.getObjectType(
						thrownInternalClassName).getClassName());
				i++;
			}
		}

		return methodDescription.toString();
	}

	public void setJar(int jar) {
		this.jar = jar;

	}

	public void setName(String name) {
		this.name = name;
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

	public int getJar() {
		return jar;
	}

	public Type[] getArgumentTypes() {
		return argumentTypes;
	}

	public boolean isConstructor() {
		return isConstructor;
	}

	public String getParameterTypes() {
		String parameters = "";
		for (Type type : argumentTypes) {
			parameters += type.getClassName() + ",";// .substring(type.getClassName().lastIndexOf('.')
													// + 1) + " ";
		}
		return parameters;
	}

	public String getReturnType() {
		return returnType.getClassName();// .substring(returnType.getClassName().lastIndexOf('.')
											// + 1);
	}

	public List<String> getThrownInternalClassNames() {
		return thrownInternalClassNames;
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

	public boolean isStatic() {
		return isStatic;
	}

	public boolean isSynchronized() {
		return isSynchronized;
	}

	public boolean matches(String methodName, int numberOfParameters) {
		boolean isMatched = false;
		
		if (name.replace('$', '.').equals(methodName)
				&& argumentTypes.length == numberOfParameters) {
			isMatched = true;
		} else if ((className + name).replace('$', '.').equals(methodName)
				&& argumentTypes.length == numberOfParameters){
			isMatched = true;
		} else if ((qualifiedClassName + name).replace('$', '.').equals(methodName)
				&& argumentTypes.length == numberOfParameters){
			isMatched = true;
		} else if (isConstructor) {
			if(qualifiedClassName.replace('$', '.').equals(methodName)
					&& argumentTypes.length == numberOfParameters) {
				isMatched = true;
			}
		}
		
		return isMatched;
	}

}
