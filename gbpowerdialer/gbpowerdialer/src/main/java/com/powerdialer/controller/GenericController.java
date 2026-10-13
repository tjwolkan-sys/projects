package com.powerdialer.controller;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.beanutils.ConvertUtils;
import org.json.JSONObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.service.GenericService;
import com.powerdialer.util.ListDetailed;

/**
 * Generic Controller Advantages: 1. Saves time (you can setup
 * controller-service-dao in seconds). No need for hours setup/test/fix 2. It
 * (controller-service-dao) is already tested! 3. Helps to avoid annoying
 * copy-pasting How to setup: 1. Extend generic dao 2. Extent generic service 3.
 * Extent generic controller Remember to do not put business logic in
 * controllers, services serve this purpose.
 * @author Pasha Omelchenko
 * @param <T> entity type
 * @param <D1> form dto type
 * @param <D2> list dto type
 */

@PreAuthorize("this.isPreAuthorized()")
public abstract class GenericController<T, D1, D2> {

  protected GenericService<T, D1, D2> service;

  public GenericController(GenericService<T, D1, D2> service) {
    this.service = service;
  }

  public GenericService<T, D1, D2> getService() {
    return service;
  }

  public void setDao(GenericService<T, D1, D2> service) {
    this.service = service;
  }

  /**
   * Get all items using pagination, sorting and filtering
   * @return
   */
  @RequestMapping(value = "/list", method = RequestMethod.GET)
  public @ResponseBody ListDetailed<D2> getAll(@RequestParam(value = "page", required = false) JSONObject page, @RequestParam(value = "sort", required = false) JSONObject sort,
                                               @RequestParam(value = "filters", required = false) JSONObject filters) {
    return service.getAll(page, sort, filters);
  }

  /**
   * Get item by id
   * @return
   */
  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  public @ResponseBody D1 get(@PathVariable long id) {
    return service.get(id);
  }

  /**
   * Save (update) item
   * @return
   * @throws DuplicatedNameException
   */
  @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
  public @ResponseBody D1 save(@PathVariable long id, @RequestBody D1 d) throws DuplicatedNameException {
    return service.merge(d);
  }

  /**
   * Create new item
   * @return
   * @throws DuplicatedNameException
   */
  @RequestMapping(method = RequestMethod.POST)
  public @ResponseBody D1 save(@RequestBody D1 d) throws DuplicatedNameException {
    return service.save(d);
  }

  /**
   * Delete item (set deleted=1)
   * @return
   */
  @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
  public @ResponseBody Long delete(@PathVariable long id) {
    service.delete(id);
    return id;
  }

  /**
   * Delete item (set deleted=1)
   * @return
   */
  @RequestMapping(value = "/batch", method = RequestMethod.DELETE)
  public @ResponseBody List<Long> delete(@RequestParam(value = "ids") String ids) {
    List<Long> idList = Arrays.asList((Long[]) ConvertUtils.convert(ids.split(","), Long[].class));
    service.delete(idList);
    return idList;
  }

  @RequestMapping(value = "/batch", method = RequestMethod.PUT)
  public @ResponseBody List<Long> setEnabled(@RequestParam(value = "ids") String ids, @RequestParam(value = "enabled") boolean enabled) {
    List<Long> idList = Arrays.asList((Long[]) ConvertUtils.convert(ids.split(","), Long[].class));
    service.setEnabled(idList, enabled);
    return idList;
  }

  public boolean isPreAuthorized() {
    return true;
  }

}
