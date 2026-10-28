package app.tidecard;

public final class AppearanceMode {
    public static String normalize(String value){return "light".equals(value)||"dark".equals(value)?value:"system";}
    public static boolean dark(String mode,boolean systemDark){return "dark".equals(mode)||!"light".equals(mode)&&systemDark;}
    private AppearanceMode(){}
}
