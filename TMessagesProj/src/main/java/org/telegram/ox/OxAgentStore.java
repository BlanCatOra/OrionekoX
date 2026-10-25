package org.telegram.ox;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Persists agents under filesDir/oxagents/<id>/{agent.json, AGENTS.md, chat.json}.
// Plain JSON for alpha; M3 moves secrets into the encrypted Vault.
public class OxAgentStore {

    private static final String TAG = "OxAgentStore";
    private static volatile Boolean seeded;

    public static File root() {
        File f = new File(ApplicationLoader.applicationContext.getFilesDir(), "oxagents");
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
    }

    private static File agentDir(String id) {
        return new File(root(), id);
    }

    public static synchronized void seedBuiltInsIfNeeded() {
        if (seeded != null) return;
        seeded = true;
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (agentDir("echo").exists()) return;

            OxAgent echo = new OxAgent();
            echo.id = "echo"; echo.name = ctx.getString(ctx.getResources().getIdentifier("OxAgentEchoName", "string", ctx.getPackageName()));
            echo.emoji = "🔁";
            echo.builtIn = true;
            echo.agentsMd = "# Echo Assistant\n\nYou are a friendly offline assistant. You cannot reach any model backend, so you reply in echo mode.";
            saveAgent(echo);

            OxAgent translator = new OxAgent();
            translator.id = "translator"; translator.name = ctx.getString(ctx.getResources().getIdentifier("OxAgentTranslatorName", "string", ctx.getPackageName()));
            translator.emoji = "🌐";
            translator.builtIn = true;
            translator.baseUrl = "https://api.deepseek.com";
            translator.model = "deepseek-chat";
            translator.agentsMd = "# Translator\n\nYou are a professional translator. Detect the input language and translate it into:\n- English if it is Chinese\n- Simplified Chinese otherwise\n\nOutput ONLY the translation, no explanations.";
            saveAgent(translator);

            OxAgent summarizer = new OxAgent();
            summarizer.id = "summarizer"; summarizer.name = ctx.getString(ctx.getResources().getIdentifier("OxAgentSummarizerName", "string", ctx.getPackageName()));
            summarizer.emoji = "📝";
            summarizer.builtIn = true;
            summarizer.baseUrl = "https://api.deepseek.com";
            summarizer.model = "deepseek-chat";
            summarizer.agentsMd = "# Summarizer\n\nYou summarize content for the user in Simplified Chinese.\n\n" +
                "For a Telegram channel or group, first call tool `tg_list_chats` to find the exact chat title, then call tool `tg_read_chat` with that title to fetch recent messages, then summarize them into 3-5 bullet points plus one line of overall sentiment. Cite message authors when a point depends on a single message.\n\n" +
                "For plain pasted text, summarize it directly into 3-5 bullet points plus one sentiment line - no tools needed.";
            saveAgent(summarizer);
        } catch (Throwable t) {
            FileLog.e(TAG, t);
        }
    }

    public static List<OxAgent> listAgents() {
        seedBuiltInsIfNeeded();
        List<OxAgent> out = new ArrayList<>();
        File[] dirs = root().listFiles();
        if (dirs != null) {
            for (File d : dirs) {
                if (!d.isDirectory()) continue;
                OxAgent a = loadAgent(d.getName());
                if (a != null) out.add(a);
            }
        }
        out.sort((x, y) -> {
            if (x.pinned != y.pinned) return x.pinned ? -1 : 1;
            int b = Boolean.compare(x.builtIn, y.builtIn);
            if (b != 0) return b;
            String nx = x.name == null ? "" : x.name;
            String ny = y.name == null ? "" : y.name;
            return nx.compareToIgnoreCase(ny);
        });
        return out;
    }

    public static OxAgent loadAgent(String id) {
        try {
            File f = new File(agentDir(id), "agent.json");
            if (!f.exists()) return null;
            JSONObject o = new JSONObject(readFile(f));
            OxAgent a = OxAgent.fromJson(o);
            a.apiKey = OxVault.unwrap(a.apiKey);
            return a;
        } catch (Exception e) {
            FileLog.e(TAG, e);
            return null;
        }
    }

    public static void saveAgent(OxAgent a) {
        try {
            File dir = agentDir(a.id);
            dir.mkdirs();
            // Vault: wrap the API key for storage, in-memory copy stays plaintext.
            String live = a.apiKey;
            if (live != null && !live.isEmpty() && !OxVault.isWrapped(live)) {
                String wrapped = OxVault.wrap(live);
                if (wrapped != null) {
                    a.apiKey = wrapped;
                }
            }
            writeFile(new File(dir, "agent.json"), a.toJson().toString());
            a.apiKey = live;
            writeFile(new File(dir, "AGENTS.md"), a.agentsMd == null ? "" : a.agentsMd);
        } catch (Exception e) {
            FileLog.e(TAG, e);
        }
    }

    public static void deleteAgent(String id) {
        File dir = agentDir(id);
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        dir.delete();
    }

    public static List<OxChatEntry> loadChat(String id) {
        try {
            File f = new File(agentDir(id), "chat.json");
            if (!f.exists()) return new ArrayList<>();
            return OxChatEntry.fromJsonArray(new JSONArray(readFile(f)));
        } catch (Exception e) {
            FileLog.e(TAG, e);
            return new ArrayList<>();
        }
    }

    public static void saveChat(String id, List<OxChatEntry> chat) {
        try {
            File dir = agentDir(id);
            dir.mkdirs();
            writeFile(new File(dir, "chat.json"), OxChatEntry.toJsonArray(chat).toString());
        } catch (Exception e) {
            FileLog.e(TAG, e);
        }
    }

    private static String readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int read = in.read(buf);
            return new String(buf, 0, Math.max(read, 0), StandardCharsets.UTF_8);
        }
    }

    private static void writeFile(File f, String s) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---------------- long-term memory + workspace ----------------

    public static File memoryFile(String id) {
        return new File(agentDir(id), "memory.txt");
    }

    public static synchronized void appendMemory(String id, String text) {
        try {
            if (text == null) return;
            text = text.trim();
            if (text.isEmpty()) return;
            File dir = agentDir(id);
            dir.mkdirs();
            String line = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(new java.util.Date())
                    + "  " + text.replace('\n', ' ').trim() + "\n";
            java.io.FileOutputStream out = new java.io.FileOutputStream(memoryFile(id), true);
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception e) {
            FileLog.e(TAG, e);
        }
    }

    public static synchronized String loadMemory(String id, int maxChars) {
        try {
            File f = memoryFile(id);
            if (!f.exists()) return "";
            String all = readFile(f);
            if (all.length() <= maxChars) return all;
            return all.substring(all.length() - maxChars);
        } catch (Exception e) {
            return "";
        }
    }

    public static synchronized String searchMemory(String id, String query, int maxResults) {
        try {
            File f = memoryFile(id);
            if (!f.exists()) return "";
            String q = query == null ? "" : query.trim().toLowerCase();
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (String line : readFile(f).split("\n")) {
                if (q.isEmpty() || line.toLowerCase().contains(q)) {
                    sb.append(line).append('\n');
                    if (++n >= maxResults) break;
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static File workspaceDir(String id) {
        File f = new File(ApplicationLoader.applicationContext.getFilesDir(), "oxworkspace/" + id);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File resolveWorkspace(String id, String path) throws IOException {
        File base = workspaceDir(id).getCanonicalFile();
        String p = path == null ? "" : path.trim();
        if (p.isEmpty()) p = ".";
        File f = new File(base, p).getCanonicalFile();
        String bp = base.getPath();
        if (!f.getPath().equals(bp) && !f.getPath().startsWith(bp + File.separator)) {
            throw new IOException("path escapes the agent workspace");
        }
        return f;
    }

}
