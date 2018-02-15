package ca.concordia.jaranalyzer.model;

import javax.persistence.*;

import ca.concordia.jaranalyzer.MethodInfo;

@Entity
@Table(name = "method")
public class Method {
	private int id;
	private int classId;
	private String name;
	private int arguments;
	private String returnType;
	private String argumentTypes;

	public Method() {
	}
	
	public Method(MethodInfo methodInfo) {
		this.name = methodInfo.getName();
		this.arguments = methodInfo.getArgumentTypes().length;
		this.argumentTypes = methodInfo.getParameterTypes();
		this.returnType = methodInfo.getReturnType();
	}
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	
	public int getClassId() {
		return classId;
	}
	public void setClassId(int classId) {
		this.classId = classId;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	public int getArguments() {
		return arguments;
	}
	public void setArguments(int arguments) {
		this.arguments = arguments;
	}
	
	public String getReturnType() {
		return returnType;
	}
	public void setReturnType(String returnType) {
		this.returnType = returnType;
	}
	
	public String getArgumentTypes() {
		return argumentTypes;
	}
	public void setArgumentTypes(String argumentTypes) {
		this.argumentTypes = argumentTypes;
	}
	
	
}
