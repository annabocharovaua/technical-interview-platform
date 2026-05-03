@echo off
REM Load .env file if it exists
if exist .env (
    echo Loading environment variables from .env...
    for /f "delims== tokens=1,2" %%G in (.env) do (
        if not "%%G"=="" (
            if not "%%G:~0,1%%" == "#" (
                set %%G=%%H
            )
        )
    )
) else (
    echo .env file not found!
    echo Please create it: copy .env.example .env
    exit /b 1
)

REM Verify critical variables
if "%OPENAI_API_KEY%"=="" (
    echo OPENAI_API_KEY not set!
    exit /b 1
)

if "%MYSQL_PASSWORD%"=="" (
    echo MYSQL_PASSWORD not set!
    exit /b 1
)

echo Environment variables loaded successfully
echo Starting Spring Boot application...

REM Run Maven with environment variables
mvn clean spring-boot:run
