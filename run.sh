#!/bin/bash
echo "Starting PetriObjModelPaint application..."
java -jar "petri-obj-paint/target/petri-obj-paint-1.2.0.jar"

if [ $? -ne 0 ]; then
    echo
    echo "Error: Application failed to start. Make sure Java is installed and the JAR file exists."
    read -p "Press any key to exit..."
fi