package searchengine.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Component
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler
    public ResponseEntity<AppError> catchIndexingException(IndexingException ie) {
        log.error(ie.getMessage(), ie);
        return new ResponseEntity<>(new AppError(HttpStatus.BAD_REQUEST.value(), ie.getMessage()), HttpStatus.BAD_REQUEST);
    }

}
