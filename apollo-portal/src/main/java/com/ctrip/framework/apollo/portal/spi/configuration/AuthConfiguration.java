package com.ctrip.framework.apollo.portal.spi.configuration;

import java.util.Collections;
import java.util.EventListener;
import java.util.Map;

import javax.servlet.Filter;
import javax.sql.DataSource;

import org.jasig.cas.client.session.SingleSignOutFilter;
import org.jasig.cas.client.validation.Cas20ServiceTicketValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.cas.ServiceProperties;
import org.springframework.security.cas.authentication.CasAuthenticationProvider;
import org.springframework.security.cas.web.CasAuthenticationEntryPoint;
import org.springframework.security.cas.web.CasAuthenticationFilter;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import com.ctrip.framework.apollo.common.condition.ConditionalOnMissingProfile;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.repository.UserRepository;
import com.ctrip.framework.apollo.portal.spi.LogoutHandler;
import com.ctrip.framework.apollo.portal.spi.SsoHeartbeatHandler;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;
import com.ctrip.framework.apollo.portal.spi.UserService;
import com.ctrip.framework.apollo.portal.spi.cas.CasAuthenticationUserDetailsService;
import com.ctrip.framework.apollo.portal.spi.cas.CasProperties;
import com.ctrip.framework.apollo.portal.spi.ctrip.CtripLogoutHandler;
import com.ctrip.framework.apollo.portal.spi.ctrip.CtripSsoHeartbeatHandler;
import com.ctrip.framework.apollo.portal.spi.ctrip.CtripUserInfoHolder;
import com.ctrip.framework.apollo.portal.spi.ctrip.CtripUserService;
import com.ctrip.framework.apollo.portal.spi.defaultimpl.DefaultLogoutHandler;
import com.ctrip.framework.apollo.portal.spi.defaultimpl.DefaultSsoHeartbeatHandler;
import com.ctrip.framework.apollo.portal.spi.defaultimpl.DefaultUserInfoHolder;
import com.ctrip.framework.apollo.portal.spi.defaultimpl.DefaultUserService;
import com.ctrip.framework.apollo.portal.spi.ldap.FilterLdapByGroupUserSearch;
import com.ctrip.framework.apollo.portal.spi.ldap.LdapUserService;
import com.ctrip.framework.apollo.portal.spi.springsecurity.SpringSecurityUserInfoHolder;
import com.ctrip.framework.apollo.portal.spi.springsecurity.SpringSecurityUserService;
import com.google.common.collect.Maps;

@Configuration
public class AuthConfiguration {

  /**
   * spring.profiles.active = ctrip
   */
  @Configuration
  @Profile("ctrip")
  static class CtripAuthAutoConfiguration {

    private final PortalConfig portalConfig;

    public CtripAuthAutoConfiguration(final PortalConfig portalConfig) {
      this.portalConfig = portalConfig;
    }

    @Bean
    public ServletListenerRegistrationBean redisAppSettingListner() {
      ServletListenerRegistrationBean redisAppSettingListener = new ServletListenerRegistrationBean();
      redisAppSettingListener
          .setListener(listener("org.jasig.cas.client.credis.CRedisAppSettingListner"));
      return redisAppSettingListener;
    }

    @Bean
    public ServletListenerRegistrationBean singleSignOutHttpSessionListener() {
      ServletListenerRegistrationBean singleSignOutHttpSessionListener = new ServletListenerRegistrationBean();
      singleSignOutHttpSessionListener
          .setListener(listener("org.jasig.cas.client.session.SingleSignOutHttpSessionListener"));
      return singleSignOutHttpSessionListener;
    }

    @Bean
    public FilterRegistrationBean casFilter() {
      FilterRegistrationBean singleSignOutFilter = new FilterRegistrationBean();
      singleSignOutFilter.setFilter(filter("org.jasig.cas.client.session.SingleSignOutFilter"));
      singleSignOutFilter.addUrlPatterns("/*");
      singleSignOutFilter.setOrder(1);
      return singleSignOutFilter;
    }

    @Bean
    public FilterRegistrationBean authenticationFilter() {
      FilterRegistrationBean casFilter = new FilterRegistrationBean();

      Map<String, String> filterInitParam = Maps.newHashMap();
      filterInitParam.put("redisClusterName", "casClientPrincipal");
      filterInitParam.put("serverName", portalConfig.portalServerName());
      filterInitParam.put("casServerLoginUrl", portalConfig.casServerLoginUrl());
      //we don't want to use session to store login information, since we will be deployed to a cluster, not a single instance
      filterInitParam.put("useSession", "false");
      filterInitParam.put("/openapi.*", "exclude");

      casFilter.setInitParameters(filterInitParam);
      casFilter
          .setFilter(filter("com.ctrip.framework.apollo.sso.filter.ApolloAuthenticationFilter"));
      casFilter.addUrlPatterns("/*");
      casFilter.setOrder(2);

      return casFilter;
    }

