# Scheduler Standalone

## Introduction

This project is a standalone desktop application for generating and managing schedules. It is designed to help
educational institutions automate the complex process of assigning teachers to classes, subjects, and time slots, while
respecting various constraints.

## Problem

Manual scheduling for schools and universities is a time-consuming and error-prone process. It involves juggling teacher
availability, classroom capacity, subject requirements, and student preferences. This often leads to conflicts,
inefficient resource allocation, and a great deal of stress for administrators.

## Solution

This application provides an automated solution to the scheduling problem. It uses a constraint-based approach to
generate optimal schedules that satisfy a wide range of predefined and customizable constraints. The application offers
a user-friendly interface for configuring constraints, managing resources (teachers, classes, subjects), and viewing the
generated schedules.

## Tech Stack

* **Java**: The core programming language.
* **JavaFX**: Used for building the graphical user interface.
* **Maven**: For project management and dependency handling.
* **Google OR-Tools**: A software suite for solving combinatorial optimization problems, used here for the scheduling
  engine.
* **SQLite**: For storing and managing application data.

## System Architecture

The application is built using a Model-View-Controller (MVC) architecture:

* **Model**: Represents the application's data and business logic (e.g., `Teacher`, `Grade`, `Clazz` (class)).
* **View**: The user interface, created with JavaFX and FXML (`.fxml` files).
* **Controller**: Manages user input and updates the model and view (e.g., `ScheduleGeneratorController`,
  `ScheduleController`).
* **Engine**: The core scheduling component, which leverages Google OR-Tools to solve the complex constraint
  satisfaction problem.

## Screenshots

### Teacher's general information screen

<img width="1919" height="1024" alt="image" src="https://github.com/user-attachments/assets/0f1cc990-abb9-4a42-a584-4336eaa81255" />

### Class management (by Grade)

<img width="1919" height="1027" alt="image" src="https://github.com/user-attachments/assets/b33603b2-b32e-4c3f-9ca8-02e5d219bb30" />

### Teacher assignment page

<img width="1919" height="1029" alt="image" src="https://github.com/user-attachments/assets/efd6efdb-2144-44b8-a180-9fd251e7cbec" />

### Engine (Google OR-Tools) is solving CP-SAT problem

<img width="1919" height="1026" alt="image" src="https://github.com/user-attachments/assets/67275d26-e5f6-4a14-9000-58b05b3f23d9" />

### Result

<img width="1919" height="1027" alt="image" src="https://github.com/user-attachments/assets/9d8f762d-a401-4596-99f2-146ba9aec199" />

