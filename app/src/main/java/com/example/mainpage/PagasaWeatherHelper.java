package com.example.mainpage;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class PagasaWeatherHelper {

    private static final String TAG = "PagasaWeatherHelper";
    public static final String PAGASA_CHANNEL_ID = "pagasa_official_alerts_v1";
    public static final int PAGASA_NOTIFICATION_ID = 1004;
    private static final String PAGASA_RSS_URL = "https://bagong.pagasa.dost.gov.ph/rss/weather";

    public static class PagasaAdvisory {
        public final String title;
        public final String description;
        public final String link;
        public final String pubDate;
        public final String hash;
        public final String targetTab; // "weather" or "flood"
        public final boolean isHighPriority;

        public PagasaAdvisory(String title, String description, String link, String pubDate, String hash, String targetTab, boolean isHighPriority) {
            this.title = title;
            this.description = description;
            this.link = link;
            this.pubDate = pubDate;
            this.hash = hash;
            this.targetTab = targetTab;
            this.isHighPriority = isHighPriority;
        }
    }

    public static List<PagasaAdvisory> fetchPagasaAdvisories() {
        List<PagasaAdvisory> list = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(PAGASA_RSS_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; PAGASA Weather Monitor)");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                try {
                    dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    dbFactory.setXIncludeAware(false);
                    dbFactory.setExpandEntityReferences(false);
                } catch (Throwable ignored) {}
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(is);
                doc.getDocumentElement().normalize();

                NodeList itemList = doc.getElementsByTagName("item");
                int maxItems = Math.min(itemList.getLength(), 10);
                for (int i = 0; i < maxItems; i++) {
                    Element item = (Element) itemList.item(i);
                    String title = getTagValue("title", item);
                    String description = getTagValue("description", item);
                    String link = getTagValue("link", item);
                    String pubDate = getTagValue("pubDate", item);

                    if (title != null && !title.trim().isEmpty()) {
                        String cleanTitle = title.trim();
                        String cleanDesc = description != null ? description.replaceAll("<[^>]*>", "").trim() : "";
                        String hash = Integer.toHexString((cleanTitle + pubDate + link).hashCode());

                        String targetTab = "weather";
                        boolean isHigh = false;

                        String lowerTitle = cleanTitle.toLowerCase(Locale.ROOT);
                        if (lowerTitle.contains("flood") || lowerTitle.contains("river") || lowerTitle.contains("basin")) {
                            targetTab = "flood";
                            isHigh = true;
                        } else if (lowerTitle.contains("warning") || lowerTitle.contains("typhoon") || lowerTitle.contains("cyclone") || lowerTitle.contains("thunderstorm") || lowerTitle.contains("gale")) {
                            isHigh = true;
                        }

                        list.add(new PagasaAdvisory(cleanTitle, cleanDesc, link, pubDate, hash, targetTab, isHigh));
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "PAGASA RSS feed fetch note: " + t.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return list;
    }

    public static PagasaAdvisory fetchLatestPagasaAdvisory() {
        List<PagasaAdvisory> list = fetchPagasaAdvisories();
        return list.isEmpty() ? null : list.get(0);
    }

    private static String getTagValue(String tag, Element element) {
        try {
            NodeList nodeList = element.getElementsByTagName(tag);
            if (nodeList != null && nodeList.getLength() > 0) {
                return nodeList.item(0).getTextContent();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static void checkAndNotifyPagasaAdvisories(Context context) {
        if (context == null) return;

        List<PagasaAdvisory> advisories = fetchPagasaAdvisories();
        if (advisories.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (PagasaAdvisory advisory : advisories) {
            if (!NotificationDeduplicator.isHashProcessed(context, advisory.hash)) {
                sendPagasaNotification(context, advisory);
                NotificationDeduplicator.markHashProcessed(context, advisory.hash);
                FloodStatusHelper.saveLastPagasaAdvisory(context, advisory.hash, advisory.title, now);
            }
        }
    }

    public static void sendPagasaNotification(Context context, PagasaAdvisory advisory) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || advisory == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    PAGASA_CHANNEL_ID,
                    "PAGASA Official Advisories",
                    advisory.isHighPriority ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, FloodNewsActivity.class);
        intent.putExtra("target_tab", advisory.targetTab);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                PAGASA_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String prefix = "flood".equalsIgnoreCase(advisory.targetTab) ? "PAGASA FLOOD BULLETIN: " : "PAGASA OFFICIAL ADVISORY: ";
        String notifTitle = advisory.title.toUpperCase(Locale.ROOT).startsWith("PAGASA") ? advisory.title : prefix + advisory.title;
        String notifMessage = advisory.description.isEmpty() ? "A new official PAGASA weather bulletin is available. Tap to view." : advisory.description;
        if (notifMessage.length() > 140) {
            notifMessage = notifMessage.substring(0, 137) + "...";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, PAGASA_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(notifTitle)
                .setContentText(notifMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(advisory.description.isEmpty() ? notifMessage : advisory.description))
                .setPriority(advisory.isHighPriority ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(false)
                .setTimeoutAfter(3600000L)
                .setContentIntent(pendingIntent);

        manager.notify(PAGASA_NOTIFICATION_ID, builder.build());
    }
}
