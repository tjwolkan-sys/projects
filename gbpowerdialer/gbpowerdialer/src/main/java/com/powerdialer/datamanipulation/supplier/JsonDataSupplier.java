package com.powerdialer.datamanipulation.supplier;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implementation for importing data from file with JSON structure. Root element
 * must be array. For example :
 *
 * <pre>
 * <code>
 * [
 *    {
 *      "someColName1" : "someValue11",
 *      "someColName2" : "someValue21",
 *    },
 *    {
 *      "someColName1" : "someValue12",
 *      "someColName2" : "someValue22",
 *    },
 *    ...
 *  ]
 * </code>
 * </pre>
 */
public class JsonDataSupplier<T> extends DataSupplier<T> {

  private static JsonFactory jsonFactory = new JsonFactory(new ObjectMapper());

  private final JsonParser parser;
  private final Class<T> dataType;

  public JsonDataSupplier(InputStream inputStream,
                          Class<T> dataType) throws IOException {
    parser = jsonFactory.createParser(inputStream);
    this.dataType = dataType;
  }

  /**
   * Sequential read JSON objects from {@link InputStream} specified in
   * constructor
   * @throws JsonParseException if JSON from {@link InputStream} has invalid
   *                            format
   */
  @Override
  protected T getNextObjectInternal() throws IOException {
    while (parser.getCurrentToken() == null || parser.getCurrentToken() == JsonToken.START_ARRAY) {
      JsonToken previousToken = parser.getCurrentToken();
      parser.nextToken();
      if (previousToken == null && parser.getCurrentToken() == null) {
        return null;
      }
    }
    return parser.readValueAs(dataType);
  }

}
