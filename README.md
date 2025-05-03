 OSU Schedule Generator

## Overview

The OSU Schedule Generator helps users create customized schedules by selecting and combining different class groups based on specific criteria. It fetches class data from Oregon State University’s (OSU) backend and uses it to generate multiple schedule options. These options are then evaluated based on user-defined factors to help you choose the best fit.

## How It Works

1. **Input Class Groups**: You provide a set of class groups. Each class group is a list of search criteria for classes you want to include in your schedule. For example, you might have a class group that contains a search for CS361, CS381 and CS374 which would tell the scheduler that you want one of these 3 in your schedule. Then you may also have a group for bacc core classes saying you want one of the those in your schedule. This is kind of like [CNF](https://en.wikipedia.org/wiki/Conjunctive_normal_form).
2. **Search and Generate**: The scheduler searches for classes based on your input and generates several possible schedules by picking one class (or none) from each class group.
3. **Sort by Criteria**: The generated schedules are then sorted according to user-defined evaluation criteria.

## How to Use

### 1. Download and Run

- Download the `.zip` for your OS from the releases page.
- Unzip the file and run either `app` for Linux/Mac or `app.bat` file. A `saves` directory will be created in the folder where the app is run. There is a cache in there and to clear it just delete the file.

### 2. Set Up Your Schedule

- **Term Selection**: The scheduler will first prompt you to select the academic term.
- **Main Menu**: Once you’ve selected the term, the app will display a menu with:
   - **Navigation Bar**: Includes back/next buttons and a name field for saving/loading.
   - **Class Groups**: Displays your class groups, where you can add more class groups.
   - **Searches**: Below each class group, you can add as many searches as you like to each class group.
   - **Search Options**: And finally there are the actually options to put in the selected search. This is just a copy of the class search page on the [OSU website](https://prodapps.isadm.oregonstate.edu/StudentRegistrationSsb/ssb/registration).

### 3. Set Evaluation Criteria

- After saving your schedule preferences, the app will show a set of criteria used to evaluate different schedules. The evaluation formula is based on the sum of the following:
   1. **Credits**: `Number of Credits * Credit Value`
   2. **Adjacency**: `Number of Adjacent Classes * Adjacent Value`
   3. **Target Time** and **Minute Distance Value**: `For each class: Minutes from Target Time * Minute Distance Value` (Essentially tries to group classes around a time)
- You can save and load these settings to/from the `saves` directory for future use using text field and save load buttons at the top.

### 4. Generate and View Schedules

- After selecting your criteria, click "Next" to download the class data and generate schedules.
- The scheduler will cache class data for faster future searches (caching lasts for 1 hour).
- You can save and load schedules to/from the `saves` directory for future use using the text field and save load buttons at the top.
- You cannot modify the schedules if it looks like you can.

## How to Build/Modify

This project is built with **IntelliJ IDEA** using **Kotlin** and **JavaFX**. If you're looking to build or modify the project, here's a general overview:

- **Main Class**: The `main` function handles the overall flow of the application, with different pages managing sections of the UI.
- **Requests**: Contains code to interact with OSU’s backend and fetch class data.
- **Responses**: Includes data classes that partially represent the responses from OSU’s backend.
- **Cache**: Handles the local caching of class data to improve performance.
- **Schedule**: Manages the logic for generating and evaluating different schedules.

### Build Instructions

- Since **JavaFX** was removed from Java, you’ll need to use **JLink** to build the project. This process can be a bit messy, but it currently works.
   - Make sure to **clean** before running `jlink`.
   - If you want to run `main` directly in IntelliJ, you must move `module-info.java` to `src/kotlin`, and clean the project before running. (My understanding is that this is a bug/quirk with Intellij)
   - The CI is able to build it so that may provide inspiration
