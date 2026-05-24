$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $root "out"
New-Item -ItemType Directory -Path $out | Out-Null
javac -encoding UTF-8 -d $out (Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
java -cp $out com.lingvoice.day2.LingVoiceDay2
