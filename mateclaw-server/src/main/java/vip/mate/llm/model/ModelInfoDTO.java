package vip.mate.llm.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfoDTO {
    private String id;
    private String name;

    /**
     * Discovery probe result. true = passed runtime-protocol ping test,
     * false = ping failed (listed by provider but unusable at runtime,
     * e.g. DashScope compatible-mode may list models the native SDK rejects).
     * null = not probed (probe disabled or still pending).
     */
    private Boolean probeOk;

    /** Reason text when probeOk=false (short, suitable for UI badge tooltip) */
    private String probeError;

    /**
     * RFC-049 PR-1-UI (narrow): whether this model's {@code ModelFamily} accepts the
     * OpenAI {@code reasoning_effort} parameter specifically. True <em>only</em> for
     * the OpenAI reasoning family (gpt-5, o1, o3, o4 variants). Retained for callers
     * that need to know parameter-level compatibility; the UI "deep thinking" toggle
     * should use {@link #supportsThinking} instead.
     */
    private boolean supportsReasoningEffort;

    /**
     * RFC-049 PR-1-UI (broad): whether this model supports <em>any</em> form of
     * deep thinking — either OpenAI-style via {@code reasoning_effort}, provider-
     * native (Kimi K2.x, DeepSeek-Reasoner, qwen-thinking), or Anthropic extended
     * thinking (Claude family). This is what the UI "deep thinking" toggle reads.
     *
     * <p>Derived from the model name so every construction site stays consistent.
     */
    private boolean supportsThinking;

    /**
     * Numeric {@code mate_model_config.id} of the row backing this model entry.
     * Lets the UI call the per-model update endpoints (e.g. {@code PUT /models/{id}/modalities})
     * without a separate lookup. Null only for ad-hoc entries not backed by a config row.
     */
    private Long configId;

    /**
     * Explicit modality declaration stored on {@code mate_model_config.modalities} - a JSON
     * array of lowercase modality names (e.g. {@code ["vision"]}), or null when the model
     * defers to {@link vip.mate.llm.service.ModelCapabilityService}'s name-based heuristics.
     * Round-tripped to the UI so the "multimodal" checkbox reflects the explicit override
     * (checked = non-null), independent of the effective capability below.
     */
    private String modalities;

    /**
     * Effective modality set resolved by {@link vip.mate.llm.service.ModelCapabilityService#resolve}
     * (explicit declaration if present, otherwise the built-in heuristic table). Uppercase
     * modality names ({@code TEXT / VISION / VIDEO / AUDIO}). Lets the UI show what the model
     * actually supports at runtime - e.g. an unchecked qwen-vl still shows "视觉" via heuristic.
     */
    private List<String> resolvedModalities;

    public ModelInfoDTO(String id, String name) {
        this.id = id;
        this.name = name;
        this.supportsReasoningEffort = ModelFamily.detect(id).supportsReasoningEffort();
        this.supportsThinking = computeSupportsThinking(id);
    }

    /**
     * Thinking capability at the product level (what the UI toggle should reflect):
     * any model family that has a thinking mode, regardless of how it is triggered
     * (parameter vs. model-native vs. Anthropic extended thinking).
     */
    private static boolean computeSupportsThinking(String modelName) {
        if (modelName == null || modelName.isBlank()) return false;
        ModelFamily family = ModelFamily.detect(modelName);
        if (family.isThinking()) return true;  // OPENAI_REASONING / KIMI_THINKING / DEEPSEEK_REASONER / GENERIC_THINKING
        // Anthropic Claude supports extended thinking via AnthropicChatOptions.thinking;
        // ModelFamily doesn't model non-OpenAI-compatible providers so match by name.
        return modelName.toLowerCase().contains("claude");
    }
}
