package ca.concordia.jaranalyzer.model;

import javax.persistence.*;

@Entity
@Table(name = "method")
public class Method {
	private int id;
	private int jar;
	private String name;
	private int arguments;
	private String returnType;
	private String argumentTypes;
	
	@Id
	@Column(name = "id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	
	@Column(name = "jar")
	public int getJar() {
		return jar;
	}
	public void setJar(int jar) {
		this.jar = jar;
	}
	@Column(name = "name")
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	@Column(name = "arguments")
	public int getArguments() {
		return arguments;
	}
	public void setArguments(int arguments) {
		this.arguments = arguments;
	}
	
	@Column(name = "returntype")
	public String getReturnType() {
		return returnType;
	}
	public void setReturnType(String returnType) {
		this.returnType = returnType;
	}
	
	@Column(name = "argumenttypes")
	public String getArgumentTypes() {
		return argumentTypes;
	}
	public void setArgumentTypes(String argumentTypes) {
		this.argumentTypes = argumentTypes;
	}
	
	
}
