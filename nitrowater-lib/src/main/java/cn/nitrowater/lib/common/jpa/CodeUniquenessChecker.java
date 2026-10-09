package cn.nitrowater.lib.common.jpa;

import cn.nitrowater.lib.common.constratin.UniquenessChecker;

public interface CodeUniquenessChecker extends UniquenessChecker {
    boolean existsByCode(String code);

    default boolean existsWithUniqueIdentify(String value){
        return existsByCode(value);
    }
}
