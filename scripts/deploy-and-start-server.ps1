$ErrorActionPreference = 'Stop'

$serverDirectory = 'C:\test server'
$pluginDirectory = Join-Path $serverDirectory 'plugins'
$artifact = Join-Path $PSScriptRoot '..\build\libs\ProjectS-0.1.0.jar'
$destination = Join-Path $pluginDirectory 'ProjectS-0.1.0.jar'
$startScript = Join-Path $serverDirectory 'start.bat'
$consoleOutput = Join-Path $serverDirectory 'logs\server-console.log'
$consoleError = Join-Path $serverDirectory 'logs\server-console-error.log'
$propertiesPath = Join-Path $serverDirectory 'server.properties'
$serverPort = 25565
$rconPort = 25575

if (-not (Test-Path -LiteralPath $artifact)) {
    throw "Plugin jar was not found: $artifact"
}
if (-not (Test-Path -LiteralPath $startScript)) {
    throw "Server start script was not found: $startScript"
}

Copy-Item -LiteralPath $artifact -Destination $destination -Force
Write-Host "Deployed plugin: $destination"

function Get-ServerProperty([string] $name) {
    $match = Select-String -LiteralPath $propertiesPath `
        -Pattern "^$([regex]::Escape($name))=(.*)$" | Select-Object -First 1
    if ($null -eq $match) { return '' }
    return $match.Matches[0].Groups[1].Value
}

function Set-ServerProperty([string] $name, [string] $value) {
    $content = Get-Content -LiteralPath $propertiesPath
    $pattern = "^$([regex]::Escape($name))=.*$"
    if ($content -match $pattern) {
        $content = $content -replace $pattern, "$name=$value"
    } else {
        $content += "$name=$value"
    }
    [IO.File]::WriteAllLines(
        $propertiesPath, $content, [Text.UTF8Encoding]::new($false))
}

function Read-Exact([System.IO.Stream] $stream, [int] $length) {
    $buffer = [byte[]]::new($length)
    $offset = 0
    while ($offset -lt $length) {
        $read = $stream.Read($buffer, $offset, $length - $offset)
        if ($read -le 0) { throw 'RCON connection closed unexpectedly.' }
        $offset += $read
    }
    return $buffer
}

function Send-RconPacket(
    [System.IO.Stream] $stream,
    [int] $requestId,
    [int] $type,
    [string] $body
) {
    $bodyBytes = [Text.Encoding]::UTF8.GetBytes($body)
    $length = 10 + $bodyBytes.Length
    $packet = [System.IO.MemoryStream]::new()
    $writer = [System.IO.BinaryWriter]::new($packet)
    $writer.Write($length)
    $writer.Write($requestId)
    $writer.Write($type)
    $writer.Write($bodyBytes)
    $writer.Write([byte]0)
    $writer.Write([byte]0)
    $packetBytes = $packet.ToArray()
    $stream.Write($packetBytes, 0, $packetBytes.Length)
    $stream.Flush()
}

function Read-RconPacket([System.IO.Stream] $stream) {
    $length = [BitConverter]::ToInt32((Read-Exact $stream 4), 0)
    if ($length -lt 10 -or $length -gt 4096) {
        throw "Invalid RCON response length: $length"
    }
    $payload = Read-Exact $stream $length
    return [PSCustomObject]@{
        RequestId = [BitConverter]::ToInt32($payload, 0)
        Type = [BitConverter]::ToInt32($payload, 4)
    }
}

function Stop-ServerWithRcon([string] $password) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $client.Connect('127.0.0.1', $rconPort)
        $stream = $client.GetStream()
        $stream.ReadTimeout = 5000
        Send-RconPacket $stream 41 3 $password
        $auth = Read-RconPacket $stream
        if ($auth.RequestId -eq -1) { throw 'RCON authentication failed.' }
        Send-RconPacket $stream 42 2 'stop'
        Write-Host 'Sent a graceful stop command through RCON.'
    } finally {
        $client.Dispose()
    }
}

function Stop-ServerWithConsoleSignal {
    $serverProcess = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'paper-26\.1\.2-74\.jar' } |
        Select-Object -First 1
    if ($null -eq $serverProcess) {
        throw 'Could not find the running Paper process.'
    }

    if (-not ('ProjectS.ConsoleInput' -as [type])) {
        Add-Type @'
using System;
using System.Runtime.InteropServices;
namespace ProjectS {
    public static class ConsoleInput {
        [StructLayout(LayoutKind.Explicit, CharSet = CharSet.Unicode)]
        private struct KeyEventRecord {
            [FieldOffset(0)] public int KeyDown;
            [FieldOffset(4)] public ushort RepeatCount;
            [FieldOffset(6)] public ushort VirtualKeyCode;
            [FieldOffset(8)] public ushort VirtualScanCode;
            [FieldOffset(10)] public char UnicodeChar;
            [FieldOffset(12)] public uint ControlKeyState;
        }

        [StructLayout(LayoutKind.Explicit)]
        private struct InputRecord {
            [FieldOffset(0)] public ushort EventType;
            [FieldOffset(4)] public KeyEventRecord KeyEvent;
        }

        [DllImport("kernel32.dll")] private static extern bool FreeConsole();
        [DllImport("kernel32.dll")] private static extern bool AttachConsole(uint processId);
        [DllImport("kernel32.dll")] private static extern IntPtr GetStdHandle(int standardHandle);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
        private static extern bool WriteConsoleInputW(
            IntPtr input, InputRecord[] records, uint length, out uint written);

        public static void SendCommand(uint processId, string command) {
            FreeConsole();
            if (!AttachConsole(processId)) {
                throw new InvalidOperationException("Could not attach to the Paper console.");
            }
            try {
                IntPtr input = GetStdHandle(-10);
                SendKey(input, (char)27, 0x1B);
                foreach (char character in command + "\r") {
                    SendKey(input, character, character == '\r' ? (ushort)0x0D : (ushort)0);
                }
            } finally {
                FreeConsole();
            }
        }

        private static void SendKey(IntPtr input, char character, ushort virtualKey) {
            InputRecord[] records = new InputRecord[2];
            records[0].EventType = 0x0001;
            records[0].KeyEvent.KeyDown = 1;
            records[0].KeyEvent.RepeatCount = 1;
            records[0].KeyEvent.VirtualKeyCode = virtualKey;
            records[0].KeyEvent.UnicodeChar = character;
            records[1] = records[0];
            records[1].KeyEvent.KeyDown = 0;
            uint written;
            if (!WriteConsoleInputW(input, records, 2, out written) || written != 2) {
                throw new InvalidOperationException("Could not write to the Paper console.");
            }
        }
    }
}
'@
    }

    [ProjectS.ConsoleInput]::SendCommand([uint32]$serverProcess.ProcessId, 'stop')
    Write-Host 'Sent a one-time graceful stop command to the server console input.'
}

function Wait-ForServerStop {
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (-not (Get-NetTCPConnection -LocalPort $serverPort -State Listen -ErrorAction SilentlyContinue)) {
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw 'Paper did not stop within 60 seconds; restart was cancelled.'
}

$rconPassword = Get-ServerProperty 'rcon.password'
if ([string]::IsNullOrWhiteSpace($rconPassword)) {
    $randomBytes = [byte[]]::new(24)
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($randomBytes)
    } finally {
        $random.Dispose()
    }
    $rconPassword = -join ($randomBytes | ForEach-Object { $_.ToString('x2') })
}
Set-ServerProperty 'enable-rcon' 'true'
Set-ServerProperty 'rcon.password' $rconPassword
Set-ServerProperty 'rcon.port' $rconPort

$serverIsRunning = Get-NetTCPConnection -LocalPort $serverPort -State Listen -ErrorAction SilentlyContinue
if ($serverIsRunning) {
    if (Get-NetTCPConnection -LocalPort $rconPort -State Listen -ErrorAction SilentlyContinue) {
        Stop-ServerWithRcon $rconPassword
    } else {
        Write-Host 'RCON is not active yet; sending one-time graceful console stop.'
        Stop-ServerWithConsoleSignal
    }
    Wait-ForServerStop
}

Start-Process -FilePath 'cmd.exe' `
    -ArgumentList '/c', 'start.bat' `
    -WorkingDirectory $serverDirectory `
    -WindowStyle Hidden `
    -RedirectStandardOutput $consoleOutput `
    -RedirectStandardError $consoleError
Write-Host 'Started the ProjectS test server.'
