# CUBRID DBEAVER EXTERNAL

## Overview

This project provides an external plugin package for **DBeaver** that adds specialized features for working with **CUBRID databases**. The plugin extends DBeaver's functionality to improve your database management and documentation workflows. 

During installation, you can choose to install all available modules or select only the specific features you need. Once installed, these tools are integrated directly into the DBeaver UI.

---

## Included Features

- **Export Table Definitions to Excel**  
  Generate Excel documentation for CUBRID tables, including the table list, column definitions, keys, indexes, and table DDL.

- **Export LoadDB**  
  Export table data in **LoadDB format** for easy data loading into CUBRID databases.

- **Import Connections**  
  Import existing database connections from **CUBRID Manager** and **CUBRID Admin**.

---

## Requirements

- **DBeaver Community Edition**
- **CUBRID database**

---

## Installation

### Install from Update Site
1. Open **DBeaver**.
2. Go to **Help → Install New Software...**.
3. Click **Add...** and enter the plugin update site URL: `https://cam.buabu.duckdns.org/cam/`
4. Select the desired CUBRID features from the list.
5. Click **Next** and follow the prompts to complete the installation.

---

## Build Instructions

To build the plugin and generate an update site for distribution:

### 1. Build Requirements
- Java 21
- Maven 3.8+

### 2. Build Process
The project includes a build.sh script that automates the Maven build and organizes the artifacts.

**Execute the build**: ./build.sh

### 3. Artifact Folder
Once the script completes, a new directory named p2-site-plugin/ will be created in the root folder.
This folder contains the generated P2 repository (Update Site).

**Usage**: This directory can be used directly in DBeaver via Install New Software -> Add -> Local to test the plugin.

### 4. Hosting and Distribution
- **Upload** the artifact folder to the web server.
- Users can then install the plugin in DBeaver using the steps in the **Installation** section, replacing the URL with the newly hosted URL.
