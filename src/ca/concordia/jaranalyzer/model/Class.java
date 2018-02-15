package ca.concordia.jaranalyzer.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import ca.concordia.jaranalyzer.ClassInfo;

@Entity
@Table(name = "class")
public class Class {
	private int id;
	private String name;
	private int packageId;
	
	public Class(){
		
	}
	
	public Class(ClassInfo classInfo) {
		this.name = classInfo.getSignature();
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getPackageId() {
		return packageId;
	}

	public void setPackageId(int packageId) {
		this.packageId = packageId;
	}
}
