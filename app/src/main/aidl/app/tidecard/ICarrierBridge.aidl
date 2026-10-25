package app.tidecard;
interface ICarrierBridge {
    String execute(String command, String payload) = 1;
    void destroy() = 16777114;
}
