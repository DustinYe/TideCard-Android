using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Windows.Forms;

namespace TideCardUsb {
    /** Own-form rendering fixtures; they do not change Windows display settings or start ADB. */
    internal static class LayoutChecks {
        private static int assertions;
        private static void Require(bool ok,string reason) { assertions++;if(!ok)throw new InvalidOperationException("Layout: "+reason); }
        private static IEnumerable<Control> Descendants(Control control) { yield return control;foreach(Control child in control.Controls)foreach(Control next in Descendants(child))yield return next; }
        private static void Settle(Form form) { for(int pass=0;pass<3;pass++) { List<Control> controls=new List<Control>(Descendants(form));for(int i=controls.Count-1;i>=0;i--)controls[i].PerformLayout();Application.DoEvents(); } }
        private static void Validate(MainForm form,string profile) {
            foreach(Control control in Descendants(form)) {
                if(!(control is Label)&&!(control is Button))continue;
                Size preferred=control.GetPreferredSize(new Size(control.Width,0));
                Require(control.Height+1>=preferred.Height,profile+" clipped "+control.Name+": actual="+control.Height+", preferred="+preferred.Height);
                Require(control.Width>0&&control.Height>0,profile+" hidden control "+control.Name);
                Require(control.Left>=0&&control.Top>=0&&control.Right<=control.Parent.ClientSize.Width+1&&control.Bottom<=control.Parent.ClientSize.Height+1,profile+" parent clips "+control.Name);
            }
            Panel viewport=(Panel)form.Controls.Find("viewport",true)[0];Control content=form.Controls.Find("content",true)[0],footer=form.Controls.Find("footer",true)[0];
            if(content.Height>viewport.ClientSize.Height+1) {
                Require(viewport.VerticalScroll.Visible,profile+" missing scroll fallback");
                // Model keyboard access to the footer. Keeping focus in the large log
                // textbox would make a forced layout scroll that field back into view.
                form.Controls.Find("readme",true)[0].Focus();
                viewport.AutoScrollPosition=new Point(0,viewport.VerticalScroll.Maximum);Settle(form);
                Rectangle rect=new Rectangle(footer.Left+content.Left,footer.Top+content.Top,footer.Width,footer.Height);
                Require(rect.Bottom<=viewport.ClientSize.Height+1&&rect.Top>=0,profile+" footer not reachable by scrolling: footer="+rect+", viewport="+viewport.ClientSize+", content="+content.Bounds+", scroll="+viewport.AutoScrollPosition+", max="+viewport.VerticalScroll.Maximum+", large="+viewport.VerticalScroll.LargeChange+", min="+viewport.AutoScrollMinSize);
                form.Controls.Find("devices",true)[0].Focus();viewport.AutoScrollPosition=Point.Empty;Settle(form);
            }
        }
        private static void Render(Form form,string path) { using(Bitmap bitmap=new Bitmap(form.Width,form.Height)) { form.DrawToBitmap(bitmap,new Rectangle(Point.Empty,bitmap.Size));bitmap.Save(path); } }
        internal static void Run(string outputDirectory) {
            Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);Directory.CreateDirectory(outputDirectory);
            using(ContextMenuStrip menu=DiagnosticDialog.CreateHelpMenu(null)) {
                Require(menu.Items.Count==4,"OEM help options");Require(menu.Items[0].Text.Contains("OPPO")&&new Uri((string)menu.Items[0].Tag).Host=="support.oppo.com","OPPO official help comes first");Require(menu.Items[3].Text.Contains("华为专用"),"HiSuite clearly scoped to Huawei");
            }
            List<string> report=new List<string>();int nativeDpi;
            using(MainForm form=new MainForm(false)) { form.Show();Settle(form);nativeDpi=form.DeviceDpi;Validate(form,"native");Render(form,Path.Combine(outputDirectory,"desktop-preview.png"));report.Add("Actual Windows form DPI: "+nativeDpi); }
            int profiles=0;
            foreach(int dpi in new[]{96,120,144,168,192,216,240})foreach(bool narrow in new[]{false,true}) {
                using(MainForm form=new MainForm(false)) {
                    form.Show();Settle(form);float factor=(float)dpi/form.DeviceDpi;
                    // Synthetic client DPI: both geometry and point sizes change. The real
                    // monitor stays unchanged, so point sizes compensate for its actual DPI.
                    List<Control> controls=new List<Control>(Descendants(form));List<Font> original=new List<Font>(),scaled=new List<Font>();
                    foreach(Control control in controls) { original.Add(control.Font);control.SuspendLayout(); }
                    form.AutoScaleMode=AutoScaleMode.None;form.MinimumSize=Size.Empty;form.Scale(new SizeF(factor,factor));
                    for(int i=0;i<controls.Count;i++) { Font font=new Font(original[i].FontFamily,original[i].SizeInPoints*factor,original[i].Style,GraphicsUnit.Point);scaled.Add(font);controls[i].Font=font; }
                    form.ClientSize=new Size((int)Math.Round((narrow?520:740)*dpi/96.0),(int)Math.Round((narrow?400:650)*dpi/96.0));
                    for(int i=controls.Count-1;i>=0;i--)controls[i].ResumeLayout(true);Settle(form);
                    Label state=(Label)form.Controls.Find("status",true)[0];string profile=dpi+"dpi-"+(narrow?"narrow":"normal");
                    foreach(string value in new[]{"未发现 ADB 调试设备。能打开手机存储不代表 ADB 已连接；请点“连接诊断”检查接口和驱动。","✓ 后台已激活，可以拔线\r\n请在手机上返回改卡界面，选择地区并开始。","请解锁手机并允许这台电脑进行 USB 调试，然后点击刷新；若系统要求确认 USB 安装，请在手机上手动允许本次安装。"}) {state.Text=value;Settle(form);Validate(form,profile);}
                    state.Text="未发现 ADB 调试设备。能打开手机存储不代表 ADB 已连接；请点“连接诊断”检查接口和驱动。";Settle(form);Render(form,Path.Combine(outputDirectory,"layout-"+profile+".png"));
                    report.Add("PASS synthetic "+profile+" client="+form.ClientSize.Width+"x"+form.ClientSize.Height);profiles++;
                    form.Close();foreach(Font font in scaled)font.Dispose();
                }
            }
            int dialogs=0;
            foreach(int dpi in new[]{96,120,144,168,192,216,240})foreach(bool narrow in new[]{false,true}) {
                Diagnosis result=ConnectionDiagnostics.Analyze(new AdbResult(0,"List of devices attached\n"),new UsbSnapshot {Available=true,Nodes=new List<UsbNode>{new UsbNode {DeviceId=@"USB\VID_22D9&PID_2765&MI_01",Name="OPPO MTP",ProblemCode=0}}});
                if(narrow) {
                    string missing=Path.Combine(outputDirectory,"missing-layout-fixture","adb.exe");Require(!File.Exists(missing),"missing client fixture must not execute a real program");
                    AdbProbe failure=AdbProbe.Run(new Adb(missing),"ADB 客户端自检",new[]{"version"},"Android Debug Bridge version",1000);
                    result=ConnectionDiagnostics.Analyze(failure.Result,new UsbSnapshot {Available=true},"",failure,new AdbProbe {Stage="ADB 设备查询"},AdbRuntime.Inspect(missing));
                }
                using(DiagnosticDialog dialog=new DiagnosticDialog(result)) {
                    dialog.Show();Settle(dialog);float factor=(float)dpi/dialog.DeviceDpi;
                    List<Control> controls=new List<Control>(Descendants(dialog));List<Font> original=new List<Font>(),scaled=new List<Font>();
                    foreach(Control control in controls){original.Add(control.Font);control.SuspendLayout();}
                    dialog.AutoScaleMode=AutoScaleMode.None;dialog.MinimumSize=Size.Empty;dialog.Scale(new SizeF(factor,factor));
                    for(int i=0;i<controls.Count;i++){Font font=new Font(original[i].FontFamily,original[i].SizeInPoints*factor,original[i].Style,GraphicsUnit.Point);scaled.Add(font);controls[i].Font=font;}
                    dialog.ClientSize=new Size((int)Math.Round((narrow?400:720)*dpi/96.0),(int)Math.Round((narrow?320:570)*dpi/96.0));
                    for(int i=controls.Count-1;i>=0;i--)controls[i].ResumeLayout(true);Settle(dialog);
                    string profile=dpi+"dpi-"+(narrow?"narrow":"normal");
                    foreach(Control control in Descendants(dialog))if(control is Label||control is Button) {
                        Require(control.Height+1>=control.GetPreferredSize(new Size(control.Width,0)).Height,profile+" diagnostic text clipped: "+control.Text);
                        Require(control.Left>=0&&control.Top>=0&&control.Right<=control.Parent.ClientSize.Width+1&&control.Bottom<=control.Parent.ClientSize.Height+1,profile+" diagnostic button bounds: "+control.Text);
                    }
                    Control reportBox=dialog.Controls.Find("diagnosisReport",true)[0];Require(reportBox.Height>=40*dpi/96,profile+" diagnostic report not usable");
                    Render(dialog,Path.Combine(outputDirectory,"diagnostic-"+profile+".png"));report.Add("PASS synthetic diagnostic "+profile+" client="+dialog.ClientSize.Width+"x"+dialog.ClientSize.Height);dialogs++;
                    dialog.Close();foreach(Font font in scaled)font.Dispose();
                }
            }
            int updateDialogs=0;
            foreach(int dpi in new[]{96,120,144,168,192,216,240})foreach(bool narrow in new[]{false,true}) {
                using(UpdateDialog dialog=new UpdateDialog(false)) {
                    dialog.Show();Settle(dialog);float factor=(float)dpi/dialog.DeviceDpi;
                    List<Control> controls=new List<Control>(Descendants(dialog));List<Font> fonts=new List<Font>(),originalFonts=new List<Font>();foreach(Control control in controls)originalFonts.Add(control.Font);
                    foreach(Control control in controls)control.SuspendLayout();dialog.AutoScaleMode=AutoScaleMode.None;dialog.MinimumSize=Size.Empty;dialog.Scale(new SizeF(factor,factor));
                    for(int i=0;i<controls.Count;i++){Font font=new Font(originalFonts[i].FontFamily,originalFonts[i].SizeInPoints*factor,originalFonts[i].Style);fonts.Add(font);controls[i].Font=font;}
                    dialog.ClientSize=new Size((int)Math.Round((narrow?340:500)*dpi/96.0),(int)Math.Round((narrow?260:360)*dpi/96.0));
                    dialog.Controls.Find("updateStatus",true)[0].Text="发现电脑工具 1.1.8（当前 1.1.7）\r\n下载大小：10.4 MB\r\n将核对 GitHub SHA-256，更新当前解压目录内的工具、ADB 和配套 APK，然后重启窗口。手机上的应用和改卡状态保持原样。";dialog.Controls.Find("updateAction",true)[0].Text="下载、更新并重启工具";
                    for(int i=controls.Count-1;i>=0;i--)controls[i].ResumeLayout(true);Settle(dialog);
                    string profile=dpi+"dpi-"+(narrow?"narrow":"normal");
                    foreach(Control control in controls)if(control is Label||control is Button){Require(control.Height+1>=control.GetPreferredSize(new Size(control.Width,0)).Height,profile+" update text clipped: "+control.Name);Require(control.Right<=control.Parent.ClientSize.Width+1&&control.Left>=0,profile+" update bounds: "+control.Name);}
                    Panel viewport=(Panel)dialog.Controls.Find("updateViewport",true)[0];Control close=dialog.Controls.Find("updateClose",true)[0];close.Focus();viewport.ScrollControlIntoView(close);Settle(dialog);Rectangle rect=viewport.RectangleToClient(close.RectangleToScreen(close.ClientRectangle));Require(rect.Top>=0&&rect.Bottom<=viewport.ClientSize.Height,profile+" update actions unreachable");
                    Render(dialog,Path.Combine(outputDirectory,"update-"+profile+".png"));report.Add("PASS synthetic update "+profile);updateDialogs++;dialog.Close();foreach(Font font in fonts)font.Dispose();
                }
            }
            report.Add("PASS "+profiles+" main, "+dialogs+" diagnostic and "+updateDialogs+" update synthetic layouts; "+assertions+" bounds, preferred text height and scroll reachability assertions. Windows scale settings unchanged.");
            File.WriteAllLines(Path.Combine(outputDirectory,"layout-checks.txt"),report);Console.WriteLine(string.Join(Environment.NewLine,report));
        }
    }
}
