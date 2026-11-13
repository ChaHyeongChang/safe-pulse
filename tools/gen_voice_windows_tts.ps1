# Windows 내장 한국어 TTS(System.Speech)로 음성 안내 WAV를 만든다.
#   powershell -ExecutionPolicy Bypass -File tools\gen_voice_windows_tts.ps1
#   powershell -ExecutionPolicy Bypass -File tools\gen_voice_windows_tts.ps1 -Rate 1 -LeadMs 150 -TailMs 250
#
# - 문장은 core/src/main/java/com/safepulse/core/Prompt.java 에서 읽는다(앱·TTS 대체 문장과 항상 같게).
# - 결과: app/src/main/res/raw/vo_{key}.wav (44.1kHz, 16bit, 모노). 앱(VoiceOutput)이 이 이름으로 찾아 재생한다.
# - 합성 엔진이 넣는 앞뒤 무음 길이가 들쭉날쭉해서, 무음을 잘라 낸 뒤 앞 LeadMs·뒤 TailMs 만큼 무음을 다시 붙인다
#   (재생 시작·끝에서 소리가 잘리지 않게).
# - 문장 사이 쉼(엔진 기본 약 0.5초)이 길어 구절마다 따로 합성한 뒤 짧은 쉼(마침표 180ms, 쉼표 100ms)으로 잇는다.
# - 위기 코칭은 앞 안내를 끊고 말하므로, 엔진 시간표(RiskConfig)보다 길면 다음 안내에 잘린다. 그래서 문장별 상한을 두고
#   (앞 무음 + 말소리 기준) 넘으면 말 속도를 한 단계씩 올린다(최대 MaxRate). 상한:
#     brake·brake_good·neutral·epb 1900ms  ← promptRepeatMs 2000(확인될 때까지 2초마다 반복), VOICE_LINES "2초 안에"
#     stopped 2400ms                       ← stoppedToSafeMs 2500 뒤 안부 확인 안내가 이어짐
#     rehearsal_start 2850ms               ← rehearsalIntroMs 3000 뒤 첫 동작 안내(반응 시간 측정 시작).
#                                            리허설 안내는 끊지 않고 이어서 재생하므로 뒤 무음까지 3초 안에 들어가야 한다
# - 한국어(ko-KR) 음성이 없으면 아무 파일도 만들지 않고 끝낸다.
# - 상용 TTS 음성의 라이선스는 docs/VOICE_LINES.md 참고. 팀원 녹음으로 바꾸려면 같은 이름으로 덮어쓰면 된다.
param(
    [int]$Rate = 0,         # 기본 말 속도(-10 ~ 10, 0 = 엔진 기본)
    [int]$MaxRate = 5,      # 상한을 맞추려고 올릴 수 있는 최대 속도(맞는 가장 느린 속도를 고름)
    [int]$LeadMs = 120,     # 앞 무음
    [int]$TailMs = 200,     # 뒤 무음
    [string]$Voice = ''     # 비우면 설치된 첫 ko-KR 음성(예: Microsoft Heami Desktop)
)
$ErrorActionPreference = 'Stop'

$budgetMs = @{ brake = 1900; brake_good = 1900; neutral = 1900; epb = 1900; stopped = 2400; rehearsal_start = 2850 }
$gapAfterSentenceMs = 180
$gapAfterCommaMs = 100

