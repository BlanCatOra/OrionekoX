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
            "custom", "自定义 OpenAI 兼容接口", "", "", new String[]{},
            "", R.drawable.ox_provider_generic, 0xff6B7280);

    public static final List<OxProviderPreset> ALL = new ArrayList<>(Arrays.asList(
            new OxProviderPreset("deepseek", "DeepSeek", "https://api.deepseek.com",
                    "deepseek-chat", new String[]{"deepseek-chat", "deepseek-reasoner"},
                    "https://platform.deepseek.com/api_keys", R.drawable.ox_provider_deepseek, 0xff4D6BFE),
            new OxProviderPreset("openai", "OpenAI", "https://api.openai.com/v1",
                    "gpt-4o-mini", new String[]{"gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "o3-mini"},
                    "https://platform.openai.com/api-keys", R.drawable.ox_provider_openai, 0xff10A37F),
            new OxProviderPreset("anthropic", "Anthropic Claude", "https://api.anthropic.com/v1",
                    "claude-sonnet-4-5", new String[]{"claude-sonnet-4-5", "claude-opus-4-1", "claude-3-5-haiku-latest"},
                    "https://console.anthropic.com/settings/keys", R.drawable.ox_provider_anthropic, 0xffD97757),
            new OxProviderPreset("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai",
                    "gemini-2.0-flash", new String[]{"gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash"},
                    "https://aistudio.google.com/app/apikey", R.drawable.ox_provider_gemini, 0xff4285F4),
            new OxProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1",
                    "deepseek/deepseek-chat", new String[]{"deepseek/deepseek-chat", "anthropic/claude-sonnet-4", "google/gemini-2.0-flash-001", "qwen/qwen-2.5-72b-instruct"},
                    "https://openrouter.ai/keys", R.drawable.ox_provider_openrouter, 0xff6366F1),
            new OxProviderPreset("moonshot", "Moonshot Kimi", "https://api.moonshot.cn/v1",
                    "moonshot-v1-8k", new String[]{"moonshot-v1-8k", "moonshot-v1-32k", "kimi-k2-0711-preview"},
                    "https://platform.moonshot.cn/console/api-keys", R.drawable.ox_provider_moonshot, 0xff111111),
            new OxProviderPreset("qwen", "阿里云百炼 Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                    "qwen-plus", new String[]{"qwen-plus", "qwen-max", "qwen-turbo", "qwen2.5-72b-instruct"},
                    "https://dashscope.console.aliyun.com/apiKey", R.drawable.ox_provider_qwen, 0xff615CED),
            new OxProviderPreset("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4",
                    "glm-4-plus", new String[]{"glm-4-plus", "glm-4-flash", "glm-4-air"},
                    "https://open.bigmodel.cn/usercenter/apikeys", R.drawable.ox_provider_zhipu, 0xff3859FF),
            new OxProviderPreset("minimax", "MiniMax", "https://api.minimax.chat/v1",
                    "MiniMax-Text-01", new String[]{"MiniMax-Text-01", "abab6.5s-chat"},
                    "https://platform.minimaxi.com/user-center/basic-information/interface-key", R.drawable.ox_provider_minimax, 0xffF23F5D),
            new OxProviderPreset("siliconflow", "硅基流动 SiliconFlow", "https://api.siliconflow.cn/v1",
                    "Qwen/Qwen2.5-72B-Instruct", new String[]{"Qwen/Qwen2.5-72B-Instruct", "deepseek-ai/DeepSeek-V3", "THUDM/GLM-4-9B-0414"},
                    "https://cloud.siliconflow.cn/account/ak", R.drawable.ox_provider_siliconflow, 0xff6C5CE7),
            new OxProviderPreset("groq", "Groq", "https://api.groq.com/openai/v1",
                    "llama-3.3-70b-versatile", new String[]{"llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768"},
                    "https://console.groq.com/keys", R.drawable.ox_provider_groq, 0xffF55036),
            new OxProviderPreset("mistral", "Mistral AI", "https://api.mistral.ai/v1",
                    "mistral-small-latest", new String[]{"mistral-small-latest", "mistral-large-latest", "open-mistral-nemo"},
                    "https://console.mistral.ai/api-keys", R.drawable.ox_provider_mistral, 0xffFA520F),
            new OxProviderPreset("xai", "xAI Grok", "https://api.x.ai/v1",
                    "grok-2-latest", new String[]{"grok-2-latest", "grok-beta"},
                    "https://console.x.ai", R.drawable.ox_provider_xai, 0xff111111),
            new OxProviderPreset("together", "Together AI", "https://api.together.xyz/v1",
                    "meta-llama/Llama-3.3-70B-Instruct-Turbo", new String[]{"meta-llama/Llama-3.3-70B-Instruct-Turbo", "Qwen/Qwen2.5-72B-Instruct-Turbo"},
                    "https://api.together.ai/settings/api-keys", R.drawable.ox_provider_together, 0xff0F6FFF),
            new OxProviderPreset("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference/v1",
                    "accounts/fireworks/models/llama-v3p3-70b-instruct", new String[]{"accounts/fireworks/models/llama-v3p3-70b-instruct", "accounts/fireworks/models/deepseek-v3"},
                    "https://fireworks.ai/account/api-keys", R.drawable.ox_provider_fireworks, 0xff5019C5),
            new OxProviderPreset("perplexity", "Perplexity", "https://api.perplexity.ai",
                    "sonar", new String[]{"sonar", "sonar-pro"},
                    "https://www.perplexity.ai/settings/api", R.drawable.ox_provider_perplexity, 0xff20808D),
            new OxProviderPreset("cohere", "Cohere", "https://api.cohere.ai/compatibility/v1",
                    "command-r-plus-08-2024", new String[]{"command-r-plus-08-2024", "command-r-08-2024"},
                    "https://dashboard.cohere.com/api-keys", R.drawable.ox_provider_cohere, 0xff39594D),
            new OxProviderPreset("cerebras", "Cerebras", "https://api.cerebras.ai/v1",
                    "llama-3.3-70b", new String[]{"llama-3.3-70b", "llama3.1-8b"},
                    "https://cloud.cerebras.ai", R.drawable.ox_provider_cerebras, 0xffF15A29),
            new OxProviderPreset("novita", "Novita AI", "https://api.novita.ai/v3/openai",
                    "deepseek/deepseek-v3", new String[]{"deepseek/deepseek-v3", "meta-llama/llama-3.3-70b-instruct"},
                    "https://novita.ai/settings/key-management", R.drawable.ox_provider_novita, 0xff0A0A0A),
            new OxProviderPreset("hyperbolic", "Hyperbolic", "https://api.hyperbolic.xyz/v1",
                    "deepseek-ai/DeepSeek-V3", new String[]{"deepseek-ai/DeepSeek-V3", "meta-llama/Llama-3.3-70B-Instruct"},
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
                    "qwen-plus", new String[]{"qwen-plus", "qwen-max"},
                    "https://dashscope.console.aliyun.com/apiKey", R.drawable.ox_provider_alibaba, 0xffFF6A00),
            new OxProviderPreset("ollama", "Ollama（本机 / 局域网）", "http://127.0.0.1:11434/v1",
                    "qwen2.5:7b", new String[]{"qwen2.5:7b", "llama3.2", "deepseek-r1:7b"},
                    "", R.drawable.ox_provider_ollama, 0xff111111),
            new OxProviderPreset("lmstudio", "LM Studio（本机 / 局域网）", "http://127.0.0.1:1234/v1",
                    "", new String[]{}, "", R.drawable.ox_provider_lmstudio, 0xff4338CA),
            new OxProviderPreset("vllm", "vLLM（本机 / 局域网）", "http://127.0.0.1:8000/v1",
                    "", new String[]{}, "", R.drawable.ox_provider_vllm, 0xffB45309),
            new OxProviderPreset("opencode", "opencode serve（容器 / 局域网）", "http://127.0.0.1:4010/v1",
                    "", new String[]{}, "", R.drawable.ox_provider_generic, 0xff111111),
            CUSTOM
    ));
}
