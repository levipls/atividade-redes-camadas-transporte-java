@echo off
setlocal
where mvn >nul 2>nul
if not errorlevel 1 (
  call mvn %*
  exit /b %errorlevel%
)
set "MAVEN_CMD="
for /d %%H in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16-bin\*") do (
  if exist "%%H\apache-maven-3.9.16\bin\mvn.cmd" set "MAVEN_CMD=%%H\apache-maven-3.9.16\bin\mvn.cmd"
)
if defined MAVEN_CMD (
  call "%MAVEN_CMD%" %*
  exit /b %errorlevel%
)
echo Apache Maven 3.9 ou superior nao foi encontrado.
echo Instale Maven e adicione mvn ao PATH; depois execute este comando novamente.
exit /b 1
