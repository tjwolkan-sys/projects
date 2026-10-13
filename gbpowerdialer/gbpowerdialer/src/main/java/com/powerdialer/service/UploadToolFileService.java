package com.powerdialer.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import javax.servlet.ServletContext;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.powerdialer.datamanipulation.supplier.DataSupplier;
import com.powerdialer.datamanipulation.supplier.MapDataSupplier;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * Service for manipulating files used by UploadTool
 */
@Service
@Lazy(false)
public class UploadToolFileService {

  private final LoggerDecorator LOG;

  @Autowired
  private ServletContext context;

  @Value("${uploadTool.fileDirectory}")
  private String fileDirectory;

  @Value("${uploadTool.filesLifetimeDays}")
  private Integer filesLifetimeDays;

  @Autowired
  public UploadToolFileService(LoggerDecorator loggerDecorator, @Value("${uploadTool.filesLifetimeDays}") Integer filesLifetimeDays) {
    this.LOG = loggerDecorator.getLogger(ManagerConnectionDecorator.class);
    this.filesLifetimeDays = filesLifetimeDays;
  }

  /**
   * Delete file which age is greater than uploadTool.filesLifetimeDays property
   */
  @Scheduled(cron = "0 0 4 * * *")
  public void cleanupFiles() {
    File directoryToCheck = new File(fileDirectory);
    File[] files = directoryToCheck.listFiles();
    LOG.info("Check files to delete: " + Arrays.toString(files) + " Max age: " + filesLifetimeDays + " days");
    if (files != null && files.length > 0) {
      long nowMillis = System.currentTimeMillis();
      for (File file : files) {
        long fileAgeInDays = TimeUnit.DAYS.convert(nowMillis - file.lastModified(), TimeUnit.MILLISECONDS);
        if (fileAgeInDays > filesLifetimeDays) {
          if (file.delete()) {
            LOG.info("File " + file + " is deleted");
          }
        }
      }
    }
  }

  /**
   * Save data from dataSupplier to temp file using CSV format
   * @param dataSupplier
   * @param tempFilename
   * @return - first 10 objects saved to temp file
   * @throws IOException
   */
  public List<Map> saveAsTempCsvFile(DataSupplier<Map> dataSupplier, String tempFilename) throws IOException {
    File tempFile = createTempFile(tempFilename);
    BufferedWriter tempFileWriter = Files.newBufferedWriter(Paths.get(tempFile.getAbsolutePath()));
    List<Map> previewRecords = new ArrayList<>();
    try (CSVPrinter csvPrinter = new CSVPrinter(tempFileWriter, CSVFormat.RFC4180)) {
      Map row = dataSupplier.getNextObject();
      List<String> columnNames = new ArrayList<>(row.keySet());
      csvPrinter.printRecord(columnNames);
      do {
        List<String> record = new ArrayList<>();
        for (String columnName : columnNames) {
          String cellStringValue = Optional.of(row)
            .map(r -> r.get(columnName))
            .map(Object::toString)
            .orElse("");
          record.add(cellStringValue);
        }
        if (previewRecords.size() < 10) {
          previewRecords.add(row);
        }
        csvPrinter.printRecord(record);
        row = dataSupplier.getNextObject();
      } while (row != null);
      csvPrinter.flush();
    } catch (Exception e) {
      LOG.error("Creating temp file failed", e);
      tempFile.delete();
    }
    return previewRecords;
  }

  private File createTempFile(String filename) {
    File folder = (File) context.getAttribute(ServletContext.TEMPDIR);
    LOG.info("Create temp file with name: " + filename);
    return new File(folder, filename);
  }

  public String getTempFilePath(String filename) {
    File folder = (File) context.getAttribute(ServletContext.TEMPDIR);
    return new File(folder, filename).getAbsolutePath();
  }

  public boolean deleteTempFile(String filename) {
    File tempFile = createTempFile(filename);
    if (tempFile.exists()) {
      return tempFile.delete();
    }
    return false;
  }

  /**
   * Write single failed row to file. Order of columns names is guaranteed by {@link MapDataSupplier#getNextObjectInternal()}
   * @param row
   * @param filename
   * @throws IOException
   */
  public void writeFailedRowToFile(Map row, String filename, List<String> columnNames) throws IOException {
    File fileForFailedRows = createFileForFailedRows(filename);
    if (!fileForFailedRows.getParentFile().exists()) {
      fileForFailedRows.getParentFile().mkdirs();
    }
    boolean isFileCreated = false;
    if (!fileForFailedRows.exists()) {
      isFileCreated = fileForFailedRows.createNewFile();
    }
    BufferedWriter tempFileWriter = Files.newBufferedWriter(Paths.get(fileForFailedRows.getAbsolutePath()), StandardOpenOption.APPEND);
    try (CSVPrinter csvPrinter = new CSVPrinter(tempFileWriter, CSVFormat.RFC4180)) {
      if (isFileCreated) {
        csvPrinter.printRecord(columnNames);
      }
      for (String columnName : columnNames) {
        csvPrinter.print(row.get(columnName));
      }
      csvPrinter.println();
      csvPrinter.flush();
    }
  }

  private File createFileForFailedRows(String filename) {
    return new File(fileDirectory, filename);
  }

  /**
   * Generate link which can be used for downloading file. Note that link will be valid only on server which generate that link
   * @param filename
   * @return
   */
  public String generateFileAccessLink(String filename) {
    return Constants.CAMPAIGN_CONTROLLER_MAPPING.substring(1) + Constants.CAMPAIGN_CONTROLLER_FAILED_ROWS_MAPPING.replaceAll("\\{.*}", filename);
  }

  public InputStream getInputStreamForFailedRowsFile(String filename) throws FileNotFoundException {
    File fileWithFailedRows = createFileForFailedRows(filename);
    if (fileWithFailedRows.exists()) {
      return new FileInputStream(fileWithFailedRows.getAbsolutePath());
    } else {
      return null;
    }
  }

}
