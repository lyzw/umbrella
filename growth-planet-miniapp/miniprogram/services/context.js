let cart = null;
module.exports = {
  setCart(value) { cart = value; },
  takeCart() { const value = cart; cart = null; return value; },
  clear() { cart = null; }
};
