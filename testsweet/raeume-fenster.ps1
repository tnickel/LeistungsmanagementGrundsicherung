# Räumt verwaiste Selenium-Fenster auf (chromedriver + deren Chrome-Fenster).
# Erkennung der Automation-Chrome über typische Selenium-Parameter.
Get-CimInstance Win32_Process | Where-Object {
    ($_.Name -eq 'chromedriver.exe') -or
    ($_.Name -eq 'chrome.exe' -and ($_.CommandLine -like '*--remote-debugging-pipe*' -or $_.CommandLine -like '*--test-type*'))
} | ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
}
Write-Output 'Aufgeraeumt'
