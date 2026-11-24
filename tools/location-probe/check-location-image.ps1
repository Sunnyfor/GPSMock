# 使用 Windows 自带 OCR 读取已有验收截图，不上传图片、不安装 OCR 依赖、不修改图片。
# 此脚本在 Windows PowerShell 5 中执行；输出仅含匹配结果，不打印识别出的地址全文。
param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$ExpectedPattern,
    [string]$KnownWrongPattern = ''
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime]
$null = [Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
$null = [Windows.Graphics.Imaging.SoftwareBitmap, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
$null = [Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime]
$null = [Windows.Media.Ocr.OcrResult, Windows.Foundation, ContentType = WindowsRuntime]
$ocrAsTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetGenericArguments().Count -eq 1 -and
    $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

# 等待 WinRT 的只读解码/识别操作，传播真实错误，不把 OCR 不可用当成界面通过。
function Wait-OcrOperation($Operation, [Type]$ResultType) {
    $ocrTask = $ocrAsTask.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
    $ocrTask.GetAwaiter().GetResult()
}

$ocrFile = Wait-OcrOperation ([Windows.Storage.StorageFile]::GetFileFromPathAsync((Resolve-Path -LiteralPath $Path).Path)) ([Windows.Storage.StorageFile])
$ocrStream = Wait-OcrOperation ($ocrFile.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
$ocrBitmap = $null
try {
    $ocrDecoder = Wait-OcrOperation ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($ocrStream)) ([Windows.Graphics.Imaging.BitmapDecoder])
    $ocrBitmap = Wait-OcrOperation ($ocrDecoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
    $ocrEngine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
    if ($null -eq $ocrEngine) { throw 'Local OCR language is unavailable; visual verdict is inconclusive.' }
    $ocrResult = Wait-OcrOperation ($ocrEngine.RecognizeAsync($ocrBitmap)) ([Windows.Media.Ocr.OcrResult])
    $ocrText = $ocrResult.Text -replace '\s', ''
    [pscustomobject]@{
        expectedTextVisible = [bool]($ocrText -match $ExpectedPattern)
        knownWrongRegionVisible = [bool]($KnownWrongPattern -and $ocrText -match $KnownWrongPattern)
        recognizedCharacterCount = $ocrText.Length
    } | ConvertTo-Json -Compress
} finally {
    if ($null -ne $ocrBitmap) { $ocrBitmap.Dispose() }
    $ocrStream.Dispose()
}
