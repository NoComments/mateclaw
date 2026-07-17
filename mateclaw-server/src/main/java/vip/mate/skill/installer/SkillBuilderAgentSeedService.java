package vip.mate.skill.installer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import vip.mate.agent.binding.model.AgentSkillBinding;
import vip.mate.agent.binding.repository.AgentSkillBindingMapper;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.repository.AgentMapper;
import vip.mate.skill.model.SkillEntity;
import vip.mate.skill.repository.SkillMapper;

import java.time.LocalDateTime;

/**
 * 把 classpath:skills/skill-creator 技能绑定到「技能创建助手」数字员工
 * （agent_id = {@value #SKILL_BUILDER_AGENT_ID}）。
 *
 * <p>为什么不直接在 Flyway 迁移里 INSERT mate_agent_skill：
 * skill-creator 的 mate_skill.id 由 {@link BuiltinSkillSeedService}
 * （Order 110）在 Flyway 之后才分配，迁移脚本拿不到稳定的 skill_id。
 * 所以本服务在 Order 120 兜底绑定。
 *
 * <p>行为规则：
 * <ul>
 *   <li>agent 已被用户软删除 → 跳过（尊重用户意图，前端会提示重建）</li>
 *   <li>skill-creator 不在 classpath → 跳过 + 警告日志</li>
 *   <li>绑定已存在 → 跳过</li>
 *   <li>否则 → 创建绑定，enabled=true</li>
 * </ul>
 */
@Slf4j
@Service
@Order(120)
@RequiredArgsConstructor
public class SkillBuilderAgentSeedService implements ApplicationRunner {

    /** 与 V170 迁移脚本一致；前端「AI 建技能」按钮也用这个 ID 跳转聊天。 */
    public static final long SKILL_BUILDER_AGENT_ID = 1000000010L;

    private static final String SKILL_CREATOR_NAME = "skill-creator";

    private final AgentMapper agentMapper;
    private final SkillMapper skillMapper;
    private final AgentSkillBindingMapper agentSkillBindingMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            bindSkillCreatorIfNeeded();
        } catch (Exception e) {
            log.warn("[SkillBuilderSeed] Failed to bind skill-creator to skill-builder agent: {}",
                    e.getMessage());
        }
    }

    private void bindSkillCreatorIfNeeded() {
        AgentEntity agent = agentMapper.selectById(SKILL_BUILDER_AGENT_ID);
        if (agent == null || (agent.getDeleted() != null && agent.getDeleted() != 0)) {
            log.info("[SkillBuilderSeed] Skill-builder agent (id={}) is absent or soft-deleted; skipping skill binding. "
                            + "User must recreate it manually from the Agents page if needed.",
                    SKILL_BUILDER_AGENT_ID);
            return;
        }

        SkillEntity skill = skillMapper.selectOne(
                new LambdaQueryWrapper<SkillEntity>()
                        .eq(SkillEntity::getName, SKILL_CREATOR_NAME)
                        .eq(SkillEntity::getDeleted, 0));
        if (skill == null) {
            log.warn("[SkillBuilderSeed] skill '{}' not found in mate_skill — ensure "
                            + "classpath:skills/skill-creator/SKILL.md exists and BuiltinSkillSeedService ran.",
                    SKILL_CREATOR_NAME);
            return;
        }

        Long existingId = agentSkillBindingMapper.selectCount(
                new LambdaQueryWrapper<AgentSkillBinding>()
                        .eq(AgentSkillBinding::getAgentId, SKILL_BUILDER_AGENT_ID)
                        .eq(AgentSkillBinding::getSkillId, skill.getId())
                        .eq(AgentSkillBinding::getDeleted, 0));
        if (existingId != null && existingId > 0) {
            return;
        }

        AgentSkillBinding binding = new AgentSkillBinding();
        binding.setAgentId(SKILL_BUILDER_AGENT_ID);
        binding.setSkillId(skill.getId());
        binding.setEnabled(true);
        binding.setCreateTime(LocalDateTime.now());
        binding.setUpdateTime(LocalDateTime.now());
        binding.setDeleted(0);
        agentSkillBindingMapper.insert(binding);
        log.info("[SkillBuilderSeed] Bound skill '{}' (id={}) to skill-builder agent (id={})",
                SKILL_CREATOR_NAME, skill.getId(), SKILL_BUILDER_AGENT_ID);
    }
}
