package com.powerdialer.service;

import static com.powerdialer.util.Constants.VALIDATOR_EXPRESSION_PATTERN;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.powerdialer.util.FileReader;

@Service
public class LocaleService {

  private Pattern expressionPattern = Pattern.compile(VALIDATOR_EXPRESSION_PATTERN);
  private Properties en;
  private Properties ru;
  private Properties tr;
  private Properties ua;

  public LocaleService() {
    super();
    this.en = FileReader.readProperties(true, "/ValidationMessages.properties");
    this.ru = FileReader.readProperties(true, "/ValidationMessages_ru_RU.properties");
    this.tr = FileReader.readProperties(true, "/ValidationMessages_tr_TR.properties");
    this.ua = FileReader.readProperties(true, "/ValidationMessages_ua_UA.properties");
  }

  public void setLocale(String lang, HttpServletRequest req, HttpServletResponse res) {
    req.getSession().setAttribute("locale", lang);
    Locale locale = new Locale(lang);
    LocaleContextHolder.setLocale(locale);
  }

  /**
   * Get localized message by template
   */
  public String get(String template, String lang) {
    String result;
    if (lang.equals("ru")) {
      return (String) ru.get(template);
    }
    if (lang.equals("tr")) {
      return (String) tr.get(template);
    }
    if (lang.equals("ua")) {
      return (String) ua.get(template);
    }
    result = (String) en.get(template);
    return result;
  }

  public String getWithArguments(String template, String lang, Object... arguments) {
    return String.format(get(template, lang), arguments);
  }

  /**
   * Get constraint message and evaluate expressions using payload
   */
  public String getEvaluatingAttributes(String template, String lang, Map<String, Object> attributes) {
    String localizedMessage = get(template, lang);
    if (localizedMessage == null) {
      return template;
    }
    if (attributes == null || attributes.isEmpty())
      return localizedMessage;
    Matcher m = expressionPattern.matcher(localizedMessage);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String expression = m.group(0);
      String key = expression.replace("{", "").replace("}", "");
      Object expressionObject = attributes.get(key);
      String expressionValue;
      expressionValue = expressionObject != null ? expressionObject.toString() : expression;
      m.appendReplacement(sb, expressionValue);
    }
    m.appendTail(sb);
    return sb.toString();
  }

}
