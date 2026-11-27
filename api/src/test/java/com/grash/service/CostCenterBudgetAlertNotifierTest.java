package com.grash.service;

import com.grash.event.CostCenterBudgetAlertEvent;
import com.grash.factory.MailServiceFactory;
import com.grash.model.Notification;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.UserSettings;
import com.grash.model.enums.NotificationType;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.StaticMessageSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostCenterBudgetAlertNotifierTest {

    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MailServiceFactory mailServiceFactory;
    @Mock
    private MailService mailService;

    private CostCenterBudgetAlertNotifier notifier;

    @BeforeEach
    void setUp() {
        // StaticMessageSource without messages: the default messages of the code are used
        notifier = new CostCenterBudgetAlertNotifier(userService, notificationService, mailServiceFactory,
                new StaticMessageSource());
        when(mailServiceFactory.getMailService()).thenReturn(mailService);
    }

    private User user(long id, RoleCode code, boolean enabled, PermissionEntity[] view, PermissionEntity[] create) {
        Role role = new Role();
        role.setCode(code);
        role.setViewPermissions(new HashSet<>(Arrays.asList(view)));
        role.setCreatePermissions(new HashSet<>(Arrays.asList(create)));
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@test.com");
        user.setEnabled(enabled);
        user.setRole(role);
        user.setUserSettings(new UserSettings());
        return user;
    }

    private static PermissionEntity[] perms(PermissionEntity... p) {
        return p;
    }

    private CostCenterBudgetAlertEvent event() {
        return new CostCenterBudgetAlertEvent(1L, 7L, "CC-7", "Planta Norte", 1000, 850, 85, 80);
    }

    @Test
    void notifiesOnlyAdminsAndFinanceRoles() {
        User admin = user(1, RoleCode.ADMIN, true, perms(), perms());
        User finance = user(2, RoleCode.USER_CREATED, true, perms(PermissionEntity.ANALYTICS),
                perms(PermissionEntity.SETTINGS));
        User onlyAnalytics = user(3, RoleCode.USER_CREATED, true, perms(PermissionEntity.ANALYTICS), perms());
        User onlySettings = user(4, RoleCode.USER_CREATED, true, perms(), perms(PermissionEntity.SETTINGS));
        User technician = user(5, RoleCode.TECHNICIAN, true, perms(PermissionEntity.WORK_ORDERS), perms());
        User disabledAdmin = user(6, RoleCode.ADMIN, false, perms(), perms());
        when(userService.findByCompany(1L)).thenReturn(List.of(admin, finance, onlyAnalytics, onlySettings,
                technician, disabledAdmin));

        notifier.onBudgetAlert(event());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, times(2)).create(captor.capture());
        List<Notification> sent = captor.getAllValues();
        assertEquals(List.of(1L, 2L), sent.stream().map(n -> n.getUser().getId()).toList());
        Notification notification = sent.get(0);
        assertEquals(NotificationType.INFO, notification.getNotificationType());
        assertEquals(7L, notification.getResourceId());
        assertTrue(notification.getMessage().contains("Planta Norte (CC-7)"), notification.getMessage());
        assertTrue(notification.getMessage().contains("85%"), notification.getMessage());
        assertTrue(notification.getMessage().contains("80%"), notification.getMessage());
        verify(mailService, times(2)).sendSimpleMessage(any(String[].class), anyString(), anyString());
    }

    @Test
    void userWithEmailDisabled_getsNotificationButNoEmail() {
        User admin = user(1, RoleCode.ADMIN, true, perms(), perms());
        admin.getUserSettings().setEmailNotified(false);
        when(userService.findByCompany(1L)).thenReturn(List.of(admin));

        notifier.onBudgetAlert(event());

        verify(notificationService).create(any(Notification.class));
        verify(mailService, never()).sendSimpleMessage(any(), anyString(), anyString());
    }

    @Test
    void failureInMailOrNotification_neverPropagates() {
        User admin = user(1, RoleCode.ADMIN, true, perms(), perms());
        when(userService.findByCompany(1L)).thenReturn(List.of(admin));
        doThrow(new RuntimeException("boom")).when(notificationService).create(any(Notification.class));
        doThrow(new RuntimeException("smtp")).when(mailService).sendSimpleMessage(any(), anyString(), anyString());

        assertDoesNotThrow(() -> notifier.onBudgetAlert(event()));
        verify(mailService).sendSimpleMessage(any(), anyString(), anyString());

        when(userService.findByCompany(1L)).thenThrow(new RuntimeException("db"));
        assertDoesNotThrow(() -> notifier.onBudgetAlert(event()));
    }

    @Test
    void eventWithoutCompany_isIgnored() {
        notifier.onBudgetAlert(new CostCenterBudgetAlertEvent(null, 7L, "CC", "N", 1, 1, 100, 80));
        verifyNoInteractions(userService, notificationService);
    }

    @Test
    void formatsPercentWithAtMostOneDecimal() {
        assertEquals("85", CostCenterBudgetAlertNotifier.format(85));
        assertEquals("85.3", CostCenterBudgetAlertNotifier.format(85.26));
        assertEquals("100", CostCenterBudgetAlertNotifier.format(100));
    }
}
