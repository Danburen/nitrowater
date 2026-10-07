package cn.nitrowater.core.lib.api.resp.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import cn.nitrowater.core.lib.api.auth.VerifyChannel;

@Builder
@AllArgsConstructor
@Getter
@Setter
public class CodeResult {
    private final boolean sendSuccess;
    private final String target;
    private final VerifyChannel channel;
    protected String key;

    public static  CodeResult success(String target, VerifyChannel channel) {
        return CodeResult.builder().sendSuccess(true).target(target).channel(channel).build();
    }

    public CodeResult withKey(String key){
        this.key = key;
        return this;
    }

    public static CodeResult fail(String target, VerifyChannel channel, String message) {
        return  CodeResult.builder()
                .sendSuccess(false)
                .target(target)
                .channel(channel)
               .build();
    }
}
