package com.bolingcavalry;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

@Slf4j
public class ConfigUtils {
    private String directoryPath;

    private String fileName;

    private Properties properties = new Properties();

    private String file;

    public ConfigUtils (String directoryPath, String fileName) {
        this.directoryPath = directoryPath
                .replace('/', File.separator.charAt(0))
                .replace('\\', File.separator.charAt(0));
        this.fileName = fileName;
        this.file = (directoryPath + File.separator + fileName)
                .replace('/', File.separator.charAt(0))
                .replace('\\', File.separator.charAt(0));
        log.debug("directoryPath: {}; file: {}", this.directoryPath, this.file);
        init();
    }

    private void init() {
        load();
    }

    private void load() {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            log.info("loading config: {}", file);
            properties.load(reader);
        } catch (IOException e) {
            log.error("loading properties [{}] error", file, e);
        }
    }

    public String getProp(String key) {
        return StringUtils.isEmpty(key) ? null : properties.getProperty(key, null);
    }

    static class ConfKeys {
        public static final String KEYS_ENDPOINT = "env.endpoint";
    }
}
