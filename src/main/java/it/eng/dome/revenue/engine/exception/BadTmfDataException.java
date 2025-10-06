package it.eng.dome.revenue.engine.exception;

public class BadTmfDataException extends Exception {

    public BadTmfDataException(String tmfType, String tmfId, String message) {
        super(String.format("TMF entity type '%s' with id '%s' has the following issue: %s", tmfType, tmfId, message));
    }

}
