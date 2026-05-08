# install.ps1
$installDir = "$env:USERPROFILE\AppData\Local\redlab"
if (!(Test-Path $installDir)) { New-Item -ItemType Directory -Path $installDir }
Copy-Item "redlab.exe" -Destination "$installDir\redlab.exe"
[Environment]::SetEnvironmentVariable("Path", "$([Environment]::GetEnvironmentVariable("Path", "User"));$installDir", "User")
Write-Host "Success! Restart the terminal and type 'redlab' to start." -ForegroundColor Green