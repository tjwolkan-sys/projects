package tests.support;

import org.mockito.Mockito;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;

/**
 * Prevents Spring from injecting legacy @Autowired fields into Mockito mocks.
 */
public final class MockitoMockBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

  @Override
  public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
    return !Mockito.mockingDetails(bean).isMock();
  }
}
