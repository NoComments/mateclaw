package vip.mate.analytics.installer;

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
 * Binds the {@code data-analyst} built-in skill to the 「数据分析专家」 agent
 * (agent_id = {@value #DATA_ANALYST_AGENT_ID}).
 *
 * <p>Runs at Order 130, after {@code BuiltinSkillSeedService} (Order 110) which
 * assigns the stable {@code mate_skill.id} for classpath skills.
 */
@Slf4j
@Service
@Order(130)
@RequiredArgsConstructor
public class DataAnalystAgentSeedService implements ApplicationRunner {

    public static final long DATA_ANALYST_AGENT_ID = 1000000020L;

    private static final String DATA_ANALYST_SKILL_NAME = "data-analyst";

    private final AgentMapper agentMapper;
    private final SkillMapper skillMapper;
    private final AgentSkillBindingMapper agentSkillBindingMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            bindDataAnalystSkillIfNeeded();
        } catch (Exception e) {
            log.warn("[DataAnalystSeed] Failed to bind data-analyst skill to agent: {}",
                    e.getMessage());
        }
    }

    private void bindDataAnalystSkillIfNeeded() {
        AgentEntity agent = agentMapper.selectById(DATA_ANALYST_AGENT_ID);
        if (agent == null || (agent.getDeleted() != null && agent.getDeleted() != 0)) {
            log.info("[DataAnalystSeed] Data-analyst agent (id={}) is absent or soft-deleted; skipping.",
                    DATA_ANALYST_AGENT_ID);
            return;
        }

        SkillEntity skill = skillMapper.selectOne(
                new LambdaQueryWrapper<SkillEntity>()
                        .eq(SkillEntity::getName, DATA_ANALYST_SKILL_NAME)
                        .eq(SkillEntity::getDeleted, 0));
        if (skill == null) {
            log.warn("[DataAnalystSeed] Skill '{}' not found in mate_skill — ensure "
                            + "classpath:skills/data-analyst/SKILL.md exists and BuiltinSkillSeedService ran.",
                    DATA_ANALYST_SKILL_NAME);
            return;
        }

        Long count = agentSkillBindingMapper.selectCount(
                new LambdaQueryWrapper<AgentSkillBinding>()
                        .eq(AgentSkillBinding::getAgentId, DATA_ANALYST_AGENT_ID)
                        .eq(AgentSkillBinding::getSkillId, skill.getId())
                        .eq(AgentSkillBinding::getDeleted, 0));
        if (count != null && count > 0) {
            return;
        }

        AgentSkillBinding binding = new AgentSkillBinding();
        binding.setAgentId(DATA_ANALYST_AGENT_ID);
        binding.setSkillId(skill.getId());
        binding.setEnabled(true);
        binding.setCreateTime(LocalDateTime.now());
        binding.setUpdateTime(LocalDateTime.now());
        binding.setDeleted(0);
        agentSkillBindingMapper.insert(binding);
        log.info("[DataAnalystSeed] Bound skill '{}' (id={}) to data-analyst agent (id={})",
                DATA_ANALYST_SKILL_NAME, skill.getId(), DATA_ANALYST_AGENT_ID);
    }
}
