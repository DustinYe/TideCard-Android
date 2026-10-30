package app.tidecard;

import org.json.JSONObject;

/** Keeps a confirmed cleanup alive across Activity recreation; it never deletes a TideCard snapshot. */
final class CleanupController {
    private final TideApp app;
    volatile JSONObject preview;
    volatile String status="选择卡槽后检查旧配置。检查本身不会修改配置。";
    volatile int mask;
    CleanupController(TideApp app){this.app=app;mask=app.mask();}
    boolean blocked(){return app.active()||app.prefs().getBoolean("restorePending",false);}
    private boolean available(){
        if(app.busy)return false;
        if(blocked()){status="请先在主界面停止并恢复当前会话，再清除旧配置";app.changed();return false;}
        if(!app.authorized()){status="请先激活内置后台，再检查旧配置";app.changed();return false;}
        return true;
    }
    void select(int value){if(app.busy||value<1||value>3)return;mask=value;preview=null;status="选择卡槽后检查旧配置。检查本身不会修改配置。";app.changed();}
    void check(){
        if(!available())return;preview=null;app.busy=true;status="正在检查旧地区配置…";app.changed();final int selected=mask;
        app.worker.execute(()->{
            JSONObject result=null;String text;
            try{result=app.call("cleanup_preview",new JSONObject().put("mask",selected));text=result.optBoolean("canClear")?"检查完成，请确认清除范围":"未发现需要清除的旧地区配置";}
            catch(Exception e){text=problem(e);}
            final JSONObject done=result;final String message=text;
            app.main.post(()->{preview=done;status=message;app.busy=false;app.changed();});
        });
    }
    boolean canClear(){return !app.busy&&!blocked()&&app.authorized()&&preview!=null&&preview.optBoolean("canClear")&&!preview.optString("token").isEmpty();}
    void clear(String token){
        if(!available()||!canClear()||!preview.optString("token").equals(token))return;
        preview=null;app.busy=true;status="正在清除旧配置并核对国家码…";app.changed();
        app.worker.execute(()->{
            JSONObject result=null;String text;
            try{result=app.call("cleanup_clear",new JSONObject().put("token",token));text="旧地区配置已清除，国家码已核对；可返回主界面重新开始";}
            catch(Exception e){text=problem(e);}
            final JSONObject done=result;final String message=text;
            app.main.post(()->{
                if(done!=null){app.cards=done;app.prefs().edit().remove("sessionFailure").apply();app.message=message;}
                status=message;app.busy=false;app.changed();
            });
        });
    }
    private String problem(Exception error){
        String text=error.getMessage();if("未知操作".equals(text))return "后台版本较旧，请重新激活内置后台后重试";
        if(error instanceof TideApp.BackendException){TideApp.BackendException result=(TideApp.BackendException)error;
            if("cleanup_incomplete".equals(result.code)&&result.completedMask>0&&result.completedMask<=3)
                return text+"\n已完成清除："+(result.completedMask==3?"双卡":result.completedMask==1?"卡 1":"卡 2");
        }return text==null?"系统调用失败":text;
    }
}
