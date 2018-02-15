package ca.concordia.jaranalyzer.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import ca.concordia.jaranalyzer.PackageInfo;

@Entity
@Table(name = "package")
public class Package {
	private int id;
	private String name;
	private int jarId;
	
	public Package(){
		
	}
	
	public Package(PackageInfo packageInfo) {
		this.name = packageInfo.getName();
		this.jarId = packageInfo.getJarId();
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

	public int getJarId() {
		return jarId;
	}

	public void setJarId(int jarId) {
		this.jarId = jarId;
	}
}
