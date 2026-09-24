package com.wuyuxiao.aippt.web;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class,NoSuchElementException.class})
    ResponseEntity<Map<String,String>> handle(RuntimeException e){HttpStatus status=e instanceof NoSuchElementException?HttpStatus.NOT_FOUND:HttpStatus.BAD_REQUEST;return ResponseEntity.status(status).body(Map.of("message",e.getMessage()==null?"请求失败":e.getMessage()));}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,String>> validation(){return ResponseEntity.badRequest().body(Map.of("message","大纲标题和页面标题不能为空"));}
}

