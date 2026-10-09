package cn.nitrowater.core.exception.notfound;

import org.springframework.http.HttpStatus;
import cn.nitrowater.lib.api.BaseResponseCode;

import java.io.Serializable;

public class ResourceNotFoundException extends NotFoundException {
    public ResourceNotFoundException() {
        super(BaseResponseCode.RESOURCE_NOT_FOUND);
    }

    public ResourceNotFoundException(Serializable uuid){
        super(BaseResponseCode.RESOURCE_NOT_FOUND_ARGS, HttpStatus.BAD_REQUEST, uuid);
    }
}
