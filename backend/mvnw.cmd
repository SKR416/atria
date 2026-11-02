@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM Pure ASCII compliant for Windows CP950 environment
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set WRAPPER_DIR=%~dp0
set WRAPPER_PROPS=%WRAPPER_DIR%.mvn\wrapper\maven-wrapper.properties

for /f "tokens=1,2 delims==" %%A in ("%WRAPPER_PROPS%") do (
    if "%%A"=="distributionUrl" set DISTRIBUTION_URL=%%B
)
for /f "usebackq tokens=1,* delims==" %%A in ("%WRAPPER_PROPS%") do (
    if "%%A"=="distributionUrl" set DISTRIBUTION_URL=%%B
)

set DISTRIBUTION_URL=%DISTRIBUTION_URL:\=%
for %%F in ("%DISTRIBUTION_URL%") do set ZIP_NAME=%%~nxF
for %%F in ("%ZIP_NAME:~0,-8%") do set DIST_DIR_NAME=%%~nxF

set MAVEN_USER_HOME=%USERPROFILE%\.m2
set M2_DIST_DIR=%MAVEN_USER_HOME%\wrapper\dists\%DIST_DIR_NAME%
set MVN_CMD=%M2_DIST_DIR%\%DIST_DIR_NAME%\bin\mvn.cmd

if exist "%MVN_CMD%" goto runMaven

echo Downloading Apache Maven from %DISTRIBUTION_URL% ...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference = 'Stop'; ^
    $distDir = '%M2_DIST_DIR%'; ^
    New-Item -ItemType Directory -Force -Path $distDir | Out-Null; ^
    $zipFile = Join-Path $distDir '%ZIP_NAME%'; ^
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; ^
    Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile $zipFile; ^
    Expand-Archive -Path $zipFile -DestinationPath $distDir -Force; ^
    Remove-Item -Path $zipFile -Force"
if errorlevel 1 (
    echo Failed to download or extract Apache Maven.
    exit /b 1
)

:runMaven
call "%MVN_CMD%" %*
exit /b %ERRORLEVEL%
