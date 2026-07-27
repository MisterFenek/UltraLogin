package com.ultralogin.email;

import com.mojang.logging.LogUtils;
import com.ultralogin.config.Messages;
import com.ultralogin.config.UltraLoginConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

public final class EmailService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final ExecutorService executor;
    private volatile String htmlTemplate = "";
    private volatile String txtTemplate = "";

    public EmailService(ExecutorService executor) {
        this.executor = executor;
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.length() <= 255 && EMAIL_PATTERN.matcher(email).matches();
    }

    public void loadTemplates(Path configDir) {
        String lang = Messages.language();
        try {
            Path dir = configDir.resolve("ultralogin");
            Files.createDirectories(dir);
            Path html = dir.resolve("email-template_" + lang + ".html");
            Path txt = dir.resolve("email-template_" + lang + ".txt");

            if (Files.notExists(html)) {
                String res = "/ultralogin/email-template_" + lang + ".html";
                if (Messages.class.getResource(res) == null) {
                    res = "/ultralogin/email-template_" + Messages.FALLBACK_LANGUAGE + ".html";
                }
                Messages.copyResource(res, html);
            }
            if (Files.notExists(txt)) {
                String res = "/ultralogin/email-template_" + lang + ".txt";
                if (Messages.class.getResource(res) == null) {
                    res = "/ultralogin/email-template_" + Messages.FALLBACK_LANGUAGE + ".txt";
                }
                Messages.copyResource(res, txt);
            }
            htmlTemplate = Files.readString(html, StandardCharsets.UTF_8);
            txtTemplate = Files.readString(txt, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[UltraLogin] Failed to load email templates ({})", lang, e);
        }
    }

    public CompletableFuture<Boolean> sendRecoveryCode(String to, String playerName, String code) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, String> vars = Map.of(
                        "{code}", code,
                        "{player}", playerName,
                        "{project}", UltraLoginConfig.PROJECT_NAME.get(),
                        "{ttl}", String.valueOf(UltraLoginConfig.RECOVERY_CODE_TTL_MINUTES.get()));
                String html = apply(htmlTemplate, vars);
                String txt = apply(txtTemplate, vars);
                send(to, Messages.plain("email.subject", UltraLoginConfig.PROJECT_NAME.get()), html, txt);
                return true;
            } catch (Exception e) {
                LOGGER.error("[UltraLogin] Failed to send recovery email to {}", to, e);
                return false;
            }
        }, executor);
    }

    private static String apply(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    private void send(String to, String subject, String html, String txt) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", UltraLoginConfig.SMTP_HOST.get());
        props.put("mail.smtp.port", String.valueOf(UltraLoginConfig.SMTP_PORT.get()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        if (UltraLoginConfig.SMTP_SSL.get()) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        if (UltraLoginConfig.SMTP_STARTTLS.get()) {
            props.put("mail.smtp.starttls.enable", "true");
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                        UltraLoginConfig.SMTP_USERNAME.get(),
                        UltraLoginConfig.SMTP_PASSWORD.get());
            }
        });

        String from = UltraLoginConfig.SMTP_FROM.get();
        if (from.isBlank()) {
            from = UltraLoginConfig.SMTP_USERNAME.get();
        }

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject, "UTF-8");

        MimeMultipart multipart = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(txt, "UTF-8");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(html, "text/html; charset=UTF-8");
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(htmlPart);
        message.setContent(multipart);

        Transport.send(message);
    }
}
