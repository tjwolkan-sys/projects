package com.powerdialer.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.powerdialer.dao.ICampaignGroupDao;
import com.powerdialer.dto.CampaignGroupFormDTO;
import com.powerdialer.dto.CampaignGroupListDTO;
import com.powerdialer.model.CampaignGroup;
import com.powerdialer.util.ListDetailed;
import com.powerdialer.validators.CampaignGroupValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.powerdialer.util.Constants.CAMPAIGN_ID_KEY;
import static com.powerdialer.util.Constants.GRID_FILTER_EXACT;

@Service("campaignGroup")
public class CampaignGroupService extends GenericService<CampaignGroup, CampaignGroupFormDTO, CampaignGroupListDTO> {

  @Autowired
  public CampaignGroupService(ICampaignGroupDao dao) {
    super(dao, CampaignGroup.class, CampaignGroupFormDTO.class, CampaignGroupListDTO.class, new CampaignGroupValidator());
  }

  private JSONObject exactFilter(String key, Object term) {
    return new JSONObject().put(key, new JSONArray().put(new JSONObject()
                                                    .put("condition", GRID_FILTER_EXACT)
                                                    .put("term", String.valueOf(term))));
  }

  @Transactional(readOnly = true)
  public List<CampaignGroupListDTO> findByCampaignId(Long campaignId) {
    ListDetailed<CampaignGroupListDTO> page = super.getAll(new JSONObject(), new JSONObject(), exactFilter(CAMPAIGN_ID_KEY, campaignId));
    return page != null && page.getData() != null ? page.getData() : java.util.Collections.<CampaignGroupListDTO>emptyList();
  }
}
