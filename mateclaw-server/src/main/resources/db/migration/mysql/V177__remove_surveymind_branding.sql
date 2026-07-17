-- Remove SurveyMind branding from default workspace
-- Update workspace name from 'Default' or 'SurveyMind' to '默认'
UPDATE mate_workspace
SET name = '默认',
    update_time = NOW()
WHERE id = 1
  AND deleted = 0
  AND name IN ('Default', 'SurveyMind');
