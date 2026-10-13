package com.powerdialer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powerdialer.dao.IFeedbackDao;
import com.powerdialer.dto.FeedbackFormDTO;
import com.powerdialer.dto.FeedbackListDTO;
import com.powerdialer.exceptions.DuplicatedNameException;
import com.powerdialer.model.Feedback;
import com.powerdialer.model.User;
import com.powerdialer.util.MailUtil;
import com.powerdialer.validators.FeedbackValidator;

@Service
@Transactional(readOnly = true)
public class FeedbackService extends GenericService<Feedback, FeedbackFormDTO, FeedbackListDTO> {

  private static final Logger LOG = LoggerFactory.getLogger(FeedbackService.class);

  @Autowired
  public FeedbackService(IFeedbackDao dao) {
    super(dao, Feedback.class, FeedbackFormDTO.class, FeedbackListDTO.class, new FeedbackValidator());
  }

  @Autowired
  private MailUtil mailUtil;

  @Autowired
  private TenantService tenantService;

  private User user;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  @Transactional(readOnly = false)
  @Override
  public FeedbackFormDTO save(FeedbackFormDTO item) {
    FeedbackFormDTO savedFeedback = null;
    user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    try {
      item.setTenant(user.getTenant().getId());
      savedFeedback = super.save(item);
      sendMail(savedFeedback);
    } catch (DuplicatedNameException e) {
      LOG.error("", e);
    }
    return savedFeedback;
  }

  private void sendMail(final FeedbackFormDTO feedback) {
    new Thread(() -> {
      try {
        String feedbackEmail = tenantService.get(feedback.getTenant()).getEmail();
        if (!feedbackEmail.isEmpty()) {
          mailUtil.sendMail(feedbackEmail, user.getId() + " - " + feedback.getType().name(),
                            "<font size='2'>" + (feedback.getMessage().length() > 0 ? feedback.getMessage() + "</br></br>" : "") + feedback.getLogArray()
                                + "</br>This is an automatically generated email, please do not reply</font>");
        }
      } catch (Exception e) {
        LOG.error("", e);
      }
    }).start();
  }

}
