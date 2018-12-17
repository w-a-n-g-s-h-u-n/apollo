package com.ctrip.framework.apollo.portal.spi.ldap;

import static org.springframework.ldap.query.LdapQueryBuilder.query;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.ContainerCriteria;
import org.springframework.ldap.query.SearchScope;
import org.springframework.util.CollectionUtils;

import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.spi.UserService;
import com.google.common.base.Strings;

/**
 * @author xm.lin xm.lin@anxincloud.com
 * @Description
 * @date 18-8-9 下午4:42
 */
public class LdapUserService implements UserService {

    @Value("${ldap.mapping.objectClass}")
    private String objectClassAttrName;
    @Value("${ldap.mapping.loginId}")
    private String loginIdAttrName;
    @Value("${ldap.mapping.userDisplayName}")
    private String userDisplayNameAttrName;
    @Value("${ldap.mapping.email}")
    private String emailAttrName;
    @Value("#{'${ldap.filter.memberOf:}'.split('\\|')}")
    private String[] memberOf;
    @Value("#{'${ldap.filter.department:}'.split('\\|')}")
    private String[] department;
    @Value("#{'${ldap.filter.division:}'.split('\\|')}")
    private String[] division;
    @Value("#{'${ldap.filter.description:}'.split('\\|')}")
    private String[] description;

    @Autowired
    private LdapTemplate ldapTemplate;

    private static final String MEMBER_OF_ATTR_NAME = "memberOf";
    private static final String DEPARTMENT_ATTR_NAME = "department";
    private static final String DIVISION_ATTR_NAME = "division";
    private static final String DESCRIPTION_ATTR_NAME = "description";

    private ContextMapper<UserInfo> ldapUserInfoMapper = (ctx) -> {
        DirContextAdapter contextAdapter = (DirContextAdapter)ctx;
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(contextAdapter.getStringAttribute(loginIdAttrName));
        userInfo.setName(contextAdapter.getStringAttribute(userDisplayNameAttrName));
        userInfo.setEmail(contextAdapter.getStringAttribute(emailAttrName));
        return userInfo;
    };

    private ContainerCriteria ldapQueryCriteria() {
        ContainerCriteria criteria = query().searchScope(SearchScope.SUBTREE).where("objectClass").is(objectClassAttrName);
        if (description.length > 0 && !StringUtils.isEmpty(description[0])) {
            ContainerCriteria descriptionFilters = query().where(DESCRIPTION_ATTR_NAME).is(description[0]);
            Arrays.stream(description).skip(1).forEach(filter -> descriptionFilters.or(DESCRIPTION_ATTR_NAME).is(filter));
            criteria.and(descriptionFilters);
        }
        if (department.length > 0 && !StringUtils.isEmpty(department[0])) {
            ContainerCriteria departmentFilters = query().where(DEPARTMENT_ATTR_NAME).is(department[0]);
            Arrays.stream(department).skip(1).forEach(filter -> departmentFilters.or(DEPARTMENT_ATTR_NAME).is(filter));
            criteria.and(departmentFilters);
        }
        if (division.length > 0 && !StringUtils.isEmpty(division[0])) {
            ContainerCriteria divisionFilters = query().where(DIVISION_ATTR_NAME).is(division[0]);
            Arrays.stream(division).skip(1).forEach(filter -> divisionFilters.or(DIVISION_ATTR_NAME).is(filter));
            criteria.and(divisionFilters);
        }
        if (memberOf.length > 0 && !StringUtils.isEmpty(memberOf[0])) {
            ContainerCriteria memberOfFilters = query().where(MEMBER_OF_ATTR_NAME).is(memberOf[0]);
            Arrays.stream(memberOf).skip(1).forEach(filter -> memberOfFilters.or(MEMBER_OF_ATTR_NAME).is(filter));
            criteria.and(memberOfFilters);
        }
        return criteria;
    }

    @Override
    public List<UserInfo> searchUsers(String keyword, int offset, int limit) {
        ContainerCriteria criteria = ldapQueryCriteria();
        if (!Strings.isNullOrEmpty(keyword)) {
            criteria.and(query().where(loginIdAttrName).like(keyword + "*").or(userDisplayNameAttrName).like(keyword + "*"));
        }
        return ldapTemplate.search(criteria, ldapUserInfoMapper);
    }

    @Override
    public UserInfo findByUserId(String userId) {
        return ldapTemplate.searchForObject(ldapQueryCriteria().and(loginIdAttrName).is(userId), ldapUserInfoMapper);
    }

    @Override
    public List<UserInfo> findByUserIds(List<String> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return null;
        } else {
            ContainerCriteria criteria = ldapQueryCriteria().and(query().where(loginIdAttrName).is(userIds.get(0)));
            userIds.stream().skip(1).forEach(userId -> criteria.or(loginIdAttrName).is(userId));
            return ldapTemplate.search(criteria, ldapUserInfoMapper);
        }
    }

}
