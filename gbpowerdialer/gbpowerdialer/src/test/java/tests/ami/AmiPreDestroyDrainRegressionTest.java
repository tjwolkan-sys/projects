package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;

/**
 * FIX-GLB90048674 (Faz 1B — Fix A): graceful shutdown (deploy/restart) yolunda
 * {@link ManagerConnectionComposition#preDestroy()} her decorator için
 * {@link ManagerConnectionDecorator#logoff()} çağırmalı — {@code getManagerConnection().logoff()}
 * DEĞİL. Yalnızca decorator-level {@code logoff()} worker drain yolunu
 * ({@code shutdown()} + {@code awaitTermination}) tetikler; aksi halde kuyrukta bekleyen
 * DB/CDR yazımları kapanışta kaybolur (invariant #2 ihlali).
 *
 * <p>{@code preDestroy} gerçek {@code openedConnections} map'ini iterate ettiği için
 * map'e mock decorator enjekte edilir (reflection); {@code preDestroy} sonrası
 * {@code decorator.logoff()}'ın çağrıldığı ve {@code getManagerConnection()}'ın logoff
 * için KULLANILMADIĞI doğrulanır.
 */
@RunWith(MockitoJUnitRunner.class)
public class AmiPreDestroyDrainRegressionTest {

  @Mock
  private ManagerConnectionDecorator decorator;

  private ManagerConnectionComposition composition;

  @Before
  public void setUp() {
    composition = new ManagerConnectionComposition();
    Map<String, ManagerConnectionDecorator> openedConnections = new HashMap<>();
    openedConnections.put("127.0.0.1", decorator);
    ReflectionTestUtils.setField(composition, "openedConnections", openedConnections);
  }

  @Test
  public void preDestroyShouldDrainViaDecoratorLogoffNotRawManagerConnectionLogoff() {
    composition.preDestroy();

    // Decorator-level logoff() (drain yolu) çağrılmalı. logoff() package-private olduğu için
    // (tests/ami farklı pakette) verify(decorator).logoff() doğrudan derlenmez; Mockito
    // invocation kayıtlarından metot adıyla doğrulanır.
    assertThat("preDestroy decorator-level logoff() çağırmalı", invokedOnce("logoff"), is(true));
    // Ham managerConnection.logoff() yolu (drain'i ATLAYAN eski davranış) kullanılmamalı:
    // getManagerConnection() public => verify ile doğrudan doğrulanabilir.
    verify(decorator, never()).getManagerConnection();
  }

  private boolean invokedOnce(String methodName) {
    int count = 0;
    for (Invocation invocation : org.mockito.Mockito.mockingDetails(decorator).getInvocations()) {
      if (invocation.getMethod().getName().equals(methodName)) {
        count++;
      }
    }
    return count == 1;
  }
}
