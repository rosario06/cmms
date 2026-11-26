package com.grash.event;

/**
 * The primary user (technician) of a work order was set or changed. The recipient is the assigned technician.
 */
public class TechnicianAssignedEvent extends WorkOrderNotificationEvent {
    public TechnicianAssignedEvent(Long companyId, Long workOrderId, String title, Long recipientUserId, String recipientPhone) {
        super(NotificationEventType.TECHNICIAN_ASSIGNED, companyId, workOrderId, title, recipientUserId, recipientPhone);
    }
}
