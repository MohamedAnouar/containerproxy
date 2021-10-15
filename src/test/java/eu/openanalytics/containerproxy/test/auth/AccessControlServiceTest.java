package eu.openanalytics.containerproxy.test.auth;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.model.spec.ProxyAccessControl;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.AccessControlService;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AccessControlServiceTest {

    private final IAuthenticationBackend authBackend;
    private final UserService userService;
    private final IProxySpecProvider specProvider;
    private final AccessControlService accessControlService;

    public AccessControlServiceTest() {
        authBackend = mock(IAuthenticationBackend.class);
        userService = mock(UserService.class);
        specProvider = mock(IProxySpecProvider.class);
        accessControlService = new AccessControlService(authBackend, userService, specProvider);
    }

    @Test
    public void authOrSpecIsNull() {
        Authentication auth = mock(Authentication.class);
        Authentication nullAuth = null;
        ProxySpec nullSpec = null;

        Assertions.assertFalse(accessControlService.canAccess(nullAuth, createProxySpec(null)));
        Assertions.assertFalse(accessControlService.canAccess(auth, nullSpec));
        Assertions.assertFalse(accessControlService.canAccess(nullAuth, nullSpec));
    }

    @Test
    public void usingSpecId() {
        when(authBackend.hasAuthorization()).thenReturn(true);
        ProxyAccessControl proxyAccessControl = new ProxyAccessControl();
        when(specProvider.getSpec("myId")).thenReturn(createProxySpec(proxyAccessControl));

        Authentication auth = mock(Authentication.class);

        Assertions.assertTrue(accessControlService.canAccess(auth, "myId"));
    }

    @Test
    public void specHasNoAccessControlTest() {
        when(authBackend.hasAuthorization()).thenReturn(true);
        Authentication auth = mock(Authentication.class);
        ProxyAccessControl proxyAccessControl = new ProxyAccessControl();

        Assertions.assertTrue(accessControlService.canAccess(auth, createProxySpec(proxyAccessControl)));
    }

    @Test
    public void specHasNullAccessControlTest() {
        when(authBackend.hasAuthorization()).thenReturn(true);
        Authentication auth = mock(Authentication.class);

        Assertions.assertTrue(accessControlService.canAccess(auth, createProxySpec(null)));
    }

    @Test
    public void anonymousAccessTest() {
        when(authBackend.hasAuthorization()).thenReturn(false);

        ProxyAccessControl proxyAccessControl = new ProxyAccessControl();
        proxyAccessControl.setGroups(new String[]{"myGroup"});

        // when anonymous -> has access
        Authentication anonymousAuth = mock(AnonymousAuthenticationToken.class);
        Assertions.assertTrue(accessControlService.canAccess(anonymousAuth, createProxySpec(proxyAccessControl)));

        // when not-anonymous -> has no access
        Authentication auth = mock(Authentication.class);
        Assertions.assertFalse(accessControlService.canAccess(auth, createProxySpec(proxyAccessControl)));

        // when spec has no Access Control -> has access
        Assertions.assertTrue(accessControlService.canAccess(anonymousAuth, createProxySpec(null)));
    }

    @Test
    public void hasGroupAccessTest() {
        when(authBackend.hasAuthorization()).thenReturn(true);
        ProxyAccessControl proxyAccessControl = new ProxyAccessControl();
        proxyAccessControl.setGroups(new String[]{"myGroup1", "myGroupAbc", "xxy"});

        // user is not part of any group -> no access
        Authentication auth1 = mock(Authentication.class);
        Assertions.assertFalse(accessControlService.canAccess(auth1, createProxySpec(proxyAccessControl)));

        // user is part of group, but not the correct group -> no access
        when(userService.isMember(auth1, "myGroup1")).thenReturn(false);
        Assertions.assertFalse(accessControlService.canAccess(auth1, createProxySpec(proxyAccessControl)));

        // user is part of the correct -> hass access
        Authentication auth2 = mock(Authentication.class);
        when(userService.isMember(auth2, "myGroup1")).thenReturn(true);
        Assertions.assertTrue(accessControlService.canAccess(auth2, createProxySpec(proxyAccessControl)));
    }

    @Test
    public void hasUserAccessTest() {
        when(authBackend.hasAuthorization()).thenReturn(true);
        ProxyAccessControl proxyAccessControl = new ProxyAccessControl();
        proxyAccessControl.setUsers(new String[]{"myUser1", "myUser2"});

        // user is not part of the user access list -> no access
        Authentication auth1 = mock(Authentication.class);
        when(auth1.getName()).thenReturn("Bart");
        Assertions.assertFalse(accessControlService.canAccess(auth1, createProxySpec(proxyAccessControl)));

        // user is part of the user access list -> hass access
        Authentication auth2 = mock(Authentication.class);
        when(auth2.getName()).thenReturn("myUser1");
        when(userService.isMember(auth2, "myGroup1")).thenReturn(true);
        Assertions.assertTrue(accessControlService.canAccess(auth2, createProxySpec(proxyAccessControl)));
    }

    @Test
    public void combinationOfGroupAndUserTest() {
        when(authBackend.hasAuthorization()).thenReturn(true);
        ProxyAccessControl proxyAccessControl = new ProxyAccessControl();
        proxyAccessControl.setUsers(new String[]{"myUser1", "myUser2"});
        proxyAccessControl.setGroups(new String[]{"myGroup1", "myGroupAbc", "xxy"});

        // user is not part of any group and not on the user access list -> no access
        Authentication auth1 = mock(Authentication.class);
        when(auth1.getName()).thenReturn("Bart");
        Assertions.assertFalse(accessControlService.canAccess(auth1, createProxySpec(proxyAccessControl)));

        // user is not part of any group, but it is on the user access list -> has access
        Authentication auth2 = mock(Authentication.class);
        when(auth2.getName()).thenReturn("myUser1");
        Assertions.assertTrue(accessControlService.canAccess(auth2, createProxySpec(proxyAccessControl)));

        // user is part of group and not on the user access list -> has access
        Authentication auth3 = mock(Authentication.class);
        when(auth3.getName()).thenReturn("Bart");
        when(userService.isMember(auth3, "myGroup1")).thenReturn(false);
        when(userService.isMember(auth3, "myGroupAbc")).thenReturn(true);
        when(userService.isMember(auth3, "xxy")).thenReturn(false);
        Assertions.assertTrue(accessControlService.canAccess(auth3, createProxySpec(proxyAccessControl)));

        // user is part of group and also on the user access list -> has access
        Authentication auth4 = mock(Authentication.class);
        when(auth4.getName()).thenReturn("myUser1");
        when(userService.isMember(auth4, "myGroup1")).thenReturn(false);
        when(userService.isMember(auth4, "myGroupAbc")).thenReturn(true);
        when(userService.isMember(auth4, "xxy")).thenReturn(false);
        Assertions.assertTrue(accessControlService.canAccess(auth4, createProxySpec(proxyAccessControl)));
    }

    private ProxySpec createProxySpec(ProxyAccessControl proxyAccessControl) {
        ProxySpec proxySpec = new ProxySpec();
        proxySpec.setId("myId");
        proxySpec.setAccessControl(proxyAccessControl);
        return proxySpec;
    }

}
