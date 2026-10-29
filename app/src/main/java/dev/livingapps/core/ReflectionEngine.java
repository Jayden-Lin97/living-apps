package dev.livingapps.core;

import static dev.livingapps.core.Messages.tr;

import java.util.*;
import org.json.JSONObject;

/** Deterministic on-device analysis. No raw text or record contents needed. */
public final class ReflectionEngine {
  public static final long DAY = 86_400_000L;

  public static final class Event {
    public final String kind;
    public final long elapsed, time;

    public Event(String kind, long elapsed, long time) {
      this.kind = kind;
      this.elapsed = elapsed;
      this.time = time;
    }
  }

  public static final class Summary {
    public int opens, saves, stats, errors, cancels;
    public final Set<Long> days = new HashSet<>();
    public final List<Long> durations = new ArrayList<>();

    public long medianMillis() {
      if (durations.isEmpty()) return 0;
      List<Long> values = new ArrayList<>(durations);
      Collections.sort(values);
      int n = values.size();
      return n % 2 == 0 ? (values.get(n / 2 - 1) + values.get(n / 2)) / 2 : values.get(n / 2);
    }

    public JSONObject json() {
      return J.obj(
          "opens",
          opens,
          "saves",
          saves,
          "statsViews",
          stats,
          "validationErrors",
          errors,
          "cancelledForms",
          cancels,
          "activeDays",
          days.size(),
          "medianSaveMillis",
          medianMillis());
    }
  }

  public static Summary summarize(List<Event> events) {
    Summary s = new Summary();
    for (Event e : events) {
      switch (e.kind) {
        case "open":
          s.opens++;
          s.days.add(e.time / DAY);
          break;
        case "save":
          s.saves++;
          if (e.elapsed > 0 && e.elapsed < 3_600_000) s.durations.add(e.elapsed);
          break;
        case "stats":
          s.stats++;
          break;
        case "validation_error":
          s.errors++;
          break;
        case "cancel":
          s.cancels++;
          break;
      }
    }
    return s;
  }

  public static final class Suggestion {
    public final String rule, reason;
    public final AppSpec candidate;
    public final boolean canAutoApply;

    Suggestion(String rule, String reason, AppSpec candidate, boolean auto) {
      this.rule = rule;
      this.reason = reason;
      this.candidate = candidate;
      this.canAutoApply = auto;
    }
  }

  public static List<Suggestion> suggest(AppSpec spec, Summary s) {
    List<Suggestion> out = new ArrayList<>();
    // Require repeated real use across days; no seeded/fabricated behavior.
    if (s.opens < 4 || s.days.size() < 2) return out;
    if (!spec.quickAdd() && s.saves >= 6)
      out.add(
          new Suggestion(
              "quick_add",
              tr("最近在 ")
                  + s.days.size()
                  + tr(" 天内完成了 ")
                  + s.saves
                  + tr(" 次记录。增加首页快速记录入口，可以少切换一次页面。"),
              spec.with("quickAdd", true),
              true));
    if (!spec.startScreen().equals("stats") && s.stats >= 6 && s.stats > s.saves * 2)
      out.add(
          new Suggestion(
              "start_stats",
              tr("你查看统计的次数（") + s.stats + tr(" 次）明显多于新增记录。可以默认打开统计，原有页面仍可随时切换。"),
              spec.with("startScreen", "stats"),
              false));
    return out;
  }

  public static String evaluation(Summary before, Summary after) {
    if (before.durations.size() < 5 || after.durations.size() < 5)
      return tr("记录耗时样本不足：更新前 ")
          + before.durations.size()
          + tr(" 次，更新后 ")
          + after.durations.size()
          + tr(" 次；各满 5 次后展示观察结果。");
    long b = before.medianMillis(), a = after.medianMillis();
    return String.format(
        Locale.CHINA,
        tr("记录耗时中位数：更新前 %.1f 秒 → 更新后 %.1f 秒。仅为使用观察，任务差异也会影响结果。"),
        b / 1000.0,
        a / 1000.0);
  }
}
