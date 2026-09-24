param([string]$JavaHome = $env:JAVA_HOME, [string]$GradleCache = (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'))
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
function DependencyJar([string]$relative) {
    $found = Get-ChildItem (Join-Path $GradleCache $relative) -Recurse -Filter '*.jar' | Select-Object -First 1
    if (!$found) { throw "Run testDebugUnitTest first: dependency $relative missing" }
    return $found.FullName
}
# JVM 17 accepts +00:00 with Instant.parse, hiding the Android desugaring regression.
# Patch only java.time from the exact library used in the APK, then rerun session tests.
$desugar = DependencyJar 'com.android.tools/desugar_jdk_libs/2.1.5'
$patchDir = Join-Path $repo 'app/build/android-time-compatibility'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($desugar)
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.StartsWith('java/time/') -and $entry.Name -and !$entry.FullName.Contains('..')) {
            $target = Join-Path $patchDir $entry.FullName
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        }
    }
} finally { $zip.Dispose() }
$classpath = @(
    (Join-Path $repo 'app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes'),
    (Join-Path $repo 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'),
    (DependencyJar 'junit/junit/4.13.2'),
    (DependencyJar 'org.hamcrest/hamcrest-core/1.3'),
    (DependencyJar 'org.json/json/20250517')
) -join [IO.Path]::PathSeparator
& (Join-Path $JavaHome 'bin/java') --patch-module "java.base=$patchDir" -cp $classpath org.junit.runner.JUnitCore br.com.rbwone.web.SessionTimestampTest br.com.rbwone.web.NotificationPolicyTest br.com.rbwone.web.UpdateReleaseTest
exit $LASTEXITCODE
