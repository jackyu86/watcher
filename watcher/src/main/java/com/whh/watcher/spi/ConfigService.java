package com.whh.watcher.spi;

import com.whh.watcher.domain.config.JvmConfig;
import com.whh.watcher.domain.config.SystemConfig;

/**
 * Dept：
 * User:wanghanghang
 * Date:2016/4/28
 * Version:1.0
 */
public interface ConfigService {
    /**
     * 获取JVM配置相关信息
     * @return
     */
    public JvmConfig getJvmConfig();


    public SystemConfig getSystemConfig();
}
