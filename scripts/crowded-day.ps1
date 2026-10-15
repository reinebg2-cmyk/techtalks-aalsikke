# KAN-354: builds a crowded calendar day, to test how the week view copes.
# Creates, for the chosen day (default: two days from now):
#   - 1 multi-day task (from the day before at 16:00 to the day after at 11:00)
#   - 5 tasks that overlap each other between 09:00 and 14:00
#   - 6 deadline-only tasks (they go in the "Due" row)
# Every title starts with "Crowded:" so they are easy to spot and to remove with -Remove.
#
# Usage (from the repo folder, with the app running):
#   powershell -ExecutionPolicy Bypass -File .\scripts\crowded-day.ps1 -Email verifytest2@gmail.com -Password "Test12345!"
#   powershell -ExecutionPolicy Bypass -File .\scripts\crowded-day.ps1 -Email verifytest2@gmail.com -Password "Test12345!" -Date 2026-10-14
#   powershell -ExecutionPolicy Bypass -File .\scripts\crowded-day.ps1 -Email verifytest2@gmail.com -Password "Test12345!" -Remove

param(
    [Parameter(Mandatory = $true)][string]$Email,
    [Parameter(Mandatory = $true)][string]$Password,
    [string]$Date = (Get-Date).AddDays(2).ToString('yyyy-MM-dd'),
    [string]$BaseUrl = 'http://localhost:8080',
    [switch]$Remove
)

$ErrorActionPreference = 'Stop'

$loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json
$token = (Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType 'application/json' -Body $loginBody).token
$headers = @{ Authorization = "Bearer $token" }

# ---------------- remove ----------------
if ($Remove) {
    $crowded = @((Invoke-RestMethod -Uri "$BaseUrl/api/schedule-items/me" -Headers $headers) | Where-Object { $_.title -like 'Crowded:*' })
    foreach ($task in $crowded) {
        Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/schedule-items/$($task.id)" -Headers $headers | Out-Null
        Write-Host "Removed  $($task.title)"
    }
    Write-Host "Done: removed $($crowded.Count) crowded-day tasks."
    return
}

# ---------------- create ----------------
$day = [datetime]::ParseExact($Date, 'yyyy-MM-dd', $null)

# "2026-10-14T09:30:00+03:00" for a day offset (-1, 0, +1) and a local time
function At([int]$dayOffset, [string]$hhmm) {
    $local = $day.Date.AddDays($dayOffset).Add([TimeSpan]::Parse($hhmm))
    $offset = [System.TimeZoneInfo]::Local.GetUtcOffset($local)
    return ([DateTimeOffset]::new($local, $offset)).ToString('yyyy-MM-ddTHH:mm:sszzz')
}

function New-Task([string]$title, [string]$type, $start, [string]$due) {
    $body = @{
        title           = $title
        itemType        = $type
        priority        = 'MEDIUM'
        deadline        = $due
        isRecurring     = $false
        reminderEnabled = $false
    }
    if ($start) {
        $body.startTime = $start
        $body.endTime = $due
    }
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/schedule-items" -Headers $headers `
        -ContentType 'application/json' -Body ($body | ConvertTo-Json) | Out-Null
    Write-Host "Created  $title"
}

Write-Host "Building a crowded day on $Date for $Email"

# 1 multi-day task
New-Task 'Crowded: hackathon (multi-day)' 'STUDY' (At -1 '16:00') (At 1 '11:00')

# 5 overlapping tasks
New-Task 'Crowded: lecture'     'CLASS'    (At 0 '09:00') (At 0 '11:00')
New-Task 'Crowded: lab'         'CLASS'    (At 0 '09:30') (At 0 '11:30')
New-Task 'Crowded: study group' 'STUDY'    (At 0 '10:00') (At 0 '12:00')
New-Task 'Crowded: work shift'  'SHIFT'    (At 0 '10:30') (At 0 '14:00')
New-Task 'Crowded: team meeting' 'PERSONAL' (At 0 '11:00') (At 0 '12:00')

# 6 deadline-only tasks (Due row)
New-Task 'Crowded: essay draft'     'ASSIGNMENT' $null (At 0 '12:00')
New-Task 'Crowded: quiz prep'       'EXAM'       $null (At 0 '13:00')
New-Task 'Crowded: lab report'      'ASSIGNMENT' $null (At 0 '15:00')
New-Task 'Crowded: reading notes'   'ASSIGNMENT' $null (At 0 '17:00')
New-Task 'Crowded: project upload'  'ASSIGNMENT' $null (At 0 '20:00')
New-Task 'Crowded: forum post'      'ASSIGNMENT' $null (At 0 '23:00')

Write-Host "Done: 12 tasks. Open the Calendar on the week of $Date."