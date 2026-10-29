package cn.nitrowater.server.web.dto;

import java.util.List;

/** Administrator reset request; an empty or omitted uid list means all users for today. */
public record AiUsageResetReq(List<Long> uids) {
}
