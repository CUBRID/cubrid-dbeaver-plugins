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
- Eclipse IDE
- Java 21
- Maven 3.8+
- DBeaver plugin dependencies

### 2. Create an Update Site Project
- In Eclipse, go to **File → New → Project... → Plug-in Development → Update Site Project...**.
- Give your project a name (e.g., `cubrid-dbeaver-update-site`) and click **Finish**.

### 3. Configure the Update Site
- The project will generate and open a `site.xml` file in the Update Site Map editor.
- In the **Site Map** tab, click **Add Feature...** and select the CUBRID features you want to include (e.g., `org.cubrid.dbeaver.export.excel.feature`, `org.cubrid.dbeaver.export.loaddb.feature`).

### 4. Build the Site
- Save the `site.xml` file.
- Click **Build All** on the right side of the `site.xml` editor (Site Map tab).
- This process generates the distribution files in the Update Site project folder:
  - `content.jar`
  - `artifacts.jar`
  - `features/` (directory)
  - `plugins/` (directory)
  - `site.xml`

### 5. Hosting and Distribution
- **Upload** the generated folder (containing the files mentioned above) to the web server.
- Users can then install the plugin in DBeaver using the steps in the **Installation** section, replacing the URL with the newly hosted URL.
