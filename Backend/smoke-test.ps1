# Cadence API smoke test - exercises every endpoint the frontend uses.
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api'

function J($obj) { $obj | ConvertTo-Json -Compress }

# ── auth ────────────────────────────────────────────────────────────
$loginBody = @{ username = 'admin'; password = 'admin-change-me' } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $loginBody
Write-Output ("LOGIN ok user={0} role={1} tokenLen={2}" -f $login.username, $login.role, $login.token.Length)
$H = @{ Authorization = "Bearer $($login.token)" }

# bad login must fail
try {
    Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
        -Body (@{ username = 'admin'; password = 'wrong' } | ConvertTo-Json -Compress) | Out-Null
    Write-Output 'BAD-LOGIN FAIL: expected 401'
} catch {
    Write-Output ("BAD-LOGIN ok status={0}" -f $_.Exception.Response.StatusCode.value__)
}

# ── projects ────────────────────────────────────────────────────────
$proj = Invoke-RestMethod -Method Post -Uri "$base/projects" -ContentType 'application/json' -Headers $H `
    -Body (@{ name = 'Smoke Project'; color = '#e8a020' } | ConvertTo-Json -Compress)
Write-Output ("PROJECT created id={0} name={1} color={2}" -f $proj.id, $proj.name, $proj.color)

$projUpd = Invoke-RestMethod -Method Put -Uri "$base/projects/$($proj.id)" -ContentType 'application/json' -Headers $H `
    -Body (@{ name = 'Smoke Renamed'; color = '#0f7a55' } | ConvertTo-Json -Compress)
Write-Output ("PROJECT updated name={0}" -f $projUpd.name)

# ── tasks ───────────────────────────────────────────────────────────
$task = Invoke-RestMethod -Method Post -Uri "$base/tasks" -ContentType 'application/json' -Headers $H `
    -Body (@{ title = 'Smoke task'; projectId = $proj.id; status = 'TODO'; priority = 'HIGH';
              dueDate = '2026-08-25'; tags = 'test, smoke'; notes = 'hello **world**' } | ConvertTo-Json -Compress)
Write-Output ("TASK created id={0} status={1} completedAt={2}" -f $task.id, $task.status, $task.completedAt)

$taskDone = Invoke-RestMethod -Method Put -Uri "$base/tasks/$($task.id)" -ContentType 'application/json' -Headers $H `
    -Body (@{ id = $task.id; title = 'Smoke task'; projectId = $proj.id; status = 'DONE'; priority = 'HIGH' } | ConvertTo-Json -Compress)
Write-Output ("TASK completed status={0} completedAt={1}" -f $taskDone.status, $taskDone.completedAt)

$tasks = Invoke-RestMethod -Method Get -Uri "$base/tasks" -Headers $H
Write-Output ("TASKS list count={0}" -f $tasks.Count)

# ── focus ───────────────────────────────────────────────────────────
$focus = Invoke-RestMethod -Method Post -Uri "$base/focus" -ContentType 'application/json' -Headers $H `
    -Body (@{ minutes = 45; note = 'smoke session'; projectId = $proj.id } | ConvertTo-Json -Compress)
Write-Output ("FOCUS added id={0} minutes={1}" -f $focus.id, $focus.minutes)

$since = (Get-Date).AddDays(-7).ToString('yyyy-MM-dd')
$sessions = Invoke-RestMethod -Method Get -Uri "$base/focus?since=$since" -Headers $H
Write-Output ("FOCUS list since={0} count={1}" -f $since, $sessions.Count)

# ── stats / report / export ─────────────────────────────────────────
$stats = Invoke-RestMethod -Method Get -Uri "$base/stats" -Headers $H
Write-Output ("STATS projects={0} openTasks={1} doneToday={2} focusToday={3} weekDays={4} streak={5}" -f `
    $stats.projects, $stats.openTasks, $stats.doneToday, $stats.focusToday, $stats.week.Count, $stats.streak)

