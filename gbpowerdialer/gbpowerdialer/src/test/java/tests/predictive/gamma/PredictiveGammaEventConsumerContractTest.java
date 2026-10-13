package tests.predictive.gamma;

import com.powerdialer.redis.event.predictive.PredictiveGammaEvent;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Gamma event'i ile tüketici proje ({@code gbpredictivecalculateviews}) arasındaki
 * ALAN ADI sözleşmesini kilitler.
 *
 * <p><b>Neden gerekli:</b> bu hattın hiçbir yerinde tip güvenliği yok ve her kopma SESSİZ:
 * <ul>
 *   <li>Redis'e Jackson ile bean adı üzerinden JSON yazılıyor; tüketici tarafta
 *       {@code @JsonProperty} yok, eşleşme birebir isim üzerinden.</li>
 *   <li>Tüketicide DTO → entity dönüşümü Gson roundtrip; tek harf farkı NULL kolon üretir,
 *       hata vermez.</li>
 *   <li>Tüketici projede hiç test yok ({@code src/test} dizini bulunmuyor).</li>
 * </ul>
 * Dolayısıyla sözleşme burada, üretici tarafta doğrulanır.
 *
 * <p>Tüketici proje çalışma ağacında yoksa test atlanır (CI'da tek repo checkout edilebilir).
 */
public class PredictiveGammaEventConsumerContractTest {

    private static final String CONSUMER_DTO =
            "src/main/java/com/predictivecalculateviews/model/dto/PredictiveGammaEventDto.java";
    private static final String CONSUMER_ENTITY =
            "src/main/java/com/predictivecalculateviews/model/entity/PredictiveGammaEvent.java";

    /**
     * Üretici tarafta olup tüketiciye bilinçli olarak taşınmayan alanlar.
     * {@code predictiveType} yalnız yönlendirme için okunup atılır; {@code createDate},
     * {@code publisher} ve {@code eventClass} tüketicide ayrı ele alınır.
     */
    private static final List<String> NOT_MAPPED_BY_DESIGN = java.util.Arrays.asList("predictiveType");

    @Test
    public void everyEventFieldExistsOnConsumerDtoAndEntity() throws IOException {
        Path consumerRoot = consumerRoot();
        assumeTrue("gbpredictivecalculateviews calisma agacinda yok, sozlesme testi atlandi", consumerRoot != null);

        String dto = read(consumerRoot.resolve(CONSUMER_DTO));
        String entity = read(consumerRoot.resolve(CONSUMER_ENTITY));

        List<String> missingInDto = new ArrayList<>();
        List<String> missingInEntity = new ArrayList<>();

        for (Field f : PredictiveGammaEvent.class.getDeclaredFields()) {
            if (f.isSynthetic() || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            String name = f.getName();
            if (NOT_MAPPED_BY_DESIGN.contains(name)) continue;

            if (!declaresField(dto, name)) missingInDto.add(name);
            if (!declaresField(entity, name)) missingInEntity.add(name);
        }

        assertTrue("Tuketici DTO'da eksik alan(lar) -> event'te yayimlanir ama sessizce yok olur: " + missingInDto,
                missingInDto.isEmpty());
        assertTrue("Tuketici entity'de eksik alan(lar) -> DB kolonu hic olusmaz: " + missingInEntity,
                missingInEntity.isEmpty());
    }

    @Test
    public void consumerEntityDeclaresAColumnForEveryGammaPlusField() throws IOException {
        Path consumerRoot = consumerRoot();
        assumeTrue("gbpredictivecalculateviews calisma agacinda yok, sozlesme testi atlandi", consumerRoot != null);

        String entity = read(consumerRoot.resolve(CONSUMER_ENTITY));

        // Gamma Plus alanlari + beklenen snake_case kolon adlari.
        String[][] expected = {
                {"gammaPlus", "gamma_plus"},
                {"cbRingAnswerRate", "cb_ring_answer_rate"},
                {"retryRingAnswerRate", "retry_ring_answer_rate"},
                {"freshRingAnswerRate", "fresh_ring_answer_rate"},
                {"ringingWeightedCount", "ringing_weighted_count"},
                {"ringingCbWeightedCount", "ringing_cb_weighted_count"},
                {"ringingRetryWeightedCount", "ringing_retry_weighted_count"},
                {"ringingFreshWeightedCount", "ringing_fresh_weighted_count"},
                {"drainMarginalCost", "drain_marginal_cost"},
                {"drainFloorApplied", "drain_floor_applied"}
        };

        List<String> problems = new ArrayList<>();
        for (String[] pair : expected) {
            if (!declaresField(entity, pair[0])) {
                problems.add("alan yok: " + pair[0]);
                continue;
            }
            if (!entity.contains("@Column(name = \"" + pair[1] + "\")")) {
                problems.add("kolon esleme yok: " + pair[1]);
            }
        }
        assertTrue(String.join(" | ", problems), problems.isEmpty());
    }

    // ---------------------------------------------------------------- helper

    /** Alanın gerçekten bildirildiğini arar; yorum içinde geçen ad eşleşmesin diye ";" ile biter. */
    private boolean declaresField(String source, String name) {
        return source.contains(" " + name + ";");
    }

    /** Kardeş repo; çalışma ağacında yoksa null döner ve test atlanır. */
    private Path consumerRoot() {
        Path[] candidates = {
                Paths.get("..", "..", "gbpredictivecalculateviews"),
                Paths.get("..", "gbpredictivecalculateviews"),
                Paths.get("gbpredictivecalculateviews")
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate.resolve("src/main/java/com/predictivecalculateviews"))) {
                return candidate;
            }
        }
        return null;
    }

    private String read(Path path) throws IOException {
        assertTrue("Tuketici dosyasi bulunamadi: " + path, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
