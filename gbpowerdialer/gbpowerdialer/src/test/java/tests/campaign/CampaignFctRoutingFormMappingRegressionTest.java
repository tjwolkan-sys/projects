package tests.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.dozer.DozerBeanMapper;
import org.dozer.Mapper;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerdialer.dto.CampaignFormDTO;
import com.powerdialer.model.Campaign;

/**
 * Regresyon: campaign edit ekranındaki "FCT Yönlendirme" checkbox'ı kaydedilmiyordu.
 *
 * <p>Kök neden: {@code fctRoutingEnabled} alanı {@link CampaignFormDTO} içinde yoktu.
 * {@code CampaignService.merge} DTO'yu {@code mapper.map(item, Campaign.class)} ile SIFIRDAN yeni
 * bir {@link Campaign} nesnesine map edip onu DB'ye merge ettiği için, DTO'da bulunmayan her alan
 * her form save'inde Java default'uyla yazılıyor. Primitive {@code boolean} olduğu için bu değer
 * her kayıtta {@code false} oluyordu: checkbox hiç kalıcı olmuyor, DB'den elle açılsa bile ilk
 * form save'inde sessizce kapanıyordu.
 *
 * <p>Bu test hem save yolunu (DTO -> Campaign) hem form yükleme yolunu (Campaign -> DTO) hem de
 * AngularJS'in beklediği JSON alan adını kilitler. Dozer eşlemesi isim bazlı (implicit) olduğu için
 * alan adlarının iki tarafta da {@code fctRoutingEnabled} kalması sözleşmedir.
 */
public class CampaignFctRoutingFormMappingRegressionTest {

  private Mapper mapper;
  private ObjectMapper objectMapper;

  @Before
  public void setUp() {
    mapper = new DozerBeanMapper();
    objectMapper = new ObjectMapper();
  }

  @Test
  public void shouldPersistEnabledFlagFromFormDtoToEntity() {
    CampaignFormDTO dto = new CampaignFormDTO();
    dto.setId(76L);
    dto.setFctRoutingEnabled(true);

    Campaign campaign = mapper.map(dto, Campaign.class);

    assertTrue("UI'da açılan FCT routing flag'i entity'ye taşınmalı", campaign.isFctRoutingEnabled());
  }

  @Test
  public void shouldPersistDisabledFlagFromFormDtoToEntity() {
    CampaignFormDTO dto = new CampaignFormDTO();
    dto.setId(76L);
    dto.setFctRoutingEnabled(false);

    Campaign campaign = mapper.map(dto, Campaign.class);

    assertEquals("UI'da kapatılan flag entity'de de kapalı olmalı", false, campaign.isFctRoutingEnabled());
  }

  @Test
  public void shouldExposeEnabledFlagFromEntityToFormDto() {
    Campaign campaign = new Campaign();
    campaign.setId(76L);
    campaign.setFctRoutingEnabled(true);

    CampaignFormDTO dto = mapper.map(campaign, CampaignFormDTO.class);

    assertTrue("form yüklenirken checkbox DB değerini göstermeli", dto.isFctRoutingEnabled());
  }

  @Test
  public void shouldSurviveEntityToDtoToEntityRoundTrip() {
    Campaign source = new Campaign();
    source.setId(76L);
    source.setFctRoutingEnabled(true);

    CampaignFormDTO dto = mapper.map(source, CampaignFormDTO.class);
    Campaign target = mapper.map(dto, Campaign.class);

    assertTrue("form aç-kaydet döngüsü flag'i kaybetmemeli", target.isFctRoutingEnabled());
  }

  @Test
  public void shouldUseFieldNameExpectedByAngularModel() throws Exception {
    CampaignFormDTO dto = new CampaignFormDTO();
    dto.setFctRoutingEnabled(true);

    String json = objectMapper.writeValueAsString(dto);

    // campaignForm.html: ng-model="campaign.item.fctRoutingEnabled"
    assertTrue("JSON alan adı AngularJS ng-model ile aynı olmalı: " + json,
        json.contains("\"fctRoutingEnabled\":true"));
  }

  /**
   * Faz 2 (A′ kararı): FCT listesi tükendiğinde kullanılacak sabit hat, mevcut
   * {@code Campaign.callerId} kolonundan okunur. Bu alan POOL kampanyalarda daha önce UI'da hiç
   * gösterilmiyordu; artık gösterildiği için kaydetme yolunun {@code fctRoutingEnabled}'ın düştüğü
   * tuzağa (DTO'da alan yokluğu) düşmediği burada kilitlenir.
   */
  @Test
  public void shouldCarryFixedLineCallerIdThroughFormRoundTrip() {
    Campaign source = new Campaign();
    source.setId(76L);
    source.setFctRoutingEnabled(true);
    source.setCallerId("02120000001");

    CampaignFormDTO dto = mapper.map(source, CampaignFormDTO.class);
    Campaign target = mapper.map(dto, Campaign.class);

    assertEquals("sabit hat form aç-kaydet döngüsünde kaybolmamalı", "02120000001", target.getCallerId());
  }

  @Test
  public void shouldLeaveFixedLineEmptyWhenNotConfigured() {
    Campaign source = new Campaign();
    source.setId(76L);
    source.setFctRoutingEnabled(true);

    Campaign target = mapper.map(mapper.map(source, CampaignFormDTO.class), Campaign.class);

    // Boş sabit hat "fallback kapalı" demektir; kaydetme yolu buraya bir değer UYDURMAMALI.
    assertEquals("tanımsız sabit hat boş kalmalı", null, target.getCallerId());
  }

  /**
   * Sabit hat alanı POOL + FCT routing açık kampanyalarda görünmelidir ve etiketi FIXED/DYNAMIC
   * anlamındaki {@code campaign.callerId} anahtarından AYRI olmalıdır (aynı kolon, farklı anlam).
   */
  @Test
  public void formShouldExposeFixedLineFieldForFctCampaignsWithDedicatedLabel() throws Exception {
    String form = readProjectFile("src/main/webapp/pages/campaignForm.html");

    assertTrue("sabit hat alanı POOL + FCT routing açıkken gösterilmeli",
        form.contains("campaign.item.callerIdBehaviour == 3 && campaign.item.fctRoutingEnabled"));
    assertTrue("ayrı i18n etiketi kullanılmalı", form.contains("campaign.fctFixedLineCallerId"));
    assertTrue("aynı kolona yazmalı (yeni alan değil)",
        form.contains("id=\"campaign.fctFixedLineCallerId\""));

    for (String fileName : new String[] {"localization_tr.js", "localization_en.js",
                                         "localization_ru.js", "localization_ua.js"}) {
      String content = readProjectFile("src/main/webapp/js/localization/" + fileName);
      assertTrue("Eksik localization anahtarı: campaign.fctFixedLineCallerId (" + fileName + ")",
          content.contains("'campaign.fctFixedLineCallerId'"));
    }
  }

  private String readProjectFile(String relativePath) throws java.io.IOException {
    java.nio.file.Path path = java.nio.file.Paths.get(relativePath);
    if (!java.nio.file.Files.exists(path)) {
      path = java.nio.file.Paths.get("gbpowerdialer", relativePath);
    }
    return new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
  }
}
