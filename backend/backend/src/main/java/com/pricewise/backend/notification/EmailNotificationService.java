package com.pricewise.backend.notification;

import com.pricewise.backend.entity.NotificationRecord;
import com.pricewise.backend.entity.TrackedProduct;
import com.pricewise.backend.repository.NotificationRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    @Value("${spring.mail.username:}")
    private String mailSenderUser;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final NotificationRecordRepository notificationRecordRepository;

    public EmailNotificationService(NotificationRecordRepository notificationRecordRepository) {
        this.notificationRecordRepository = notificationRecordRepository;
    }

    @Override
    public void sendPriceDropAlert(TrackedProduct tracked, double previousPrice, double newPrice, double dropAmount, double dropPercentage) {
        String recipient = tracked.getUserEmail();
        String productName = tracked.getStoreProduct().getProduct().getCanonicalName();
        String store = tracked.getStoreProduct().getStore();

        String message = String.format(
                "Great news! The price of '%s' at %s dropped by ₹%,.0f (%.1f%%) from ₹%,.0f to ₹%,.0f.",
                productName, store, dropAmount, dropPercentage, previousPrice, newPrice
        );

        log.info("PRICE DROP ALERT: {}", message);

        String status = "EMAIL NOT CONFIGURED";

        if (recipient != null && !recipient.trim().isEmpty() && mailSender != null && mailSenderUser != null && !mailSenderUser.trim().isEmpty()) {
            try {
                SimpleMailMessage mailMessage = new SimpleMailMessage();
                mailMessage.setFrom(mailSenderUser);
                mailMessage.setTo(recipient);
                mailMessage.setSubject("🔥 Price Drop Alert: " + productName + " is now ₹" + String.format("%,.0f", newPrice));
                mailMessage.setText(message + "\n\nView deal: " + tracked.getStoreProduct().getProductUrl());

                mailSender.send(mailMessage);
                status = "SENT";
                log.info("Email alert successfully sent to: {}", recipient);
            } catch (Exception e) {
                status = "FAILED";
                log.error("Failed to send email to {}: {}", recipient, e.getMessage());
            }
        } else {
            log.warn("EMAIL NOT CONFIGURED: SMTP mail sender is not configured. Price drop recorded internally with status 'EMAIL NOT CONFIGURED'.");
        }

        NotificationRecord record = new NotificationRecord(
                tracked,
                recipient,
                previousPrice,
                newPrice,
                dropAmount,
                dropPercentage,
                status,
                message
        );
        notificationRecordRepository.save(record);
    }
}
