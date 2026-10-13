package tests.fastagi;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.asteriskjava.fastagi.DefaultAgiServer;
import org.asteriskjava.fastagi.MappingStrategy;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;

import com.powerdialer.engine.asterisk.fastagi.CustomAgiServerThread;

/**
 * Unit tests for the FastAGI thread pool sizing of {@link CustomAgiServerThread}.
 *
 * <p>Every bridged call holds one AGI thread for the whole Dial. On 01.10.2026 the former hard-coded
 * maximum of 150 was exhausted on Tcell and asterisk-java rejected the AGI request of every newly
 * answered call ("Execution was rejected by pool"), dropping the customer before an agent was
 * reached. These tests lock the configurable sizes, their defaults and the safe fallbacks.
 */
public class CustomAgiServerThreadPoolSizeTest {

  private final MappingStrategy mappingStrategy = Mockito.mock(MappingStrategy.class);

  @Test
  public void configuredPoolSizesShouldBeApplied() {
    DefaultAgiServer agiServer = CustomAgiServerThread.createAgiServer(mappingStrategy, 20, 1500);

    assertThat(agiServer.getPoolSize(), is(20));
    assertThat(agiServer.getMaximumPoolSize(), is(1500));
  }

  @Test
  public void defaultsShouldKeepCoreSizeAndRaiseMaximumAboveFormerLimit() {
    DefaultAgiServer agiServer = CustomAgiServerThread.createAgiServer(mappingStrategy,
        CustomAgiServerThread.DEFAULT_POOL_SIZE, CustomAgiServerThread.DEFAULT_MAX_POOL_SIZE);

    assertThat(agiServer.getPoolSize(), is(10));
    assertThat(agiServer.getMaximumPoolSize(), is(1000));
  }

  @Test
  public void nonPositiveSizesShouldFallBackToDefaults() {
    DefaultAgiServer zero = CustomAgiServerThread.createAgiServer(mappingStrategy, 0, 0);
    DefaultAgiServer negative = CustomAgiServerThread.createAgiServer(mappingStrategy, -5, -1);

    assertThat(zero.getPoolSize(), is(CustomAgiServerThread.DEFAULT_POOL_SIZE));
    assertThat(zero.getMaximumPoolSize(), is(CustomAgiServerThread.DEFAULT_MAX_POOL_SIZE));
    assertThat(negative.getPoolSize(), is(CustomAgiServerThread.DEFAULT_POOL_SIZE));
    assertThat(negative.getMaximumPoolSize(), is(CustomAgiServerThread.DEFAULT_MAX_POOL_SIZE));
  }

  @Test
  public void maximumBelowCoreShouldBeRaisedToCoreInsteadOfFailing() {
    // asterisk-java throws IllegalArgumentException when maximum < core; startup must not fail.
    DefaultAgiServer agiServer = CustomAgiServerThread.createAgiServer(mappingStrategy, 50, 20);

    assertThat(agiServer.getPoolSize(), is(50));
    assertThat(agiServer.getMaximumPoolSize(), is(50));
  }

  @Test
  public void poolSizePropertyKeysAndDefaultsShouldBeStable() throws Exception {
    assertThat(valueExpressionOf("poolSize"), is("${agi.server.pool.size:10}"));
    assertThat(valueExpressionOf("maxPoolSize"), is("${agi.server.max.pool.size:1000}"));
  }

  @Test
  public void applicationPropertiesShouldDefineTheSamePoolSizes() throws Exception {
    Properties properties = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      properties.load(in);
    }

    assertThat(properties.getProperty("agi.server.pool.size").trim(), is("10"));
    assertThat(properties.getProperty("agi.server.max.pool.size").trim(), is("1000"));
  }

  private static String valueExpressionOf(String fieldName) throws NoSuchFieldException {
    return CustomAgiServerThread.class.getDeclaredField(fieldName).getAnnotation(Value.class).value();
  }
}
