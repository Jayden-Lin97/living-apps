package dev.livingapps;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import java.security.MessageDigest;
import java.util.*;

/** Launcher-scoped package visibility, no QUERY_ALL_PACKAGES permission. */
final class RebuildTarget {
  final String pkg, label, certificate;
  final long version;

  private RebuildTarget(String p, String l, String c, long v) {
    pkg = p;
    label = l;
    certificate = c;
    version = v;
  }

  static RebuildTarget get(Context c, String pkg) {
    try {
      PackageManager pm = c.getPackageManager();
      PackageInfo info =
          pm.getPackageInfo(
              pkg,
              Build.VERSION.SDK_INT >= 28
                  ? PackageManager.GET_SIGNING_CERTIFICATES
                  : PackageManager.GET_SIGNATURES);
      Signature[] signatures =
          Build.VERSION.SDK_INT >= 28 ? info.signingInfo.getApkContentsSigners() : info.signatures;
      List<String> hashes = new ArrayList<>();
      for (Signature s : signatures)
        hashes.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));
      Collections.sort(hashes);
      String digest =
          hex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      String.join(":", hashes).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      return new RebuildTarget(
          pkg,
          pm.getApplicationLabel(info.applicationInfo).toString(),
          digest,
          Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode);
    } catch (Exception e) {
      throw new IllegalStateException("原应用不可用或无法检查签名");
    }
  }

  static List<RebuildTarget> installed(Context c) {
    List<RebuildTarget> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    for (ResolveInfo info : c.getPackageManager().queryIntentActivities(intent, 0)) {
      String p = info.activityInfo.packageName;
      if (p.equals(c.getPackageName()) || !seen.add(p)) continue;
      try {
        out.add(get(c, p));
      } catch (IllegalStateException ignored) {
      }
    }
    out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
    return out;
  }

  private static String hex(byte[] b) {
    StringBuilder s = new StringBuilder();
    for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x & 255));
    return s.toString();
  }
}
