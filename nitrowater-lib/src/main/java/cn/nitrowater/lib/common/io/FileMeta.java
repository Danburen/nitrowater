package cn.nitrowater.lib.common.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import cn.nitrowater.lib.utils.JsonUtil;

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
