package com.powerdialer.datamanipulation.supplier;

import java.util.Map;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;

import com.powerdialer.datamanipulation.supplier.datasource.StreamingXlsxTableDataSource;
import org.apache.commons.io.FilenameUtils;
import org.springframework.web.multipart.MultipartFile;

import com.powerdialer.datamanipulation.supplier.datasource.CsvDataSource;
import com.powerdialer.datamanipulation.supplier.datasource.XlsDataSource;
import com.powerdialer.datamanipulation.supplier.datasource.XlsxDataSource;
import com.powerdialer.exceptions.BadContentException;
import com.powerdialer.exceptions.MultipartFileProcessingException;
import com.powerdialer.exceptions.UnknownFileExtensionException;

public class DataSupplierBuilder<T> {

  private InputStream inputStream;
  private String contentFormat;
  private Class<T> dataType;
  private boolean sourceFirstRowColumnNames = true;

  private DataSupplierBuilder() {}

  public static <T> DataSupplierBuilder<T> getInstance() {
    return new DataSupplierBuilder<>();
  }

  public DataSupplierBuilder<T> usingMultipartFileSource(MultipartFile source) throws IOException {
    inputStream = source.getInputStream();
    contentFormat = FilenameUtils.getExtension(source.getOriginalFilename());
    return this;
  }

  public DataSupplierBuilder<T> usingStreamingXlsxSource(MultipartFile file) throws IOException {
      inputStream = file.getInputStream();
      contentFormat = "xlsx_streaming";
      return this;
  }

  /**
   * Supply datasource as string. Supported content types: {"application/json", "application/xml", "text/csv"}
   * @param source
   * @param contentType
   * @return
   * @throws IOException
   */
  public DataSupplierBuilder<T> usingStringSource(String source, String contentType) throws IOException {
    inputStream = new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
    if (contentType.equalsIgnoreCase("application/json")) {
      contentFormat = "json";
    } else if (contentType.equalsIgnoreCase("application/xml")) {
      contentFormat = "xml";
    } else if (contentType.equalsIgnoreCase("text/csv")) {
      contentFormat = "csv";
    } else {
      throw new BadContentException("Can't server can't handle request with content type: " + contentType);
    }
    return this;
  }


    /**
     * Supply datasource as an InputStream. Supported content types: {"application/json", "application/xml", "text/csv"}
     * @param inputStream input stream of request
     * @param contentType content-type header of request
     * @return
     * @throws IOException
     */
    public DataSupplierBuilder<T> usingInputStreamSource(InputStream inputStream, String contentType) throws IOException {
        this.inputStream = inputStream;

        if (contentType.equalsIgnoreCase("application/json")) {
            contentFormat = "json";
        } else if (contentType.equalsIgnoreCase("application/xml")) {
            contentFormat = "xml";
        } else if (contentType.equalsIgnoreCase("text/csv")) {
            contentFormat = "csv";
        } else {
            throw new BadContentException("Can't server can't handle request with content type: " + contentType);
        }
        return this;
    }

  /**
   * Supply datasource as file from server
   * @param file
   * @return
   * @throws FileNotFoundException
   */
  public DataSupplierBuilder<T> usingFile(String file) throws FileNotFoundException {
    inputStream = new FileInputStream(file);
    contentFormat = "csv";
    return this;
  }

  public DataSupplierBuilder<T> dataType(Class<T> dataType) {
    this.dataType = dataType;
    return this;
  }

  /**
   * Set flag is first row contains column names
   * @param sourceFirstRowColumnNames
   * @return
   */
  public DataSupplierBuilder<T> sourceFirstRowColumnNames(boolean sourceFirstRowColumnNames) {
    this.sourceFirstRowColumnNames = sourceFirstRowColumnNames;
    return this;
  }

  public DataSupplier<T> build() {
    DataSupplier dataSupplier;
    PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream);
    try {
      int readByte = pushbackInputStream.read();
      if (readByte == -1) {
        return null;
      } else {
        pushbackInputStream.unread(readByte);
      }
      if ("json".equalsIgnoreCase(contentFormat)) {
        dataSupplier = new JsonDataSupplier<>(pushbackInputStream, dataType);
      } else if ("xml".equalsIgnoreCase(contentFormat)) {
        dataSupplier = new XmlDataSupplier<>(pushbackInputStream, dataType);
      } else if ("xlsx".equalsIgnoreCase(contentFormat) || "xlsx_streaming".equalsIgnoreCase(contentFormat)) {

          if (Map.class.equals(dataType)) {
              if ("xlsx_streaming".equalsIgnoreCase(contentFormat)) {
                  dataSupplier = new MapDataSupplier(
                          new StreamingXlsxTableDataSource(pushbackInputStream, sourceFirstRowColumnNames)
                  );
              } else {
                  dataSupplier = new MapDataSupplier(
                          new XlsxDataSource(pushbackInputStream, sourceFirstRowColumnNames)
                  );
              }
          } else if (String.class.equals(dataType)) {
              dataSupplier = new StringDataSupplier(new XlsxDataSource(pushbackInputStream, false));
          } else {
              throw new MultipartFileProcessingException("Importing form xlsx files to " + dataType + " not implemented");
          }

      } else if ("xls".equalsIgnoreCase(contentFormat)) {
        if (Map.class.equals(dataType)) {
          dataSupplier = new MapDataSupplier(new XlsDataSource(pushbackInputStream, sourceFirstRowColumnNames));
        } else if (String.class.equals(dataType)) {
          dataSupplier = new StringDataSupplier(new XlsDataSource(pushbackInputStream, false));
        } else {
          throw new MultipartFileProcessingException("Importing form xls files to " + dataType + " not implemented");
        }
      } else if ("csv".equalsIgnoreCase(contentFormat)) {
        if (Map.class.equals(dataType)) {
          dataSupplier = new MapDataSupplier(new CsvDataSource(pushbackInputStream, sourceFirstRowColumnNames));
        } else if (String.class.equals(dataType)) {
          dataSupplier = new StringDataSupplier(new CsvDataSource(pushbackInputStream, false));
        } else {
          throw new MultipartFileProcessingException("Importing form csv files to " + dataType + " not implemented");
        }
      } else {
        throw new UnknownFileExtensionException("Importing form " + contentFormat + " files not implemented");
      }
    } catch (IOException e) {
      throw new MultipartFileProcessingException("Exception occurred during file processing", e);
    }
    return dataSupplier;
  }

}
