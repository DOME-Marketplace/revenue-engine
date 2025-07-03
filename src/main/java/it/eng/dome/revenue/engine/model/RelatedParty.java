package it.eng.dome.revenue.engine.model;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelatedParty {

	private String id;
	private String name;
	private String role;
	private String href;
	
	public RelatedParty() {}	
	
	public RelatedParty(String id, String name, String role, String href) {
		
        this.id = id; 
		this.name = name;
		this.role = role;
		this.href = href;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getHref() {
		return href;
	}

	public void setHref(String href) {
		this.href = href;
	}
	
	
	
	
}
