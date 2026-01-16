<p align="center">
  <img width="100%" src="https://github.com/user-attachments/assets/dedb6651-cc26-4244-86d9-1add93288115" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-orange?style=for-the-badge&logo=openjdk" />
  <img src="https://img.shields.io/badge/JavaFX-26--ea+19-blue?style=for-the-badge&logo=java" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Build-Maven-green?style=for-the-badge&logo=apache-maven" />
</p>

## 🎯 Overview

Automated desktop solution for school scheduling. This application transforms the error-prone manual task of time-slot
assignment into an optimized process using high-performance constraint solvers.

> [!IMPORTANT]
> **Core Engine:** The optimization logic (CP-SAT) is hosted in a private repository.
> Contact [dhl26052004@gmail.com](mailto:dhl26052004@gmail.com) for access to the `scheduler.engine.jar`.

## 🚀 Key Features

* **Smart Resource Management:** CRUD for Teachers, Classes, and Subjects with SQLite persistence.
* **Constraint Configuration:** Visual "Busy Matrix" for teachers and customizable teaching loads.
* **Automated Solver:** Deep integration with **Google OR-Tools** to solve NP-hard scheduling problems.
* **Modern UI:** Responsive desktop experience built with JavaFX and FXML.

## 🛠️ Tech Stack

* **Language:** Java 25
* **GUI:** JavaFX 26
* **Database:** SQLite
* **Optimization:** Google OR-Tools (CP-SAT Solver)

## 🏗️ Architecture

Decoupled **MVC** (Model-View-Controller) for clear separation of UI and logic.

<details>
  <summary>Click to see diagram</summary>

  ```mermaid
  graph TD
      subgraph Public_Application_Core [Application Core<br/>Open Source]
          direction TB
          DB[(SQLite Database)]
          
          subgraph MVC_Pattern [MVC Architecture]
              M[Models<br/>Teacher, Class, Subject]
              V[View: JavaFX FXML Screens]
              C[Controller<br/>ScheduleGeneratorController]
          end
          
          API[Optimization Interface]
      end
  
      subgraph Private_Engine [Private Engine - Proprietary]
          SOLVER[Google OR-Tools<br/>CP-SAT]
          LOGIC[Constraint Logic & Rules]
      end
  
      %% Workflow Flowchart
      DB <--> M
      M <--> C
      V <--> C
      
      C -->|1. Request Solve| API
      API -->|2. Data Transfer Object| LOGIC
      LOGIC -->|3. Define Constraints| SOLVER
      SOLVER -->|4. Return Optimal Solution| API
      API -->|5. Update Model| M
      M -->|6. Refresh UI| V
  ```

</details>

## 🗺️ Roadmap

- [x] Base MVC Architecture
- [x] Google OR-Tools Integration (CP-SAT)
- [ ] Export schedule to Excel/PDF
- [ ] Multi-language support (Vietnamese/English)
- [ ] Interactive schedule view where admin can drag and drop the slots

<details>
  <summary><h2>📸 Screenshots</h2></summary>

  <p align="center">
    <span>
      <img width="85%" src="https://github.com/user-attachments/assets/0f1cc990-abb9-4a42-a584-4336eaa81255" />
      <br>
      <em>Fig 1. Teacher general information and busy matrix configuration.</em>
    </span>
  </p>

  <br>

  <p align="center">
    <span>
      <img width="85%" src="https://github.com/user-attachments/assets/b33603b2-b32e-4c3f-9ca8-02e5d219bb30" />
      <br>
      <em>Fig 2. Class management organized by Grade.</em>
    </span>
  </p>

  <br>

  <p align="center">
    <span>
      <img width="85%" src="https://github.com/user-attachments/assets/67275d26-e5f6-4a14-9000-58b05b3f23d9" />
      <br>
      <em>Fig 3. Scheduling engine solving complex constraints using CP-SAT.</em>
    </span>
  </p>

  <br>

  <p align="center">
    <span>
      <img width="85%" src="https://github.com/user-attachments/assets/9d8f762d-a401-4596-99f2-146ba9aec199" />
      <br>
      <em>Fig 4. Final generated schedule view.</em>
    </span>
  </p>

</details>
