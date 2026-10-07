package cn.nitrowater.core.services.auth.code;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import cn.nitrowater.core.api.auth.VerifyChannel;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CodeVerifierFactory {
    private final Map<VerifyChannel, CodeVerifier> verifierMap;
    public CodeVerifierFactory(List<CodeVerifier> verifiers) {
        verifierMap = verifiers.stream()
                .filter(v -> v.channel() != null)
                .collect(Collectors.toUnmodifiableMap(CodeVerifier::channel, Function.identity()));
    }

    public CodeVerifier of(@NotNull VerifyChannel channel) {
        return verifierMap.get(channel);
    }
}
