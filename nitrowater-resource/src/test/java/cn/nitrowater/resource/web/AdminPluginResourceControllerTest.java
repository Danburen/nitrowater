package cn.nitrowater.resource.web;

import cn.nitrowater.resource.application.PluginArtifactPolicy;
import cn.nitrowater.core.web.ApiResponseAdvice;
import cn.nitrowater.resource.application.ResourceService;
import cn.nitrowater.resource.application.dto.ResourceMeta;
import cn.nitrowater.resource.config.ResourceProperties;
import cn.nitrowater.resource.domain.ResourceStatus;
import cn.nitrowater.resource.domain.ResourceType;
import cn.nitrowater.resource.domain.ResourceVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.SpringDataJackson3Configuration;
import org.springframework.data.web.config.SpringDataWebSettings;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-contract tests for the admin plugin-artifact surface: raw-body upload, the mandatory
 * {@code Content-Length}, the jar/ZIP gate before the service is called, list mapping and delete.
 */
@ExtendWith(MockitoExtension.class)
class AdminPluginResourceControllerTest {

    private static final String KEY = "velochatx";
    private static final String NAME = "plugin.jar";
    private static final byte[] JAR = new byte[] {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0, 1, 2, 3, 4, 5, 6};
    private static final ResourceMeta META = new ResourceMeta(
            "11111111-2222-3333-4444-555555555555", PluginArtifactPolicy.PLUGIN_APP, KEY, NAME,
            ResourceType.ARCHIVE, MediaType.APPLICATION_OCTET_STREAM_VALUE, (long) JAR.length,
            ResourceVisibility.PUBLIC, ResourceStatus.ACTIVE, "1.2.3", Instant.parse("2026-10-27T00:00:00Z"),
            "/api/public/resource/11111111-2222-3333-4444-555555555555/download");

    @Mock
    private ResourceService resourceService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ResourceProperties properties = new ResourceProperties(
                "local", 600, 10L * 1024 * 1024, 64L * 1024 * 1024, null, null, null, null, null);
        // Standalone MockMvc does not pick up @EnableSpringDataWebSupport; register the same
        // VIA_DTO page module the application uses so Page bodies serialize as {content, page}.
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new SpringDataJackson3Configuration.PageModule(
                        new SpringDataWebSettings(EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)))
                .build();
        mvc = MockMvcBuilders.standaloneSetup(
                        new AdminPluginResourceController(resourceService, new PluginArtifactPolicy(properties)))
                .setControllerAdvice(new ApiResponseAdvice(), new ResourceExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .build();
    }

    @Test
    void uploadPassesTheSanitizedNameAndDeclaredSize() throws Exception {
        when(resourceService.publishPluginArtifact(
                eq(KEY), eq(NAME), eq("1.2.3"), any(InputStream.class), eq((long) JAR.length)))
                .thenReturn(META);

        mvc.perform(post("/api/admin/plugin-resources/{key}", KEY)
                        .param("name", "..\\" + NAME)
                        .param("version", "1.2.3")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(JAR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uuid").value(META.uuid()))
                .andExpect(jsonPath("$.data.name").value(NAME))
                .andExpect(jsonPath("$.data.version").value("1.2.3"));

        verify(resourceService).publishPluginArtifact(
                eq(KEY), eq(NAME), eq("1.2.3"), any(InputStream.class), eq((long) JAR.length));
    }

    @Test
    void uploadRequiresContentLength() throws Exception {
        mvc.perform(post("/api/admin/plugin-resources/{key}", KEY).param("name", NAME))
                .andExpect(status().isLengthRequired())
                .andExpect(jsonPath("$.code").value("length_required"));

        verifyNoInteractions(resourceService);
    }

    @Test
    void uploadRejectsNonZipContentBeforeTheService() throws Exception {
        byte[] notJar = "not a jar".getBytes(StandardCharsets.UTF_8);

        mvc.perform(post("/api/admin/plugin-resources/{key}", KEY)
                        .param("name", NAME)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(notJar))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported_type"));

        verifyNoInteractions(resourceService);
    }

    @Test
    void uploadRejectsNonJarNameBeforeTheService() throws Exception {
        mvc.perform(post("/api/admin/plugin-resources/{key}", KEY)
                        .param("name", "plugin.zip")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(JAR))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported_type"));

        verifyNoInteractions(resourceService);
    }

    @Test
    void listMapsThePublicDownloadLink() throws Exception {
        when(resourceService.listPluginArtifacts(eq(KEY), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(META)));

        mvc.perform(get("/api/admin/plugin-resources/{key}", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].uuid").value(META.uuid()))
                .andExpect(jsonPath("$.data.content[0].app").value(PluginArtifactPolicy.PLUGIN_APP))
                .andExpect(jsonPath("$.data.content[0].url").value(META.url()));
    }

    @Test
    void deleteScopesTheUuidToThePlugin() throws Exception {
        mvc.perform(delete("/api/admin/plugin-resources/{key}/{uuid}", KEY, META.uuid()))
                .andExpect(status().isNoContent());

        verify(resourceService).deletePluginArtifact(KEY, META.uuid());
    }
}