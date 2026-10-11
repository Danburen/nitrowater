package cn.nitrowater.server.infra.third;

import cn.nitrowater.server.service.shared.ExecResult;
import cn.nitrowater.server.service.shared.ExecSpec;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * The single point of contact with the Piston execution service.
 *
 * <p>Builds the {@code POST /api/v2/execute} request, sends it through the timeout-configured
 * {@link RestClient}, and normalises the two-stage response into {@link ExecResult}. All knowledge
 * of Piston's request/response shape lives here so the rest of the server code stays decoupled.</p>
 */
@Component
public class PistonClient {

    private final RestClient client;

    public PistonClient(RestClient pistonRestClient) {
        this.client = pistonRestClient;
    }

    /**
     * Executes one piece of code in the sandbox and returns the normalised result.
     *
     * @param spec the language-agnostic execution request
     * @return normalised compile/run outcome
     * @throws org.springframework.web.client.RestClientException on transport or HTTP errors
     */
    public ExecResult execute(ExecSpec spec) {
        PistonDtos.ExecuteRequest request = new PistonDtos.ExecuteRequest(
                spec.pistonLanguage(),
                spec.version(),
                List.of(new PistonDtos.File(spec.fileName(), spec.code())),
                spec.stdin(),
                spec.runTimeoutMs(),
                spec.compileTimeoutMs(),
                spec.runMemoryBytes(),
                spec.compileMemoryBytes());

        PistonDtos.ExecuteResponse response = client.post()
                .uri("/api/v2/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PistonDtos.ExecuteResponse.class);

        if (response == null) {
            throw new IllegalStateException("Sandbox returned an empty response");
        }
        return new ExecResult(
                toStage(response.compile()),
                toStage(response.run()),
                response.language(),
                response.version());
    }

    /** Converts a Piston stage into the normalised {@link ExecResult.Stage}. */
    private static ExecResult.Stage toStage(PistonDtos.Stage stage) {
        if (stage == null) {
            return null;
        }
        return new ExecResult.Stage(
                stage.stdout(),
                stage.stderr(),
                stage.output(),
                stage.code(),
                stage.signal(),
                stage.status(),
                stage.message(),
                stage.cpuTime(),
                stage.wallTime(),
                stage.memory());
    }
}
