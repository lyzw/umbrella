const api = require('./api');

// 难度落库为英文枚举（与后端 CHECK 及 DishRecipeService.DIFFICULTIES 一致），界面展示为中文。
const DIFFICULTY_CHOICES = [
  { label: '未设置', value: '' },
  { label: '简单', value: 'EASY' },
  { label: '中等', value: 'MEDIUM' },
  { label: '有挑战', value: 'HARD' }
];
const DIFFICULTY_LABELS = { EASY: '简单', MEDIUM: '中等', HARD: '有挑战' };
const DIFFICULTY_OPTIONS = DIFFICULTY_CHOICES.map(choice => choice.label);

// 与后端 DishRecipeService 的容量上限一致（超限由服务端最终裁决，此处仅提前拦截）。
const MAX_INGREDIENTS = 30;
const MAX_STEPS = 20;

function difficultyLabel(code) {
  if (!code) return '';
  return DIFFICULTY_LABELS[code] || code;
}
/** 落库值 → picker 下标；未知值落到「未设置」，避免界面显示错位。 */
function difficultyIndex(code) {
  const index = DIFFICULTY_CHOICES.findIndex(choice => choice.value === (code || ''));
  return index < 0 ? 0 : index;
}
/** picker 下标 → 落库值（空串表示不填，提交前会归一化为 undefined）。 */
function difficultyValue(index) {
  const choice = DIFFICULTY_CHOICES[Number(index) || 0];
  return choice ? choice.value : '';
}
/** 想吃清单里的菜品引用键：两表自增序列独立，必须带 type 才能唯一定位一道菜。 */
function recipeKey(type, dishId) {
  return type + ':' + dishId;
}
/**
 * 配方响应归一化为展示结构。
 * 语义要点：接口返回 null 字段表示「这道菜确实没配方」，与「响应没带配方」不同 ——
 * 本函数只处理前者，空配方以 empty=true 显式表达，由页面提示「还没有录入配方」。
 */
function presentRecipe(payload) {
  const source = payload || {};
  const ingredients = (source.ingredients || []).filter(item => item && item.name).map(item => ({
    name: item.name,
    amountText: item.amount || '适量'
  }));
  const steps = (source.cookSteps || []).filter(Boolean).map((text, index) => ({ no: index + 1, text }));
  const meta = [
    source.cookMinutes ? source.cookMinutes + ' 分钟' : '',
    source.servings ? source.servings + ' 人份' : '',
    difficultyLabel(source.difficulty)
  ].filter(Boolean).join(' · ');
  const cookTips = source.cookTips || '';
  return {
    status: 'ready', ingredients, steps, cookTips, meta,
    hasIngredients: ingredients.length > 0,
    hasSteps: steps.length > 0,
    empty: ingredients.length === 0 && steps.length === 0 && !cookTips && !meta
  };
}
/** 列表摘要：「食材 3 · 步骤 5 · 30 分钟」，无配方时给出明确文案而非空白。 */
function recipeSummary(item) {
  const source = item || {};
  const parts = [];
  if (source.ingredientCount) parts.push('食材 ' + source.ingredientCount);
  if (source.stepCount) parts.push('步骤 ' + source.stepCount);
  if (source.cookMinutes) parts.push(source.cookMinutes + ' 分钟');
  return parts.length ? parts.join(' · ') : '未填写配方';
}
function fetchRecipe(type, dishId) {
  return api.get('/dishes/' + encodeURIComponent(type) + '/' + encodeURIComponent(dishId) + '/recipe');
}
module.exports = {
  DIFFICULTY_OPTIONS, DIFFICULTY_CHOICES, MAX_INGREDIENTS, MAX_STEPS,
  difficultyLabel, difficultyIndex, difficultyValue, recipeKey, presentRecipe, recipeSummary, fetchRecipe
};
