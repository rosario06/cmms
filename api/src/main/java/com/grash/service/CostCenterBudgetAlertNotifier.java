package com.grash.service;

import com.grash.event.CostCenterBudgetAlertEvent;
import com.grash.factory.MailServiceFactory;
import com.grash.model.Notification;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.enums.NotificationType;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleCode;
import com.grash.utils.Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Delivers the cost center budget alert to the people who manage costs: an in-app notification (and an e-mail, which
 * the mail service only sends when ENABLE_EMAIL_NOTIFICATIONS is true) for every enabled user of the company whose
 * role is ADMIN or has ANALYTICS in viewPermissions and SETTINGS in createPermissions.
 * <p>
 * The event is published once per threshold crossing (CostCenter.alertSent), so this class sends one notification per
 * recipient per crossing. Runs after commit and never throws: a failure here must not affect the original operation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CostCenterBudgetAlertNotifier {

    // MessageFormat pattern: the cost center label is passed as an argument, never concatenated into the pattern
    private static final String DEFAULT_CONTENT =
            "Cost center {0} has spent {1}% of its allocated budget (alert threshold: {2}%).";

    private final UserService userService;
    private final NotificationService notificationService;
    private final MailServiceFactory mailServiceFactory;
    private final MessageSource messageSource;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onBudgetAlert(CostCenterBudgetAlertEvent event) {
        try {
            if (event == null || event.getCompanyId() == null) return;
            List<User> recipients = userService.findByCompany(event.getCompanyId()).stream()
                    .filter(CostCenterBudgetAlertNotifier::isRecipient)
                    .collect(Collectors.toList());
            for (User recipient : recipients) {
                notifyRecipient(event, recipient);
            }
        } catch (Exception e) {
            log.warn("Could not notify the budget alert of cost center {}: {}", event == null ? null :
                    event.getCostCenterId(), e.getClass().getSimpleName());
        }
    }

    static boolean isRecipient(User user) {
        if (user == null || !user.isEnabled() || user.getRole() == null) return false;
        Role role = user.getRole();
        if (role.getCode() == RoleCode.ADMIN) return true;
        return role.getViewPermissions() != null && role.getViewPermissions().contains(PermissionEntity.ANALYTICS)
                && role.getCreatePermissions() != null
                && role.getCreatePermissions().contains(PermissionEntity.SETTINGS);
    }

    private void notifyRecipient(CostCenterBudgetAlertEvent event, User recipient) {
        Locale locale = localeOf(recipient);
        String title = messageSource.getMessage("budget_alert_title", null, "Cost center budget alert", locale);
        String message = messageSource.getMessage("budget_alert_content", new Object[]{label(event),
                        format(event.getExecutionPercent()), format(event.getAlertThresholdPercent())},
                DEFAULT_CONTENT, locale);
        try {
            notificationService.create(new Notification(message, recipient, NotificationType.INFO,
                    event.getCostCenterId()));
        } catch (Exception e) {
            log.warn("Could not create the budget alert notification for cost center {}: {}",
                    event.getCostCenterId(), e.getClass().getSimpleName());
        }
        boolean wantsEmail = recipient.getEmail() != null && !recipient.getEmail().isBlank()
                && (recipient.getUserSettings() == null || recipient.getUserSettings().isEmailNotified());
        if (wantsEmail) {
            try {
                // the mail service itself skips the delivery when ENABLE_EMAIL_NOTIFICATIONS is false
                mailServiceFactory.getMailService().sendSimpleMessage(new String[]{recipient.getEmail()}, title,
                        message);
            } catch (Exception e) {
                log.warn("Could not e-mail the budget alert of cost center {}: {}", event.getCostCenterId(),
                        e.getClass().getSimpleName());
            }
        }
    }

    private static Locale localeOf(User user) {
        try {
            return Helper.getLocale(user);
        } catch (Exception e) {
            return Locale.ENGLISH;
        }
    }

    private static String label(CostCenterBudgetAlertEvent event) {
        String code = event.getCode() == null ? "" : event.getCode().trim();
        String name = event.getName() == null ? "" : event.getName().trim();
        if (code.isEmpty()) return name;
        return name.isEmpty() ? code : name + " (" + code + ")";
    }

    /** One decimal at most, locale independent (so MessageFormat does not group digits). */
    static String format(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
