package it.eng.dome.revenue.engine.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Report {

    private String label;			// key
    private String text;            // value
    private String link;            // optional
    private List<Report> items;  // recursive
    
    
    public Report() {}

    public Report(String label) {
		this.label = label;
	}
    public Report(String label, String text) {
        this.label = label;
        this.text = text;
    }
    
    public Report(String label, String text, String link) {
        this.label = label;
        this.text = text;
        this.link = link;

    }

    public Report(String label, String text, String link, List<Report> items) {
        this.label = label;
        this.text = text;
        this.link = link;
        this.items = items;
    }

    public Report(String string, List<Report> items) {
    	this.label = string;
		this.items = items;
    }

	public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public List<Report> getItems() {
        return items;
    }

    public void setItems(List<Report> items) {
        this.items = items;
    }
}

