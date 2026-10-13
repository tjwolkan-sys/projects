package com.powerdialer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.powerdialer.dto.NumberFormDTO;
import com.powerdialer.dto.NumberListDTO;
import com.powerdialer.model.Number;
import com.powerdialer.service.NumberService;

@Controller
@RequestMapping("/number")
public class NumberController extends GenericController<Number, NumberFormDTO, NumberListDTO> {

  @Autowired
  public NumberController(NumberService numberService) {
    super(numberService);
  }

}
