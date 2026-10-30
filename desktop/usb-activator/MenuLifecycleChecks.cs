using System;
using System.Collections.Generic;
using System.Windows.Forms;

namespace TideCardUsb {
    internal static class MenuLifecycleChecks {
        private static int assertions;
        private static void Require(bool ok,string reason){assertions++;if(!ok)throw new InvalidOperationException("Help menu: "+reason);}
        internal static void Run() {
            Diagnosis result=ConnectionDiagnostics.Analyze(new AdbResult(0,"List of devices attached\n"),new UsbSnapshot {Available=true});
            ContextMenuStrip previous=null;
            for(int window=0;window<2;window++) {
                List<string> opened=new List<string>();ContextMenuStrip menu;List<ToolStripItem> items=new List<ToolStripItem>();
                using(DiagnosticDialog dialog=new DiagnosticDialog(result,opened.Add)) {
                    dialog.Show();Application.DoEvents();Button button=(Button)dialog.Controls.Find("vendorHelp",true)[0];menu=button.ContextMenuStrip;
                    Require(menu!=null&&!menu.IsDisposed,"window owns a live menu");Require(!object.ReferenceEquals(menu,previous),"new window gets a new menu");foreach(ToolStripItem item in menu.Items)items.Add(item);
                    for(int pass=0;pass<5;pass++)foreach(ToolStripDropDownCloseReason reason in new[]{ToolStripDropDownCloseReason.AppClicked,ToolStripDropDownCloseReason.Keyboard,ToolStripDropDownCloseReason.ItemClicked,ToolStripDropDownCloseReason.CloseCalled,ToolStripDropDownCloseReason.AppFocusChange}) {
                        button.PerformClick();Require(menu.Visible&&!menu.IsDisposed,"reopen after "+reason);menu.Close(reason);Application.DoEvents();Require(!menu.Visible&&!menu.IsDisposed,"close retains reusable menu: "+reason);Require(object.ReferenceEquals(menu,button.ContextMenuStrip),"same window reuses its menu");
                    }
                    for(int i=0;i<items.Count;i++) {
                        button.PerformClick();((ToolStripMenuItem)items[i]).PerformClick();menu.Close(ToolStripDropDownCloseReason.ItemClicked);Application.DoEvents();
                        Require(opened.Count==i+1&&opened[i]==(string)items[i].Tag,"selected help target routed exactly once");Require(!menu.IsDisposed,"selection does not dispose the menu");
                    }
                    Require(opened.Count==4,"dismissal never opens a browser");button.PerformClick();Require(menu.Visible,"menu visible during owner teardown");
                    // Exercise both closing a window and directly disposing a visible menu owner.
                    if(window==0)dialog.Close();else dialog.Dispose();
                }
                Require(menu.IsDisposed,"menu released with its window");foreach(ToolStripItem item in items)Require(item.IsDisposed,"menu item released with its window");previous=menu;
            }
            Console.WriteLine("PASS "+assertions+" help-menu lifecycle assertions: repeated open/close, all close reasons, selection callbacks, reuse and owner disposal. No browser or phone access.");
        }
    }
}
