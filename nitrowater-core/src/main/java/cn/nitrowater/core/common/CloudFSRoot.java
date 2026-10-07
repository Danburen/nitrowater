package cn.nitrowater.core.common;


import lombok.Getter;

@Getter
public enum CloudFSRoot {
    UPLOADS("uploads"),
    SYSTEM("sys");

    private final String key;
    CloudFSRoot(String key) {
        this.key = key;
    }

}
