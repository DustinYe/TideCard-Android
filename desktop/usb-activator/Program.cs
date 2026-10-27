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
        private readonly ComboBox devices=new ComboBox(); private readonly Button refresh=new Button(), activate=new Button();
        private readonly Label state=new Label(); private readonly TextBox logs=new TextBox(); private readonly Workflow workflow;
        private bool working;
        internal MainForm() : this(true) {}
        internal MainForm(bool autoDiscover) {
            Text="潮汐改卡 · USB 激活工具"; StartPosition=FormStartPosition.CenterScreen; AutoScaleMode=AutoScaleMode.Dpi;
            ClientSize=new Size(720,650); MinimumSize=new Size(670,650); BackColor=Color.FromArgb(243,247,247); Font=new Font("Microsoft YaHei UI",10);
            workflow=new Workflow(new Adb(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"adb","adb.exe")),Log);
            TableLayoutPanel root=new TableLayoutPanel { Dock=DockStyle.Fill, Padding=new Padding(28,20,28,22), ColumnCount=1, RowCount=8 };
            root.RowStyles.Add(new RowStyle(SizeType.Absolute,40)); root.RowStyles.Add(new RowStyle(SizeType.Absolute,35)); root.RowStyles.Add(new RowStyle(SizeType.Absolute,102)); root.RowStyles.Add(new RowStyle(SizeType.Absolute,48)); root.RowStyles.Add(new RowStyle(SizeType.Absolute,57)); root.RowStyles.Add(new RowStyle(SizeType.Absolute,68)); root.RowStyles.Add(new RowStyle(SizeType.Percent,100)); root.RowStyles.Add(new RowStyle(SizeType.Absolute,52)); Controls.Add(root);
            root.Controls.Add(new Label {Text="潮汐改卡  /  电脑激活",Font=new Font(Font.FontFamily,20,FontStyle.Bold),ForeColor=Color.FromArgb(22,60,68),Dock=DockStyle.Fill},0,0);
            root.Controls.Add(new Label {Text="USB 连接 · 无需无线配对码或 IP · "+BundleInfo.VersionName+" 测试版",ForeColor=Color.FromArgb(8,127,140),Dock=DockStyle.Fill,TextAlign=ContentAlignment.MiddleLeft},0,1);
            root.Controls.Add(new Label {Text="① 手机开启「USB 调试」，用数据线连接电脑。\r\n② 解锁手机，在「允许 USB 调试」弹窗中允许这台电脑。\r\n③ 选择手机并点击下方按钮；手机提示 USB 安装时手动允许。",Dock=DockStyle.Fill,Padding=new Padding(0,8,0,0)},0,2);
            TableLayoutPanel pick=new TableLayoutPanel { Dock=DockStyle.Fill,ColumnCount=2,RowCount=1,Margin=new Padding(0,2,0,8) }; pick.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100)); pick.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute,94));
            devices.DropDownStyle=ComboBoxStyle.DropDownList; devices.Dock=DockStyle.Fill; devices.Margin=new Padding(0,3,10,0); refresh.Text="刷新设备"; refresh.Dock=DockStyle.Fill; refresh.Click+=async (s,e)=>await RefreshDevices(); pick.Controls.Add(devices,0,0); pick.Controls.Add(refresh,1,0); root.Controls.Add(pick,0,3);
            activate.Text="安装 / 更新并激活"; activate.Dock=DockStyle.Fill; activate.FlatStyle=FlatStyle.Flat; activate.FlatAppearance.BorderSize=0; activate.BackColor=Color.FromArgb(8,127,140); activate.ForeColor=Color.White; activate.Font=new Font(Font.FontFamily,13,FontStyle.Bold); activate.Margin=new Padding(0,0,0,8); activate.Click+=async (s,e)=>await ActivateSelected(); root.Controls.Add(activate,0,4);
            state.Text="正在检测 USB 设备…"; state.Dock=DockStyle.Fill; state.BackColor=Color.FromArgb(226,244,239); state.ForeColor=Color.FromArgb(22,60,68); state.Padding=new Padding(12,8,8,4); state.Margin=new Padding(0,3,0,12); root.Controls.Add(state,0,5);
            logs.Multiline=true; logs.ReadOnly=true; logs.ScrollBars=ScrollBars.Vertical; logs.Dock=DockStyle.Fill; logs.BackColor=Color.White; logs.BorderStyle=BorderStyle.FixedSingle; logs.Margin=new Padding(0); root.Controls.Add(logs,0,6);
            FlowLayoutPanel footer=new FlowLayoutPanel {Dock=DockStyle.Fill,FlowDirection=FlowDirection.LeftToRight,Padding=new Padding(0,8,0,0)};
            Button help=new Button {Text="连接帮助",AutoSize=true}; help.Click+=(s,e)=>MessageBox.Show(this,"没有设备：换用可传输数据的数据线、换 USB 接口，确认已开启 USB 调试；必要时安装 vivo 官方 USB 驱动。\r\n\r\n待授权：解锁手机，允许这台电脑进行 USB 调试。若没有弹窗，可重新插线，或在开发者选项撤销旧 USB 调试授权后重新连接。\r\n\r\n安装被拒绝：查看手机上的 USB 安装提示和开发者选项中的 USB 安装设置。工具不会卸载应用或清除数据。\r\n\r\n激活成功后可以拔线。重启或系统终止后台后可能需要再次激活。本工具不提供 VPN，后台就绪也不代表系统允许修改地区。\r\n\r\n本工具仅在本机调用随附 ADB，不上传信息。APK 沿用原签名；Windows EXE 尚无代码签名。","连接帮助",MessageBoxButtons.OK,MessageBoxIcon.Information);
            Button readme=new Button {Text="打开说明",AutoSize=true}; readme.Click+=(s,e)=>{string p=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"使用说明.txt");if(File.Exists(p))System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(p){UseShellExecute=true});};
            footer.Controls.Add(help);footer.Controls.Add(readme);root.Controls.Add(footer,0,7);
            if(autoDiscover)Shown+=async (s,e)=>await RefreshDevices(); FormClosing+=(s,e)=>{if(working){e.Cancel=true;MessageBox.Show(this,"安装或激活仍在进行，请等待结果后关闭窗口。","正在操作");}};
        }
        private void Log(string text) { if(InvokeRequired) { BeginInvoke(new Action<string>(Log),text); return; } logs.AppendText(DateTime.Now.ToString("HH:mm:ss")+"  "+text+Environment.NewLine); }
        private void Busy(bool value) {working=value;refresh.Enabled=!value;devices.Enabled=!value;activate.Enabled=!value;}
        private async Task RefreshDevices() {
            Busy(true);string previous=(devices.SelectedItem as Device)==null?null:((Device)devices.SelectedItem).Serial;
            try {List<Device> list=await Task.Run(()=>workflow.Devices());devices.Items.Clear();foreach(Device d in list)devices.Items.Add(d);
                int selected=list.FindIndex(d=>d.Serial==previous);if(selected>=0)devices.SelectedIndex=selected;else if(list.Count==1)devices.SelectedIndex=0;
                state.Text=list.Count==0?"未检测到 USB 手机。请检查数据线、USB 调试和驱动。":list.Count>1&&selected<0?"检测到多台手机，请明确选择要激活的设备。":((Device)devices.SelectedItem).State=="unauthorized"?"请解锁手机并允许 USB 调试，再点击刷新。":"设备列表已更新。请选择目标手机后安装并激活。";
            }catch(Exception e){state.Text="检测未完成";Log(e.Message);}finally{Busy(false);}
        }
        private async Task ActivateSelected() {
            Device d=devices.SelectedItem as Device;if(d==null){state.Text="请先选择一台 USB 手机。";return;}
            Busy(true);state.Text="正在安装和激活，请保持手机解锁并留意安装提示…";
            try {await Task.Run(()=>workflow.Activate(d.Serial,Path.Combine(AppDomain.CurrentDomain.BaseDirectory,BundleInfo.ApkName)));state.Text="✓ 后台已激活，可以拔线\r\n请在手机上返回改卡界面，选择地区并开始。";Log("完成。尚未替你开始改卡；请在手机端操作。");}
            catch(Exception e){state.Text="尚未完成激活，请查看下方提示。";Log(e.Message);}finally{Busy(false);}
        }
    }
}
