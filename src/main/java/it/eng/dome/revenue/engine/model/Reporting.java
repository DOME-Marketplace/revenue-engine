package it.eng.dome.revenue.engine.model;

import java.util.List;

public class Reporting {

    private String label;			// key
    private String text;            // value
    private String link;            // optional
    private List<Reporting> items;  // recursive
    
    
    public Reporting() {}

    public Reporting(String label) {
		this.label = label;
	}
    public Reporting(String label, String text) {
        this.label = label;
        this.text = text;
    }
    
    public Reporting(String label, String text, String link) {
        this.label = label;
        this.text = text;
        this.link = link;

    }

    public Reporting(String label, String text, String link, List<Reporting> items) {
        this.label = label;
        this.text = text;
        this.link = link;
        this.items = items;
    }

    public Reporting(String string, List<Reporting> items) {
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

    public List<Reporting> getItems() {
        return items;
    }

    public void setItems(List<Reporting> items) {
        this.items = items;
    }
}

