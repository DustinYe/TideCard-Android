package app.tidecard;

public final class AppPalette {
    public final boolean dark;public final int background,surface,ink,muted,accent,onAccent,soft,heroStart,heroEnd;
    public AppPalette(boolean dark){this.dark=dark;
        background=dark?0xFF0D1412:0xFFF6F8F4;surface=dark?0xFF17221E:0xFFFFFFFF;
        ink=dark?0xFFE7EFE7:0xFF17362E;muted=dark?0xFF9DB1A6:0xFF5B7267;
        accent=dark?0xFFB0D9AD:0xFF176C53;onAccent=dark?0xFF17351F:0xFFFFFFFF;
        soft=dark?0xFF243B2D:0xFFE4EEE1;heroStart=dark?0xFF24382B:0xFFE3EEDC;heroEnd=dark?0xFF1C352F:0xFFD3E6DB;
    }
}
