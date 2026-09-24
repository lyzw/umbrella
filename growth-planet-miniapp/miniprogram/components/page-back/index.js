const ui = require('../../utils/page');

Component({
  properties: {
    role: String,
    fallbackTab: String,
    disabled: Boolean
  },
  methods: {
    back() {
      if (this.data.disabled) return;
      return ui.back({ data: { role: this.data.role } }, this.data.fallbackTab);
    }
  }
});
