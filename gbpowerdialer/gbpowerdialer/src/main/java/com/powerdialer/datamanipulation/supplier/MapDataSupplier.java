package com.powerdialer.datamanipulation.supplier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.powerdialer.datamanipulation.supplier.datasource.TableDataSource;
import com.powerdialer.request.ProcessUploadParameterRequest;

/**
 * Supply data as Map from specified {@link TableDataSource}. Key of map is column names, values - string cell values
 */
public class MapDataSupplier extends DataSupplier<Map<String, String>> {

  private TableDataSource tableDataSource;

  public MapDataSupplier(TableDataSource tableDataSource) {
    this.tableDataSource = tableDataSource;
  }

  /**
   * Read single row from table datasource. Here we use LinkedHashMap in order to guarantee order of column.
   * Order is critical in {@link com.powerdialer.service.CampaignDataManipulationService#handleProcessUpload(Long, ProcessUploadParameterRequest)}
   */
  @Override
  protected Map<String, String> getNextObjectInternal() {
    List<String> nextRow = tableDataSource.getNextRow();
    if (nextRow != null) {
      Map<String, String> object = new LinkedHashMap<>();
      for (int i = 0; i < tableDataSource.getColumnNames().length; i++) {
        if (i < nextRow.size()) {
          object.put(tableDataSource.getColumnNames()[i], nextRow.get(i));
        } else {
          object.put(tableDataSource.getColumnNames()[i], "");
        }
      }
      return object;
    } else {
      return null;
    }
  }
}
