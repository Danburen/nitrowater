package cn.nitrowater.core.exception;

import cn.nitrowater.core.api.BaseResponseCode;

public class TicketNotFoundException extends BizException {
    public TicketNotFoundException() {
        super(BaseResponseCode.TicketNotFoundException);

    }
}
