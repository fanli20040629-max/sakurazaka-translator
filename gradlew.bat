@echo off
setlocal
set DIRNAME=%~dp0
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%DIRNAME%.gradle-home"
if "%JAVA_HOME%"=="" set "JAVA_HOME=D:\android_stufio\android-studio\jbr"
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA_EXE%" (
  echo JAVA_HOME does not point to a valid Java installation: %JAVA_HOME%
  exit /b 1
)
set "CLASSPATH=%DIRNAME%gradle\wrapper\gradle-wrapper.jar"
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%~n0" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
