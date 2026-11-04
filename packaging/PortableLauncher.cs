using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;

[assembly: AssemblyTitle("本草中药材管理平台")]
[assembly: AssemblyDescription("本草云端管理平台免安装客户端")]
[assembly: AssemblyVersion("1.1.0.0")]

internal static class PortableLauncher
{
    private const string BuildId = "__BUILD_ID__";
    [STAThread]
    private static int Main(string[] args)
    {
        bool check = args.Length > 0 && args[0] == "--self-test";
        string home = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "BencaoManager");
        string version = Path.Combine(home, "versions", BuildId);
        Directory.CreateDirectory(home);
        try
        {
            using (Mutex gate = new Mutex(false, "Local\\BencaoManagerPayload"))
            {
                bool locked;
                try { locked = gate.WaitOne(60000); } catch (AbandonedMutexException) { locked = true; }
                if (!locked) throw new IOException("另一个程序正在准备运行环境，请稍后重试。");
                try
                {
                    if (!File.Exists(Path.Combine(version, ".ready")))
                    {
                        string staging = Path.Combine(home, "versions", BuildId + "-" + Guid.NewGuid().ToString("N"));
                        Directory.CreateDirectory(staging);
                        using (Stream resource = Assembly.GetExecutingAssembly().GetManifestResourceStream("payload.zip"))
                        using (ZipArchive archive = new ZipArchive(resource, ZipArchiveMode.Read))
                        {
                            string prefix = Path.GetFullPath(staging) + Path.DirectorySeparatorChar;
                            foreach (ZipArchiveEntry entry in archive.Entries)
                            {
                                string target = Path.GetFullPath(Path.Combine(staging, entry.FullName));
                                if (!target.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) throw new IOException("运行环境文件路径无效。");
                                if (entry.FullName.EndsWith("/")) { Directory.CreateDirectory(target); continue; }
                                Directory.CreateDirectory(Path.GetDirectoryName(target));
                                using (Stream source = entry.Open())
                                using (Stream output = File.Create(target)) source.CopyTo(output);
                            }
                        }
                        File.WriteAllText(Path.Combine(staging, ".ready"), BuildId);
                        // 只创建当前构建的目录，不删除用户文件或旧版本。
                        if (Directory.Exists(version)) throw new IOException("运行环境准备未完成，请联系维护人员。");
                        Directory.Move(staging, version);
                    }
                }
                finally { gate.ReleaseMutex(); }
            }
            string logs = Path.Combine(home, "logs");
            Directory.CreateDirectory(logs);
            string logPath = Path.Combine(logs, check ? "self-test.log" : "client-" + DateTime.Now.ToString("yyyyMMdd-HHmmss") + ".log");
            ProcessStartInfo start = new ProcessStartInfo();
            start.FileName = Path.Combine(version, "runtime", "bin", check ? "java.exe" : "javaw.exe");
            start.Arguments = "-Dfile.encoding=UTF-8 -jar app.jar" + (check ? " --check-connection" : "");
            start.WorkingDirectory = version;
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            start.WindowStyle = ProcessWindowStyle.Hidden;
            start.RedirectStandardOutput = true;
            start.RedirectStandardError = true;
            using (StreamWriter log = new StreamWriter(logPath, false, System.Text.Encoding.UTF8))
            using (Process process = new Process())
            {
                object sync = new object();
                log.AutoFlush = true;
                process.StartInfo = start;
                process.OutputDataReceived += delegate(object sender, DataReceivedEventArgs e) { if (e.Data != null) lock (sync) { log.WriteLine(e.Data); } };
                process.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs e) { if (e.Data != null) lock (sync) { log.WriteLine(e.Data); } };
                process.Start();
                process.BeginOutputReadLine();
                process.BeginErrorReadLine();
                process.WaitForExit();
                int exit = process.ExitCode;
                if (exit != 0 && !check) MessageBox.Show("程序未能正常运行，请检查网络连接。\n日志：" + logPath, "本草管理平台", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return exit;
            }
        }
        catch (Exception error)
        {
            File.WriteAllText(Path.Combine(home, "launcher-error.log"), error.ToString());
            if (!check) MessageBox.Show("启动失败：" + error.Message, "本草管理平台", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }
}
