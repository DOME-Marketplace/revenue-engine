package it.eng.dome.revenue.engine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

	private static final long serialVersionUID = -2359903864142722447L;

	public NotFoundException(String message) {
        super(message);
    }
}