$root = Split-Path -Parent $PSScriptRoot
$promptFile = Join-Path $root 'core\src\main\java\com\safepulse\core\Prompt.java'
$outDir = Join-Path $root 'app\src\main\res\raw'
$sampleRate = 44100
$threshold = 300          # 무음 판정(16bit 진폭, 약 -40dBFS)
$marginMs = 30            # 자를 때 소리 앞뒤로 남기는 여유

Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$korean = @($synth.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -eq 'ko-KR' })
if ($Voice) { $korean = @($korean | Where-Object { $_.VoiceInfo.Name -eq $Voice }) }
if ($korean.Count -eq 0) {
    $synth.Dispose()
    Write-Host 'System.Speech에 한국어(ko-KR) 음성이 없습니다. 파일을 만들지 않았습니다.'
    exit 1
}
$voiceName = $korean[0].VoiceInfo.Name
$synth.SelectVoice($voiceName)
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo($sampleRate,
        [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)

$source = [System.IO.File]::ReadAllText($promptFile, [System.Text.Encoding]::UTF8)
$prompts = [regex]::Matches($source, '[A-Z_]+\("([a-z0-9_]+)",\s*"([^"]+)"\)')
if ($prompts.Count -eq 0) { throw "Prompt.java에서 문장을 찾지 못했습니다: $promptFile" }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Write-Wav([string]$path, [byte[]]$pcm, [int]$rate) {
    $ascii = [System.Text.Encoding]::ASCII
    $fs = [System.IO.File]::Create($path)
    $bw = New-Object System.IO.BinaryWriter($fs)
    $bw.Write($ascii.GetBytes('RIFF')); $bw.Write([int](36 + $pcm.Length)); $bw.Write($ascii.GetBytes('WAVE'))
    $bw.Write($ascii.GetBytes('fmt ')); $bw.Write([int]16); $bw.Write([int16]1); $bw.Write([int16]1)
    $bw.Write([int]$rate); $bw.Write([int]($rate * 2)); $bw.Write([int16]2); $bw.Write([int16]16)
    $bw.Write($ascii.GetBytes('data')); $bw.Write([int]$pcm.Length); $bw.Write($pcm)
    $bw.Close()
}

function Get-Amp([byte[]]$pcm, [int]$i) { [Math]::Abs([int][BitConverter]::ToInt16($pcm, $i * 2)) }

function Get-Silence([int]$ms) { New-Object byte[] ([int]($sampleRate * $ms / 1000) * 2) }

# 한 구절을 합성해 앞뒤 무음을 잘라 낸 PCM을 돌려준다.
function Get-SpeechPcm([string]$text) {
    $stream = New-Object System.IO.MemoryStream
    $synth.SetOutputToAudioStream($stream, $format)
    $synth.Speak($text)
    $synth.SetOutputToNull()
    $raw = $stream.ToArray()
    $stream.Dispose()
    $n = [int]($raw.Length / 2)
    $first = 0
    while ($first -lt $n -and (Get-Amp $raw $first) -lt $threshold) { $first++ }
    $last = $n - 1
    while ($last -gt $first -and (Get-Amp $raw $last) -lt $threshold) { $last-- }
    if ($first -ge $n) { throw "무음만 합성됐습니다: $text" }
    $margin = [int]($sampleRate * $marginMs / 1000)
    $first = [Math]::Max(0, $first - $margin)
    $last = [Math]::Min($n - 1, $last + $margin)
    $body = New-Object byte[] (($last - $first + 1) * 2)
    [Array]::Copy($raw, $first * 2, $body, 0, $body.Length)
    return , $body
}

# 문장을 구절(마침표·쉼표·느낌표·물음표 뒤)로 나눠 합성하고 짧은 쉼으로 잇는다.
function Get-LinePcm([string]$text) {
    $parts = [regex]::Split($text.Trim(), '(?<=[.,!?])\s+')
    $chunks = New-Object System.Collections.Generic.List[byte[]]
    for ($i = 0; $i -lt $parts.Count; $i++) {
        $chunks.Add((Get-SpeechPcm $parts[$i]))
        if ($i -lt $parts.Count - 1) {
            $gap = if ($parts[$i].EndsWith(',')) { $gapAfterCommaMs } else { $gapAfterSentenceMs }
            $chunks.Add((Get-Silence $gap))
        }
    }
    $total = 0; foreach ($c in $chunks) { $total += $c.Length }
    $out = New-Object byte[] $total
    $pos = 0; foreach ($c in $chunks) { [Array]::Copy($c, 0, $out, $pos, $c.Length); $pos += $c.Length }
    return , $out
}

Write-Host "음성: $voiceName · 기본 속도 $Rate(최대 $MaxRate) · 앞 ${LeadMs}ms / 뒤 ${TailMs}ms 무음 → $outDir"
$warnings = 0
foreach ($p in $prompts) {
    $key = $p.Groups[1].Value
    $text = $p.Groups[2].Value
    $limit = if ($budgetMs.ContainsKey($key)) { $budgetMs[$key] } else { 0 }

    $r = $Rate
    while ($true) {
        $synth.Rate = $r
        $speech = Get-LinePcm $text
        $speechMs = $LeadMs + [int]($speech.Length / 2 * 1000 / $sampleRate)
        if ($limit -eq 0 -or $speechMs -le $limit -or $r -ge $MaxRate) { break }
        $r++
    }

    $lead = Get-Silence $LeadMs
    $tail = Get-Silence $TailMs
    $pcm = New-Object byte[] ($lead.Length + $speech.Length + $tail.Length)
    [Array]::Copy($speech, 0, $pcm, $lead.Length, $speech.Length)
    $path = Join-Path $outDir ("vo_" + $key + ".wav")
    Write-Wav $path $pcm $sampleRate

    $totalMs = [int]($pcm.Length / 2 * 1000 / $sampleRate)
    $note = ''
    if ($limit -gt 0) {
        if ($speechMs -le $limit) { $note = "상한 ${limit}ms OK" } else { $note = "상한 ${limit}ms 초과!"; $warnings++ }
    }
    Write-Host ("  vo_{0,-16} 속도 {1}  말소리까지 {2,5}ms  전체 {3,5}ms  {4}  {5}" -f $key, $r, $speechMs, $totalMs, $note, $text)
}
$synth.Dispose()
Write-Host "완료: $($prompts.Count)개" $(if ($warnings -gt 0) { "· 상한 초과 ${warnings}개(문장을 줄이거나 MaxRate를 올리세요)" } else { '' })
