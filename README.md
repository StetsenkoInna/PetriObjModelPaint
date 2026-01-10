# PetriObjModelPaint

A Java-based Petri Net modeling and simulation tool with graphical interface supporting PNML (Petri Net Markup Language) import/export functionality according to ISO/IEC 15909 standard.
Notice: AbsoluteLayout.jar library should be added to the project if not NetBeans IDE is used.

PetriObjModelPaint is the project of Petri-object simulation technique implementation. Petri-object simulation technique, the main concept of which is to compose the code of model of complicated discrete event system in a fast and flexible way, simultaneously providing fast running the simulation, is requisite. The behaviour description of the model based on stochastic multichannel Petri net while the model composition is grounded on object-oriented technology. The Petri-object simulation software provides scalable simulation algorithm, graphical editor, correct transformation graphical images into model, correct simulation results. Graphical editor helps to cope with error-prone process of linking elements with each other. Petri-object simulation software is developed using Java language. It consists of package PetriObjLib implementing Petri-object simulation algorithm and the packages providing graphical presentation of nets. The graphical editor helps to build Petri net, save it as a Java method and add method to the NetLibrary class. The opening net from file or reproducing net from Java method are supported by the software. In addition, animation of simulation is provided to check the rightness of created Petri net. It should be noted, exception will be generated if Petri net consists a transition which has not input or output places. After successful saving, the method can be used to create Petri-objects. When the list of Petri-objects is prepared and the links between objects are determined, the model can be created using PetriObjModel class. The method go(double time) of this class run the simulation. Exception will be generated if a time delay generator will produce a negative value. Software main responsibilities are to provide correct simulation algorithm and correct simulation results including mean values of markers in Petri net places, mean value of buffers in transitions and the state of Petri net in the last moment of simulation.


## Features

- **Visual Petri Net Editor**: Create and edit Petri nets with intuitive drag-and-drop interface
- **PNML Support**: Import and export Petri nets in standard PNML format (ISO/IEC 15909)
- **Statistics Module**: Advanced simulation statistics and charting capabilities
- **Simulation**: Run and animate Petri net simulations
- **Net Library**: Built-in library of common Petri net patterns
- **Extensible**: Support for custom net components and annotations

## Technology Stack

### Core Technologies
- **Java**: 23
- **Maven**: 3.6+
- **JavaFX**: 25.0.1

## Quick Start

### Prerequisites
- **Java 23 or higher** - [Download Java](https://www.oracle.com/java/technologies/downloads/)
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
   java -jar target/PetriObjModelPaint-1.2.1.jar
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

### Element IDs
All Petri net elements (places, transitions, arcs) are assigned unique, human-readable IDs:
- **Format:** `{type}-{name}-{uuid}` (e.g., `p-buffer-a3f2b8c1`, `t-process-5d8f3e2a`)
- **Auto-generated:** IDs are automatically created when elements are added
- **Preserved:** Existing IDs are maintained when importing PNML files
- **Type-safe:** IDs are wrapped in `PetriElementId` class for compile-time validation

## Project Structure

```
PetriObjModelPaint/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── ua/stetsenkoinna/
│   │   │       ├── PetriObj/          # Core Petri net objects
│   │   │       ├── graphnet/          # Graphical net components
│   │   │       ├── graphpresentation/ # UI components
│   │   │       │   └── statistic/     # Statistics and charting module
│   │   │       ├── libnetannotation/  # Annotation processing
│   │   │       ├── LibNet/            # Net library
│   │   │       ├── config/            # Configuration classes
│   │   │       └── pnml/              # PNML import/export
│   │   └── resources/                 # Application resources
│   └── test/                          # Test sources
├── target/                            # Build output
├── pom.xml                            # Maven configuration
├── run.bat                            # Windows launch script
├── run.sh                             # Unix launch script
└── README.md                          # This file
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
- Ensure Java 23+ is installed and in your PATH
- Check that the JAR file was built successfully in `target/`
- Try running with: `java -jar target/PetriObjModelPaint-1.2.1.jar`

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
  java -Xmx2g -jar target/PetriObjModelPaint-1.2.1.jar
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