    @Bean
    public FilterRegistrationBean casValidationFilter() {
      FilterRegistrationBean casValidationFilter = new FilterRegistrationBean();
      Map<String, String> filterInitParam = Maps.newHashMap();
      filterInitParam.put("casServerUrlPrefix", portalConfig.casServerUrlPrefix());
      filterInitParam.put("serverName", portalConfig.portalServerName());
      filterInitParam.put("encoding", "UTF-8");
      //we don't want to use session to store login information, since we will be deployed to a cluster, not a single instance
      filterInitParam.put("useSession", "false");
      filterInitParam.put("useRedis", "true");
      filterInitParam.put("redisClusterName", "casClientPrincipal");

      casValidationFilter
          .setFilter(
              filter("org.jasig.cas.client.validation.Cas20ProxyReceivingTicketValidationFilter"));
      casValidationFilter.setInitParameters(filterInitParam);
      casValidationFilter.addUrlPatterns("/*");
      casValidationFilter.setOrder(3);

      return casValidationFilter;
    }

    @Bean
    public FilterRegistrationBean assertionHolder() {
      FilterRegistrationBean assertionHolderFilter = new FilterRegistrationBean();

      Map<String, String> filterInitParam = Maps.newHashMap();
      filterInitParam.put("/openapi.*", "exclude");

      assertionHolderFilter.setInitParameters(filterInitParam);

      assertionHolderFilter.setFilter(
          filter("com.ctrip.framework.apollo.sso.filter.ApolloAssertionThreadLocalFilter"));
      assertionHolderFilter.addUrlPatterns("/*");
      assertionHolderFilter.setOrder(4);

      return assertionHolderFilter;
    }

    @Bean
    public CtripUserInfoHolder ctripUserInfoHolder() {
      return new CtripUserInfoHolder();
    }

    @Bean
    public CtripLogoutHandler logoutHandler() {
      return new CtripLogoutHandler();
    }

    private Filter filter(String className) {
      Class clazz = null;
      try {
        clazz = Class.forName(className);
        Object obj = clazz.newInstance();
        return (Filter) obj;
      } catch (Exception e) {
        throw new RuntimeException("instance filter fail", e);
      }
    }

    private EventListener listener(String className) {
      Class clazz = null;
      try {
        clazz = Class.forName(className);
        Object obj = clazz.newInstance();
        return (EventListener) obj;
      } catch (Exception e) {
        throw new RuntimeException("instance listener fail", e);
      }
    }

    @Bean
    public UserService ctripUserService(PortalConfig portalConfig) {
      return new CtripUserService(portalConfig);
    }

    @Bean
    public SsoHeartbeatHandler ctripSsoHeartbeatHandler() {
      return new CtripSsoHeartbeatHandler();
    }
  }

