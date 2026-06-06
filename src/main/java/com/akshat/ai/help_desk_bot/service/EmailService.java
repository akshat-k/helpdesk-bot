package com.akshat.ai.help_desk_bot.service;

import com.akshat.ai.help_desk_bot.event.TicketEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.support-team}")
    private String supportTeamEmail;

    // Builds common context for all ticket emails
    private Context buildContext(TicketEvent event) {
        Context context = new Context();
        context.setVariable("ticketId", event.getTicketId());
        context.setVariable("title", event.getTitle());
        context.setVariable("description", event.getDescription());
        context.setVariable("priority", event.getPriority());
        context.setVariable("status", event.getStatus());
        context.setVariable("assignee", event.getAssignee());
        context.setVariable("occurredAt", event.getOccurredAt());
        context.setVariable("supportEmail", supportTeamEmail);
        return context;
    }

    public void sendTicketCreatedEmail(TicketEvent event) {
        sendEmail(
                event.getUserEmail(),
                "Ticket #" + event.getTicketId() + " Created — " + event.getTitle(),
                "email/ticket-created",
                buildContext(event)
        );
    }

    public void sendTicketUpdatedEmail(TicketEvent event) {
        sendEmail(
                event.getUserEmail(),
                "Ticket #" + event.getTicketId() + " Updated — " + event.getTitle(),
                "email/ticket-updated",
                buildContext(event)  // ← full details now included
        );
    }

    public void sendTicketResolvedEmail(TicketEvent event) {
        sendEmail(
                event.getUserEmail(),
                "✅ Ticket #" + event.getTicketId() + " Resolved — " + event.getTitle(),
                "email/ticket-resolved",
                buildContext(event)  // ← resolved template only uses ticketId + title, extra vars are harmless
        );
    }

    private void sendEmail(String to, String subject, String template, Context context) {
        try {
            String html = templateEngine.process(template, context);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent to={} subject='{}'", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to={} subject='{}'", to, subject, e);
        }
    }
}