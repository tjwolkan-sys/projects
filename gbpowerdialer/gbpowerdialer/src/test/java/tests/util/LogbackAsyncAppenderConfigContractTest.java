package tests.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Task 325469 (A4-5): Uretim dump'inda 84 thread FILE+CONSOLE senkron
 * appender'larinin CachingDateFormatter kilidinde bekliyordu. Bu contract
 * testi root logger'in appender'lara dogrudan degil, kayipsiz yapilandirilmis
 * (discardingThreshold=0) AsyncAppender sarmalayicilari uzerinden bagli
 * oldugunu kilitler; log icerigi/formati degismez, yalniz yazim async olur.
 */
public class LogbackAsyncAppenderConfigContractTest {

  private Document loadLogbackXml() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/logback.xml")) {
      assertNotNull("logback.xml test classpath'inde bulunamadi", in);
      return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
    }
  }

  private Map<String, Element> appendersByName(Document doc) {
    Map<String, Element> appenders = new HashMap<>();
    NodeList nodes = doc.getDocumentElement().getElementsByTagName("appender");
    for (int i = 0; i < nodes.getLength(); i++) {
      Element appender = (Element) nodes.item(i);
      appenders.put(appender.getAttribute("name"), appender);
    }
    return appenders;
  }

  private Set<String> appenderRefsOf(Element element) {
    Set<String> refs = new HashSet<>();
    NodeList nodes = element.getElementsByTagName("appender-ref");
    for (int i = 0; i < nodes.getLength(); i++) {
      refs.add(((Element) nodes.item(i)).getAttribute("ref"));
    }
    return refs;
  }

  private String childText(Element element, String tagName) {
    NodeList nodes = element.getElementsByTagName(tagName);
    return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : null;
  }

  @Test
  public void rootLoggerWritesThroughAsyncAppendersOnly() throws Exception {
    Document doc = loadLogbackXml();
    Element root = (Element) doc.getDocumentElement().getElementsByTagName("root").item(0);
    assertNotNull("root logger tanimli olmali", root);

    Set<String> expected = new HashSet<>();
    expected.add("ASYNC_FILE");
    expected.add("ASYNC_CONSOLE");
    assertEquals("root yalniz async sarmalayicilara bagli olmali", expected, appenderRefsOf(root));
  }

  @Test
  public void asyncWrappersAreLosslessAndDelegateToOriginalAppenders() throws Exception {
    Map<String, Element> appenders = appendersByName(loadLogbackXml());

    Element asyncFile = appenders.get("ASYNC_FILE");
    Element asyncConsole = appenders.get("ASYNC_CONSOLE");
    assertNotNull("ASYNC_FILE appender tanimli olmali", asyncFile);
    assertNotNull("ASYNC_CONSOLE appender tanimli olmali", asyncConsole);

    assertEquals("ch.qos.logback.classic.AsyncAppender", asyncFile.getAttribute("class"));
    assertEquals("ch.qos.logback.classic.AsyncAppender", asyncConsole.getAttribute("class"));

    assertEquals("Kayipsizlik icin discardingThreshold=0 olmali (ASYNC_FILE)", "0", childText(asyncFile, "discardingThreshold"));
    assertEquals("Kayipsizlik icin discardingThreshold=0 olmali (ASYNC_CONSOLE)", "0", childText(asyncConsole, "discardingThreshold"));

    assertEquals(new HashSet<>(java.util.Collections.singletonList("FILE")), appenderRefsOf(asyncFile));
    assertEquals(new HashSet<>(java.util.Collections.singletonList("CONSOLE")), appenderRefsOf(asyncConsole));
  }

  @Test
  public void originalFileAndConsoleAppendersArePreservedUnchanged() throws Exception {
    Map<String, Element> appenders = appendersByName(loadLogbackXml());

    Element file = appenders.get("FILE");
    Element console = appenders.get("CONSOLE");
    assertNotNull("FILE appender korunmali", file);
    assertNotNull("CONSOLE appender korunmali", console);
    assertEquals("ch.qos.logback.core.rolling.RollingFileAppender", file.getAttribute("class"));
    assertEquals("ch.qos.logback.core.ConsoleAppender", console.getAttribute("class"));
    assertTrue("FILE hedefi gbpowerdialer.log kalmali",
        childText(file, "file") != null && childText(file, "file").contains("gbpowerdialer.log"));
  }
}
