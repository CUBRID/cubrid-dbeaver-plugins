# cubrid-dbeaver-external

## Overview
This project provides an external plugin package for **DBeaver** that allows users to install additional **CUBRID-related tools**.

The plugin extends DBeaver with features designed specifically for working with **CUBRID databases**, improving database management and documentation workflows.

---

## Description
The plugin package contains several **sub-modules that extend DBeaver functionality**.

During installation, users can:

- Install **all available modules**
- Select **specific features** they want to enable

After installation is completed, the selected features will be **integrated directly into the DBeaver UI**.

---

## Included Features

- **Export Table Definitions to Excel**  
  Generate Excel documentation for CUBRID tables, including table list, column definitions, keys, indexes, and table DDL.

- **Export LoadDB**  
  Export table data in **LoadDB format** for easy data loading into CUBRID databases.

- **Import Connections**  
  Import existing database connections from **CUBRID Manager** and **CUBRID Admin**.

## Installation

### Install from Update Site
1. Open **DBeaver**
2. Go to **Help → Install New Software**
3. Add the plugin update site https://cam.buabu.duckdns.org/cam/
4. Select the desired features
5. Click **Install**

Users can choose to install:

- Individual modules
- All available CUBRID tools

---

## Requirements

- DBeaver Community Edition
- CUBRID database
