package app.tidecard;

import java.util.regex.*;

/** Notification input contains only local ports and a transient pairing code. */
final class PairingInput {
    record Pair(int port,String code) {}
    static int port(String value) {
        String text=value==null?"":value.trim();
        if(!text.matches("[0-9]{1,5}"))throw new IllegalArgumentException("请输入系统当前显示的端口（1–65535）");
        int port=Integer.parseInt(text);
        if(port<1||port>65535)throw new IllegalArgumentException("请输入系统当前显示的端口（1–65535）");
        return port;
    }
    static Pair pair(String value) {
        Matcher m=Pattern.compile("([0-9]{1,5})[\\s,:，：]+([0-9]{6})").matcher(value==null?"":value.trim());
        if(!m.matches())throw new IllegalArgumentException("请输入配对弹窗底部的端口和六位码，中间用空格分隔");
        return new Pair(port(m.group(1)),m.group(2));
    }
    private PairingInput() {}
}