$report = Invoke-RestMethod -Method Get -Uri "$base/report/weekly" -Headers $H
Write-Output ("REPORT thisWeek={0}->{1} tasksCompleted={2} focusMinutes={3} byProject={4}" -f `
    $report.thisWeek.start, $report.thisWeek.end, $report.thisWeek.tasksCompleted, $report.thisWeek.focusMinutes, $report.byProject.Count)

$export = Invoke-RestMethod -Method Get -Uri "$base/export" -Headers $H
Write-Output ("EXPORT projects={0} tasks={1} focus={2}" -f $export.projects.Count, $export.tasks.Count, $export.focus.Count)

# ── prefs ───────────────────────────────────────────────────────────
Invoke-RestMethod -Method Put -Uri "$base/prefs" -ContentType 'application/json' -Headers $H `
    -Body (@{ theme = 'dark'; goalMinutes = 90 } | ConvertTo-Json -Compress) | Out-Null
$prefs = Invoke-RestMethod -Method Get -Uri "$base/prefs" -Headers $H
Write-Output ("PREFS theme={0} goalMinutes={1}" -f $prefs.theme, $prefs.goalMinutes)

# ── audio logs ──────────────────────────────────────────────────────
$bytes = [byte[]](1..64 | ForEach-Object { $_ })
$b64 = [Convert]::ToBase64String($bytes)
$audio = Invoke-RestMethod -Method Post -Uri "$base/audiologs" -ContentType 'application/json' -Headers $H `
    -Body (@{ projectId = $proj.id; note = 'smoke memo'; durationSec = 5; mimeType = 'audio/webm'; data = $b64 } | ConvertTo-Json -Compress)
Write-Output ("AUDIO created id={0} sizeBytes={1}" -f $audio.id, $audio.sizeBytes)

$audioList = Invoke-RestMethod -Method Get -Uri "$base/audiologs" -Headers $H
Write-Output ("AUDIO list count={0}" -f $audioList.Count)

$raw = Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$base/audiologs/$($audio.id)/data" -Headers $H
Write-Output ("AUDIO data bytes={0} contentType={1}" -f $raw.Content.Length, $raw.Headers['Content-Type'])

Invoke-RestMethod -Method Delete -Uri "$base/audiologs/$($audio.id)" -Headers $H | Out-Null
$audioList2 = Invoke-RestMethod -Method Get -Uri "$base/audiologs" -Headers $H
Write-Output ("AUDIO after delete count={0}" -f $audioList2.Count)

# ── admin ───────────────────────────────────────────────────────────
$ov = Invoke-RestMethod -Method Get -Uri "$base/admin/overview" -Headers $H
Write-Output ("ADMIN overview projects={0} tasks={1} users={2} audit={3}" -f $ov.projects, $ov.tasks, $ov.users, $ov.auditEntries)

$sys = Invoke-RestMethod -Method Get -Uri "$base/admin/system" -Headers $H
Write-Output ("ADMIN system version={0} java={1} uptime={2}s securityEnabled={3}" -f $sys.version, $sys.javaVersion, $sys.uptimeSec, $sys.securityEnabled)

$users = Invoke-RestMethod -Method Get -Uri "$base/admin/users" -Headers $H
Write-Output ("ADMIN users count={0}" -f $users.Count)

$uniqueUser = 'smoke.' + (Get-Date -Format 'HHmmss')
$newUser = Invoke-RestMethod -Method Post -Uri "$base/admin/users" -ContentType 'application/json' -Headers $H `
    -Body (@{ username = $uniqueUser; password = 'secret123'; role = 'USER' } | ConvertTo-Json -Compress)
Write-Output ("ADMIN user created id={0} username={1} role={2}" -f $newUser.id, $newUser.username, $newUser.role)

# duplicate username must be rejected
try {
    Invoke-RestMethod -Method Post -Uri "$base/admin/users" -ContentType 'application/json' -Headers $H `
        -Body (@{ username = $uniqueUser; password = 'secret123'; role = 'USER' } | ConvertTo-Json -Compress) | Out-Null
    Write-Output 'DUP-USER FAIL: expected 400'
} catch {
    Write-Output ("DUP-USER ok status={0}" -f $_.Exception.Response.StatusCode.value__)
}

$roleUpd = Invoke-RestMethod -Method Put -Uri "$base/admin/users/$($newUser.id)" -ContentType 'application/json' -Headers $H `
    -Body (@{ role = 'ADMIN' } | ConvertTo-Json -Compress)
Write-Output ("ADMIN role updated role={0}" -f $roleUpd.role)

Invoke-RestMethod -Method Put -Uri "$base/admin/users/$($newUser.id)/password" -ContentType 'application/json' -Headers $H `
    -Body (@{ password = 'newpass456' } | ConvertTo-Json -Compress) | Out-Null
Write-Output 'ADMIN password reset ok'

$adminProjects = Invoke-RestMethod -Method Get -Uri "$base/admin/projects" -Headers $H
Write-Output ("ADMIN projects count={0} health={1}" -f $adminProjects.Count, $adminProjects[0].health)

$audit = Invoke-RestMethod -Method Get -Uri "$base/admin/audit" -Headers $H
Write-Output ("ADMIN audit count={0} latest={1}" -f $audit.Count, $audit[0].action)

# admin without token must be rejected
try {
    Invoke-RestMethod -Method Get -Uri "$base/admin/overview" | Out-Null
    Write-Output 'ADMIN-NOAUTH FAIL: expected 401/403'
} catch {
    Write-Output ("ADMIN-NOAUTH ok status={0}" -f $_.Exception.Response.StatusCode.value__)
}

# ── static frontend ─────────────────────────────────────────────────
$homePage = Invoke-WebRequest -UseBasicParsing -Method Get -Uri 'http://localhost:8080/'
Write-Output ("FRONTEND status={0} bytes={1} isCadence={2}" -f $homePage.StatusCode, $homePage.Content.Length, ($homePage.Content -match 'Cadence'))

# ── cleanup: delete project (cascades task), verify ─────────────────
Invoke-RestMethod -Method Delete -Uri "$base/projects/$($proj.id)" -Headers $H | Out-Null
$tasksAfter = Invoke-RestMethod -Method Get -Uri "$base/tasks" -Headers $H
Write-Output ("CLEANUP project deleted, tasks remaining={0}" -f $tasksAfter.Count)

Write-Output 'SMOKE TEST COMPLETE'