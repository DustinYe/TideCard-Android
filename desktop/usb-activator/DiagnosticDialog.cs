using System;
using System.Diagnostics;
using System.Drawing;
using System.Windows.Forms;

namespace TideCardUsb {
    internal sealed class DiagnosticDialog : Form {
        private readonly System.ComponentModel.IContainer components=new System.ComponentModel.Container();
        private readonly ContextMenuStrip helpMenu;
        internal const string HuaweiDriverUrl="https://consumer.huawei.com/cn/support/hisuite/";
        internal const string OppoHelpUrl="https://support.oppo.com/ph/answer/?aid=neu10634",OemDriverUrl="https://developer.android.com/studio/run/oem-usb",ColorOsHelpUrl="https://shizuku.rikka.app/zh-hans/guide/setup/#通过无线调试启动-通过连接电脑启动-adb-权限受限";
        internal static ContextMenuStrip CreateHelpMenu(IWin32Window owner,Action<string> opener=null) {
            Action<string> open=opener??(target=>Open(owner,target));
            ContextMenuStrip menu=new ContextMenuStrip();
            string[] names={"OPPO 官方 USB 连接排查","ColorOS：已连接但 ADB 权限受限","其它品牌：Android 官方 OEM 驱动目录","华为专用：HiSuite 手机助手"};
            string[] urls={OppoHelpUrl,ColorOsHelpUrl,OemDriverUrl,HuaweiDriverUrl};
            for(int i=0;i<names.Length;i++){string url=urls[i];ToolStripMenuItem item=new ToolStripMenuItem(names[i]){Tag=url};item.Click+=(s,e)=>open(url);menu.Items.Add(item);}
            return menu;
        }
        internal DiagnosticDialog(Diagnosis result) : this(result,null) {}
        internal DiagnosticDialog(Diagnosis result,Action<string> opener) {
            helpMenu=CreateHelpMenu(this,opener);components.Add(helpMenu);
            Text="USB 连接诊断";StartPosition=FormStartPosition.CenterParent;Font=new Font("Microsoft YaHei UI",10);AutoScaleDimensions=new SizeF(96,96);AutoScaleMode=AutoScaleMode.Dpi;ClientSize=new Size(720,570);MinimumSize=new Size(400,320);
            TableLayoutPanel root=new TableLayoutPanel {Dock=DockStyle.Fill,Padding=new Padding(18),ColumnCount=1,RowCount=3};root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));root.RowStyles.Add(new RowStyle(SizeType.AutoSize));root.RowStyles.Add(new RowStyle(SizeType.Percent,100));root.RowStyles.Add(new RowStyle(SizeType.AutoSize));Controls.Add(root);
            root.Controls.Add(new Label {Name="diagnosisSummary",Text=result.Summary,AutoSize=true,Dock=DockStyle.Fill,ForeColor=Color.FromArgb(22,60,68),Margin=new Padding(0,0,0,12)},0,0);
            root.Controls.Add(new TextBox {Name="diagnosisReport",Text=result.Report,Multiline=true,ReadOnly=true,ScrollBars=ScrollBars.Vertical,Dock=DockStyle.Fill,BackColor=Color.White},0,1);
            FlowLayoutPanel buttons=new FlowLayoutPanel {Name="diagnosisButtons",AutoSize=true,Dock=DockStyle.Fill,WrapContents=true,Margin=new Padding(0,12,0,0)};root.Controls.Add(buttons,0,2);
            Button driver=Button("OPPO / 厂商帮助"),manager=Button("设备管理器"),copy=Button("复制脱敏诊断"),close=Button("关闭");buttons.Controls.AddRange(new Control[]{driver,manager,copy,close});
            driver.Name="vendorHelp";driver.ContextMenuStrip=helpMenu;
            driver.Click+=(s,e)=>helpMenu.Show(driver,new Point(0,driver.Height));manager.Click+=(s,e)=>Open(this,"devmgmt.msc");copy.Click+=(s,e)=>{try{Clipboard.SetText(result.Report);}catch(Exception){MessageBox.Show(this,"剪贴板暂不可用，请稍后重试。");}};close.Click+=(s,e)=>Close();AcceptButton=close;CancelButton=close;
            Shown+=(s,e)=>{Rectangle area=Screen.FromControl(this).WorkingArea;MinimumSize=new Size(Math.Min(MinimumSize.Width,Math.Max(250,area.Width-24)),Math.Min(MinimumSize.Height,Math.Max(250,area.Height-24)));Size=new Size(Math.Min(Width,area.Width-24),Math.Min(Height,area.Height-24));Location=new Point(Math.Max(area.Left+12,Math.Min(Left,area.Right-Width-12)),Math.Max(area.Top+12,Math.Min(Top,area.Bottom-Height-12)));ActiveControl=close;};
        }
        protected override void Dispose(bool disposing) {if(disposing)components.Dispose();base.Dispose(disposing);}
        private static Button Button(string text){return new Button {Text=text,AutoSize=true,Padding=new Padding(8,5,8,5),Margin=new Padding(0,0,8,0)};}
        internal static void Open(IWin32Window owner,string target){try{Process.Start(new ProcessStartInfo(target){UseShellExecute=true});}catch(Exception){MessageBox.Show(owner,target=="devmgmt.msc"?"请按 Win + X，手动打开设备管理器。":"请在浏览器中手动打开："+target,"打开入口");}}
    }
}
