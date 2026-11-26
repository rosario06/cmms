package com.grash.service;

import com.grash.dto.integration.IntegrationTestResultDTO;
import com.grash.event.WorkOrderNotificationEvent;
import com.grash.model.IntegrationConfig;
import com.grash.repository.IntegrationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Decides and performs (in the background, with the executor of AsyncConfig) the WhatsApp delivery of a work order
 * event. Failures are only logged, without secrets or phone numbers.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private static final int MAX_TITLE_LENGTH = 120;

    private final IntegrationConfigRepository integrationConfigRepository;
    private final WhatsAppClient whatsAppClient;

    @Async
    public void process(WorkOrderNotificationEvent event) {
        try {
            Optional<IntegrationConfig> optionalConfig = integrationConfigRepository
                    .findByCompanyId(event.getCompanyId());
            if (optionalConfig.isEmpty()) {
                return;
            }
            IntegrationConfig config = optionalConfig.get();
            if (!config.isWhatsappEnabled() || !isEventEnabled(config, event)) {
                return;
            }
            String phone = normalizePhone(event.getRecipientPhone());
            if (phone == null) {
                log.debug("WhatsApp notification skipped for work order {}: recipient has no valid phone",
                        event.getWorkOrderId());
                return;
            }
            IntegrationTestResultDTO result = whatsAppClient.send(config, phone, buildMessage(event));
            if (!result.isSuccess()) {
                log.warn("WhatsApp notification for work order {} failed: {}", event.getWorkOrderId(),
                        result.getMessage());
            }
        } catch (Exception e) {
            log.warn("WhatsApp notification for work order {} failed: {}", event.getWorkOrderId(),
                    e.getClass().getSimpleName());
        }
    }

    static boolean isEventEnabled(IntegrationConfig config, WorkOrderNotificationEvent event) {
        return switch (event.getType()) {
            case WORK_ORDER_CREATED -> config.isNotifyOnWorkOrderCreated();
            case TECHNICIAN_ASSIGNED -> config.isNotifyOnTechnicianAssigned();
            case TECHNICIAN_ARRIVED -> config.isNotifyOnTechnicianArrived();
            case WORK_ORDER_COMPLETED -> config.isNotifyOnWorkOrderCompleted();
        };
    }

    /**
     * Returns the phone in E.164 format, or null when it is missing or cannot be normalized
     * (only numbers that already start with + are accepted).
     */
    static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String cleaned = phone.trim().replaceAll("[\s().-]", "");
        if (!cleaned.startsWith("+")) {
            return null;
        }
        return WhatsAppClient.E164.matcher(cleaned).matches() ? cleaned : null;
    }

    static String buildMessage(WorkOrderNotificationEvent event) {
        String title = event.getTitle() == null ? "" : event.getTitle().replaceAll("[\r\n]+", " ").trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH) + "...";
        }
        return switch (event.getType()) {
            case WORK_ORDER_CREATED -> "Atlas CMMS: se creó la orden de trabajo \"" + title + "\".";
            case TECHNICIAN_ASSIGNED -> "Atlas CMMS: se te asignó la orden de trabajo \"" + title + "\".";
            case TECHNICIAN_ARRIVED -> "Atlas CMMS: el técnico llegó al sitio de la orden de trabajo \"" + title
                    + "\".";
            case WORK_ORDER_COMPLETED -> "Atlas CMMS: la orden de trabajo \"" + title + "\" fue completada.";
        };
    }
}
