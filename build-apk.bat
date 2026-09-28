@echo off
rem build-apk v1.3 (28/09/2026) - tu tim Java 17+ khi JAVA_HOME trong
cd /d "%~dp0"
title ChiaBill - build APK

rem Java tren Windows khong doc duoc duong dan co chu tieng Viet.
rem Gan tam thu muc nay thanh mot o dia ao (chi chu ASCII) roi build tu do.
set "DRV="
for %%L in (X Y Z W V) do (
  if not defined DRV if not exist %%L:\ set "DRV=%%L:"
)
if not defined DRV (
  echo Khong tim duoc o dia trong de gan. Hay chuyen project sang duong dan khong dau.
  pause
  exit /b 1
)
set "PROJ=%~dp0"
set "PROJ=%PROJ:~0,-1%"
subst %DRV% "%PROJ%"
if errorlevel 1 (
  echo Lenh subst loi. Hay chuyen project sang duong dan khong dau, vi du D:\ChiaBill
  pause
  exit /b 1
)
pushd %DRV%\

if not exist local.properties (
  powershell -NoProfile -Command "'sdk.dir=' + ($env:LOCALAPPDATA -replace '\\','/') + '/Android/Sdk' | Set-Content -Encoding ascii local.properties"
)
rem Tim Java 17+: JAVA_HOME san co -> JDK Microsoft/Adoptium/Oracle -> JBR cua Android Studio -> java tren PATH
call :findjava
if defined JAVA_HOME (
  set "PATH=%JAVA_HOME%\bin;%PATH%"
) else (
  where java >nul 2>nul
  if errorlevel 1 goto :nojava
)
echo JAVA_HOME=%JAVA_HOME%
echo Build tu o ao %DRV%\
echo.
echo [1/2] Chay test domain
echo [2/2] Build APK debug
echo Lan dau mat 3-10 phut vi Gradle tai thu vien. Log luu o build-log.txt
echo.

rem Xoa APK cu de chi APK cua lan build nay moi duoc luu
if exist "app\build\outputs\apk\debug\app-debug.apk" del /q "app\build\outputs\apk\debug\app-debug.apk"

powershell -NoProfile -Command "cmd /c 'gradlew.bat :domain:test :app:assembleDebug --console=plain 2>&1' | Tee-Object -FilePath 'build-log.txt'"

echo.
if not exist "app\build\outputs\apk\debug\app-debug.apk" goto :fail

rem Luu ban sao APK theo so phien ban (versionName trong app\build.gradle) vao thu muc outputs\vX.Y.Z
set "VER="
for /f "tokens=2 delims='" %%v in ('findstr /c:"versionName" app\build.gradle') do set "VER=%%v"
if not defined VER set "VER=dev"
if not exist "outputs\v%VER%" mkdir "outputs\v%VER%"
copy /y "app\build\outputs\apk\debug\app-debug.apk" "outputs\v%VER%\ChiaBill-v%VER%-debug.apk" >nul
copy /y "build-log.txt" "outputs\v%VER%\build-log-v%VER%.txt" >nul
echo ===== XONG. APK: outputs\v%VER%\ChiaBill-v%VER%-debug.apk =====
start "" explorer "%~dp0outputs\v%VER%"
goto :done

:fail
echo ===== BUILD LOI. Nhan Claude doc build-log.txt =====
goto :done

:nojava
echo.
echo ===== KHONG TIM THAY JAVA =====
echo Cai JDK 17 (vd. Microsoft Build of OpenJDK 17) hoac Android Studio,
echo hoac chay 1 lan trong cmd:  setx JAVA_HOME "C:\duong\dan\toi\jdk-17"
echo roi mo cua so moi va chay lai build-apk.bat.

:done
popd
subst %DRV% /d
pause
exit /b

:findjava
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" exit /b 0
set "JAVA_HOME="
for /d %%D in ("C:\Program Files\Microsoft\jdk-17*" "C:\Program Files\Eclipse Adoptium\jdk-17*" "C:\Program Files\Java\jdk-17*" "C:\Program Files\Microsoft\jdk-21*" "C:\Program Files\Eclipse Adoptium\jdk-21*" "C:\Program Files\Java\jdk-21*") do (
  if exist "%%~D\bin\java.exe" set "JAVA_HOME=%%~D"
)
if defined JAVA_HOME exit /b 0
for %%D in ("C:\Program Files\Android\Android Studio\jbr" "%LOCALAPPDATA%\Programs\Android Studio\jbr" "D:\Android Studio\jbr" "D:\Android Stuido\jbr") do (
  if not defined JAVA_HOME if exist "%%~D\bin\java.exe" set "JAVA_HOME=%%~D"
)
if defined JAVA_HOME exit /b 0
for /d %%D in ("D:\Android Stuido\*" "D:\Android Studio\*") do (
  if not defined JAVA_HOME if exist "%%~D\jbr\bin\java.exe" set "JAVA_HOME=%%~D\jbr"
)
exit /b 0
