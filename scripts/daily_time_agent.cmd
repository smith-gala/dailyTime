@echo off
setlocal
set "DAILY_TIME_PROJECT=D:\yyyPlot\video\dailyTime"
set "DAILY_TIME_PYTHON_EXE=D:\anaconda\anaconda_envs\my_video\python.exe"

if not exist "%DAILY_TIME_PYTHON_EXE%" set "DAILY_TIME_PYTHON_EXE=python"

cd /d "%DAILY_TIME_PROJECT%"
"%DAILY_TIME_PYTHON_EXE%" "%DAILY_TIME_PROJECT%\scripts\daily_time_agent.py" %*
exit /b %ERRORLEVEL%
