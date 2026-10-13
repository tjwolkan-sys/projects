package com.powerdialer.dao.impl;

import org.springframework.stereotype.Repository;

import com.powerdialer.dao.IFeedbackDao;
import com.powerdialer.model.Feedback;

@Repository
public class FeedbackDao extends GenericDao<Feedback> implements IFeedbackDao {

  public FeedbackDao() {
    super(Feedback.class);
  }

}
