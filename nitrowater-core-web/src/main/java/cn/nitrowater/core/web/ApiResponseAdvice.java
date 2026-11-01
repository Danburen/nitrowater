package cn.nitrowater.core.web;

import cn.nitrowater.lib.api.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps every JSON controller response into {@link ApiResponse}, so controllers keep returning
 * plain payloads while the wire contract stays uniform.
 *
 * <p>Only the JSON converter is intercepted: HTML views, {@code byte[]}/streamed downloads,
 * {@code SseEmitter} and plain strings go through other converters and are left untouched. A
 * handler that already returns an {@code ApiResponse} (every exception handler now does) is passed
 * through unchanged, so errors keep {@code success=false}.</p>
 *
 * <p>Services opt in with {@code @Import(ApiResponseAdvice.class)}: the library package is not
 * component-scanned, and the BFF must stay opt-out because it proxies upstream JSON verbatim.</p>
 */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (converterType == null) {
            return false;
        }
        // Jackson 2 ships MappingJackson2HttpMessageConverter; Spring Framework 7 / Jackson 3 ships
        // JacksonJsonHttpMessageConverter. Matching on the name keeps this module independent of
        // which of the two a service happens to pull in.
        String converter = converterType.getName();
        return converter.contains("Jackson") || converter.contains("Json");
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?>) {
            return body;
        }
        return ApiResponse.success(body);
    }
}
