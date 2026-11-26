package com.grash.service;

import com.grash.event.WorkOrderNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to the work order domain events (WorkOrderCreatedEvent, TechnicianAssignedEvent,
 * TechnicianArrivedEvent, WorkOrderCompletedEvent) once the publishing transaction committed (or immediately when
 * there is no transaction) and hands them over to the asynchronous {@link NotificationDeliveryService}.
 * <p>
 * The hand-over is wrapped so that a failure (for example a rejected task when the async executor is saturated)
 * can never break the original operation: this listener runs in the commit callback of the caller.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationDispatcherService {

    private final NotificationDeliveryService notificationDeliveryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkOrderEvent(WorkOrderNotificationEvent event) {
        try {
            notificationDeliveryService.process(event);
        } catch (Exception e) {
            log.warn("Could not dispatch notification for work order {}: {}", event.getWorkOrderId(),
                    e.getClass().getSimpleName());
        }
    }
}
