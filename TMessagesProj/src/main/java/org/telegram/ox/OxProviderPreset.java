package org.telegram.ox;

import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Built-in OpenAI-compatible providers. Kept in one place so the editor stays declarative. */
public class OxProviderPreset {

    public final String id;
    public final String name;
    public final String baseUrl;
    public final String defaultModel;
    public final String[] models;
    public final String keyUrl;
    public final int iconRes;
    public final int color;
    /** Extra headers required by some gateways; merged into the agent's headers. */
    public String[][] headers = new String[0][];

    public OxProviderPreset(String id, String name, String baseUrl, String defaultModel,
                            String[] models, String keyUrl, int iconRes, int color) {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.models = models;
        this.keyUrl = keyUrl;
        this.iconRes = iconRes;
        this.color = color;
    }

    public static OxProviderPreset byId(String id) {
        for (OxProviderPreset p : ALL) {
            if (p.id.equals(id)) return p;
        }
        return CUSTOM;
    }

    public static final OxProviderPreset CUSTOM = new OxProviderPreset(
            "custom", "自定义（BYOK）", "", "", new String[]{},
            "", R.drawable.ox_provider_generic, 0xff6B7280);

    public static final List<OxProviderPreset> ALL = new ArrayList<>(Arrays.asList(
            new OxProviderPreset("deepseek", "DeepSeek", "https://api.deepseek.com",
                    "deepseek-v4-flash", new String[]{"deepseek-v4-flash", "deepseek-v4-pro", "deepseek-v4-flash-vision-exp"},
                    "https://platform.deepseek.com/api_keys", R.drawable.ox_provider_deepseek, 0xff4D6BFE),
            new OxProviderPreset("openai", "OpenAI", "https://api.openai.com/v1",
                    "gpt-5.5", new String[]{"gpt-5.5", "gpt-6-luna", "gpt-5.4-mini", "gpt-5.3-codex"},
                    "https://platform.openai.com/api-keys", R.drawable.ox_provider_openai, 0xff10A37F),
            new OxProviderPreset("anthropic", "Anthropic Claude", "https://api.anthropic.com/v1",
                    "claude-sonnet-5", new String[]{"claude-sonnet-5", "claude-opus-5", "claude-haiku-5-5"},
                    "https://console.anthropic.com/settings/keys", R.drawable.ox_provider_anthropic, 0xffD97757),
            new OxProviderPreset("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
                    "gemini-3.8-flash", new String[]{"gemini-3.8-flash", "gemini-4-argon", "gemini-3.5-flash"},
                    "https://aistudio.google.com/app/apikey", R.drawable.ox_provider_gemini, 0xff4285F4),
            new OxProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1",
                    "deepseek/deepseek-v4.1-flash", new String[]{"deepseek/deepseek-v4.1-flash", "anthropic/claude-sonnet-5", "google/gemini-3.8-flash", "qwen/qwen3.8-max"},
                    "https://openrouter.ai/keys", R.drawable.ox_provider_openrouter, 0xff6366F1),
            new OxProviderPreset("moonshot", "Moonshot Kimi", "https://api.moonshot.cn/v1",
                    "kimi-k3", new String[]{"kimi-k3", "kimi-k2.7-code", "moonshot-v1-8k"},
                    "https://platform.moonshot.cn/console/api-keys", R.drawable.ox_provider_moonshot, 0xff111111),
            new OxProviderPreset("qwen", "阿里云百炼 Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    "qwen3.8-max", new String[]{"qwen3.8-max", "qwen3.8-flash", "qwen-plus"},
                    "https://dashscope.console.aliyun.com/apiKey", R.drawable.ox_provider_qwen, 0xff615CED),
            new OxProviderPreset("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4",
                    "glm-5.3", new String[]{"glm-5.3", "glm-5.3-flash", "glm-4-plus"},
                    "https://open.bigmodel.cn/usercenter/apikeys", R.drawable.ox_provider_zhipu, 0xff3859FF),
            new OxProviderPreset("minimax", "MiniMax", "https://api.minimax.chat/v1",
                    "MiniMax-M3", new String[]{"MiniMax-M3", "MiniMax-M2.7", "MiniMax-M3.1-Flash"},
                    "https://platform.minimaxi.com/user-center/basic-information/interface-key", R.drawable.ox_provider_minimax, 0xffF23F5D),
            new OxProviderPreset("siliconflow", "硅基流动 SiliconFlow", "https://api.siliconflow.cn/v1",
                    "deepseek-ai/DeepSeek-V4.1", new String[]{"deepseek-ai/DeepSeek-V4.1", "Qwen/Qwen3.8-27B", "zai-org/GLM-5.3"},
                    "https://cloud.siliconflow.cn/account/ak", R.drawable.ox_provider_siliconflow, 0xff6C5CE7),
            new OxProviderPreset("groq", "Groq", "https://api.groq.com/openai/v1",
                    "llama-3.3-70b-versatile", new String[]{"llama-3.3-70b-versatile", "qwen/qwen3-32b", "llama-3.1-8b-instant"},
                    "https://console.groq.com/keys", R.drawable.ox_provider_groq, 0xffF55036),
            new OxProviderPreset("mistral", "Mistral AI", "https://api.mistral.ai/v1",
                    "mistral-large-4", new String[]{"mistral-large-4", "mistral-small-latest", "open-mistral-nemo"},
                    "https://console.mistral.ai/api-keys", R.drawable.ox_provider_mistral, 0xffFA520F),
            new OxProviderPreset("xai", "xAI Grok", "https://api.x.ai/v1",
                    "grok-4.7", new String[]{"grok-4.7", "grok-4.6", "grok-4.5"},
                    "https://console.x.ai", R.drawable.ox_provider_xai, 0xff111111),
            new OxProviderPreset("together", "Together AI", "https://api.together.xyz/v1",
                    "meta-llama/Llama-3.3-70B-Instruct-Turbo", new String[]{"meta-llama/Llama-3.3-70B-Instruct-Turbo", "Qwen/Qwen3.8-27B"},
                    "https://api.together.ai/settings/api-keys", R.drawable.ox_provider_together, 0xff0F6FFF),
            new OxProviderPreset("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference/v1",
                    "accounts/fireworks/models/llama-v3p3-70b-instruct", new String[]{"accounts/fireworks/models/llama-v3p3-70b-instruct", "accounts/fireworks/models/deepseek-v4"},
                    "https://fireworks.ai/account/api-keys", R.drawable.ox_provider_fireworks, 0xff5019C5),
            new OxProviderPreset("perplexity", "Perplexity", "https://api.perplexity.ai",
                    "sonar", new String[]{"sonar", "sonar-pro", "sonar-reasoning-pro"},
                    "https://www.perplexity.ai/settings/api", R.drawable.ox_provider_perplexity, 0xff20808D),
            new OxProviderPreset("cohere", "Cohere", "https://api.cohere.ai/compatibility/v1",
                    "command-a-03-2025", new String[]{"command-a-03-2025", "command-r-plus-08-2024"},
                    "https://dashboard.cohere.com/api-keys", R.drawable.ox_provider_cohere, 0xff39594D),
            new OxProviderPreset("cerebras", "Cerebras", "https://api.cerebras.ai/v1",
                    "llama-3.3-70b", new String[]{"llama-3.3-70b", "qwen-3-32b"},
                    "https://cloud.cerebras.ai", R.drawable.ox_provider_cerebras, 0xffF15A29),
            new OxProviderPreset("novita", "Novita AI", "https://api.novita.ai/v3/openai",
                    "deepseek/deepseek-v4", new String[]{"deepseek/deepseek-v4", "meta-llama/llama-3.3-70b-instruct"},
                    "https://novita.ai/settings/key-management", R.drawable.ox_provider_novita, 0xff0A0A0A),
            new OxProviderPreset("hyperbolic", "Hyperbolic", "https://api.hyperbolic.xyz/v1",
                    "deepseek-ai/DeepSeek-V4", new String[]{"deepseek-ai/DeepSeek-V4", "meta-llama/Llama-3.3-70B-Instruct"},
                    "https://app.hyperbolic.xyz/settings", R.drawable.ox_provider_hyperbolic, 0xff7C3AED),
            new OxProviderPreset("baichuan", "百川智能 Baichuan", "https://api.baichuan-ai.com/v1",
                    "Baichuan4", new String[]{"Baichuan4", "Baichuan3-Turbo"},
                    "https://platform.baichuan-ai.com/console/apikey", R.drawable.ox_provider_baichuan, 0xffFF6A00),
            new OxProviderPreset("stepfun", "阶跃星辰 StepFun", "https://api.stepfun.com/v1",
                    "step-2-16k", new String[]{"step-2-16k", "step-1-8k"},
                    "https://platform.stepfun.com/interface-key", R.drawable.ox_provider_stepfun, 0xff1A73E8),
            new OxProviderPreset("yi", "零一万物 Yi", "https://api.lingyiwanwu.com/v1",
                    "yi-large", new String[]{"yi-large", "yi-lightning"},
                    "https://platform.lingyiwanwu.com/apikeys", R.drawable.ox_provider_yi, 0xff003425),
            new OxProviderPreset("alibaba", "阿里云 DashScope", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    "qwen3.8-max", new String[]{"qwen3.8-max", "qwen3.8-flash", "qwen-plus"},
                    "https://dashscope.console.aliyun.com/apiKey", R.drawable.ox_provider_alibaba, 0xffFF6A00),
            new OxProviderPreset("opencode", "opencode Zen", "https://opencode.ai/zen/v1",
                    "deepseek-v4.1-flash", new String[]{"deepseek-v4.1-flash", "claude-sonnet-5", "qwen3.8-max", "big-pickle"},
                    "https://opencode.ai/auth", R.drawable.ox_provider_opencode, 0xff111111),
            new OxProviderPreset("commandcode", "CommandCode", "https://api.commandcode.ai/provider/v1",
                    "deepseek/deepseek-v4.1-flash", new String[]{"deepseek/deepseek-v4.1-flash", "gpt-5.5", "Qwen/Qwen3.8-Max", "z-ai/glm-5.3-flash"},
                    "https://commandcode.ai/", R.drawable.ox_provider_commandcode, 0xffE5484D),
            new OxProviderPreset("opendesign", "OpenDesign", "https://amr-link.open-design.ai/v1",
                    "deepseek-v4.1-flash", new String[]{"deepseek-v4.1-flash", "claude-haiku-5.5", "glm-5.3-flash", "gpt-6-luna"},
                    "https://open-design.ai/", R.drawable.ox_provider_generic, 0xff0EA5E9),
            new OxProviderPreset("ollama", "Ollama（本机 / 局域网）", "http://127.0.0.1:11434/v1",
                    "qwen3.8:27b", new String[]{"qwen3.8:27b", "qwen3.8:7b", "llama3.2"},
                    "", R.drawable.ox_provider_ollama, 0xff111111),
            new OxProviderPreset("lmstudio", "LM Studio（本机 / 局域网）", "http://127.0.0.1:1234/v1",
                    "", new String[]{}, "", R.drawable.ox_provider_lmstudio, 0xff4338CA),
            new OxProviderPreset("vllm", "vLLM（本机 / 局域网）", "http://127.0.0.1:8000/v1",
                    "", new String[]{}, "", R.drawable.ox_provider_vllm, 0xffB45309),
            new OxProviderPreset("opencode-local", "opencode serve（本机 / 局域网）", "http://127.0.0.1:4010/v1",
                    "", new String[]{}, "", R.drawable.ox_provider_opencode, 0xff111111),
            CUSTOM
    ));

    static {
        for (OxProviderPreset p : ALL) {
            if ("openrouter".equals(p.id)) {
                p.headers = new String[][]{
                        {"HTTP-Referer", "https://github.com/BlanCatOra/OrionekoX"},
                        {"X-Title", "OrionekoX"}};
            } else if ("anthropic".equals(p.id)) {
                p.headers = new String[][]{{"anthropic-version", "2023-06-01"}};
            }
        }
    }
}
