package cn.nitrowater.core.common.jpa;

import cn.nitrowater.core.common.constratin.UniquenessChecker;

public interface CodeUniquenessChecker extends UniquenessChecker {
    boolean existsByCode(String code);

    default boolean existsWithUniqueIdentify(String value){
        return existsByCode(value);
    }
}
