package com.mediqueue.appointment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediqueue.appointment.domain.Appointment;
import com.mediqueue.appointment.domain.AppointmentAudit;
import com.mediqueue.appointment.domain.AppointmentHold;
import com.mediqueue.appointment.domain.OutboxEvent;
import com.mediqueue.appointment.domain.enums.AppointmentStatus;
import com.mediqueue.appointment.domain.enums.HoldStatus;
import com.mediqueue.appointment.domain.enums.OutboxPublicationStatus;
import com.mediqueue.appointment.repository.AppointmentAuditRepository;
import com.mediqueue.appointment.repository.AppointmentHoldRepository;
import com.mediqueue.appointment.repository.AppointmentRepository;
import com.mediqueue.appointment.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job that detects expired appointment holds and transitions
 * the associated appointments to {@code EXPIRED} status.
 *
 * <p>Each hold is processed in its own transaction so that a failure
 * on one hold does not prevent the remaining holds from being expired.</p>
 *
 * @since 0.0.1
 */
@Component
public class HoldExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpirationScheduler.class);

    private final AppointmentHoldRepository holdRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentAuditRepository auditRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public HoldExpirationScheduler(AppointmentHoldRepository holdRepository,
                                   AppointmentRepository appointmentRepository,
                                   AppointmentAuditRepository auditRepository,
                                   OutboxEventRepository outboxEventRepository,
                                   ObjectMapper objectMapper,
                                   TransactionTemplate transactionTemplate) {
        this.holdRepository = holdRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditRepository = auditRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Scans for active holds whose TTL has expired and transitions them.
     *
     * <p>Runs on a fixed delay configured via
     * {@code mediqueue.hold.expiration-scan-seconds} (default: 30 seconds).</p>
     */
    @Scheduled(fixedDelayString = "${mediqueue.hold.expiration-scan-seconds:30}000")
    public void expireHolds() {
        List<AppointmentHold> expiredHolds = holdRepository
                .findByHoldStatusAndExpiresAtBefore(HoldStatus.ACTIVE, Instant.now());

        for (AppointmentHold hold : expiredHolds) {
            try {
                transactionTemplate.executeWithoutResult(status -> processExpiredHold(hold));
                log.info("hold_expired holdId={} appointmentId={}",
                        hold.getHoldId(), hold.getAppointment().getAppointmentId());
            } catch (Exception ex) {
                log.error("hold_expiration_failed holdId={}", hold.getHoldId(), ex);
            }
        }
    }

    private void processExpiredHold(AppointmentHold hold) {
        hold.setHoldStatus(HoldStatus.EXPIRED);
        hold.setReleasedAt(Instant.now());
        holdRepository.save(hold);

        Appointment appointment = hold.getAppointment();
        appointment.setAppointmentStatus(AppointmentStatus.EXPIRED);
        appointmentRepository.save(appointment);

        AppointmentAudit audit = new AppointmentAudit();
        audit.setAppointmentId(appointment.getAppointmentId());
        audit.setPreviousStatus(AppointmentStatus.PENDING_PAYMENT);
        audit.setNewStatus(AppointmentStatus.EXPIRED);
        audit.setChangeReason("Hold expirado por TTL");
        auditRepository.save(audit);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("appointments-exchange");
        event.setAggregateId(appointment.getAppointmentId());
        event.setEventType("appointment.expired");
        event.setPayload(buildPayload(appointment));
        event.setPublicationStatus(OutboxPublicationStatus.PENDING);
        outboxEventRepository.save(event);
    }

    private String buildPayload(Appointment appointment) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("appointmentId", appointment.getAppointmentId().toString());
        node.put("patientId", appointment.getPatientId().toString());
        node.put("dentistId", appointment.getDentistId().toString());
        node.put("appointmentDate", appointment.getAppointmentDate().toString());
        node.put("startTime", appointment.getStartTime().toString());
        return node.toString();
    }
}
