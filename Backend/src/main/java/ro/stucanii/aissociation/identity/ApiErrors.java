package ro.stucanii.aissociation.identity;

import java.util.LinkedHashMap;
import java.util.Map;
import ro.stucanii.aissociation.platform.PlatformAssociationController;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AuthController.class, PlatformAssociationController.class})
public class ApiErrors {
    public record ErrorBody(String code, String message, Map<String, String> fieldErrors) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> business(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(new ErrorBody(ex.code(), ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ErrorBody> duplicate() {
        return ResponseEntity.status(409).body(new ErrorBody("EMAIL_EXISTS", "An account with this email already exists.", Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorBody("VALIDATION_ERROR", "Check the submitted fields.", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> malformed() {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_JSON", "Provide a valid JSON request body.", Map.of()));
    }
}
