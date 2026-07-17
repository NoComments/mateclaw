-- V119: 将已有实例中残留的 MateClaw 品牌文本更新为 QingwenClaws
-- 影响范围：mate_user.nickname / mate_agent.system_prompt / mate_skill.author + description
-- 仅更新精确匹配或包含品牌字符串的行，不触碰其他字段。

-- 1. 管理员用户昵称
UPDATE mate_user
SET nickname    = 'QingwenClaws Admin',
    update_time = NOW()
WHERE nickname = 'MateClaw Admin';

-- 2. 技能创建助手 system_prompt（V113 种子 agent ID = 1000000010）
UPDATE mate_agent
SET system_prompt = REPLACE(system_prompt, 'MateClaw 技能（Skill）', 'QingwenClaws 技能（Skill）'),
    update_time   = NOW()
WHERE id = 1000000010
  AND system_prompt LIKE '%MateClaw 技能%';

-- 3. 通用兜底：agent system_prompt 中仍残留 MateClaw 品牌文本的行
UPDATE mate_agent
SET system_prompt = REPLACE(system_prompt, 'MateClaw', 'QingwenClaws'),
    update_time   = NOW()
WHERE system_prompt LIKE '%MateClaw%'
  AND id != 1000000010;

-- 4. 技能 author 字段（V30 种子的四个协作技能）
UPDATE mate_skill
SET author      = 'QingwenClaws',
    update_time = NOW()
WHERE author = 'MateClaw';

-- 5. 技能 description 中的品牌文本
UPDATE mate_skill
SET description = REPLACE(description, 'MateClaw', 'QingwenClaws'),
    update_time = NOW()
WHERE description LIKE '%MateClaw%';
