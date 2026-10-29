package dev.livingapps.core;

import static org.junit.Assert.*;

import java.util.Locale;
import org.junit.*;

public class MessageCatalogTest {
  private Locale previous;

  @Before
  public void prepare() {
    previous = Locale.getDefault();
  }

  @After
  public void finish() {
    Locale.setDefault(previous);
  }

  @Test
  public void authoredMessagesFollowTheSelectedLanguage() {
    Locale.setDefault(Locale.ENGLISH);
    assertEquals("Save", Messages.tr("保存"));
    assertTrue(
        AppSpec.parse(Templates.all().get(0).toString())
            .validateRecord(J.obj())
            .toString()
            .contains("Fill in this field"));
    Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
    assertEquals("保存", Messages.tr("保存"));
    assertTrue(
        AppSpec.parse(Templates.all().get(0).toString())
            .validateRecord(J.obj())
            .toString()
            .contains("请填写此项"));
  }

  @Test
  public void builtInsLocalizeOnlyOnCreationAndKeepTheirSchema() {
    Locale.setDefault(Locale.ENGLISH);
    AppSpec en = Templates.localized().get(0);
    assertEquals("Daily spending", en.name());
    assertEquals("CNY", en.unit());
    assertEquals("amount", en.metricField());
    assertEquals("Amount", en.field("amount").label);
    assertEquals("Food", en.field("category").options.get(0));
    String saved = en.toString();
    Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
    assertEquals(saved, en.toString());
    assertEquals("随手记账", Templates.localized().get(0).name());
    assertEquals("Daily spending", AppSpec.parse(saved).name());
  }

  @Test
  public void waitingDraftRecoveryRecognizesBothPersistedLanguages() {
    Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
    assertTrue(
        Messages.isWaitingStatus("Waiting for the model; you can cancel or keep editing locally"));
    Locale.setDefault(Locale.ENGLISH);
    assertTrue(Messages.isWaitingStatus("正在等待模型；可取消，或继续本地调整"));
    assertFalse(Messages.isWaitingStatus("Draft saved on this phone"));
  }

  @Test
  public void chineseSensitiveKeywordsRemainActiveInEnglish() {
    Locale.setDefault(Locale.ENGLISH);
    assertTrue(RebuildSpec.sensitive("确认支付"));
    assertTrue(RebuildSpec.sensitive("验证码"));
    assertTrue(RebuildSpec.sensitive("转账"));
    assertFalse(RebuildSpec.sensitive("Play"));
  }
}
