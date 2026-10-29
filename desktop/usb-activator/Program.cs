using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace TideCardUsb {
    internal static class Program {
        [STAThread] private static void Main() {
            Application.EnableVisualStyles(); Application.SetCompatibleTextRenderingDefault(false); Application.Run(new MainForm());
        }
    }
    internal sealed class MainForm : Form {
        private readonly ComboBox devices=new ComboBox(); private readonly Button refresh=new Button(), activate=new Button(),diagnostic=new Button();
        private readonly Label state=new Label(); private readonly TextBox logs=new TextBox(); private readonly Workflow workflow;private readonly Adb adb;
        private bool working;
        internal MainForm() : this(true) {}
        internal MainForm(bool autoDiscover) {
            SuspendLayout();
            Text="潮汐改卡 · USB 激活工具"; StartPosition=FormStartPosition.CenterScreen;
            AutoScaleDimensions=new SizeF(96,96); AutoScaleMode=AutoScaleMode.Dpi;
            ClientSize=new Size(740,650); MinimumSize=new Size(600,440); BackColor=Color.FromArgb(243,247,247); Font=new Font("Microsoft YaHei UI",10);
            adb=new Adb(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"adb","adb.exe"));workflow=new Workflow(adb,Log);
            Panel viewport=new Panel {Name="viewport",Dock=DockStyle.Fill,AutoScroll=true}; Controls.Add(viewport);
            TableLayoutPanel root=new TableLayoutPanel {Name="content",Dock=DockStyle.Top,AutoSize=true,AutoSizeMode=AutoSizeMode.GrowAndShrink,Padding=new Padding(28,22,28,22),ColumnCount=1,RowCount=8,Margin=new Padding(0)};
            root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));
            for(int row=0;row<8;row++)root.RowStyles.Add(new RowStyle(row==6?SizeType.Percent:SizeType.AutoSize,row==6?100:0));
            viewport.Controls.Add(root); viewport.ClientSizeChanged+=(s,e)=>root.MinimumSize=new Size(0,viewport.ClientSize.Height);
            root.SizeChanged+=(s,e)=>viewport.AutoScrollMinSize=new Size(0,root.Height);
            root.Controls.Add(new Label {Name="title",Text="潮汐改卡  /  电脑激活",AutoSize=true,Font=new Font(Font.FontFamily,20,FontStyle.Bold),ForeColor=Color.FromArgb(22,60,68),Dock=DockStyle.Fill,Margin=new Padding(0,0,0,10)},0,0);
            root.Controls.Add(new Label {Name="subtitle",Text="USB 连接 · 无需无线配对码或 IP · "+BundleInfo.VersionName+(BundleInfo.VersionName.Contains("-rc")?" 测试版":" 正式版")+"\r\n电脑工具 "+DesktopInfo.Version,AutoSize=true,ForeColor=Color.FromArgb(8,127,140),Dock=DockStyle.Fill,Margin=new Padding(0,0,0,16)},0,1);
            root.Controls.Add(new Label {Name="instructions",Text="① 手机开启「USB 调试」，用数据线连接电脑。\r\n② 解锁手机，在「允许 USB 调试」弹窗中允许这台电脑。\r\n③ 选择手机并点击下方按钮；手机提示 USB 安装时手动允许。",AutoSize=true,Dock=DockStyle.Fill,Margin=new Padding(0,0,0,18)},0,2);
            TableLayoutPanel pick=new TableLayoutPanel {Name="devicePicker",Dock=DockStyle.Fill,AutoSize=true,AutoSizeMode=AutoSizeMode.GrowAndShrink,ColumnCount=2,RowCount=1,Margin=new Padding(0,0,0,12)}; pick.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100)); pick.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));pick.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            devices.Name="devices";devices.DropDownStyle=ComboBoxStyle.DropDownList; devices.Dock=DockStyle.Top; devices.Margin=new Padding(0,4,12,4); refresh.Name="refresh";refresh.Text="刷新设备"; refresh.AutoSize=true;refresh.AutoSizeMode=AutoSizeMode.GrowAndShrink;refresh.Padding=new Padding(12,6,12,6);refresh.Margin=new Padding(0);refresh.Dock=DockStyle.Fill; refresh.Click+=async (s,e)=>await RefreshDevices(); pick.Controls.Add(devices,0,0); pick.Controls.Add(refresh,1,0); root.Controls.Add(pick,0,3);
            activate.Name="activate";activate.Text="安装 / 更新并激活";activate.AutoSize=true;activate.AutoSizeMode=AutoSizeMode.GrowAndShrink;activate.Padding=new Padding(14,10,14,10); activate.Dock=DockStyle.Fill; activate.FlatStyle=FlatStyle.Flat; activate.FlatAppearance.BorderSize=0; activate.BackColor=Color.FromArgb(8,127,140); activate.ForeColor=Color.White; activate.Font=new Font(Font.FontFamily,13,FontStyle.Bold); activate.Margin=new Padding(0,0,0,14); activate.Click+=async (s,e)=>await ActivateSelected(); root.Controls.Add(activate,0,4);
            state.Name="status";state.Text="正在检测 USB 设备…";state.AutoSize=true; state.Dock=DockStyle.Fill; state.BackColor=Color.FromArgb(226,244,239); state.ForeColor=Color.FromArgb(22,60,68); state.Padding=new Padding(14,12,14,12); state.Margin=new Padding(0,0,0,14); root.Controls.Add(state,0,5);
            logs.Name="logs";logs.TabStop=false;logs.Multiline=true; logs.ReadOnly=true; logs.ScrollBars=ScrollBars.Vertical; logs.Dock=DockStyle.Fill;logs.MinimumSize=new Size(0,140); logs.BackColor=Color.White; logs.BorderStyle=BorderStyle.FixedSingle; logs.Margin=new Padding(0); root.Controls.Add(logs,0,6);
            FlowLayoutPanel footer=new FlowLayoutPanel {Name="footer",Dock=DockStyle.Fill,AutoSize=true,AutoSizeMode=AutoSizeMode.GrowAndShrink,FlowDirection=FlowDirection.LeftToRight,WrapContents=true,Margin=new Padding(0,12,0,0)};
            Button help=new Button {Name="help",Text="连接帮助",AutoSize=true,AutoSizeMode=AutoSizeMode.GrowAndShrink,Padding=new Padding(12,6,12,6),Margin=new Padding(0,0,8,0)}; help.Click+=(s,e)=>MessageBox.Show(this,"没有设备：换用可传输数据的数据线、换 USB 接口，确认已开启 USB 调试；华为 / 鸿蒙 4.x 请点“连接诊断 / 华为”，检查 ADB 接口并从华为官网获取 USB 驱动；其它手机使用其厂商官方驱动。\r\n\r\n待授权：解锁手机，允许这台电脑进行 USB 调试。若没有弹窗，可重新插线，或在开发者选项撤销旧 USB 调试授权后重新连接。\r\n\r\n安装被拒绝：查看手机上的 USB 安装提示和开发者选项中的 USB 安装设置。工具不会卸载应用或清除数据。\r\n\r\n激活成功后可以拔线。重启或系统终止后台后可能需要再次激活。本工具不提供 VPN，后台就绪也不代表系统允许修改地区。\r\n\r\n本工具仅在本机调用随附 ADB，不上传信息。APK 沿用原签名；Windows EXE 尚无代码签名。","连接帮助",MessageBoxButtons.OK,MessageBoxIcon.Information);
            Button readme=new Button {Name="readme",Text="打开说明",AutoSize=true,AutoSizeMode=AutoSizeMode.GrowAndShrink,Padding=new Padding(12,6,12,6),Margin=new Padding(0)}; readme.Click+=(s,e)=>{string p=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"使用说明.txt");if(File.Exists(p))System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(p){UseShellExecute=true});};
            diagnostic.Name="diagnostics";diagnostic.Text="连接诊断 / 华为";diagnostic.AutoSize=true;diagnostic.AutoSizeMode=AutoSizeMode.GrowAndShrink;diagnostic.Padding=new Padding(12,6,12,6);diagnostic.Margin=new Padding(8,0,0,0);diagnostic.Click+=async (s,e)=>await Diagnose();
            footer.Controls.Add(help);footer.Controls.Add(readme);footer.Controls.Add(diagnostic);root.Controls.Add(footer,0,7);
            Shown+=(s,e)=>FitToWorkingArea();DpiChanged+=(s,e)=>{if(IsHandleCreated)BeginInvoke(new Action(FitToWorkingArea));};
            if(autoDiscover)Shown+=async (s,e)=>await RefreshDevices(); FormClosing+=(s,e)=>{if(working){e.Cancel=true;MessageBox.Show(this,"检测、安装或激活仍在进行，请等待结果后关闭窗口。","正在操作");}};
            ResumeLayout(true);
        }
        private void FitToWorkingArea() {
            if(IsDisposed||WindowState!=FormWindowState.Normal)return;
            Rectangle area=Screen.FromControl(this).WorkingArea;int edge=LogicalToDeviceUnits(12);
            int width=Math.Max(200,area.Width-edge*2),height=Math.Max(200,area.Height-edge*2);
            MinimumSize=new Size(Math.Min(LogicalToDeviceUnits(600),width),Math.Min(LogicalToDeviceUnits(440),height));
            Size size=new Size(Math.Min(Width,width),Math.Min(Height,height));
            Location=new Point(Math.Max(area.Left+edge,Math.Min(Left,area.Right-edge-size.Width)),Math.Max(area.Top+edge,Math.Min(Top,area.Bottom-edge-size.Height)));Size=size;
        }
        private void Log(string text) { if(InvokeRequired) { BeginInvoke(new Action<string>(Log),text); return; } logs.AppendText(DateTime.Now.ToString("HH:mm:ss")+"  "+text+Environment.NewLine); }
        private void Busy(bool value) {working=value;refresh.Enabled=!value;devices.Enabled=!value;activate.Enabled=!value;diagnostic.Enabled=!value;}
        private async Task RefreshDevices() {
            Busy(true);string previous=(devices.SelectedItem as Device)==null?null:((Device)devices.SelectedItem).Serial;
            try {List<Device> list=await Task.Run(()=>workflow.Devices());devices.Items.Clear();foreach(Device d in list)devices.Items.Add(d);
                int selected=list.FindIndex(d=>d.Serial==previous);if(selected>=0)devices.SelectedIndex=selected;else if(list.Count==1)devices.SelectedIndex=0;
                state.Text=list.Count==0?"未发现 ADB 调试设备。能打开手机存储不代表 ADB 已连接；请点“连接诊断 / 华为”检查接口和驱动。":list.Count>1&&selected<0?"检测到多台手机，请明确选择要激活的设备。":((Device)devices.SelectedItem).State=="unauthorized"?"请解锁手机并允许 USB 调试，再点击刷新。":"设备列表已更新。请选择目标手机后安装并激活。";
            }catch(Exception e){state.Text="检测未完成";Log(e.Message);}finally{Busy(false);}
        }
        private async Task Diagnose() {
            Busy(true);state.Text="正在检查 ADB 列表与 Windows 华为 USB 接口…";
            try {Diagnosis result=await Task.Run(()=>ConnectionDiagnostics.Read(adb,new WindowsUsbInventory(),adb.Runtime()));state.Text=result.Summary;using(DiagnosticDialog dialog=new DiagnosticDialog(result))dialog.ShowDialog(this);}
            catch(Exception){state.Text="诊断未完成，请打开设备管理器检查 USB 接口与驱动。";}finally{Busy(false);}
        }
        private async Task ActivateSelected() {
            Device d=devices.SelectedItem as Device;if(d==null){state.Text="请先选择一台 USB 手机。";return;}
            Busy(true);state.Text="正在安装和激活，请保持手机解锁并留意安装提示…";
            try {await Task.Run(()=>workflow.Activate(d.Serial,Path.Combine(AppDomain.CurrentDomain.BaseDirectory,BundleInfo.ApkName)));state.Text="✓ 后台已激活，可以拔线\r\n请在手机上返回改卡界面，选择地区并开始。";Log("完成。尚未替你开始改卡；请在手机端操作。");}
            catch(Exception e){state.Text="尚未完成激活，请查看下方提示。";Log(e.Message);}finally{Busy(false);}
        }
    }
}
