package app.tidecard;
interface ICarrierOverrideCallback {
    boolean begin(String nonce);
    void end(String nonce);
    void complete(String nonce, boolean success, boolean denied, String error);
}
