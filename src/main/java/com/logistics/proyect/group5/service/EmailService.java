package com.logistics.proyect.group5.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        String subject = "Recuperación de Contraseña — NEXORA STORE";
        String body = "Hola,\n\n"
                + "Hemos recibido una solicitud para restablecer la contraseña de tu cuenta en NEXORA STORE.\n\n"
                + "Haz clic en el siguiente enlace para crear tu nueva contraseña:\n"
                + resetLink + "\n\n"
                + "Este enlace tiene una validez de 60 minutos.\n"
                + "Si no realizaste esta solicitud, puedes ignorar este mensaje con total seguridad.\n\n"
                + "Atentamente,\n"
                + "El equipo de NEXORA STORE & Logistics";

        log.info("================================================================================");
        log.info("[EMAIL SERVICE] Solicitud de restablecimiento de contraseña para: {}", toEmail);
        log.info("[EMAIL SERVICE] Asunto: {}", subject);
        log.info("[EMAIL SERVICE] Enlace de recuperación: {}", resetLink);
        log.info("[EMAIL SERVICE] Token: {}", token);
        log.info("================================================================================");

        if (mailSender != null && mailFrom != null && !mailFrom.isBlank()) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(toEmail);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
                log.info("[EMAIL SERVICE] Correo de recuperación enviado vía SMTP exitosamente a {}", toEmail);
            } catch (Exception ex) {
                log.warn("[EMAIL SERVICE] No se pudo enviar el correo vía SMTP (servidor SMTP no disponible o credenciales inválidas): {}", ex.getMessage());
            }
        } else {
            log.info("[EMAIL SERVICE] Modo desarrollo/sin SMTP: Enlace generado y registrado en consola para pruebas.");
        }
    }
}
