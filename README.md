# PetriObjModelPaint

A Java-based Petri Net modeling and simulation tool with graphical interface supporting PNML (Petri Net Markup Language) import/export functionality according to ISO/IEC 15909 standard.

## Features

- **Visual Petri Net Editor**: Create and edit Petri nets with intuitive drag-and-drop interface
- **PNML Support**: Import and export Petri nets in standard PNML format (ISO/IEC 15909)
- **Simulation**: Run and animate Petri net simulations
- **Net Library**: Built-in library of common Petri net patterns
- **Extensible**: Support for custom net components and annotations

## Quick Start

### Prerequisites

- **Java 8 or higher** - [Download Java](https://www.oracle.com/java/technologies/downloads/)
- **Maven 3.6 or higher** - [Download Maven](https://maven.apache.org/download.cgi)

To check if you have the required software installed:
```bash
java -version
mvn -version
```

### Running the Application

#### Option 1: Build and Run (Recommended)

1. **Clone the repository:**
   ```bash
   git clone https://github.com/StetsenkoInna/PetriObjModelPaint.git
   cd PetriObjModelPaint
   ```

2. **Build the project:**
   ```bash
   mvn clean package -DskipTests
   ```

3. **Run the application:**
   ```bash
   java -jar petri-obj-paint/target/petri-obj-paint-1.1.jar
   ```

#### Option 2: Use Convenience Scripts

After building the project, you can use the provided scripts:

- **Windows:**
  ```bash
  run.bat
  ```

- **Linux/macOS:**
  ```bash
  ./run.sh
  ```

#### Option 3: Maven Exec Plugin

```bash
cd petri-obj-paint
mvn exec:java -Dexec.mainClass="ua.stetsenkoinna.graphpresentation.PetriNetsFrame"
```

## PNML Import/Export

The application supports importing and exporting Petri nets in PNML format:

### Import PNML
- **Menu:** File → Import PNML (Ctrl+I)
- **Supported elements:** Places, transitions, arcs with weights, initial markings
- **Extensions:** Time delays, priorities, probabilities via toolspecific elements

### Export PNML
- **Menu:** Save → Export to PNML (Ctrl+P)
- **Format:** ISO/IEC 15909 compliant PNML files
- **Preservation:** All Petri net properties and custom attributes

## Project Structure

```
PetriObjModelPaint/
├── petri-obj-paint/          # Main application module
│   ├── src/main/java/
│   │   ├── ua/stetsenkoinna/
│   │   │   ├── PetriObj/      # Core Petri net objects
│   │   │   ├── graphnet/      # Graphical net components
│   │   │   ├── graphpresentation/ # UI components
│   │   │   └── pnml/          # PNML import/export
│   │   └── resources/
│   └── target/                # Build output
├── lib-net-annotations/       # Annotation processing
├── run.bat                    # Windows launch script
├── run.sh                     # Unix launch script
└── README.md                  # This file
```

## Building from Source

### Development Build
```bash
mvn clean compile
```

### Create Distribution JAR
```bash
mvn clean package -DskipTests
```

### Run Tests
```bash
mvn test
```

## Troubleshooting

### Application Won't Start
- Ensure Java 8+ is installed and in your PATH
- Check that the JAR file was built successfully in `petri-obj-paint/target/`
- Try running with: `java -jar petri-obj-paint/target/petri-obj-paint-1.1.jar`

### Build Failures
- Ensure Maven 3.6+ is installed
- Check internet connection (Maven downloads dependencies)
- Try: `mvn clean install -U -DskipTests`

### PNML Import Issues
- Verify your PNML file follows ISO/IEC 15909 standard
- Check console output for detailed error messages
- Ensure XML is well-formed and uses correct namespace

### Performance Issues
- For large nets, consider increasing Java heap size:
  ```bash
  java -Xmx2g -jar petri-obj-paint/target/petri-obj-paint-1.1.jar
  ```

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature-name`
3. Make your changes and test thoroughly
4. Commit your changes: `git commit -am 'Add feature'`
5. Push to the branch: `git push origin feature-name`
6. Submit a pull request

## License

This project is licensed under the terms specified in the LICENSE file.

## Support

For bug reports and feature requests, please create an issue in the project repository.