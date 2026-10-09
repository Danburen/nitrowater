package cn.nitrowater.core.exception;

import cn.nitrowater.lib.api.BaseResponseCode;

public class TicketNotFoundException extends BizException {
    public TicketNotFoundException() {
        super(BaseResponseCode.TicketNotFoundException);

    }
}
