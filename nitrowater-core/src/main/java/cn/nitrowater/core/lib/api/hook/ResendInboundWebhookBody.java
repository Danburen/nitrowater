package cn.nitrowater.core.lib.api.hook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResendInboundWebhookBody {
    private String type;
    private String createdAt;
    private EmailData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmailData {
        private String emailId;
        private String createdAt;
        private String from;
        private List<String> to;
        private List<String> bcc;
        private List<String> cc;
        private List<String> receivedFor;
        private String messageId;
        private String subject;
        private List<Attachment> attachments;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attachment {
        private String id;
        private String filename;
        private String contentType;
        private String contentDisposition;
        private String contentId;
    }
}