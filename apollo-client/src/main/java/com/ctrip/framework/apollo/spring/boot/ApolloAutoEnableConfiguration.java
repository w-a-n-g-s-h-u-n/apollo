package com.ctrip.framework.apollo.spring.boot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;

@Configuration
@EnableApolloConfig
@ConditionalOnProperty(name = "apollo.enable", havingValue = "true")
public class ApolloAutoEnableConfiguration {

}
