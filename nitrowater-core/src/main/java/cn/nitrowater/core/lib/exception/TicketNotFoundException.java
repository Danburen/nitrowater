package cn.nitrowater.core.lib.exception;

import cn.nitrowater.core.lib.api.BaseResponseCode;

public class TicketNotFoundException extends BizException {
    public TicketNotFoundException() {
        super(BaseResponseCode.TicketNotFoundException);

    }
}
