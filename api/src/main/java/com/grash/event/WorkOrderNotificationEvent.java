package com.grash.event;

import lombok.Getter;

/**
 * Base of the work order domain events that may trigger an outgoing notification.
 * Carries only plain values (no JPA entities), so it is safe to handle after the transaction ended
 * and in another thread.
 */
@Getter
public abstract class WorkOrderNotificationEvent {
    private final NotificationEventType type;
    private final Long companyId;
    private final Long workOrderId;
    private final String title;
    /**
     * User that must receive the notification (may be null when unknown).
     */
    private final Long recipientUserId;
    /**
     * Phone of the recipient as stored in the user profile (may be null or not E.164).
     */
    private final String recipientPhone;

    protected WorkOrderNotificationEvent(NotificationEventType type, Long companyId, Long workOrderId, String title,
                                         Long recipientUserId, String recipientPhone) {
        this.type = type;
        this.companyId = companyId;
        this.workOrderId = workOrderId;
        this.title = title;
        this.recipientUserId = recipientUserId;
        this.recipientPhone = recipientPhone;
    }
}
