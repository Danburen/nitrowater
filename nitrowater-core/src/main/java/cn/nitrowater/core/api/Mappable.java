package cn.nitrowater.core.api;

import java.util.Map;

public interface Mappable<T> {
    Map<String,T> toMap();
}
