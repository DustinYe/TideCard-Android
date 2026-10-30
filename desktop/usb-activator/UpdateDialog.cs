using System;
using System.Drawing;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace TideCardUsb {
    internal sealed class UpdateDialog : Form {
        private readonly Label status=new Label();private readonly Button action=new Button(),cancel=new Button();private readonly ProgressBar progress=new ProgressBar();
        private UsbRelease release;private CancellationTokenSource cancellation;private bool busy;
        internal string PreparedWork;
        internal UpdateDialog(bool autoCheck=true) {
            Text="电脑工具更新";StartPosition=FormStartPosition.CenterParent;AutoScaleDimensions=new SizeF(96,96);AutoScaleMode=AutoScaleMode.Dpi;ClientSize=new Size(500,320);MinimumSize=new Size(360,260);Font=new Font("Microsoft YaHei UI",10);BackColor=Color.FromArgb(243,247,247);
            Panel viewport=new Panel {Name="updateViewport",Dock=DockStyle.Fill,AutoScroll=true};Controls.Add(viewport);
            TableLayoutPanel root=new TableLayoutPanel {Name="updateContent",Dock=DockStyle.Top,AutoSize=true,AutoSizeMode=AutoSizeMode.GrowAndShrink,ColumnCount=1,RowCount=5,Padding=new Padding(22)};root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent,100));root.RowStyles.Add(new RowStyle(SizeType.AutoSize));root.RowStyles.Add(new RowStyle(SizeType.AutoSize));root.RowStyles.Add(new RowStyle(SizeType.AutoSize));root.RowStyles.Add(new RowStyle(SizeType.AutoSize));root.RowStyles.Add(new RowStyle(SizeType.AutoSize));viewport.Controls.Add(root);
            root.Controls.Add(new Label {Text="潮汐改卡 USB · "+DesktopInfo.Version,AutoSize=true,Dock=DockStyle.Fill,Font=new Font(Font.FontFamily,15,FontStyle.Bold),Margin=new Padding(0,0,0,12)},0,0);
            status.Name="updateStatus";action.Name="updateAction";cancel.Name="updateClose";status.Text="点击检查 GitHub 上的电脑工具正式版。\r\n更新电脑工具不会在手机上安装或激活应用。";status.AutoSize=true;status.Dock=DockStyle.Fill;status.Margin=new Padding(0,0,0,12);root.Controls.Add(status,0,1);
            progress.Dock=DockStyle.Fill;progress.Margin=new Padding(0,0,0,12);root.Controls.Add(progress,0,2);
            action.Text="检查更新";action.AutoSize=true;action.Dock=DockStyle.Fill;action.Padding=new Padding(8);action.Margin=new Padding(0,0,0,8);action.Click+=async (s,e)=>{if(release==null)await Check();else await Download();};root.Controls.Add(action,0,3);
            cancel.Text="关闭";cancel.AutoSize=true;cancel.Dock=DockStyle.Fill;cancel.Click+=(s,e)=>{if(busy){cancellation.Cancel();cancel.Enabled=false;status.Text="正在取消，请稍候…";}else Close();};root.Controls.Add(cancel,0,4);
            Shown+=(s,e)=>{Rectangle area=Screen.FromControl(this).WorkingArea;MinimumSize=new Size(Math.Min(MinimumSize.Width,area.Width-24),Math.Min(MinimumSize.Height,area.Height-24));Size=new Size(Math.Min(Width,area.Width-24),Math.Min(Height,area.Height-24));};
            if(autoCheck)Shown+=async (s,e)=>await Check();
            FormClosing+=(s,e)=>{if(busy){e.Cancel=true;cancellation.Cancel();cancel.Enabled=false;status.Text="正在取消，请稍候…";}};
        }
        private void SetBusy(bool value) {busy=value;action.Enabled=!value;cancel.Enabled=true;cancel.Text=value?"取消":"关闭";if(value)cancellation=new CancellationTokenSource();else if(cancellation!=null){cancellation.Dispose();cancellation=null;}}
        private async Task Check() {
            SetBusy(true);progress.Style=ProgressBarStyle.Marquee;status.Text="正在检查 GitHub 电脑工具正式版…";
            try {UsbRelease next=await Task.Run(()=>UsbUpdates.FindLatest(cancellation.Token));if(next==null)throw new InvalidOperationException("尚未找到可验证的电脑工具正式版。");
                if(next.Version<=UsbUpdates.ParseVersion(DesktopInfo.Version)){status.Text="当前已是最新电脑工具（"+DesktopInfo.Version+"）。\r\n配套 APK："+BundleInfo.VersionName;release=null;}
                else {release=next;action.Text="下载、更新并重启工具";status.Text="发现电脑工具 "+next.Version+"（当前 "+DesktopInfo.Version+"）\r\n下载大小："+(next.Size/1048576.0).ToString("0.0")+" MB\r\n将核对 GitHub SHA-256，更新当前解压目录内的工具、ADB 和配套 APK，然后重启窗口。手机上的应用和改卡状态保持原样。新版仍可能显示 Windows 安全确认。";}
            }catch(OperationCanceledException){status.Text="检查已取消，当前工具未改动。";}catch(Exception e){status.Text=e is InvalidOperationException?e.Message:"检查更新失败，请稍后重试。";}finally{progress.Style=ProgressBarStyle.Blocks;SetBusy(false);}
        }
        private async Task Download() {
            try {UpdateInstaller.Writable(AppDomain.CurrentDomain.BaseDirectory);}catch(Exception){status.Text="当前工具目录不可写。请将完整工具包移动到自己的“下载”或“文档”文件夹后重试。";return;}
            SetBusy(true);progress.Value=0;status.Text="正在下载并校验更新…";IProgress<int> report=new Progress<int>(value=>{if(!IsDisposed)progress.Value=value;});
            try {PreparedWork=await Task.Run(()=>UsbUpdates.Prepare(release,cancellation.Token,value=>report.Report(value)));}
            catch(OperationCanceledException){status.Text="下载已取消，当前工具未改动。";}catch(Exception e){status.Text=e is InvalidOperationException?e.Message:"下载或准备更新失败，当前工具未改动。请重新检查更新或从 GitHub 下载完整工具包。";}
            finally{SetBusy(false);}if(PreparedWork!=null){DialogResult=DialogResult.OK;Close();}
        }
    }
}