  /**
   * spring.profiles.active = auth,cas
   */
  @Configuration
  @Profile({"auth","cas"})
  static class SpringSecurityAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsoHeartbeatHandler.class)
    public SsoHeartbeatHandler defaultSsoHeartbeatHandler() {
      return new DefaultSsoHeartbeatHandler();
    }

    @Bean
    @ConditionalOnMissingBean(UserInfoHolder.class)
    public UserInfoHolder springSecurityUserInfoHolder() {
      return new SpringSecurityUserInfoHolder();
    }

    @Bean
    @ConditionalOnMissingBean(LogoutHandler.class)
    public LogoutHandler logoutHandler() {
      return new DefaultLogoutHandler();
    }

    @Bean
    public JdbcUserDetailsManager jdbcUserDetailsManager(AuthenticationManagerBuilder auth,
        DataSource datasource) throws Exception {
      JdbcUserDetailsManager jdbcUserDetailsManager = auth.jdbcAuthentication()
          .passwordEncoder(new BCryptPasswordEncoder()).dataSource(datasource)
          .usersByUsernameQuery("select Username,Password,Enabled from `Users` where Username = ?")
          .authoritiesByUsernameQuery(
              "select Username,Authority from `Authorities` where Username = ?")
          .getUserDetailsService();

      jdbcUserDetailsManager.setUserExistsSql("select Username from `Users` where Username = ?");
      jdbcUserDetailsManager
          .setCreateUserSql("insert into `Users` (Username, Password, Enabled) values (?,?,?)");
      jdbcUserDetailsManager
          .setUpdateUserSql("update `Users` set Password = ?, Enabled = ? where id = (select u.id from (select id from `Users` where Username = ?) as u)");
      jdbcUserDetailsManager.setDeleteUserSql("delete from `Users` where id = (select u.id from (select id from `Users` where Username = ?) as u)");
      jdbcUserDetailsManager
          .setCreateAuthoritySql("insert into `Authorities` (Username, Authority) values (?,?)");
      jdbcUserDetailsManager
          .setDeleteUserAuthoritiesSql("delete from `Authorities` where id = (select u.id from (select id from `Users` where Username = ?) as u)");
      jdbcUserDetailsManager
          .setChangePasswordSql("update `Users` set Password = ? where id = (select u.id from (select id from `Users` where Username = ?) as u)");

      return jdbcUserDetailsManager;
    }

    @Bean
    @ConditionalOnMissingBean(UserService.class)
    public UserService springSecurityUserService() {
      return new SpringSecurityUserService();
    }

  }

  @Order(99)
  @Profile("auth")
  @Configuration
  @EnableWebSecurity
  @EnableGlobalMethodSecurity(prePostEnabled = true)
  static class SpringSecurityConfigurer extends WebSecurityConfigurerAdapter {

    public static final String USER_ROLE = "user";

    @Override
    protected void configure(HttpSecurity http) throws Exception {
      http.csrf().disable();
      http.headers().frameOptions().sameOrigin();
      http.authorizeRequests()
          .antMatchers("/openapi/**", "/vendor/**", "/styles/**", "/scripts/**", "/views/**", "/img/**").permitAll()
          .antMatchers("/**").hasAnyRole(USER_ROLE);
      http.formLogin().loginPage("/signin").permitAll().failureUrl("/signin?#/error").and().httpBasic();
      SimpleUrlLogoutSuccessHandler urlLogoutHandler = new SimpleUrlLogoutSuccessHandler();
      urlLogoutHandler.setDefaultTargetUrl("/signin?#/logout");
      http.logout().logoutUrl("/user/logout").invalidateHttpSession(true).clearAuthentication(true)
          .logoutSuccessHandler(urlLogoutHandler);
      http.exceptionHandling().authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/signin"));
    }

  }
  @Order(99)
  @Profile("cas")
  @Configuration
  @EnableWebSecurity
  @EnableGlobalMethodSecurity(prePostEnabled = true)
  @EnableConfigurationProperties(CasProperties.class)
  static class SpringSecurityCASAuthAutoConfiguration extends WebSecurityConfigurerAdapter {
      
      public static final String USER_ROLE = "user";
      private final CasProperties casProperties;
      private final UserRepository userRepository;
      
      public SpringSecurityCASAuthAutoConfiguration(CasProperties casProperties,UserRepository userRepository) {
        this.casProperties = casProperties;
        this.userRepository = userRepository;
    }
      @Bean
      public ServiceProperties casServiceProperties() {
          ServiceProperties casServiceProperties = new ServiceProperties();
          casServiceProperties.setService(casProperties.getService());
          casServiceProperties.setSendRenew(casProperties.getSendRenew());
          casServiceProperties.setServiceParameter(casProperties.getServiceParameter());
          casServiceProperties.setArtifactParameter(casProperties.getArtifactParameter());
          casServiceProperties.setAuthenticateAllArtifacts(casProperties.getAuthenticateAllArtifacts());
          return casServiceProperties;
      }
      @Override
      protected void configure(HttpSecurity http) throws Exception {
          http.csrf().disable();
          http.headers().frameOptions().sameOrigin();
          http.authorizeRequests()
          .antMatchers("/openapi/**", "/vendor/**", "/styles/**", "/scripts/**", "/views/**", "/img/**").permitAll()
          .antMatchers("/**").hasAnyRole(USER_ROLE);
          http.exceptionHandling().authenticationEntryPoint(casAuthenticationEntryPoint());
          http.addFilterAt(casAuthenticationFilter(),CasAuthenticationFilter.class);
          http.addFilterBefore(casLogoutFilter(),LogoutFilter.class);
          http.addFilterBefore(singleSignOutFilter(),CasAuthenticationFilter.class);
      }
      @Override
      protected void configure(AuthenticationManagerBuilder auth) throws Exception {
          auth.authenticationProvider(casAuthenticationProvider());
      }

      private CasAuthenticationProvider casAuthenticationProvider() {
          CasAuthenticationProvider casAuthenticationProvider = new CasAuthenticationProvider();
          casAuthenticationProvider.setServiceProperties(casServiceProperties());
          Cas20ServiceTicketValidator cas20ServiceTicketValidator = new Cas20ServiceTicketValidator(casProperties.getCasServerLoginUrl());
          casAuthenticationProvider.setTicketValidator(cas20ServiceTicketValidator);
          casAuthenticationProvider.setKey("casAuthenticationProviderKey");
          casAuthenticationProvider.setAuthenticationUserDetailsService(new CasAuthenticationUserDetailsService(userRepository));
          return casAuthenticationProvider;
      }

      private CasAuthenticationEntryPoint casAuthenticationEntryPoint() {
          CasAuthenticationEntryPoint casAuthenticationEntryPoint = new CasAuthenticationEntryPoint();
          casAuthenticationEntryPoint.setLoginUrl(casProperties.getCasServerLoginUrl());
          casAuthenticationEntryPoint.setServiceProperties(casServiceProperties());
          return casAuthenticationEntryPoint;
      }

      @Bean
      public CasAuthenticationFilter casAuthenticationFilter() throws Exception {
          CasAuthenticationFilter casAuthenticationFilter = new CasAuthenticationFilter();
          casAuthenticationFilter.setAuthenticationManager(authenticationManagerBean());
          casAuthenticationFilter.setFilterProcessesUrl(casProperties.getClient().getLoginUrl());
          return casAuthenticationFilter;
      }

      private SingleSignOutFilter singleSignOutFilter() {
          SingleSignOutFilter singleSignOutFilter = new SingleSignOutFilter();
          singleSignOutFilter.setCasServerUrlPrefix(casProperties.getCasServerLoginUrl());
          // singleSignOutFilter.setIgnoreInitConfiguration(true);
          return singleSignOutFilter;
      }

      private LogoutFilter casLogoutFilter() {
          LogoutFilter logoutFilter = new LogoutFilter(casProperties.getCasServerLogoutUrl(), new SecurityContextLogoutHandler());
          logoutFilter.setFilterProcessesUrl(casProperties.getClient().getLogoutUrl());
          return logoutFilter;
      }
  }

  /**
   * spring.profiles.active = ldap
   */
  @Configuration
  @Profile("ldap")
  @EnableConfigurationProperties({LdapProperties.class,LdapExtendProperties.class})
  static class SpringSecurityLDAPAuthAutoConfiguration {

    private final LdapProperties properties;
    private final Environment environment;

    public SpringSecurityLDAPAuthAutoConfiguration(final LdapProperties properties, final Environment environment) {
      this.properties = properties;
      this.environment = environment;
    }

    @Bean
    @ConditionalOnMissingBean(SsoHeartbeatHandler.class)
    public SsoHeartbeatHandler defaultSsoHeartbeatHandler() {
      return new DefaultSsoHeartbeatHandler();
    }

    @Bean
    @ConditionalOnMissingBean(UserInfoHolder.class)
    public UserInfoHolder springSecurityUserInfoHolder() {
      return new SpringSecurityUserInfoHolder();
    }

    @Bean
    @ConditionalOnMissingBean(LogoutHandler.class)
    public LogoutHandler logoutHandler() {
      return new DefaultLogoutHandler();
    }

    @Bean
    @ConditionalOnMissingBean(UserService.class)
    public UserService springSecurityUserService() {
      return new LdapUserService();
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextSource ldapContextSource() {
      LdapContextSource source = new LdapContextSource();
      source.setUserDn(this.properties.getUsername());
      source.setPassword(this.properties.getPassword());
      source.setAnonymousReadOnly(this.properties.getAnonymousReadOnly());
      source.setBase(this.properties.getBase());
      source.setUrls(this.properties.determineUrls(this.environment));
      source.setBaseEnvironmentProperties(
          Collections.unmodifiableMap(this.properties.getBaseEnvironment()));
      return source;
    }

    @Bean
    @ConditionalOnMissingBean(LdapOperations.class)
    public LdapTemplate ldapTemplate(ContextSource contextSource) {
      LdapTemplate ldapTemplate = new LdapTemplate(contextSource);
      ldapTemplate.setIgnorePartialResultException(true);
      return ldapTemplate;
    }
  }

  @Order(99)
  @Profile("ldap")
  @Configuration
  @EnableWebSecurity
  @EnableGlobalMethodSecurity(prePostEnabled = true)
  static class SpringSecurityLDAPConfigurer extends WebSecurityConfigurerAdapter {

    private final LdapProperties ldapProperties;
    private final LdapContextSource ldapContextSource;

    private final LdapExtendProperties ldapExtendProperties;

    public SpringSecurityLDAPConfigurer(final LdapProperties ldapProperties,
        final LdapContextSource ldapContextSource,
       final LdapExtendProperties ldapExtendProperties) {
      this.ldapProperties = ldapProperties;
      this.ldapContextSource = ldapContextSource;
      this.ldapExtendProperties = ldapExtendProperties;
    }

    @Bean
    public FilterBasedLdapUserSearch userSearch() {
      if (ldapExtendProperties.getGroup() == null || StringUtils
          .isBlank(ldapExtendProperties.getGroup().getGroupSearch())) {
        FilterBasedLdapUserSearch filterBasedLdapUserSearch = new FilterBasedLdapUserSearch("",
            ldapProperties.getSearchFilter(), ldapContextSource);
        filterBasedLdapUserSearch.setSearchSubtree(true);
        return filterBasedLdapUserSearch;
      } else {
        FilterLdapByGroupUserSearch filterLdapByGroupUserSearch = new FilterLdapByGroupUserSearch(
            ldapProperties.getBase(), ldapProperties.getSearchFilter(), ldapExtendProperties.getGroup().getGroupBase(),
            ldapContextSource, ldapExtendProperties.getGroup().getGroupSearch(),
            ldapExtendProperties.getMapping().getRdnKey(),
            ldapExtendProperties.getGroup().getGroupMembership(),ldapExtendProperties.getMapping().getLoginId());
        filterLdapByGroupUserSearch.setSearchSubtree(true);
        return filterLdapByGroupUserSearch;
      }
    }

    @Bean
    public LdapAuthenticationProvider ldapAuthProvider() {
      BindAuthenticator bindAuthenticator = new BindAuthenticator(ldapContextSource);
      bindAuthenticator.setUserSearch(userSearch());
      DefaultLdapAuthoritiesPopulator defaultAuthAutoConfiguration = new DefaultLdapAuthoritiesPopulator(
          ldapContextSource, null);
      defaultAuthAutoConfiguration.setIgnorePartialResultException(true);
      defaultAuthAutoConfiguration.setSearchSubtree(true);
      LdapAuthenticationProvider ldapAuthenticationProvider = new LdapAuthenticationProvider(
          bindAuthenticator, defaultAuthAutoConfiguration);
      return ldapAuthenticationProvider;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
      http.csrf().disable();
      http.headers().frameOptions().sameOrigin();
      http.authorizeRequests()
          .antMatchers("/openapi/**", "/vendor/**", "/styles/**", "/scripts/**", "/views/**", "/img/**").permitAll()
          .antMatchers("/**").authenticated();
      http.formLogin().loginPage("/signin").permitAll().failureUrl("/signin?#/error").and().httpBasic();
      SimpleUrlLogoutSuccessHandler urlLogoutHandler = new SimpleUrlLogoutSuccessHandler();
      urlLogoutHandler.setDefaultTargetUrl("/signin?#/logout");
      http.logout().logoutUrl("/user/logout").invalidateHttpSession(true).clearAuthentication(true)
          .logoutSuccessHandler(urlLogoutHandler);
      http.exceptionHandling().authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/signin"));
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
      auth.authenticationProvider(ldapAuthProvider());
    }
  }

  /**
   * default profile
   */
  @Configuration
  @ConditionalOnMissingProfile({"ctrip", "auth", "ldap", "cas"})
  static class DefaultAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsoHeartbeatHandler.class)
    public SsoHeartbeatHandler defaultSsoHeartbeatHandler() {
      return new DefaultSsoHeartbeatHandler();
    }

    @Bean
    @ConditionalOnMissingBean(UserInfoHolder.class)
    public DefaultUserInfoHolder defaultUserInfoHolder() {
      return new DefaultUserInfoHolder();
    }

    @Bean
    @ConditionalOnMissingBean(LogoutHandler.class)
    public DefaultLogoutHandler logoutHandler() {
      return new DefaultLogoutHandler();
    }

    @Bean
    @ConditionalOnMissingBean(UserService.class)
    public UserService defaultUserService() {
      return new DefaultUserService();
    }
  }

  @ConditionalOnMissingProfile({"auth", "ldap", "cas"})
  @Configuration
  @EnableWebSecurity
  @EnableGlobalMethodSecurity(prePostEnabled = true)
  static class DefaultWebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
      http.csrf().disable();
      http.headers().frameOptions().sameOrigin();
    }
  }
}
