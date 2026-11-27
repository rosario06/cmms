package com.grash.event;

/**
 * A work order was created. The recipient is its creator.
 */
public class WorkOrderCreatedEvent extends WorkOrderNotificationEvent {
    public WorkOrderCreatedEvent(Long companyId, Long workOrderId, String title, Long recipientUserId, String recipientPhone) {
        super(NotificationEventType.WORK_ORDER_CREATED, companyId, workOrderId, title, recipientUserId, recipientPhone);
    }
}
