package org.telegram.ox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Exports a local agent as a SillyTavern V2 character card, either as plain
 * JSON or as a PNG whose avatar carries the card in a tEXt "chara" chunk.
 * Only persona text, name and greeting travel; provider wiring, keys and
 * request headers are intentionally absent.
 */
public class OxCardExporter {

    private static final int MAX_FIELD = 32 * 1024;
    private static final int MAX_GREETING = 16 * 1024;
    private static final int MAX_AVATAR = 8 * 1024 * 1024;
    private static final int MAX_DIMENSION = 1024;
    private static final long MAX_PIXELS = 1_600_000L;

    private static final String[] CARD_SECTIONS = {
            "Description", "Personality", "Scenario", "System prompt",
            "Post-history instructions", "Example dialogue"
    };

    public static void export(Context context, Uri uri, OxAgent agent, boolean png)
            throws Exception {
        byte[] data = png
                ? png(agent)
                : card(agent).toString(2).getBytes(StandardCharsets.UTF_8);
        OutputStream out = context.getContentResolver().openOutputStream(uri);
        if (out == null) throw new IllegalStateException("无法写入目标文件");
        try {
            out.write(data);
            out.flush();
        } finally {
            out.close();
        }
    }

    /** Builds a V2 card with the six persona sections mapped back when the
     *  agent was itself created from a card. */
    public static JSONObject card(OxAgent agent) throws Exception {
        String name = clip(agent.name == null ? "" : agent.name, 256);
        if (name.isEmpty()) name = "Character Card";
        String markdown = agent.agentsMd == null ? "" : agent.agentsMd.trim();
        Map<String, String> sections = sections(markdown);
        boolean structured = !sections.isEmpty();
        String description = structured
                ? clip(sections.get("Description"), MAX_FIELD)
                : clip(markdown, MAX_FIELD);

        JSONObject data = new JSONObject();
        data.put("name", name);
        data.put("description", description);
        data.put("personality", structured ? clip(sections.get("Personality"), MAX_FIELD) : "");
        data.put("scenario", structured ? clip(sections.get("Scenario"), MAX_FIELD) : "");
        data.put("first_mes", clip(agent.firstMes == null ? "" : agent.firstMes, MAX_GREETING));
        data.put("mes_example", structured
                ? clip(sections.get("Example dialogue"), MAX_FIELD) : "");
        data.put("creator_notes", "Exported from OrionekoX");
        data.put("system_prompt", structured
                ? clip(sections.get("System prompt"), MAX_FIELD) : "");
        data.put("post_history_instructions", structured
                ? clip(sections.get("Post-history instructions"), MAX_FIELD) : "");
        data.put("alternate_greetings", new JSONArray());
        data.put("character_book", JSONObject.NULL);
        data.put("tags", new JSONArray().put("orionekox"));
        data.put("creator", "OrionekoX");
        data.put("character_version", "1.0");
        data.put("extensions", new JSONObject());
        data.put("avatar", "none");

        JSONObject root = new JSONObject();
        root.put("spec", "chara_card_v2");
        root.put("spec_version", "2.0");
        root.put("data", data);
        // Flat aliases let older V1 readers pick up the essential fields.
        root.put("name", name);
        root.put("description", description);
        root.put("first_mes", data.optString("first_mes"));
        return root;
    }

    /** Encodes the card into a PNG avatar. The avatar is re-encoded to keep the
     *  file bounded; agents without a usable image get a simple name card. */
    public static byte[] png(OxAgent agent) throws Exception {
        JSONObject root = card(agent);
        byte[] avatar = avatarPng(agent);
        String payload = Base64.encodeToString(root.toString().getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP);
        return withTextChunk(avatar, "chara", payload);
    }

    private static Map<String, String> sections(String markdown) {
        Map<String, String> out = new LinkedHashMap<>();
        if (markdown == null || markdown.isEmpty()) return out;
        String[] lines = markdown.split("\n", -1);
        String current = null;
        StringBuilder body = null;
        StringBuilder preamble = new StringBuilder();
        for (String line : lines) {
            String heading = sectionHeading(line);
            if (heading != null) {
                if (body != null && current != null) {
                    out.put(current, body.toString().trim());
                }
                current = heading;
                body = new StringBuilder();
            } else if (body != null) {
                if (body.length() > 0) body.append('\n');
                body.append(line);
            } else {
                if (preamble.length() > 0) preamble.append('\n');
                preamble.append(line);
            }
        }
        if (body != null && current != null) out.put(current, body.toString().trim());
        // Text before the first heading means this is not one of our generated
        // cards; exporting the whole markdown as the description is safer.
        if (preamble.toString().trim().length() > 0) return new LinkedHashMap<>();
        return out;
    }

