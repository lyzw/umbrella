const session = require('./services/session');
App({
  onLaunch() {
    session.restore();
  },
  onHide() {
    // No child profile or cart draft is persisted when the app enters the background.
    require('./services/context').clear();
  }
});
