package com.powerdialer.datamanipulation.supplier.datasource;

import com.monitorjbl.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Memory-efficient XLSX streaming reader.
 * Compatible with Apache POI 3.13 (legacy API).
 * Reads Excel rows without loading entire file into memory.
 */
public class StreamingXlsxTableDataSource extends TableDataSource {

    private final Iterator<Row> rowIterator;
    private final int columnCount;

    public StreamingXlsxTableDataSource(InputStream inputStream, boolean firstRowColumnNames) {
        super(firstRowColumnNames);

        Workbook workbook = StreamingReader.builder()
                .rowCacheSize(100)
                .bufferSize(4096)
                .open(inputStream);

        Sheet sheet = workbook.getSheetAt(0);
        this.rowIterator = sheet.iterator();

        int detectedColumnCount = 0;

        if (rowIterator.hasNext()) {
            Row headerRow = rowIterator.next();
            List<String> headerValues = new ArrayList<>();

            for (Cell cell : headerRow) {
                headerValues.add(cell != null ? cell.toString() : "");
            }

            detectedColumnCount = headerValues.size();
            if (firstRowColumnNames) {
                initColumnNames(headerValues);
            }
        }

        this.columnCount = detectedColumnCount;
    }

    @Override
    protected List<String> readRow() {
        if (!rowIterator.hasNext()) return null;
        Row row = rowIterator.next();
        List<String> values = new ArrayList<>(columnCount);

        int i = 0;
        for (Cell cell : row) {
            if (cell == null) {
                values.add("");
            } else {
                switch (cell.getCellType()) {
                    case Cell.CELL_TYPE_STRING:
                        values.add(cell.getStringCellValue());
                        break;
                    case Cell.CELL_TYPE_NUMERIC:
                        if (DateUtil.isCellDateFormatted(cell))
                            values.add(cell.getDateCellValue().toString());
                        else
                            values.add(String.valueOf(cell.getNumericCellValue()));
                        break;
                    case Cell.CELL_TYPE_BOOLEAN:
                        values.add(String.valueOf(cell.getBooleanCellValue()));
                        break;
                    default:
                        values.add("");
                        break;
                }
            }
            i++;
        }

        while (values.size() < columnCount) {
            values.add("");
        }

        return values;
    }
}