    private static String sectionHeading(String line) {
        String trimmed = line == null ? "" : line.trim();
        for (String heading : CARD_SECTIONS) {
            if (trimmed.equals("## " + heading)) return heading;
        }
        return null;
    }

    private static byte[] avatarPng(OxAgent agent) {
        Bitmap bitmap = decodeAvatar(agent.avatarPath);
        if (bitmap == null) bitmap = placeholder(agent);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
        if (bos.size() > MAX_AVATAR) {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                    Math.max(1, bitmap.getWidth() / 2),
                    Math.max(1, bitmap.getHeight() / 2), true);
            if (scaled != bitmap) bitmap.recycle();
            bitmap = scaled;
            bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
        }
        bitmap.recycle();
        return bos.toByteArray();
    }

    private static Bitmap decodeAvatar(String path) {
        try {
            if (path == null || path.isEmpty()) return null;
            File file = new File(path);
            if (!file.isFile() || file.length() <= 0 || file.length() > MAX_AVATAR) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= MAX_DIMENSION
                    && bounds.outHeight / (sample * 2) >= MAX_DIMENSION) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(path, opts);
            if (bitmap == null) return null;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float scale = 1f;
            if (Math.max(width, height) > MAX_DIMENSION) {
                scale = MAX_DIMENSION / (float) Math.max(width, height);
            }
            if ((long) width * height > MAX_PIXELS) {
                scale = Math.min(scale, (float) Math.sqrt(MAX_PIXELS / (double) (width * height)));
            }
            if (scale < 1f) {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                        Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale)), true);
                if (scaled != bitmap) bitmap.recycle();
                bitmap = scaled;
            }
            return bitmap;
        } catch (Throwable t) {
            FileLog.e("OxCardExporter", t);
            return null;
        }
    }

    private static Bitmap placeholder(OxAgent agent) {
        int width = 512;
        int height = 640;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int seed = (agent.name == null ? "agent" : agent.name).hashCode();
        int hue = ((seed % 360) + 360) % 360;
        int background = Color.HSVToColor(255, new float[]{hue, 0.55f, 0.72f});
        canvas.drawColor(background);
        Paint band = new Paint();
        band.setColor(Color.argb(70, 0, 0, 0));
        canvas.drawRect(0, height * 0.72f, width, height, band);

        Paint emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emojiPaint.setTextAlign(Paint.Align.CENTER);
        emojiPaint.setTypeface(Typeface.DEFAULT);
        emojiPaint.setColor(Color.WHITE);
        emojiPaint.setTextSize(190);
        String emoji = agent.emoji == null || agent.emoji.trim().isEmpty()
                ? "🤖" : agent.emoji.trim();
        canvas.drawText(emoji, width / 2f, height * 0.46f, emojiPaint);

        Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setTextAlign(Paint.Align.CENTER);
        namePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        namePaint.setColor(Color.WHITE);
        namePaint.setTextSize(38);
        String name = agent.name == null ? "" : agent.name;
        if (name.length() > 28) name = name.substring(0, 27) + "…";
        canvas.drawText(name, width / 2f, height * 0.85f, namePaint);
        return bitmap;
    }

    private static byte[] withTextChunk(byte[] png, String keyword, String text)
            throws Exception {
        if (!isPng(png) || png.length < 33) throw new IllegalStateException("头像不是有效 PNG");
        int ihdrLength = readInt(png, 8);
        if (ihdrLength < 13 || ihdrLength > 1024) {
            throw new IllegalStateException("PNG 结构无效");
        }
        int ihdrEnd = 8 + 12 + ihdrLength;
        if (ihdrEnd > png.length) throw new IllegalStateException("PNG 结构无效");
        byte[] textData = (keyword + "\u0000" + text).getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out = new ByteArrayOutputStream(png.length + textData.length + 12);
        out.write(png, 0, 8);
        out.write(png, 8, ihdrEnd - 8);
        writeChunk(out, "tEXt", textData);
        out.write(png, ihdrEnd, png.length - ihdrEnd);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data)
            throws Exception {
        writeInt(out, data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static boolean isPng(byte[] data) {
        return data != null && data.length >= 8
                && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N'
                && data[3] == 'G' && data[4] == 0x0D && data[5] == 0x0A
                && data[6] == 0x1A && data[7] == 0x0A;
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        int end = max;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
}
