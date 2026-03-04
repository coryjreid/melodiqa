# Melodiqa

Stream the music being played on your computer to your friends in Discord.

## Usage

### Running

1. Extract the zip file to your desired location
2. Copy the contents of `src/test/resources/melodiqa.conf` to an accessible location \
   Note: It is recommended to place this file next to the `release` file in your extracted zip
3. Fill in the values appropriately in your configuration file \
   Example:
    ```text
    melodiqa {
        discordBotToken = "MTMz00000000000000000000000000000000000000000000000000000000000000000000"
        audioDeviceName = "VBMatrix Out 7 (VB-Audio Matrix VAIO)"
        guildId = 0000000000000000000
        voiceChannelId = 0000000000000000000
    }
    ```
4. Invoke the start script provided in `bin/` for your platform from the root of your extracted folder

### Stopping

1. Write an empty file to `C:\Users\USERNAME\AppData\Roaming\Aezshma\Melodiqa\.stop`

### Tips

- Create a unique configuration file for each channel/guild you want to use the bot in and pass the correct
  configuration file on invocation

### Stream Deck

Here's an example invoking the bot from a Stream Deck action:

`cmd.exe /c "start /min cmd.exe /c ""C:\Streaming\Software\Melodiqa\bin\melodiqa.bat -f C:\Streaming\Software\Melodiqa\melodiqa.conf"""`

This results in the bot starting with the configuration file at `C:\Streaming\Software\Melodiqa\melodiqa.conf` and
running inside a minimized `cmd.exe` instance allowing you to see the console output for debugging.

## Developer Notes

### Distribution

1. Run Gradle task `runtimeZip`
2. Navigate to `build/image/`
3. Use the `melodiqa-VERSION/` or the `melodiqa-VERSION.zip` for distribution