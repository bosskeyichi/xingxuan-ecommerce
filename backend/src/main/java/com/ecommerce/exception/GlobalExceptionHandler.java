package com.ecommerce.exception;

import com.ecommerce.common.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("参数校验失败");
        return Result.error(400, msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("参数绑定失败");
        return Result.error(400, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        return Result.error(400, e.getMessage());
    }

    /** 请求体格式错误（如日期字段传了空字符串）：返回 400 而不是 500 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return Result.error(400, "请求参数格式不正确，请检查日期、数字等字段");
    }

    /** 路径 / 查询参数类型不匹配 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.error(400, "参数 " + e.getName() + " 格式不正确");
    }

    /** 唯一索引冲突：返回友好提示而不是 500 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一约束冲突：{}", e.getMessage());
        return Result.error(400, "数据已存在，请勿重复提交");
    }

    /**
     * 兜底异常处理：只记录日志，不把内部堆栈信息返回给前端，别让泄露表结构等敏感信息。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleAll(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "服务器开小差了，请稍后再试"));
    }
}
