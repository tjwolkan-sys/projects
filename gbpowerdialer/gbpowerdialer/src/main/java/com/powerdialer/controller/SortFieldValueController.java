package com.powerdialer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.powerdialer.dto.SortFieldValueFormDTO;
import com.powerdialer.dto.SortFieldValueListDTO;
import com.powerdialer.model.SortFieldValue;
import com.powerdialer.service.SortFieldValueService;

@Controller
@RequestMapping("/sortFieldValue")
public class SortFieldValueController extends GenericController<SortFieldValue, SortFieldValueFormDTO, SortFieldValueListDTO> {

  @Autowired
  public SortFieldValueController(SortFieldValueService sortFieldValueService) {
    super(sortFieldValueService);
  }

}
