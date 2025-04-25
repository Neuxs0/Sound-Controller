# Sound Controller
Sound Controller is a mod to allow you to change the volume of any sound you want in the game.

## Dependencies:
- Puzzle Loader any version or Cosmic Quilt any version
- Cosmic Reach Alpha v0.4.7

## How to use
- Run the mod as any other Quilt/Puzzle mod
- Open the Options Menu and you should see a new button on the bottom left with the text "Sound Controller".
- Click that to open the configuration menu.
- Press the back button in the top left, or press Esc, or Press the back button on your mouse, to go back to the Options Menu.

## How to build
Requirements:
- JDK 17 (You can install it [here](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html).)
- python 3 (You can install it [here](https://www.python.org/downloads/).)

1. (Optional) Configure `build_config.json`
2. Run `python3 build.py` and wait for the build script to finish
3. The jar will be in the newly generated `dist/` folder

## Save location
You can manually configure the save file, named `sound_controller.json`, in the config folder in your Cosmic Reach folder.
Deleting this file or its data will result in a data loss of your sound configurations.