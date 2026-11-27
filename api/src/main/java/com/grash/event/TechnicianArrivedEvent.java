package com.grash.event;

/**
 * A technician arrived on site. NOT published anywhere yet: the code base has no concept of arrival.
 */
public class TechnicianArrivedEvent extends WorkOrderNotificationEvent {
    public TechnicianArrivedEvent(Long companyId, Long workOrderId, String title, Long recipientUserId, String recipientPhone) {
        super(NotificationEventType.TECHNICIAN_ARRIVED, companyId, workOrderId, title, recipientUserId, recipientPhone);
    }
}
