let revision = 0;
module.exports = { current: () => revision, invalidate: () => ++revision };
