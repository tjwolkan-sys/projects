package tests.support;

import org.mockito.stubbing.Answer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

public final class MockitoSpringMocks {

  private MockitoSpringMocks() {
  }

  public static <T> T mockBean(Class<T> type) {
    return mock(type, withSettings().withoutAnnotations());
  }

  public static <T> T mockBean(Class<T> type, Answer<?> defaultAnswer) {
    return mock(type, withSettings().defaultAnswer(defaultAnswer).withoutAnnotations());
  }
}
