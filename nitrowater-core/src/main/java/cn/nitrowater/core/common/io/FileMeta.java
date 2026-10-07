package cn.nitrowater.core.common.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import cn.nitrowater.core.utils.JsonUtil;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileMeta {
    @JsonProperty("etag")
    private String eTag;

    public String toJson() {
        return JsonUtil.toJson(this);
    }
}
