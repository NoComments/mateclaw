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
 * 把 classpath:skills/info-harvester 技能绑定到「信息采集专家」数字员工
 * （agent_id = {@value #INFO_HARVESTER_AGENT_ID}）。
 *
 * <p>结构与 {@link SkillBuilderAgentSeedService} 完全对称：V114 迁移已经
 * 种好 agent 和工具绑定，但 info-harvester 这一 builtin skill 的 mate_skill.id
 * 由 {@link BuiltinSkillSeedService}（Order 110）在 Flyway 之后才分配，
 * 迁移脚本里拿不到稳定的 skill_id，所以放到这里 Order 121 兜底绑定。
 *
 * <p>行为规则：
 * <ul>
 *   <li>agent 已被用户软删除 → 跳过（尊重用户意图）</li>
 *   <li>info-harvester 不在 classpath → 跳过 + 警告日志</li>
 *   <li>绑定已存在 → 跳过</li>
 *   <li>否则 → 创建绑定，enabled=true</li>
 * </ul>
 */
@Slf4j
@Service
@Order(121)
@RequiredArgsConstructor
public class InfoHarvesterAgentSeedService implements ApplicationRunner {

    /** 与 V114 迁移脚本一致。 */
    public static final long INFO_HARVESTER_AGENT_ID = 1000000030L;

    private static final String INFO_HARVESTER_SKILL_NAME = "info-harvester";

    private final AgentMapper agentMapper;
    private final SkillMapper skillMapper;
    private final AgentSkillBindingMapper agentSkillBindingMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            bindSkillIfNeeded();
        } catch (Exception e) {
            log.warn("[InfoHarvesterSeed] Failed to bind info-harvester skill to agent: {}",
                    e.getMessage());
        }
    }

    private void bindSkillIfNeeded() {
        AgentEntity agent = agentMapper.selectById(INFO_HARVESTER_AGENT_ID);
        if (agent == null || (agent.getDeleted() != null && agent.getDeleted() != 0)) {
            log.info("[InfoHarvesterSeed] Info-harvester agent (id={}) is absent or soft-deleted; skipping skill binding.",
                    INFO_HARVESTER_AGENT_ID);
            return;
        }

        SkillEntity skill = skillMapper.selectOne(
                new LambdaQueryWrapper<SkillEntity>()
                        .eq(SkillEntity::getName, INFO_HARVESTER_SKILL_NAME)
                        .eq(SkillEntity::getDeleted, 0));
        if (skill == null) {
            log.warn("[InfoHarvesterSeed] skill '{}' not found in mate_skill — ensure "
                            + "classpath:skills/info-harvester/SKILL.md exists and BuiltinSkillSeedService ran.",
                    INFO_HARVESTER_SKILL_NAME);
            return;
        }

        Long existingId = agentSkillBindingMapper.selectCount(
                new LambdaQueryWrapper<AgentSkillBinding>()
                        .eq(AgentSkillBinding::getAgentId, INFO_HARVESTER_AGENT_ID)
                        .eq(AgentSkillBinding::getSkillId, skill.getId())
                        .eq(AgentSkillBinding::getDeleted, 0));
        if (existingId != null && existingId > 0) {
            return;
        }

        AgentSkillBinding binding = new AgentSkillBinding();
        binding.setAgentId(INFO_HARVESTER_AGENT_ID);
        binding.setSkillId(skill.getId());
        binding.setEnabled(true);
        binding.setCreateTime(LocalDateTime.now());
        binding.setUpdateTime(LocalDateTime.now());
        binding.setDeleted(0);
        agentSkillBindingMapper.insert(binding);
        log.info("[InfoHarvesterSeed] Bound skill '{}' (id={}) to info-harvester agent (id={})",
                INFO_HARVESTER_SKILL_NAME, skill.getId(), INFO_HARVESTER_AGENT_ID);
    }
}
