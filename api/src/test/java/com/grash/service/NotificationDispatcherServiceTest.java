package com.grash.service;

import com.grash.event.WorkOrderCreatedEvent;
import com.grash.event.WorkOrderNotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherServiceTest {

    @Mock
    private NotificationDeliveryService notificationDeliveryService;

    @Test
    void event_isHandedOverToTheAsyncDeliveryService() {
        NotificationDispatcherService dispatcher = new NotificationDispatcherService(notificationDeliveryService);
        WorkOrderNotificationEvent event = new WorkOrderCreatedEvent(1L, 2L, "t", 3L, "+18095551234");

        dispatcher.onWorkOrderEvent(event);

        verify(notificationDeliveryService).process(event);
    }

    @Test
    void rejectedTask_doesNotBreakTheOriginalOperation() {
        NotificationDispatcherService dispatcher = new NotificationDispatcherService(notificationDeliveryService);
        WorkOrderNotificationEvent event = new WorkOrderCreatedEvent(1L, 2L, "t", 3L, "+18095551234");
        doThrow(new TaskRejectedException("queue full")).when(notificationDeliveryService).process(event);

        dispatcher.onWorkOrderEvent(event);
        // reaching this line means nothing was thrown
    }
}
