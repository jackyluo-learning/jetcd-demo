package com.bolingcavalry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@SpringBootApplication
public class AdvancedOperateApplication {

    private static final ConfigUtils configUtils = new ConfigUtils(ConfConstants.EVENTMESH_CONF_HOME, ConfConstants.EVENTMESH_CONF_FILE);

    private static final String endpoints = configUtils.getProp(ConfigUtils.ConfKeys.KEYS_ENDPOINT);


    @Bean
    public EtcdService getEtcdService(){
        return new EtcdServiceImpl(endpoints);
    }

    @Bean
    public AdvancedEtcdService getAdvancedEtcdService(){
        return new AdvancedEtcdServiceImpl(endpoints);
    }

    public static void main(String[] args) {
        SpringApplication.run(AdvancedOperateApplication.class, args);
    }
}
