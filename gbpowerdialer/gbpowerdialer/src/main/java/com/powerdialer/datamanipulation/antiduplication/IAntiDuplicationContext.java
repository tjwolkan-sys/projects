package com.powerdialer.datamanipulation.antiduplication;

import java.util.Map;

public interface IAntiDuplicationContext {

  Map<String, String> performAntiDuplication(Map<String, String> row);

}
