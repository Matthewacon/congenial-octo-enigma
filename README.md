# congenial-octo-enigma
> A simple set of classes that can be used to convert player files from one version of MC to another. Particularly useful for modded worlds. 

### Requirements
To run congenial-octo-enigma, all you need is Java 8

### Running
To convert over a world, you need:
1. The `item.csv` and `block.csv` dumps from NEI for your original pack.
2. The `item.csv` and `block.csv` dumps from NEI for your updated pack.
3. The NBT player files from your `world/playerdata` folder.

Run `java -jar congenial-octo-enigma-1.0.jar --help` for a list of arguments to see what goes where

### Building
Simply run: `mvn package` to build a runnable jar

### License
The project is licensed under the [M.I.T. License](https://github.com/Matthewacon/congenial-octo-enigma/blob/master/LICENSE), do whatever you want :)

### Contributing
My code is pretty messy and mostly undocumented so if you want to contribute to this project, make sure your pull request has a concise description with your changes and comment all of your code. If you're cleaning up the project, then comment my code, or at least the code that you change and give reasons for your additions / deletions.