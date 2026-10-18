package com.logistics.proyect.group5.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

        log.info("================================================================================");
        log.info("[EMAIL SERVICE] Solicitud de restablecimiento de contraseña para: {}", toEmail);
        log.info("[EMAIL SERVICE] Enlace de recuperación: {}", resetLink);
        log.info("================================================================================");

        if (mailSender != null && mailFrom != null && !mailFrom.isBlank()) {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

                helper.setFrom(mailFrom, "NEXORA STORE");
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(buildPasswordResetHtml(resetLink), true);

                mailSender.send(mimeMessage);
                log.info("[EMAIL SERVICE] Correo HTML de recuperación enviado exitosamente a {}", toEmail);
            } catch (Exception ex) {
                log.warn("[EMAIL SERVICE] No se pudo enviar el correo SMTP: {}", ex.getMessage());
            }
        } else {
            log.info("[EMAIL SERVICE] Modo sin SMTP — enlace generado en consola para pruebas.");
        }
    }

    private String buildPasswordResetHtml(String resetLink) {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
              <title>Recuperar contraseña</title>
            </head>
            <body style="margin:0;padding:0;background-color:#0f0f11;font-family:'Segoe UI',Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0f0f11;padding:40px 20px;">
                <tr>
                  <td align="center">
                    <table width="560" cellpadding="0" cellspacing="0"
                           style="background:#1a1a2e;border-radius:16px;overflow:hidden;border:1px solid #2a2a4a;">

                      <!-- Header -->
                      <tr>
                        <td style="background:linear-gradient(135deg,#6c63ff,#4f46e5);padding:36px 40px;text-align:center;">
                          <h1 style="margin:0;color:#ffffff;font-size:26px;font-weight:700;letter-spacing:-0.5px;">
                            NEXORA STORE
                          </h1>
                          <p style="margin:6px 0 0;color:#c4c0ff;font-size:13px;letter-spacing:2px;text-transform:uppercase;">
                            Sistema de Paquetería &amp; Logistics
                          </p>
                        </td>
                      </tr>

                      <!-- Body -->
                      <tr>
                        <td style="padding:40px 40px 32px;">
                          <h2 style="margin:0 0 12px;color:#ffffff;font-size:20px;font-weight:600;">
                            Solicitud de recuperación de contraseña
                          </h2>
                          <p style="margin:0 0 24px;color:#a0a0b8;font-size:15px;line-height:1.6;">
                            Hemos recibido una solicitud para restablecer la contraseña de tu cuenta.
                            Haz clic en el botón a continuación para crear una nueva contraseña.
                          </p>

                          <!-- CTA Button -->
                          <table cellpadding="0" cellspacing="0" width="100%%">
                            <tr>
                              <td align="center" style="padding:8px 0 28px;">
                                <a href="%s"
                                   style="display:inline-block;background:linear-gradient(135deg,#6c63ff,#4f46e5);
                                          color:#ffffff;text-decoration:none;font-size:15px;font-weight:600;
                                          padding:14px 36px;border-radius:8px;letter-spacing:0.3px;">
                                  Restablecer mi contraseña
                                </a>
                              </td>
                            </tr>
                          </table>

                          <!-- Warning box -->
                          <table cellpadding="0" cellspacing="0" width="100%%"
                                 style="background:#1e1e35;border:1px solid #2a2a4a;border-left:3px solid #6c63ff;
                                        border-radius:8px;margin-bottom:24px;">
                            <tr>
                              <td style="padding:14px 18px;">
                                <p style="margin:0;color:#a0a0b8;font-size:13px;line-height:1.6;">
                                  ⏰ <strong style="color:#c4c0ff;">Este enlace expira en 60 minutos.</strong><br/>
                                  Si no solicitaste este cambio, puedes ignorar este correo con total seguridad.
                                  Tu contraseña no será modificada.
                                </p>
                              </td>
                            </tr>
                          </table>

                          <!-- Fallback link -->
                          <p style="margin:0;color:#606080;font-size:12px;line-height:1.6;">
                            Si el botón no funciona, copia y pega este enlace en tu navegador:<br/>
                            <a href="%s" style="color:#6c63ff;word-break:break-all;">%s</a>
                          </p>
                        </td>
                      </tr>

                      <!-- Footer -->
                      <tr>
                        <td style="background:#12122a;padding:20px 40px;text-align:center;
                                   border-top:1px solid #2a2a4a;">
                          <p style="margin:0;color:#505070;font-size:12px;line-height:1.6;">
                            © 2025 NEXORA STORE &amp; Logistics — Todos los derechos reservados.<br/>
                            Este es un correo automático, por favor no respondas a este mensaje.
                          </p>
                        </td>
                      </tr>

                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(resetLink, resetLink, resetLink);
    }
}
