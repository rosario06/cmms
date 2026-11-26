package com.grash.event;

/**
 * A work order was completed. The recipient is the creator of the work order.
 */
public class WorkOrderCompletedEvent extends WorkOrderNotificationEvent {
    public WorkOrderCompletedEvent(Long companyId, Long workOrderId, String title, Long recipientUserId, String recipientPhone) {
        super(NotificationEventType.WORK_ORDER_COMPLETED, companyId, workOrderId, title, recipientUserId, recipientPhone);
    }
}
