package com.powerdialer.datamanipulation.supplier;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * Implementation for importing data from file with XML structure. For example :
 *
 * <pre>
 * {@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <!DOCTYPE root>
 * <root>
 * <element>
 * <someColName1>someValue11</id>
 * <someColName2>someValue21</fullname>
 * </element>
 * <element>
 * <someColName1>someValue12</id>
 * <someColName2>someValue22</fullname>
 * </element>
 * </root>
 * }
 * </pre>
 */
public class XmlDataSupplier<T> extends DataSupplier<T> {

  private static final JsonFactory xmlFactory;

  static {
    JacksonXmlModule module = new JacksonXmlModule();
    module.setDefaultUseWrapper(false);
    XmlMapper xmlMapper = new XmlMapper(module);
    xmlFactory = new XmlFactory(xmlMapper);
  }

  private JsonParser parser;
  private Class<T> dataType;

  public XmlDataSupplier(InputStream inputStream,
                         Class<T> dataType) throws IOException {
    this.dataType = dataType;
    parser = xmlFactory.createParser(inputStream);
    parser.nextToken();
  }

  @Override
  protected T getNextObjectInternal() throws IOException {
    parser.nextToken();
    if (parser.getCurrentToken() == JsonToken.END_OBJECT)
      parser.nextToken();
    if (parser.getCurrentToken() == null)
      return null;
    if ("element".equals(parser.getCurrentName()))
      parser.nextToken();
    return parser.readValueAs(dataType);
  }
}
