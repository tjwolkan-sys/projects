package tests.redis;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.powerdialer.model.Group;

/**
 * Task 327734: Webphone'un Redis user JSON'unda gönderdiği {@code persistentGroup.groupPropertyXML}
 * içindeki grup "Wrapup timer" değeri ({@code generalProperties/resultCodeTimer}, saniye)
 * {@link Group#getWrapupTimerSeconds()} alanına bağlanır. Alan/tag yok veya değer sayı değilse
 * null kalır; mevcut alanların (id, groupName) eşlemesi değişmez.
 */
public class GroupDeserializerWrapupTimerContractTest {

  private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void shouldReadWrapupTimerFromGroupPropertyXml() throws Exception {
    Group group = deserialize(groupNode(XML_HEADER + "<groupProperties><transferPointList/>"
        + "<generalProperties transferToUnavailable=\"false\">"
        + "<allowEmptyResultCode>false</allowEmptyResultCode><resultCodeTimer>40</resultCodeTimer><hasHold>true</hasHold>"
        + "</generalProperties></groupProperties>"));

    assertThat(group.getWrapupTimerSeconds(), is(40));
  }

  @Test
  public void shouldReadZeroWrapupTimerAsZero() throws Exception {
    Group group = deserialize(groupNode(generalPropertiesXml("<resultCodeTimer>0</resultCodeTimer>")));

    assertThat("0 değeri olduğu gibi taşınır; varsayılana düşme kararı tüketicinindir.",
        group.getWrapupTimerSeconds(), is(0));
  }

  @Test
  public void shouldLeaveWrapupTimerNullWhenPayloadHasNoGroupPropertyXml() throws Exception {
    Group group = deserialize(groupNode(null));

    assertThat(group.getWrapupTimerSeconds(), nullValue());
  }

  @Test
  public void shouldLeaveWrapupTimerNullWhenGeneralPropertiesHasNoTimer() throws Exception {
    Group group = deserialize(groupNode(XML_HEADER + "<groupProperties>"
        + "<generalProperties><hasHold>true</hasHold></generalProperties>"
        + "<otherSection><resultCodeTimer>99</resultCodeTimer></otherSection>"
        + "</groupProperties>"));

    assertThat("generalProperties dışındaki bir resultCodeTimer wrapup timer sayılmaz.",
        group.getWrapupTimerSeconds(), nullValue());
  }

  @Test
  public void shouldLeaveWrapupTimerNullWhenTimerIsNotNumeric() throws Exception {
    Group group = deserialize(groupNode(generalPropertiesXml("<resultCodeTimer>abc</resultCodeTimer>")));

    assertThat(group.getWrapupTimerSeconds(), nullValue());
  }

  @Test
  public void shouldKeepExistingGroupFieldsMapped() throws Exception {
    Group group = deserialize(groupNode(generalPropertiesXml("<resultCodeTimer>40</resultCodeTimer>")));

    assertThat(group.getId(), is(5L));
    assertThat(group.getName(), is("YukTesti"));
  }

  private Group deserialize(ObjectNode node) throws Exception {
    return objectMapper.treeToValue(node, Group.class);
  }

  private ObjectNode groupNode(String groupPropertyXml) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("id", 5L);
    node.put("groupName", "YukTesti");
    node.putArray("queues");
    node.putArray("groupResultCodes");
    if (groupPropertyXml != null) {
      node.put("groupPropertyXML", groupPropertyXml);
    }
    return node;
  }

  private String generalPropertiesXml(String innerXml) {
    return XML_HEADER + "<groupProperties><generalProperties>" + innerXml + "</generalProperties></groupProperties>";
  }
}
