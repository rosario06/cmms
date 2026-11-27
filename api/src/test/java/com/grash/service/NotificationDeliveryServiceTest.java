package com.grash.service;

import com.grash.dto.integration.IntegrationTestResultDTO;
import com.grash.event.TechnicianArrivedEvent;
import com.grash.event.TechnicianAssignedEvent;
import com.grash.event.WorkOrderCompletedEvent;
import com.grash.event.WorkOrderCreatedEvent;
import com.grash.event.WorkOrderNotificationEvent;
import com.grash.model.IntegrationConfig;
import com.grash.repository.IntegrationConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock
    private IntegrationConfigRepository integrationConfigRepository;
    @Mock
    private WhatsAppClient whatsAppClient;

    private NotificationDeliveryService service;
    private IntegrationConfig config;

    @BeforeEach
    void setUp() {
        service = new NotificationDeliveryService(integrationConfigRepository, whatsAppClient);
        config = new IntegrationConfig();
    }

    private void givenConfig() {
        when(integrationConfigRepository.findByCompanyId(1L)).thenReturn(Optional.of(config));
    }

    private WorkOrderNotificationEvent assigned(String phone) {
        return new TechnicianAssignedEvent(1L, 10L, "Fix pump", 5L, phone);
    }

    @Test
    void whatsappDisabled_doesNotSend() {
        config.setWhatsappEnabled(false);
        config.setNotifyOnTechnicianAssigned(true);
        givenConfig();

        service.process(assigned("+18095551234"));

        verifyNoInteractions(whatsAppClient);
    }

    @Test
    void eventFlagOff_doesNotSend() {
        config.setWhatsappEnabled(true);
        config.setNotifyOnTechnicianAssigned(false);
        config.setNotifyOnWorkOrderCreated(true);
        givenConfig();

        service.process(assigned("+18095551234"));

        verifyNoInteractions(whatsAppClient);
    }

    @Test
    void noConfigForCompany_doesNotSend() {
        when(integrationConfigRepository.findByCompanyId(1L)).thenReturn(Optional.empty());

        service.process(assigned("+18095551234"));

        verifyNoInteractions(whatsAppClient);
    }

    @Test
    void missingOrInvalidPhone_isSkippedWithoutError() {
        config.setWhatsappEnabled(true);
        config.setNotifyOnTechnicianAssigned(true);
        when(integrationConfigRepository.findByCompanyId(1L)).thenReturn(Optional.of(config));

        service.process(assigned(null));
        service.process(assigned("   "));
        service.process(assigned("8095551234"));
        service.process(assigned("+12"));

        verifyNoInteractions(whatsAppClient);
    }

    @Test
    void enabledWithValidPhone_sendsToNormalizedNumber() {
        config.setWhatsappEnabled(true);
        config.setNotifyOnTechnicianAssigned(true);
        givenConfig();
        when(whatsAppClient.send(any(), anyString(), anyString())).thenReturn(new IntegrationTestResultDTO(true, "ok"));

        service.process(assigned("+1 (809) 555-1234"));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(whatsAppClient).send(any(IntegrationConfig.class), org.mockito.ArgumentMatchers.eq("+18095551234"),
                message.capture());
        assertTrue(message.getValue().contains("Fix pump"));
    }

    @Test
    void clientFailureOrException_neverPropagates() {
        config.setWhatsappEnabled(true);
        config.setNotifyOnWorkOrderCreated(true);
        givenConfig();
        when(whatsAppClient.send(any(), anyString(), anyString())).thenThrow(new IllegalStateException("boom"));

        service.process(new WorkOrderCreatedEvent(1L, 10L, "T", 5L, "+18095551234"));
        // reaching this line means nothing was thrown
    }

    @Test
    void eachEventTypeUsesItsOwnFlag() {
        config.setNotifyOnWorkOrderCreated(true);
        assertTrue(NotificationDeliveryService.isEventEnabled(config,
                new WorkOrderCreatedEvent(1L, 1L, "t", null, null)));
        assertFalse(NotificationDeliveryService.isEventEnabled(config,
                new TechnicianAssignedEvent(1L, 1L, "t", null, null)));
        assertFalse(NotificationDeliveryService.isEventEnabled(config,
                new TechnicianArrivedEvent(1L, 1L, "t", null, null)));
        assertFalse(NotificationDeliveryService.isEventEnabled(config,
                new WorkOrderCompletedEvent(1L, 1L, "t", null, null)));
        config.setNotifyOnTechnicianArrived(true);
        config.setNotifyOnWorkOrderCompleted(true);
        assertTrue(NotificationDeliveryService.isEventEnabled(config,
                new TechnicianArrivedEvent(1L, 1L, "t", null, null)));
        assertTrue(NotificationDeliveryService.isEventEnabled(config,
                new WorkOrderCompletedEvent(1L, 1L, "t", null, null)));
    }

    @Test
    void normalizePhone_acceptsOnlyE164LikeNumbers() {
        assertEquals("+18095551234", NotificationDeliveryService.normalizePhone(" +1-809-555-1234 "));
        assertNull(NotificationDeliveryService.normalizePhone("18095551234"));
        assertNull(NotificationDeliveryService.normalizePhone("+0123456789"));
        assertNull(NotificationDeliveryService.normalizePhone("+1809555123456789"));
        assertNull(NotificationDeliveryService.normalizePhone(null));
    }

    @Test
    void buildMessage_collapsesNewlinesAndCapsTitle() {
        String longTitle = "x".repeat(500);
        String message = NotificationDeliveryService.buildMessage(
                new WorkOrderCompletedEvent(1L, 1L, "line1\nline2 " + longTitle, null, null));

        assertTrue(message.length() < 300);
        assertTrue(!message.contains("\n"));
    }
}
