package org.mfa.util;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailSender {
    private final JavaMailSender mail;
    public EmailSender(JavaMailSender mail) { this.mail = mail; }

    public void sendOtp(String to, String code) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("Your verification code");
        msg.setText("Your one-time verification code is: " + code + "\nIt expires in 5 minutes.");
        mail.send(msg);
    }
}
