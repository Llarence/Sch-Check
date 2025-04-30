# An osu schedule generator.

## How it works

The user inputs a selection of class groups which are lists of search criteria for classes.
The scheduler then does all the searches input to the system.
Then the scheduler generates many schedules using one class (or none) from each class group.
After that the scheduler takes those schedules and sorts them by user given criteria.

## How to use

1. Download the jar file from the releases
2. Run the jar file (it will create a ```saves``` directory in the folder)
3. It will first prompt you for the term
4. Then it will open a menu with
   1. The top bar with back and next buttons as well as a name field and save/load.
If you type in the name field the save and load buttons will save/load your current selection.
   2. Then there is a row of Class Group which holds each group of classes
   3. Below that are the searches in each class group.
   4. Finally, there is the actual search which just uses the OSU default search
5. After saving your schedule goal the next page has the criteria used to evaluate which schedules are good.
A schedules value is equal to the sum of:
   1. ```Number Of Credits * Credit Value```
   2. ```Number of Adajcent Class (Classes that start/end within 15 minutes) * Adjacent Value```
   3. ```For every class pick either the start or end which every is closest to Target Time then multiply the minutes between the start/end and Target Time by Minute Distance Value```
6. When you hit next it will download the classes and generate the schedules.
It will be faster subsequent times because it caches the network requests for an hour.
7. From there you can view different schedules as well as save and load them.

## How to build/modify
This is an Intellij Kotlin project and is kind of a mess.
It is also a JavaFx project with extensive kotlin coroutines.
If you want to add to it the rough layout is main defines the whole project and the different pages have different sections of the UI that run according to the rules in main.
Requests handles actually requesting to the OSU backend and Responses has a collection of data classes that partially represent the response you can get from requests.
Cache contains code that writes to teh cache.
Schedule contains the code that does the schedule generation.

JavaFX has been removed from java for a while so to build it you need to use jlink this has made the project very messy, but it does currently build.
Just try to clean/build before running jlink. If you want to run main directly through intellij you must move module-info.java to kotlin and clean before running.
AFAIK this is a problem with intellij and there is a current issue about it.
