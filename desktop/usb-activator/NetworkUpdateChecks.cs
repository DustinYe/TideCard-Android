using System;
using System.IO;
using System.Threading;

namespace TideCardUsb {
    /** Optional online check; never installs an update or invokes ADB. */
    internal static class NetworkUpdateChecks {
        private static int Main() {
            try {
                UsbRelease release=UsbUpdates.FindLatest(CancellationToken.None);if(release==null)throw new Exception("No stable USB release");Console.WriteLine("Public latest USB: "+release.Tag+"; current tool: "+DesktopInfo.Version);
                string work=UsbUpdates.Prepare(release,CancellationToken.None,null);
                if(UsbUpdates.Hash(Path.Combine(work,"package.zip"))!=release.Sha256||!UsbUpdates.HasInternetOrigin(Path.Combine(work,"stage","TideCard-USB.exe")))throw new Exception("Public asset verification failed");
                Console.WriteLine("PASS live GitHub lookup, HTTPS download, exact size, API SHA-256, ZIP extraction, internal manifest, executable version and Internet origin. No installation.");Console.WriteLine("Verified staged package: "+work);return 0;
            }catch(Exception e){Console.Error.WriteLine(e.Message);return 1;}
        }
    }
}
