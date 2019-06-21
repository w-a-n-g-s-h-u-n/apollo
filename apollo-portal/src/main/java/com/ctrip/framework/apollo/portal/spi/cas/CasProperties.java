package com.ctrip.framework.apollo.portal.spi.cas;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.security.cas")
public class CasProperties {
    public static final String DEFAULT_CAS_ARTIFACT_PARAMETER = "ticket";
    public static final String DEFAULT_CAS_SERVICE_PARAMETER = "service";
    private boolean authenticateAllArtifacts = true;
    private boolean sendRenew = false;
    private String artifactParameter = DEFAULT_CAS_ARTIFACT_PARAMETER;
    private String serviceParameter = DEFAULT_CAS_SERVICE_PARAMETER;
    private CasClient client;
    private CasServer server;

    public String getService() {
        return client.getHost() + client.getLoginUrl();
    }

    public String getCasServerLoginUrl() {
        return server.getHost() + server.getLoginUrl();
    }

    public String getCasServerLogoutUrl() {
        return server.getHost() + server.getLogoutUrl() + "?" + serviceParameter + "=" + getService();
    }

    public static class CasClient {

        private String host;
        private String loginUrl;
        private String logoutUrl;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getLoginUrl() {
            return loginUrl;
        }

        public void setLoginUrl(String loginUrl) {
            this.loginUrl = loginUrl;
        }

        public String getLogoutUrl() {
            return logoutUrl;
        }

        public void setLogoutUrl(String logoutUrl) {
            this.logoutUrl = logoutUrl;
        }
    }

    public static class CasServer {
        private String host;
        private String loginUrl;
        private String logoutUrl;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getLoginUrl() {
            return loginUrl;
        }

        public void setLoginUrl(String loginUrl) {
            this.loginUrl = loginUrl;
        }

        public String getLogoutUrl() {
            return logoutUrl;
        }

        public void setLogoutUrl(String logoutUrl) {
            this.logoutUrl = logoutUrl;
        }
    }

    public boolean getAuthenticateAllArtifacts() {
        return authenticateAllArtifacts;
    }

    public void setAuthenticateAllArtifacts(boolean authenticateAllArtifacts) {
        this.authenticateAllArtifacts = authenticateAllArtifacts;
    }

    public boolean getSendRenew() {
        return sendRenew;
    }

    public void setSendRenew(boolean sendRenew) {
        this.sendRenew = sendRenew;
    }

    public String getArtifactParameter() {
        return artifactParameter;
    }

    public void setArtifactParameter(String artifactParameter) {
        this.artifactParameter = artifactParameter;
    }

    public String getServiceParameter() {
        return serviceParameter;
    }

    public void setServiceParameter(String serviceParameter) {
        this.serviceParameter = serviceParameter;
    }

    public CasClient getClient() {
        return client;
    }

    public void setClient(CasClient client) {
        this.client = client;
    }

    public CasServer getServer() {
        return server;
    }

    public void setServer(CasServer server) {
        this.server = server;
    }
}
