const DEFAULT_THRESHOLD = 1;

function positiveInteger(value, fallback) {
  const number = Number(value);
  if (!Number.isFinite(number) || number <= 0) return fallback;
  return Math.floor(number);
}

function nonNegativeInteger(value) {
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0) return 0;
  return Math.floor(number);
}

function medalStateText(item) {
  if (item && item.earned) return '已点亮';
  const definition = item && item.definition && typeof item.definition === 'object'
    ? item.definition : {};
  const threshold = positiveInteger(definition.threshold, DEFAULT_THRESHOLD);
  const progress = Math.min(nonNegativeInteger(item && item.progress), threshold);
  return '进度 ' + progress + '/' + threshold;
}

function mapMedals(awards) {
  if (!Array.isArray(awards)) return [];
  return awards.map((award, index) => {
    const item = award && typeof award === 'object' ? award : {};
    const definition = item.definition && typeof item.definition === 'object'
      ? item.definition : {};
    return {
      ...item,
      definition,
      definitionId: definition.definitionId || item.awardId || 'medal-' + index,
      stateText: medalStateText(item)
    };
  });
}

function summarizeMedals(awards) {
  const medals = mapMedals(awards);
  return {
    medals,
    medalEarned: medals.filter(item => item.earned).length,
    medalTotal: medals.length
  };
}

module.exports = { mapMedals, summarizeMedals };
