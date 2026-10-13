package com.powerdialer.util;

import java.util.Properties;

import javax.inject.Inject;
import javax.mail.internet.MimeMessage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Util class to send e-mail
 */
@Component
@Lazy(false)
public class MailUtil {
  private static final Log LOG = LogFactory.getLog(MailUtil.class);

  private JavaMailSenderImpl sender = new JavaMailSenderImpl();
  private String mailFrom;

  /**
   * Initializing all parameters to prepare to mail sending
   * @param feedbackMailPort
   * @param feedbackMailLogin
   * @param feedbackMailPassword
   * @param feedbackMailHost
   * @param feedbackMailAuth
   * @param feedbackMailFrom
   */
  @Inject
  public MailUtil(@Value("${mail.port}") String feedbackMailPort,
                  @Value("${mail.LOGin}") String feedbackMailLogin,
                  @Value("${mail.password}") String feedbackMailPassword,
                  @Value("${mail.host}") String feedbackMailHost,
                  @Value("${mail.auth}") String feedbackMailAuth,
                  @Value("${mail.from}") String feedbackMailFrom) {
    Properties properties = new Properties();
    if (feedbackMailAuth != null && !Boolean.parseBoolean(feedbackMailAuth)) {
      properties.put("mail.smtp.auth", feedbackMailAuth);
    } else {
      properties.put("mail.smtp.auth", "true");
      sender.setPassword(feedbackMailPassword);
      sender.setUsername(feedbackMailLogin);
    }
    sender.setHost(feedbackMailHost);
    sender.setPort(Integer.parseInt(feedbackMailPort));
    sender.setJavaMailProperties(properties);
    mailFrom = feedbackMailFrom;
  }

  /**
   * This method send mail to appropriate recipient with appropriate data
   * @param to
   * @param subject
   * @param text
   */
  public void sendMail(String to, String subject, String text) {
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setFrom(mailFrom);
      helper.setTo(to.split(";"));
      helper.setSubject(subject);
      helper.setText(text, true); // 'true' means HTML email mode on
      sender.send(message);
      LOG.debug("Mail sent to " + to + " successfully!");
    } catch (Exception e) {
      LOG.error("", e);
    }

  }
}
