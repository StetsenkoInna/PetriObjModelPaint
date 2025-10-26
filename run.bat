@echo off
echo Starting PetriObjModelPaint application...
java -jar "target\PetriObjModelPaint-1.2.0.jar"
if %ERRORLEVEL% neq 0 (
    echo.
    echo Error: Application failed to start. Make sure Java is installed and the JAR file exists.
    echo Press any key to exit...
    pause >nul
)