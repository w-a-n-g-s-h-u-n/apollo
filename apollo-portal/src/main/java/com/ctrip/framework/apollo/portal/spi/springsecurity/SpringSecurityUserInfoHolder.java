package com.ctrip.framework.apollo.portal.spi.springsecurity;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.userdetails.LdapUserDetails;

import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.po.UserPO;
import com.ctrip.framework.apollo.portal.spi.UserInfoHolder;

public class SpringSecurityUserInfoHolder implements UserInfoHolder {

    @Override
    public UserInfo getUser() {
        UserInfo userInfo = new UserInfo();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LdapUserDetails) {
            LdapUserDetails ldapPrincipal = (LdapUserDetails)principal;
            userInfo.setUserId(ldapPrincipal.getUsername());
            String dn = ldapPrincipal.getDn();
            Pattern pattern = Pattern.compile("(?<=cn\\=).*?(?=\\,)");
            Matcher matcher = pattern.matcher(dn);
            while (matcher.find()) {
                userInfo.setName(matcher.group());
                return userInfo;
            }
        } else if (principal instanceof UserPO) {
            return ((UserPO)principal).toUserInfo();
        } else if (principal instanceof UserDetails) {
            userInfo.setUserId(((UserDetails)principal).getUsername());
        } else if (principal instanceof Principal) {
            userInfo.setUserId(((Principal)principal).getName());
        } else {
            userInfo.setUserId(String.valueOf(principal));
        }
        return userInfo;
    }

}
