package searchengine.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IndexingSitesException.class)
    public ResponseEntity<AppError> catchIndexingException(IndexingSitesException ie) {
        log.error(ie.getMessage(), ie);
        return new ResponseEntity<>(new AppError(false, ie.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourcesNotFoundException.class)
    public ResponseEntity<AppError> catchResourceNotFoundException(ResourcesNotFoundException re) {
        log.error(re.getMessage(), re);
        return new ResponseEntity<>(new AppError(false, re.getMessage()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AppError> catchAllExceptions(Exception e) {
        log.error("Непредвиденная ошибка: {}", e.getMessage(), e);
        return new ResponseEntity<>(new AppError(false, "Внутренняя ошибка сервера"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
